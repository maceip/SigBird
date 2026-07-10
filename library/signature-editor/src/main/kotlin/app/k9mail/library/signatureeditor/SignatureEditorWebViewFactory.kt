package app.k9mail.library.signatureeditor

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

internal object SignatureEditorWebViewFactory {
    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: android.content.Context,
        initialHtml: String,
        onHtmlChange: (String) -> Unit,
        readOnly: Boolean = false,
        fontSizeSp: Float? = null,
    ): WebView {
        return WebView(context).apply {
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
                    return true
                }
            }
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            if (!readOnly) {
                addJavascriptInterface(
                    SignatureEditorBridge { html ->
                        val sanitized = SignatureStorage.sanitizeForStorage(html).orEmpty()
                        mainHandler.post { onHtmlChange(sanitized) }
                    },
                    "AndroidSignatureEditor",
                )
            }
            loadDataWithBaseURL(
                null,
                buildEditorDocument(initialHtml, readOnly = readOnly, fontSizeSp = fontSizeSp),
                "text/html",
                Charsets.UTF_8.name(),
                null,
            )
        }
    }

    @Suppress("LongMethod")
    fun buildEditorDocument(
        signatureHtml: String,
        readOnly: Boolean = false,
        fontSizeSp: Float? = null,
    ): String {
        val bodyContent = when {
            signatureHtml.isBlank() -> ""

            SignatureStorage.isHtml(signatureHtml) ->
                SignatureStorage.sanitizeForStorage(signatureHtml).orEmpty()

            else ->
                signatureHtml
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\n", "<br>")
        }

        val fontSizeCss = fontSizeSp?.let { "font-size: ${it}sp;" } ?: "font-size: 15px;"
        val contentEditable = if (readOnly) "false" else "true"

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
              $fontSizeCss
              line-height: 1.4;
              color: #1a1a1a;
            }
            #editor {
              min-height: 80px;
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
          <div id="editor" contenteditable="$contentEditable" spellcheck="true">$bodyContent</div>
          <script>
            (function() {
              var editor = document.getElementById('editor');
              function emit() {
                if (typeof AndroidSignatureEditor !== 'undefined') {
                  AndroidSignatureEditor.onContentChanged(editor.innerHTML);
                }
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
                },
                getHtml: function() {
                  return editor.innerHTML;
                },
                setHtml: function(html) {
                  editor.innerHTML = html;
                }
              };
            })();
          </script>
        </body>
        </html>
        """.trimIndent()
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

internal fun String.toJsString(): String {
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
