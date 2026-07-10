package app.k9mail.library.signatureeditor

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import org.junit.Test

class SignatureEditorWebViewFactoryTest {
    @Test
    fun `buildEditorDocument debounces input and flushes on blur`() {
        val document = SignatureEditorWebViewFactory.buildEditorDocument("<b>Hi</b>")

        assertThat(document).contains("emitDebounced")
        assertThat(document).contains("addEventListener('blur'")
        assertThat(document).contains("flush:")
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
}
