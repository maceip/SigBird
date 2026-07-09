package com.fsck.k9.ui.identity

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.fsck.k9.message.html.SignatureContent
import com.fsck.k9.ui.R
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
@Composable
fun SignatureHtmlEditor(
    html: String,
    onHtmlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
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
            text = stringResource(R.string.edit_identity_signature_label),
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
                        // Content updates are pushed from JS; avoid reloading on every recomposition.
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
            label = stringResource(R.string.edit_identity_signature_link_label),
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            ButtonText(
                text = stringResource(R.string.edit_identity_save),
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
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.blockNetworkLoads = true
        settings.blockNetworkImage = true
        settings.loadsImagesAutomatically = true
        isVerticalScrollBarEnabled = true
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Isolation: never navigate away from the editor document.
                return true
            }
        }
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        addJavascriptInterface(
            SignatureEditorBridge { html ->
                val sanitized = SignatureContent.sanitizeForStorage(html).orEmpty()
                mainHandler.post { onHtmlChange(sanitized) }
            },
            "AndroidSignatureEditor",
        )
        loadDataWithBaseURL(
            null,
            buildEditorDocument(initialHtml),
            "text/html",
            Charsets.UTF_8.name(),
            null,
        )
    }
}

private class SignatureEditorBridge(
    private val onHtmlChange: (String) -> Unit,
) {
    @JavascriptInterface
    fun onContentChanged(html: String) {
        onHtmlChange(html)
    }
}

private fun buildEditorDocument(signatureHtml: String): String {
    // Always sanitize before embedding into the isolated editor document.
    val bodyContent = when {
        signatureHtml.isBlank() -> ""
        SignatureContent.isHtml(signatureHtml) ->
            SignatureContent.sanitizeForStorage(signatureHtml).orEmpty()
        else -> signatureHtml
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            html, body {
              margin: 0;
              padding: 0;
              background: transparent;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
              font-size: 15px;
              line-height: 1.4;
              color: #1a1a1a;
            }
            #editor {
              min-height: 140px;
              padding: 12px;
              outline: none;
              word-wrap: break-word;
            }
            #editor img {
              max-width: 100%;
              height: auto;
            }
            #editor a { color: #0b57d0; }
          </style>
        </head>
        <body>
          <div id="editor" contenteditable="true" spellcheck="true">$bodyContent</div>
          <script>
            (function() {
              var editor = document.getElementById('editor');
              function emit() {
                AndroidSignatureEditor.onContentChanged(editor.innerHTML);
              }
              editor.addEventListener('input', emit);
              editor.addEventListener('keyup', emit);
              editor.addEventListener('blur', emit);
              window.SignatureEditor = {
                insertLink: function(url) {
                  document.execCommand('createLink', false, url);
                  emit();
                },
                insertImage: function(src) {
                  document.execCommand('insertImage', false, src);
                  emit();
                }
              };
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun String.toJsString(): String {
    return buildString {
        append('"')
        for (ch in this@toJsString) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(ch)
            }
        }
        append('"')
    }
}

private fun android.net.Uri.toInlineImageDataUri(context: android.content.Context): String? {
    return try {
        context.contentResolver.openInputStream(this)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.isEmpty() || bytes.size > MAX_INLINE_IMAGE_BYTES) return null

            val mime = context.contentResolver.getType(this)?.lowercase()
            when (mime) {
                "image/png" -> "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                "image/jpeg", "image/jpg" -> "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                else -> {
                    // Re-encode unknown/unsupported types to PNG for email-safe embedding.
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                    val output = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    "data:image/png;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}

private const val EDITOR_MIN_HEIGHT_DP = 160
private const val MAX_INLINE_IMAGE_BYTES = 1_500_000
