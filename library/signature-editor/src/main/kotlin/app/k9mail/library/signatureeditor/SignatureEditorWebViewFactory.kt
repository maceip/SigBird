package app.k9mail.library.signatureeditor

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

internal object SignatureEditorWebViewFactory {
    private const val CONTENT_CHANGE_DEBOUNCE_MS = 400L

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
            val mainHandler = Handler(Looper.getMainLooper())
            if (!readOnly) {
                addJavascriptInterface(
                    SignatureEditorBridge(
                        mainHandler = mainHandler,
                        onHtmlChange = onHtmlChange,
                    ),
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

            SignatureStorage.isHtml(signatureHtml) -> {
                // prepareForEditing already optimizes + sanitizes.
                SignatureStorage.prepareForEditing(signatureHtml)
            }

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
              max-height: 320px;
              height: auto;
              object-fit: contain;
            }
            #editor a { color: #0b57d0; }
          </style>
        </head>
        <body>
          <div id="editor" contenteditable="$contentEditable" spellcheck="true">$bodyContent</div>
          <script>
            (function() {
              var editor = document.getElementById('editor');
              var debounceTimer = null;
              var DEBOUNCE_MS = $CONTENT_CHANGE_DEBOUNCE_MS;
              function emitNow() {
                if (typeof AndroidSignatureEditor !== 'undefined') {
                  AndroidSignatureEditor.onContentChanged(editor.innerHTML);
                }
              }
              function emitDebounced() {
                if (debounceTimer) {
                  clearTimeout(debounceTimer);
                }
                debounceTimer = setTimeout(function() {
                  debounceTimer = null;
                  emitNow();
                }, DEBOUNCE_MS);
              }
              editor.addEventListener('input', emitDebounced);
              editor.addEventListener('blur', function() {
                if (debounceTimer) {
                  clearTimeout(debounceTimer);
                  debounceTimer = null;
                }
                emitNow();
              });
              window.SignatureEditor = {
                insertLink: function(url) {
                  document.execCommand('createLink', false, url);
                  emitNow();
                },
                insertImage: function(src) {
                  document.execCommand('insertImage', false, src);
                  emitNow();
                },
                getHtml: function() {
                  return editor.innerHTML;
                },
                setHtml: function(html) {
                  editor.innerHTML = html;
                },
                flush: function() {
                  if (debounceTimer) {
                    clearTimeout(debounceTimer);
                    debounceTimer = null;
                  }
                  emitNow();
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
    private val mainHandler: Handler,
    private val onHtmlChange: (String) -> Unit,
) {
    @JavascriptInterface
    fun onContentChanged(html: String) {
        // Pass through without Jsoup sanitization — that belongs on save / initial load.
        // JS already debounces input events; post to main for Compose state updates.
        mainHandler.post { onHtmlChange(html) }
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
