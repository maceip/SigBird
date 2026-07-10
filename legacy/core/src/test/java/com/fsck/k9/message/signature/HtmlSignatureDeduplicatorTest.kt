package com.fsck.k9.message.signature

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import org.junit.Test

class HtmlSignatureDeduplicatorTest {
    @Test
    fun `returns html fragment unchanged when no signatures are present`() {
        val html = """<pre class="k9mail"><div dir="auto">Hello</div></pre>"""

        val result = HtmlSignatureDeduplicator.deduplicate(html)

        assertThat(result).isEqualTo(html)
    }

    @Test
    fun `keeps single signature unchanged`() {
        val html = """
            <html><body>
            <div>Hello</div>
            <div class="k9mail-signature">-- <br>Jane</div>
            </body></html>
        """.trimIndent()

        val result = HtmlSignatureDeduplicator.deduplicate(html)

        assertThat(result).contains("k9mail-signature")
        assertThat(result).contains("Jane")
        assertThat(countOccurrences(result, "k9mail-signature")).isEqualTo(1)
    }

    @Test
    fun `removes repeated signatures in thread quotes`() {
        val html = """
            <html><body>
            <div>Reply text</div>
            <div class="k9mail-signature">-- <br>Jane</div>
            <blockquote>
              <div>Earlier</div>
              <div class="k9mail-signature">-- <br>Jane</div>
            </blockquote>
            <blockquote>
              <div class="k9mail-signature">-- <br>Jane</div>
            </blockquote>
            </body></html>
        """.trimIndent()

        val result = HtmlSignatureDeduplicator.deduplicate(html)

        assertThat(countOccurrences(result, "k9mail-signature")).isEqualTo(1)
        assertThat(result).contains("Reply text")
        assertThat(result).contains("Jane")
    }

    @Test
    fun `recognizes namespaced signature class`() {
        val html = """
            <html><body>
            <div class="tb__signature">One</div>
            <div class="tb__signature">Two</div>
            </body></html>
        """.trimIndent()

        val result = HtmlSignatureDeduplicator.deduplicate(html)

        assertThat(countOccurrences(result, "__signature")).isEqualTo(1)
        assertThat(result).contains("One")
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var index = 0
        while (true) {
            index = text.indexOf(needle, index)
            if (index < 0) return count
            count++
            index += needle.length
        }
    }
}
