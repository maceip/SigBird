package app.k9mail.library.signatureeditor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import org.junit.Test

class SignatureHtmlEditorTest {
    @Test
    fun `resolvePendingSignatureImageHtml swaps pending placeholder to hosted url`() {
        // Arrange
        val html = """<p>Hello<img alt="" data-sig-id="sig-123"></p>"""
        val publicUrl = "https://tokens.public.computer/v1/dev-get/sig/2026/07/abcd/obj.webp"

        // Act
        val result = resolvePendingSignatureImageHtml(html, "sig-123", publicUrl)

        // Assert
        assertThat(result).contains(publicUrl)
        assertThat(result).doesNotContain("data-sig-id")
    }

    @Test
    fun `resolvePendingSignatureImageHtml swaps serialized pending image when marker was stripped`() {
        // Arrange
        val pendingSrc = "data:image/webp;base64,UklGRiQAAABXRUJQVlA4WAoAAAAQAAAA"
        val html = """<p>Hello<img src="$pendingSrc" alt=""></p>"""
        val publicUrl = "https://tokens.public.computer/v1/dev-get/sig/2026/07/abcd/obj.webp"

        // Act
        val result = resolvePendingSignatureImageHtml(html, "sig-123", publicUrl, pendingSrc)

        // Assert
        assertThat(result).contains(publicUrl)
        assertThat(result).doesNotContain(pendingSrc)
    }

    @Test
    fun `resolvePendingSignatureImageHtml leaves unrelated html unchanged`() {
        // Arrange
        val html = """<p>Hello<img alt="" data-sig-id="sig-123"></p>"""

        // Act
        val result = resolvePendingSignatureImageHtml(html, "sig-999", "https://tokens.public.computer/x.webp")

        // Assert
        assertThat(result).isEqualTo(html)
    }
}
