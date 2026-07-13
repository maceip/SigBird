package app.k9mail.library.signatureeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonIconDefaults
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Single-row formatting toolbar: bold, italic, underline, text size, link, image.
 * Toggleable commands render highlighted while active at the current selection.
 */
@Composable
internal fun SignatureFormattingToolbar(
    boldActive: Boolean,
    italicActive: Boolean,
    underlineActive: Boolean,
    onCommand: (String, String?) -> Unit,
    onFontSize: () -> Unit,
    onInsertLink: () -> Unit,
    onInsertImage: () -> Unit,
) {
    val items = listOf(
        ToolbarItem("bold", Icons.Outlined.FormatBold, R.string.signature_editor_bold, boldActive) {
            onCommand("bold", null)
        },
        ToolbarItem("italic", Icons.Outlined.FormatItalic, R.string.signature_editor_italic, italicActive) {
            onCommand("italic", null)
        },
        ToolbarItem(
            "underline",
            Icons.Outlined.FormatUnderlined,
            R.string.signature_editor_underline,
            underlineActive,
        ) {
            onCommand("underline", null)
        },
        ToolbarItem(
            "font_size",
            Icons.Outlined.FormatSize,
            R.string.signature_editor_font_size,
            active = false,
            onFontSize,
        ),
        ToolbarItem("link", Icons.Outlined.Link, R.string.signature_editor_insert_link, active = false, onInsertLink),
        ToolbarItem(
            "image",
            Icons.Outlined.Image,
            R.string.signature_editor_insert_image,
            active = false,
            onInsertImage,
        ),
    )

    val idleColors = ButtonIconDefaults.buttonIconColors(
        contentColor = BoltTheme.colors.onSurface,
    )
    // Active toggles get a filled, tinted container so state is obvious at a glance.
    val activeColors = ButtonIconDefaults.buttonIconColors(
        containerColor = BoltTheme.colors.primaryContainer,
        contentColor = BoltTheme.colors.primary,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = BoltTheme.spacings.half,
                vertical = BoltTheme.spacings.quarter,
            ),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { item ->
            ButtonIcon(
                onClick = item.onClick,
                imageVector = item.icon,
                contentDescription = stringResource(item.labelRes),
                colors = if (item.active) activeColors else idleColors,
                modifier = Modifier.testTag("signature_editor_${item.tag}"),
            )
        }
    }
}

private data class ToolbarItem(
    val tag: String,
    val icon: ImageVector,
    val labelRes: Int,
    val active: Boolean,
    val onClick: () -> Unit,
)
