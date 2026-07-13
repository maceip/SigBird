package app.k9mail.library.signatureeditor

import androidx.compose.ui.graphics.vector.ImageVector
import net.thunderbird.components.ui.bolt.atom.icon.Icons

internal sealed interface ToolbarEntry {
    data class Action(
        val testTag: String,
        val descriptionRes: Int,
        val imageVector: ImageVector,
        val onClick: () -> Unit,
    ) : ToolbarEntry

    data object Divider : ToolbarEntry
}

/**
 * Image/link sit immediately after basic styles so they land on the first wrapped
 * row on narrow screens instead of a fifth row under the keyboard.
 */
internal fun signatureToolbarEntries(
    onCommand: (String, String?) -> Unit,
    onInsertLink: () -> Unit,
    onTextColor: () -> Unit,
    onFontSize: () -> Unit,
    onFontFamily: () -> Unit,
    onInsertImage: () -> Unit,
): List<ToolbarEntry> {
    return buildList {
        addAll(styleToolbarEntries(onCommand))
        add(ToolbarEntry.Divider)
        addAll(insertToolbarEntries(onInsertLink, onInsertImage))
        add(ToolbarEntry.Divider)
        addAll(typographyToolbarEntries(onFontFamily, onFontSize, onTextColor))
        add(ToolbarEntry.Divider)
        addAll(listToolbarEntries(onCommand))
        add(ToolbarEntry.Divider)
        addAll(alignToolbarEntries(onCommand))
        add(
            ToolbarEntry.Action(
                testTag = "signature_editor_horizontal_rule",
                descriptionRes = R.string.signature_editor_horizontal_rule,
                imageVector = Icons.Outlined.HorizontalRule,
                onClick = { onCommand("insertHorizontalRule", null) },
            ),
        )
    }
}

private fun styleToolbarEntries(onCommand: (String, String?) -> Unit): List<ToolbarEntry> = listOf(
    ToolbarEntry.Action(
        testTag = "signature_editor_bold",
        descriptionRes = R.string.signature_editor_bold,
        imageVector = Icons.Outlined.FormatBold,
        onClick = { onCommand("bold", null) },
    ),
    ToolbarEntry.Action(
        testTag = "signature_editor_italic",
        descriptionRes = R.string.signature_editor_italic,
        imageVector = Icons.Outlined.FormatItalic,
        onClick = { onCommand("italic", null) },
    ),
    ToolbarEntry.Action(
        testTag = "signature_editor_underline",
        descriptionRes = R.string.signature_editor_underline,
        imageVector = Icons.Outlined.FormatUnderlined,
        onClick = { onCommand("underline", null) },
    ),
    ToolbarEntry.Action(
        testTag = "signature_editor_strikethrough",
        descriptionRes = R.string.signature_editor_strikethrough,
        imageVector = Icons.Outlined.FormatStrikethrough,
        onClick = { onCommand("strikeThrough", null) },
    ),
)

private fun insertToolbarEntries(
    onInsertLink: () -> Unit,
    onInsertImage: () -> Unit,
): List<ToolbarEntry> = listOf(
    ToolbarEntry.Action(
        testTag = "signature_editor_link",
        descriptionRes = R.string.signature_editor_insert_link,
        imageVector = Icons.Outlined.Link,
        onClick = onInsertLink,
    ),
    ToolbarEntry.Action(
        testTag = "signature_editor_image",
        descriptionRes = R.string.signature_editor_insert_image,
        imageVector = Icons.Outlined.Image,
        onClick = onInsertImage,
    ),
)

private fun typographyToolbarEntries(
    onFontFamily: () -> Unit,
    onFontSize: () -> Unit,
    onTextColor: () -> Unit,
): List<ToolbarEntry> = listOf(
    ToolbarEntry.Action(
        testTag = "signature_editor_font_family",
        descriptionRes = R.string.signature_editor_font_family,
        imageVector = Icons.Outlined.Description,
        onClick = onFontFamily,
    ),
    ToolbarEntry.Action(
        testTag = "signature_editor_font_size",
        descriptionRes = R.string.signature_editor_font_size,
        imageVector = Icons.Outlined.FormatSize,
        onClick = onFontSize,
    ),
    ToolbarEntry.Action(
        testTag = "signature_editor_text_color",
        descriptionRes = R.string.signature_editor_text_color,
        imageVector = Icons.Outlined.FormatColorText,
        onClick = onTextColor,
    ),
)

private fun listToolbarEntries(onCommand: (String, String?) -> Unit): List<ToolbarEntry> = listOf(
    ToolbarEntry.Action(
        testTag = "signature_editor_bulleted_list",
        descriptionRes = R.string.signature_editor_bulleted_list,
        imageVector = Icons.Outlined.FormatListBulleted,
        onClick = { onCommand("insertUnorderedList", null) },
    ),
    ToolbarEntry.Action(
        testTag = "signature_editor_numbered_list",
        descriptionRes = R.string.signature_editor_numbered_list,
        imageVector = Icons.Outlined.FormatListNumbered,
        onClick = { onCommand("insertOrderedList", null) },
    ),
)

private fun alignToolbarEntries(onCommand: (String, String?) -> Unit): List<ToolbarEntry> = listOf(
    ToolbarEntry.Action(
        testTag = "signature_editor_align_left",
        descriptionRes = R.string.signature_editor_align_left,
        imageVector = Icons.Outlined.FormatAlignLeft,
        onClick = { onCommand("justifyLeft", null) },
    ),
    ToolbarEntry.Action(
        testTag = "signature_editor_align_center",
        descriptionRes = R.string.signature_editor_align_center,
        imageVector = Icons.Outlined.FormatAlignCenter,
        onClick = { onCommand("justifyCenter", null) },
    ),
    ToolbarEntry.Action(
        testTag = "signature_editor_align_right",
        descriptionRes = R.string.signature_editor_align_right,
        imageVector = Icons.Outlined.FormatAlignRight,
        onClick = { onCommand("justifyRight", null) },
    ),
)
