package app.k9mail.library.signatureeditor

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import org.junit.Test

class SignatureHtmlEditorToolbarTest {
    @Test
    fun `image and link actions stay early so they land on the first toolbar row`() {
        // Arrange
        val entries = signatureToolbarEntries(
            onCommand = { _, _ -> },
            onInsertLink = {},
            onTextColor = {},
            onFontSize = {},
            onFontFamily = {},
            onInsertImage = {},
        )
        val actionTags = entries.filterIsInstance<ToolbarEntry.Action>().map { it.testTag }

        // Act / Assert — image must not be buried after five visual groups.
        val imageIndex = actionTags.indexOf("signature_editor_image")
        val linkIndex = actionTags.indexOf("signature_editor_link")
        assertThat(linkIndex).isEqualTo(4)
        assertThat(imageIndex).isEqualTo(5)
        assertThat(imageIndex).isLessThan(actionTags.indexOf("signature_editor_font_family"))
        assertThat(actionTags.take(6)).containsExactly(
            "signature_editor_bold",
            "signature_editor_italic",
            "signature_editor_underline",
            "signature_editor_strikethrough",
            "signature_editor_link",
            "signature_editor_image",
        )
    }
}
