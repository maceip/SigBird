package app.k9mail.library.signatureeditor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class SignatureEditorWebViewFactoryTest {
    @Test
    fun `buildEditorDocument exposes formatting command API`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument("<b>Hi</b>")

        assertThat(document).contains("command: function")
        assertThat(document).contains("restoreSelection")
        assertThat(document).contains("document.createElement('img')")
        assertThat(document).contains("emitDebounced")
        assertThat(document).contains("flush:")
    }

    @Test
    fun `buildEditorDocument preserves selection across toolbar taps`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument("<b>Hi</b>")

        assertThat(document).contains("savedRange = range.cloneRange()")
        assertThat(document).contains("addEventListener('selectionchange'")
    }

    @Test
    fun `buildEditorDocument reports active format state to the bridge`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument("<b>Hi</b>")

        assertThat(document).contains("queryCommandState('bold')")
        assertThat(document).contains("queryCommandState('italic')")
        assertThat(document).contains("queryCommandState('underline')")
        assertThat(document).contains("onFormatStateChanged(bold, italic, underline)")
    }

    @Test
    fun `buildEditorDocument supports background hosted-url swap`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument("<b>Hi</b>")

        assertThat(document).contains("swapImageSrc: function(sigId, newSrc)")
        assertThat(document).contains("data-sig-id")
    }

    @Test
    fun `buildEditorDocument debounces input and flushes on blur`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument("<b>Hi</b>")

        assertThat(document).contains("addEventListener('blur'")
        assertThat(document).contains("max-height: 320px")
    }

    @Test
    fun `buildEditorDocument does not wire keyup emit hot path`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument("plain")

        assertThat(document).doesNotContain("addEventListener('keyup'")
        assertThat(document).contains("contenteditable=\"true\"")
    }

    @Test
    fun `buildEditorDocument marks readOnly editors non-editable`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument(
            signatureHtml = "<i>sig</i>",
            readOnly = true,
        )

        assertThat(document).contains("contenteditable=\"false\"")
    }

    @Test
    fun `toHostedImageWebResourceResponse returns null for failed fetches`() {
        // Arrange
        val response = createResponse(
            code = 404,
            body = "<html>not found</html>",
            contentType = "text/html",
        )

        // Act
        val result = response.toHostedImageWebResourceResponse()

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `toHostedImageWebResourceResponse keeps successful image responses`() {
        // Arrange
        val response = createResponse(
            code = 200,
            body = "webp",
            contentType = "image/webp",
        )

        // Act
        val result = response.toHostedImageWebResourceResponse()

        // Assert
        assertThat(result).isNotNull()
    }

    private fun createResponse(code: Int, body: String, contentType: String): Response {
        val request = Request.Builder()
            .url("https://tokens.public.computer/images/test.webp")
            .build()

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()
    }
}
