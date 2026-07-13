package app.k9mail.library.signatureeditor

import androidx.compose.foundation.layout.Arrangement
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
import net.thunderbird.components.ui.bolt.atom.DividerVertical
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonIconColors
import net.thunderbird.components.ui.bolt.atom.button.ButtonIconDefaults
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Compact wrapping toolbar. A single [FlowRow] keeps controls (especially image)
 * reachable without the previous five stacked rows that the IME covered.
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
    val entries = signatureToolbarEntries(
        onCommand = onCommand,
        onInsertLink = onInsertLink,
        onTextColor = onTextColor,
        onFontSize = onFontSize,
        onFontFamily = onFontFamily,
        onInsertImage = onInsertImage,
    )

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = BoltTheme.spacings.half,
                vertical = BoltTheme.spacings.quarter,
            )
            .testTag("signature_editor_toolbar"),
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
}

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
