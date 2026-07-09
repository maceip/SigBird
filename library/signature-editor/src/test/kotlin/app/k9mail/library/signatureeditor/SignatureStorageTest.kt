package app.k9mail.library.signatureeditor

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class SignatureStorageTest {
    @Test
    fun `html signatures use shared plain text conversion`() {
        val result = SignatureStorage.toPlainText("<a href='https://domain.example/'>Link text</a>")

        assertThat(result).isEqualTo("Link text <https://domain.example/>")
    }
}
