package app.k9mail.library.signatureeditor

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import java.io.ByteArrayOutputStream
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlined
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Isolated WYSIWYG signature editor.
 *
 * Supports only formatting that modern Outlook (Windows), Gmail, and Apple Mail
 * reliably render. Images are inlined as PNG/JPEG data URIs (no remote hosting UI).
 * Navigation and network loads are blocked inside the editor WebView.
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
                        .heightIn(min = EDITOR_MIN_HEIGHT_DP.dp)
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

        if (showLinkDialog) {
            LinkInsertRow(
                linkUrl = linkUrl,
                onLinkUrlChange = { linkUrl = it },
                onConfirm = {
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
                onDismiss = {
                    showLinkDialog = false
                    linkUrl = "https://"
                },
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
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

@Composable
private fun LinkInsertRow(
    linkUrl: String,
    onLinkUrlChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BoltTheme.spacings.double, vertical = BoltTheme.spacings.default),
    ) {
        TextFieldOutlined(
            value = linkUrl,
            onValueChange = onLinkUrlChange,
            label = stringResource(R.string.signature_editor_link_label),
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            ButtonText(
                text = stringResource(R.string.signature_editor_save),
                onClick = onConfirm,
            )
            ButtonText(
                text = stringResource(android.R.string.cancel),
                onClick = onDismiss,
            )
        }
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
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
}

private fun android.net.Uri.toInlineImageDataUri(context: android.content.Context): String? {
    return try {
        context.contentResolver.openInputStream(this)?.use { input ->
            encodeInlineImageDataUri(context, this, input.readBytes())
        }
    } catch (_: Exception) {
        null
    }
}

private fun encodeInlineImageDataUri(
    context: android.content.Context,
    uri: android.net.Uri,
    bytes: ByteArray,
): String? {
    if (bytes.isEmpty() || bytes.size > MAX_INLINE_IMAGE_BYTES) {
        return null
    }

    val mime = context.contentResolver.getType(uri)?.lowercase()
    return when (mime) {
        "image/png" -> toDataUri("image/png", bytes)
        "image/jpeg", "image/jpg" -> toDataUri("image/jpeg", bytes)
        else -> encodeBitmapAsPngDataUri(bytes)
    }
}

private fun encodeBitmapAsPngDataUri(bytes: ByteArray): String? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, PNG_COMPRESS_QUALITY, output)
    return toDataUri("image/png", output.toByteArray())
}

private fun toDataUri(mimeType: String, bytes: ByteArray): String {
    return "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}

private const val EDITOR_MIN_HEIGHT_DP = 160
private const val MAX_INLINE_IMAGE_BYTES = 1_500_000
private const val PNG_COMPRESS_QUALITY = 100
