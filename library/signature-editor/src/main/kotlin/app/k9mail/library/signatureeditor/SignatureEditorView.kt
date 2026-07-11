package app.k9mail.library.signatureeditor

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import app.k9mail.library.signatureeditor.R

/**
 * View-based WYSIWYG signature editor for legacy compose screens.
 */
class SignatureEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var webView: WebView? = null
    private var currentHtml: String = ""
    private var readOnly: Boolean = false
    private var fontSizeSp: Float? = null
    private var changeListener: OnSignatureChangeListener? = null

    fun setOnSignatureChangeListener(listener: OnSignatureChangeListener?) {
        changeListener = listener
    }

    fun setReadOnly(readOnly: Boolean) {
        this.readOnly = readOnly
    }

    fun setFontSizeSp(fontSizeSp: Float) {
        this.fontSizeSp = fontSizeSp
        webView?.let { reloadContent(currentHtml) }
    }

    fun setSignature(signature: String?) {
        val content = signature.orEmpty()
        currentHtml = content
        ensureWebView()
        reloadContent(content)
    }

    fun getSignatureHtml(): String = currentHtml

    fun getSignaturePlainText(): String = SignatureStorage.toPlainText(currentHtml)

    fun append(text: String) {
        webView?.evaluateJavascript(
            "window.SignatureEditor.setHtml(window.SignatureEditor.getHtml() + ${text.toJsString()});",
            null,
        )
        currentHtml += text
        changeListener?.onSignatureChanged()
    }

    fun flushPendingChanges() {
        webView?.evaluateJavascript("window.SignatureEditor && window.SignatureEditor.flush();", null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView() {
        if (webView != null) return

        val created = SignatureEditorWebViewFactory.create(
            context = context,
            initialHtml = currentHtml,
            onHtmlChange = { html ->
                currentHtml = html
                changeListener?.onSignatureChanged()
            },
            readOnly = readOnly,
            fontSizeSp = fontSizeSp,
        ).also { view ->
            view.layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            view.contentDescription = context.getString(R.string.signature_editor_label)
        }
        webView = created
        addView(created)
    }

    private fun reloadContent(content: String) {
        webView?.loadDataWithBaseURL(
            null,
            SignatureEditorWebViewFactory.buildEditorDocument(
                signatureHtml = content,
                readOnly = readOnly,
                fontSizeSp = fontSizeSp,
            ),
            "text/html",
            Charsets.UTF_8.name(),
            null,
        )
    }

    override fun onDetachedFromWindow() {
        webView?.destroy()
        webView = null
        super.onDetachedFromWindow()
    }

    fun interface OnSignatureChangeListener {
        fun onSignatureChanged()
    }
}
