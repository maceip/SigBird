package com.fsck.k9.message.html

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class SignatureContentTest {
    @Test
    fun `plain text signature is not detected as html`() {
        assertThat(SignatureContent.isHtml("-- \nJane Doe")).isFalse()
    }

    @Test
    fun `html signature is detected`() {
        assertThat(SignatureContent.isHtml("<div><b>Jane</b></div>")).isTrue()
        assertThat(SignatureContent.isHtml("<script>alert(1)</script>")).isTrue()
        assertThat(SignatureContent.isHtml("<user@example.com>")).isFalse()
    }

    @Test
    fun `plain text converts through historical path`() {
        val result = SignatureContent.toHtmlFragment("-- \nJane Doe")

        assertThat(result).contains("k9mail-signature")
        assertThat(result).contains("Jane Doe")
    }

    @Test
    fun `html signature is sanitized and wrapped`() {
        val result = SignatureContent.toHtmlFragment(
            """<div><b>Jane</b><script>alert(1)</script><img src="https://evil.example/x.png"></div>""",
        )

        assertThat(result).contains("k9mail-signature")
        assertThat(result).contains("<b>Jane</b>")
        assertThat(result).doesNotContain("<script")
        assertThat(result).doesNotContain("evil.example")
    }

    @Test
    fun `html signature plain text extraction strips tags`() {
        val result = SignatureContent.toPlainText("<div><b>Jane Doe</b><br>Engineer</div>")

        assertThat(result).contains("Jane Doe")
        assertThat(result).contains("Engineer")
        assertThat(result).doesNotContain("<b>")
    }

    @Test
    fun `sanitizeForStorage leaves plain text unchanged`() {
        assertThat(SignatureContent.sanitizeForStorage("Hello")).isEqualTo("Hello")
    }

    @Test
    fun `sanitizeForStorage preserves inline png and jpeg data images`() {
        val html = """
            <div>
              <img src="data:image/png;base64,iVBORw0KGgo=" alt="PNG">
              <img src="data:image/jpeg;base64,/9j/4AAQ=" alt="JPEG">
            </div>
        """.trimIndent()

        val result = SignatureContent.sanitizeForStorage(html).orEmpty()

        assertThat(result).contains("data:image/png;base64,")
        assertThat(result).contains("data:image/jpeg;base64,")
    }

    @Test
    fun `toHtmlFragment includes sanitized inline images`() {
        val html = """<div><b>Jane</b><img src="data:image/png;base64,iVBORw0KGgo=" alt="Logo"></div>"""

        val result = SignatureContent.toHtmlFragment(html)

        assertThat(result).contains("k9mail-signature")
        assertThat(result).contains("<b>Jane</b>")
        assertThat(result).contains("data:image/png;base64,")
    }

    @Test
    fun `empty signature returns empty html`() {
        assertThat(SignatureContent.toHtmlFragment("")).isEqualTo("")
        assertThat(SignatureContent.toHtmlFragment(null)).isEqualTo("")
    }
}
