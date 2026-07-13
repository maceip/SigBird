package app.k9mail.library.signatureeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.thunderbird.components.ui.bolt.atom.DividerHorizontal
import net.thunderbird.components.ui.bolt.atom.DividerVertical
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonIconColors
import net.thunderbird.components.ui.bolt.atom.button.ButtonIconDefaults
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.theme.BoltTheme

private sealed interface ToolbarEntry {
    data class Action(
        val testTag: String,
        val descriptionRes: Int,
        val imageVector: ImageVector,
        val onClick: () -> Unit,
    ) : ToolbarEntry

    data object Divider : ToolbarEntry
}

/**
 * Multi-line formatting toolbar. Uses [FlowRow] so every control (including image)
 * stays visible without horizontal scrolling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SignatureFormattingToolbar(
    onCommand: (String, String?) -> Unit,
    onInsertLink: () -> Unit,
    onTextColor: () -> Unit,
    onFontSize: () -> Unit,
    onFontFamily: () -> Unit,
    onInsertImage: () -> Unit,
) {
    val iconColors = ButtonIconDefaults.buttonIconColors(
        contentColor = BoltTheme.colors.onSurface,
    )
    // Rebuild each composition so lambdas always close over the latest WebView/actions.
    val rows = listOf(
        styleToolbarEntries(onCommand),
        typographyToolbarEntries(onFontFamily, onFontSize, onTextColor),
        listToolbarEntries(onCommand),
        alignToolbarEntries(onCommand),
        insertToolbarEntries(onCommand, onInsertLink, onInsertImage),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = BoltTheme.spacings.half,
                vertical = BoltTheme.spacings.quarter,
            ),
    ) {
        rows.forEachIndexed { index, entries ->
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.Center,
            ) {
                entries.forEach { entry ->
                    when (entry) {
                        is ToolbarEntry.Divider -> ToolbarDivider()

                        is ToolbarEntry.Action -> ToolbarIconButton(
                            onClick = entry.onClick,
                            imageVector = entry.imageVector,
                            contentDescription = stringResource(entry.descriptionRes),
                            colors = iconColors,
                            modifier = Modifier.testTag(entry.testTag),
                        )
                    }
                }
            }
            if (index < rows.lastIndex) {
                DividerHorizontal(
                    modifier = Modifier.padding(vertical = BoltTheme.spacings.quarter),
                    color = BoltTheme.colors.outlineVariant,
                )
            }
        }
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

private fun insertToolbarEntries(
    onCommand: (String, String?) -> Unit,
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
    ToolbarEntry.Action(
        testTag = "signature_editor_horizontal_rule",
        descriptionRes = R.string.signature_editor_horizontal_rule,
        imageVector = Icons.Outlined.HorizontalRule,
        onClick = { onCommand("insertHorizontalRule", null) },
    ),
)

@Composable
private fun ToolbarDivider() {
    DividerVertical(
        modifier = Modifier
            .height(TOOLBAR_DIVIDER_HEIGHT_DP.dp)
            .padding(horizontal = BoltTheme.spacings.half),
        color = BoltTheme.colors.outlineVariant,
    )
}

@Composable
private fun ToolbarIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    colors: ButtonIconColors,
    modifier: Modifier = Modifier,
) {
    ButtonIcon(
        onClick = onClick,
        imageVector = imageVector,
        contentDescription = contentDescription,
        colors = colors,
        modifier = modifier,
    )
}

private const val TOOLBAR_DIVIDER_HEIGHT_DP = 24
