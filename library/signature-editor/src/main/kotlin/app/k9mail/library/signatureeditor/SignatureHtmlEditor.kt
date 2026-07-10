package app.k9mail.library.signatureeditor

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlined
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Isolated WYSIWYG signature editor.
 *
 * Supports only formatting that modern Outlook (Windows), Gmail, and Apple Mail
 * reliably render. Images are inlined as compressed PNG/JPEG data URIs (no remote
 * hosting UI). Navigation and network loads are blocked inside the editor WebView.
 */
@Suppress("LongMethod")
@Composable
fun SignatureHtmlEditor(
    html: String,
    onHtmlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.signature_editor_label),
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("https://") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dataUri = uri.toInlineImageDataUri(context) ?: return@rememberLauncherForActivityResult
        webView?.evaluateJavascript(
            "window.SignatureEditor.insertImage(${dataUri.toJsString()});",
            null,
        )
    }

    Column(modifier = modifier.fillMaxWidth().testTag("signature_html_editor")) {
        TextBodySmall(
            text = label,
            modifier = Modifier.padding(horizontal = BoltTheme.spacings.double),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BoltTheme.spacings.double),
        ) {
            Column {
                FormattingToolbar(
                    onBold = { webView?.evaluateJavascript("document.execCommand('bold');", null) },
                    onItalic = { webView?.evaluateJavascript("document.execCommand('italic');", null) },
                    onUnderline = { webView?.evaluateJavascript("document.execCommand('underline');", null) },
                    onInsertLink = { showLinkDialog = true },
                    onInsertImage = {
                        imagePicker.launch(arrayOf("image/png", "image/jpeg"))
                    },
                )

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(EDITOR_HEIGHT_DP.dp)
                        .testTag("signature_html_editor_webview"),
                    factory = { ctx ->
                        createSignatureEditorWebView(
                            context = ctx,
                            initialHtml = html,
                            onHtmlChange = onHtmlChange,
                        ).also { created ->
                            created.contentDescription = "signature_html_editor_webview"
                            webView = created
                        }
                    },
                    update = { view ->
                        webView = view
                    },
                )
            }
        }
    }

    if (showLinkDialog) {
        AlertDialog(
            title = stringResource(R.string.signature_editor_link_label),
            confirmText = stringResource(R.string.signature_editor_save),
            dismissText = stringResource(android.R.string.cancel),
            onConfirmClick = {
                val safeUrl = linkUrl.trim()
                if (safeUrl.startsWith("https://") ||
                    safeUrl.startsWith("http://") ||
                    safeUrl.startsWith("mailto:")
                ) {
                    webView?.evaluateJavascript(
                        "window.SignatureEditor.insertLink(${safeUrl.toJsString()});",
                        null,
                    )
                }
                showLinkDialog = false
                linkUrl = "https://"
            },
            onDismissClick = {
                showLinkDialog = false
                linkUrl = "https://"
            },
            onDismissRequest = {
                showLinkDialog = false
                linkUrl = "https://"
            },
            modifier = Modifier.testTag("signature_editor_link_dialog"),
        ) {
            TextFieldOutlined(
                value = linkUrl,
                onValueChange = { linkUrl = it },
                label = stringResource(R.string.signature_editor_link_label),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.evaluateJavascript("window.SignatureEditor && window.SignatureEditor.flush();", null)
            webView?.destroy()
            webView = null
        }
    }
}

@Composable
private fun FormattingToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onInsertLink: () -> Unit,
    onInsertImage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = BoltTheme.spacings.half),
    ) {
        ButtonText(
            text = "B",
            onClick = onBold,
            modifier = Modifier.testTag("signature_editor_bold"),
        )
        ButtonText(
            text = "I",
            onClick = onItalic,
            modifier = Modifier.testTag("signature_editor_italic"),
        )
        ButtonText(
            text = "U",
            onClick = onUnderline,
            modifier = Modifier.testTag("signature_editor_underline"),
        )
        ButtonIcon(
            onClick = onInsertLink,
            imageVector = Icons.Outlined.OpenInNew,
            modifier = Modifier.testTag("signature_editor_link"),
        )
        ButtonIcon(
            onClick = onInsertImage,
            imageVector = Icons.Outlined.Image,
            modifier = Modifier.testTag("signature_editor_image"),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createSignatureEditorWebView(
    context: android.content.Context,
    initialHtml: String,
    onHtmlChange: (String) -> Unit,
): WebView {
    return SignatureEditorWebViewFactory.create(
        context = context,
        initialHtml = initialHtml,
        onHtmlChange = onHtmlChange,
    ).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
}

private fun android.net.Uri.toInlineImageDataUri(context: android.content.Context): String? {
    return try {
        context.contentResolver.openInputStream(this)?.use { input ->
            val bytes = input.readBytes()
            val mime = context.contentResolver.getType(this)
            SignatureInlineImages.encodeBytes(bytes, mime)
        }
    } catch (_: Exception) {
        null
    }
}

private const val EDITOR_HEIGHT_DP = 200
