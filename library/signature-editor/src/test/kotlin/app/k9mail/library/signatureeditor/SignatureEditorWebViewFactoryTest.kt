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
        assertThat(document).contains("commitPendingImage: function(sigId)")
        assertThat(document).contains("img.removeAttribute('data-sig-id')")
        assertThat(document).doesNotContain("img.removeAttribute('src')")
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
    fun `buildEditorDocument offers Gmail-style image size options`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument("plain")

        assertThat(document).contains("sig-resize-bar")
        assertThat(document).contains("{ key: 'small', label: \"Small\", factor: 0.25, min: 96 }")
        assertThat(document).contains("{ key: 'medium', label: \"Medium\", factor: 0.5, min: 160 }")
        assertThat(document).contains("{ key: 'original', label: \"Original\", factor: 1 }")
        assertThat(document).contains("applyImageSize")
        assertThat(document).contains("img.setAttribute('width', w)")
    }

    @Test
    fun `buildEditorDocument localizes image size labels`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument(
            signatureHtml = "plain",
            imageSizeLabels = ImageSizeLabels(small = "Klein", medium = "Mittel", original = "Original"),
        )

        assertThat(document).contains("label: \"Klein\"")
        assertThat(document).contains("label: \"Mittel\"")
    }

    @Test
    fun `buildEditorDocument keeps image selection markers out of serialized html`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument("plain")

        assertThat(document).contains("img.classList.remove('sig-img-active')")
    }

    @Test
    fun `buildEditorDocument guarantees a caret anchor after trailing images`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument("plain")

        assertThat(document).contains("ensureEditableTail")
        assertThat(document).contains("function lastMeaningfulDescendant(node)")
        assertThat(document).contains("var last = lastMeaningfulDescendant(editor);")
        assertThat(document).contains("placeCaretAtEnd")
        assertThat(document).contains("document.createElement('br')")
    }

    @Test
    fun `buildEditorDocument keeps pending image widths when resized before load completes`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument("plain")

        assertThat(document).contains("} else if (w == null) {")
        assertThat(document).contains("return;")
        assertThat(document).doesNotContain("size.key === 'original' || w == null")
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
