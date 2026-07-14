package app.k9mail.library.signatureeditor

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

internal class SignatureEditorWebView(
    context: android.content.Context,
) : WebView(context) {
    private var activeActionMode: ActionMode? = null

    /**
     * When set, the text-selection floating toolbar gains Bold / Italic / Underline
     * items so formatting works directly from the popup Android shows anyway —
     * no fight between the system ActionMode and the toolbar row below the editor.
     */
    var formatActionHandler: ((String) -> Unit)? = null

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
        return super.startActionMode(wrapCallback(callback)).also { activeActionMode = it }
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        val wrapped = if (type == ActionMode.TYPE_FLOATING) wrapCallback(callback) else callback
        return super.startActionMode(wrapped, type).also { activeActionMode = it }
    }

    fun finishActiveActionMode() {
        activeActionMode?.finish()
        activeActionMode = null
    }

    private fun wrapCallback(callback: ActionMode.Callback?): ActionMode.Callback? {
        val handler = formatActionHandler
        return if (handler == null || callback == null) {
            callback
        } else {
            FormatActionModeCallback(callback, handler, context)
        }
    }

    private class FormatActionModeCallback(
        private val delegate: ActionMode.Callback,
        private val onFormat: (String) -> Unit,
        private val context: android.content.Context,
    ) : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            if (!delegate.onCreateActionMode(mode, menu)) return false
            addFormatItems(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val changed = delegate.onPrepareActionMode(mode, menu)
            if (menu.findItem(MENU_ID_BOLD) == null) {
                addFormatItems(menu)
                return true
            }
            return changed
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val command = when (item.itemId) {
                MENU_ID_BOLD -> "bold"
                MENU_ID_ITALIC -> "italic"
                MENU_ID_UNDERLINE -> "underline"
                else -> null
            }
            if (command != null) {
                onFormat(command)
                return true
            }
            return delegate.onActionItemClicked(mode, item)
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            delegate.onDestroyActionMode(mode)
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            val delegate2 = delegate as? ActionMode.Callback2
            if (delegate2 != null) {
                delegate2.onGetContentRect(mode, view, outRect)
            } else {
                super.onGetContentRect(mode, view, outRect)
            }
        }

        private fun addFormatItems(menu: Menu) {
            menu.add(Menu.NONE, MENU_ID_BOLD, ORDER_FIRST, context.getString(R.string.signature_editor_bold))
            menu.add(Menu.NONE, MENU_ID_ITALIC, ORDER_FIRST + 1, context.getString(R.string.signature_editor_italic))
            menu.add(
                Menu.NONE,
                MENU_ID_UNDERLINE,
                ORDER_FIRST + 2,
                context.getString(R.string.signature_editor_underline),
            )
        }

        private companion object {
            const val MENU_ID_BOLD = 0x5160001
            const val MENU_ID_ITALIC = 0x5160002
            const val MENU_ID_UNDERLINE = 0x5160003
            const val ORDER_FIRST = 100
        }
    }
}

/**
 * Shared request handling for WebViews that render signature HTML: allow-listed
 * hosted signature images are fetched by the app, every other network request is
 * answered with an empty response. Callers must leave blockNetworkLoads /
 * blockNetworkImage off — those flags suppress the intercept path entirely.
 */
object SignatureImageIntercepts {
    private val hostedImageHttpClient: okhttp3.OkHttpClient by lazy { okhttp3.OkHttpClient() }

    fun blockedResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", null, ByteArrayInputStream(ByteArray(0)))
    }

    fun fetchAllowlistedHostedImage(url: String): WebResourceResponse? {
        if (!SignatureImageHostClient.isAllowedHostedImageUrl(url)) {
            return null
        }
        return runCatching {
            hostedImageHttpClient.newCall(
                okhttp3.Request.Builder().url(url).get().build(),
            )
                .execute()
                .toHostedImageWebResourceResponse()
        }.getOrNull()
    }

    /** Hosted image if allow-listed, otherwise an empty response. */
    fun interceptOrBlock(url: String?): WebResourceResponse {
        if (url == null) return blockedResponse()
        return fetchAllowlistedHostedImage(url) ?: blockedResponse()
    }
}

