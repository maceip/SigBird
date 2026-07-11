package app.k9mail.library.signatureeditor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class SignatureStorageTest {
    @Test
    fun `isHtml detects common tags`() {
        assertThat(SignatureStorage.isHtml("<div>Hi</div>")).isTrue()
        assertThat(SignatureStorage.isHtml("<script>alert(1)</script>")).isTrue()
        assertThat(SignatureStorage.isHtml("Plain text")).isFalse()
        assertThat(SignatureStorage.isHtml("<user@example.com>")).isFalse()
    }

    @Test
    fun `sanitizeForStorage empties entirely unsafe html`() {
        val html = """<script>alert(1)</script><img src="https://evil.example/track.png">"""

        val result = SignatureStorage.sanitizeForStorage(html).orEmpty()

        assertThat(result.isBlank()).isTrue()
    }

    @Test
    fun `sanitizeForStorage keeps inline png and jpeg data images`() {
        val html = """
            <div>
              <img src="data:image/png;base64,iVBORw0KGgo=" alt="PNG">
              <img src="data:image/jpeg;base64,/9j/4AAQ=" alt="JPEG">
            </div>
        """.trimIndent()

        val result = SignatureStorage.sanitizeForStorage(html).orEmpty()

        assertThat(result).contains("data:image/png;base64,")
        assertThat(result).contains("data:image/jpeg;base64,")
    }

    @Test
    fun `sanitizeForStorage removes remote images and scripts`() {
        val html = """
            <div>
              <script>alert(1)</script>
              <img src="https://evil.example/track.png">
            </div>
        """.trimIndent()

        val result = SignatureStorage.sanitizeForStorage(html).orEmpty()

        assertThat(result).doesNotContain("<script")
        assertThat(result).doesNotContain("https://evil.example")
    }

    @Test
    fun `sanitizeForStorage leaves plain text unchanged`() {
        assertThat(SignatureStorage.sanitizeForStorage("Hello")).isEqualTo("Hello")
    }

    @Test
    fun `sanitizeForStorage keeps lists colors and alignment markup`() {
        val html = """
            <div style="text-align: center; color: #0B57D0; font-family: Arial">
              <ul><li><b>One</b></li></ul>
              <ol><li><i>Two</i></li></ol>
              <s>Old</s>
            </div>
        """.trimIndent()

        val result = SignatureStorage.sanitizeForStorage(html).orEmpty()

        assertThat(result).contains("<ul>")
        assertThat(result).contains("<ol>")
        assertThat(result).contains("text-align: center")
        assertThat(result).contains("color: #0B57D0")
        assertThat(result).contains("<s>")
    }

    @Test
    fun `prepareForEditing returns plain text unchanged`() {
        assertThat(SignatureStorage.prepareForEditing("Just text")).isEqualTo("Just text")
    }

    @Test
    fun `sanitizeForStorage keeps tokens public computer dev-get image urls`() {
        val hosted =
            "https://tokens.public.computer/v1/dev-get/sig/2026/07/abcd/obj.webp"
        val html = """<div>Sig<img src="$hosted" alt="logo"></div>"""

        val saved = SignatureStorage.sanitizeForStorage(html).orEmpty()
        val reopened = SignatureStorage.prepareForEditing(saved)

        assertThat(saved).contains(hosted)
        assertThat(reopened).contains(hosted)
        assertThat(reopened).contains("""alt="logo"""")
    }

    @Test
    fun `toPlainText extracts text from html with inline image`() {
        val html = """<div><b>Jane</b><img src="data:image/png;base64,abc=" alt="x"></div>"""

        val result = SignatureStorage.toPlainText(html)

        assertThat(result).contains("Jane")
        assertThat(result).doesNotContain("<b>")
    }
}
