package app.k9mail.html.cleaner

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import org.junit.Test

class SignatureHtmlSanitizerTest {
    private val testSubject = SignatureHtmlSanitizer()

    @Test
    fun `keeps basic formatting supported by modern email clients`() {
        val html = """
            <div>
              <b>Jane Doe</b><br>
              <i>Product</i><br>
              <a href="https://example.com">example.com</a>
            </div>
        """.trimIndent()

        val result = testSubject.sanitize(html)

        assertThat(result).contains("<b>Jane Doe</b>")
        assertThat(result).contains("<i>Product</i>")
        assertThat(result).contains("""href="https://example.com"""")
    }

    @Test
    fun `allows cid and png jpeg data images`() {
        val html = """
            <div>
              <img src="cid:logo@thunderbird" alt="Logo">
              <img src="data:image/png;base64,iVBORw0KGgo=" alt="PNG">
              <img src="data:image/jpeg;base64,/9j/4AAQ=" alt="JPEG">
            </div>
        """.trimIndent()

        val result = testSubject.sanitize(html)

        assertThat(result).contains("""src="cid:logo@thunderbird"""")
        assertThat(result).contains("data:image/png;base64,")
        assertThat(result).contains("data:image/jpeg;base64,")
    }

    @Test
    fun `removes scripts styles and remote http images`() {
        val html = """
            <div>
              <script>alert(1)</script>
              <style>.x{color:red}</style>
              <img src="https://evil.example/track.png">
              <img src="javascript:alert(1)">
              Hello
            </div>
        """.trimIndent()

        val result = testSubject.sanitize(html)

        assertThat(result).doesNotContain("<script")
        assertThat(result).doesNotContain("<style")
        assertThat(result).doesNotContain("https://evil.example")
        assertThat(result).doesNotContain("javascript:")
        assertThat(result).contains("Hello")
    }

    @Test
    fun `strips disallowed inline style properties`() {
        val html = """<p style="color: red; position: absolute; behavior: url(x)">Hi</p>"""

        val result = testSubject.sanitize(html)

        assertThat(result).contains("color: red")
        assertThat(result).doesNotContain("position")
        assertThat(result).doesNotContain("behavior")
    }

    @Test
    fun `allows mailto and https links only`() {
        val html = """
            <a href="mailto:jane@example.com">mail</a>
            <a href="ftp://files.example/x">ftp</a>
        """.trimIndent()

        val result = testSubject.sanitize(html)

        assertThat(result).contains("mailto:jane@example.com")
        assertThat(result).doesNotContain("ftp://")
    }

    @Test
    fun `empty input returns empty fragment`() {
        assertThat(testSubject.sanitize("")).isEqualTo("")
    }
}
