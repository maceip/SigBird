package app.k9mail.library.signatureeditor

import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebView
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SignatureHtmlEditorControllerTest {
    @Test
    fun `captureHtml returns fallback when no WebView is attached`() {
        // Arrange
        val testSubject = SignatureHtmlEditorController()
        var capturedHtml: String? = null

        // Act
        testSubject.captureHtml("<p>draft</p>") { html ->
            capturedHtml = html
        }

        // Assert
        assertThat(capturedHtml).isEqualTo("<p>draft</p>")
    }

    @Test
    fun `captureHtml returns current WebView document html`() {
        // Arrange
        val expectedHtml = "<p>fresh</p>"
        val webView = FakeSignatureEditorWebView(
            context = RuntimeEnvironment.getApplication(),
            html = expectedHtml,
        )
        val testSubject = SignatureHtmlEditorController().apply {
            attachWebView(webView)
        }
        var capturedHtml: String? = null

        // Act
        testSubject.captureHtml("<p>stale</p>") { html ->
            capturedHtml = html
        }

        // Assert
        assertThat(webView.lastScript).isEqualTo("(window.SignatureEditor && window.SignatureEditor.getHtml()) || null")
        assertThat(capturedHtml).isEqualTo(expectedHtml)
    }
}

private class FakeSignatureEditorWebView(
    context: Context,
    private val html: String,
) : WebView(context) {
    var lastScript: String? = null

    override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String?>?) {
        lastScript = script
        resultCallback?.onReceiveValue(JSONObject.quote(html))
    }
}