internal object SignatureEditorWebViewFactory {
    private const val CONTENT_CHANGE_DEBOUNCE_MS = 400L

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: android.content.Context,
        initialHtml: String,
        onHtmlChange: (String) -> Unit,
        readOnly: Boolean = false,
        fontSizeSp: Float? = null,
        onFormatStateChange: ((Set<String>) -> Unit)? = null,
    ): SignatureEditorWebView {
        return SignatureEditorWebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            // Network stays locked down via the intercept below, which answers every
            // request itself. blockNetworkLoads would also suppress the intercept path,
            // which is why hosted signature images never rendered before.
            settings.blockNetworkLoads = false
            settings.blockNetworkImage = false
            settings.loadsImagesAutomatically = true
            isVerticalScrollBarEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return true
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    return SignatureImageIntercepts.interceptOrBlock(request?.url?.toString())
                }
            }
            val mainHandler = Handler(Looper.getMainLooper())
            if (!readOnly) {
                addJavascriptInterface(
                    SignatureEditorBridge(
                        mainHandler = mainHandler,
                        onHtmlChange = onHtmlChange,
                        onFormatStateChange = onFormatStateChange,
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
                // Optimize oversized images + sanitize once when the document is built.
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
              // Tapping a native toolbar button (or the selection popup) can clear the
              // WebView selection before the command arrives. Remember the last real
              // selection inside the editor so commands act on what the user selected.
              var savedRange = null;
              function serializeHtml() {
                var clone = editor.cloneNode(true);
                clone.querySelectorAll('img[data-sig-id]').forEach(function(img) {
                  img.removeAttribute('data-sig-id');
                });
                return clone.innerHTML;
              }
              function emitNow() {
                if (typeof AndroidSignatureEditor !== 'undefined') {
                  AndroidSignatureEditor.onContentChanged(serializeHtml());
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
              function emitFormatState() {
                if (typeof AndroidSignatureEditor === 'undefined' ||
                    !AndroidSignatureEditor.onFormatStateChanged) {
                  return;
                }
                var bold = false, italic = false, underline = false;
                try {
                  bold = document.queryCommandState('bold');
                  italic = document.queryCommandState('italic');
                  underline = document.queryCommandState('underline');
                } catch (e) { /* queryCommandState can throw with no document focus */ }
                AndroidSignatureEditor.onFormatStateChanged(bold, italic, underline);
              }
              editor.addEventListener('input', emitDebounced);
              editor.addEventListener('blur', function() {
                if (debounceTimer) {
                  clearTimeout(debounceTimer);
                  debounceTimer = null;
                }
                emitNow();
              });
              document.addEventListener('selectionchange', function() {
                var sel = window.getSelection();
                if (sel && sel.rangeCount > 0) {
                  var range = sel.getRangeAt(0);
                  if (editor.contains(range.commonAncestorContainer)) {
                    savedRange = range.cloneRange();
                  }
                }
                emitFormatState();
              });
              function restoreSelection() {
                editor.focus();
                var sel = window.getSelection();
                if (!sel) {
                  return null;
                }
                if (sel.rangeCount > 0) {
                  var existing = sel.getRangeAt(0);
                  if (editor.contains(existing.commonAncestorContainer) &&
                      (!existing.collapsed || !savedRange)) {
                    return existing;
                  }
                }
                var range = savedRange;
                if (!range || !editor.contains(range.commonAncestorContainer)) {
                  range = document.createRange();
                  range.selectNodeContents(editor);
                  range.collapse(false);
                }
                sel.removeAllRanges();
                sel.addRange(range);
                return range;
              }
              window.SignatureEditor = {
                command: function(name, value) {
                  restoreSelection();
                  if (typeof value === 'undefined' || value === null) {
                    document.execCommand(name, false, null);
                  } else {
                    document.execCommand(name, false, value);
                  }
                  emitFormatState();
                  emitNow();
                },
                insertLink: function(url) {
                  restoreSelection();
                  document.execCommand('createLink', false, url);
                  emitNow();
                },
                insertImage: function(src, sigId) {
                  var range = restoreSelection();
                  var img = document.createElement('img');
                  img.setAttribute('src', src);
                  img.setAttribute('alt', '');
                  if (sigId) {
                    img.setAttribute('data-sig-id', sigId);
                  }
                  if (range) {
                    range.deleteContents();
                    range.insertNode(img);
                    range.setStartAfter(img);
                    range.collapse(true);
                    var sel = window.getSelection();
                    if (sel) {
                      sel.removeAllRanges();
                      sel.addRange(range);
                    }
                    savedRange = range.cloneRange();
                  } else {
                    editor.appendChild(img);
                  }
                  emitNow();
                },
                swapImageSrc: function(sigId, newSrc) {
                  var img = editor.querySelector('img[data-sig-id="' + sigId + '"]');
                  if (!img) {
                    return false;
                  }
                  img.setAttribute('src', newSrc);
                  img.removeAttribute('data-sig-id');
                  emitNow();
                  return true;
                },
                commitPendingImage: function(sigId) {
                  var img = editor.querySelector('img[data-sig-id="' + sigId + '"]');
                  if (!img) {
                    return false;
                  }
                  img.removeAttribute('data-sig-id');
                  emitNow();
                  return true;
                },
                getHtml: function() {
                  return serializeHtml();
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

internal fun okhttp3.Response.toHostedImageWebResourceResponse(): WebResourceResponse? {
    val responseBody = if (isSuccessful) {
        body
    } else {
        close()
        null
    }
    if (responseBody == null && isSuccessful) {
        close()
    }
    val body = responseBody ?: return null

    val mime = body.contentType()?.toString() ?: "image/webp"
    return WebResourceResponse(
        mime.substringBefore(';'),
        null,
        body.byteStream(),
    )
}

private class SignatureEditorBridge(
    private val mainHandler: Handler,
    private val onHtmlChange: (String) -> Unit,
    private val onFormatStateChange: ((Set<String>) -> Unit)?,
) {
    @JavascriptInterface
    fun onContentChanged(html: String) {
        // Pass through without Jsoup sanitization — that belongs on save / initial load.
        // JS already debounces input events; post to main for Compose state updates.
        mainHandler.post { onHtmlChange(html) }
    }

    @JavascriptInterface
    fun onFormatStateChanged(bold: Boolean, italic: Boolean, underline: Boolean) {
        val listener = onFormatStateChange ?: return
        val active = buildSet {
            if (bold) add("bold")
            if (italic) add("italic")
            if (underline) add("underline")
        }
        mainHandler.post { listener(active) }
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
