package app.k9mail.library.signatureeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlined
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import androidx.compose.ui.graphics.Color as ComposeColor

@Composable
internal fun SignatureLinkDialog(
    linkUrl: String,
    onLinkUrlChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.signature_editor_link_label),
        confirmText = stringResource(R.string.signature_editor_save),
        dismissText = stringResource(android.R.string.cancel),
        onConfirmClick = onConfirm,
        onDismissClick = onDismiss,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("signature_editor_link_dialog"),
    ) {
        TextFieldOutlined(
            value = linkUrl,
            onValueChange = onLinkUrlChange,
            label = stringResource(R.string.signature_editor_link_label),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SignatureColorDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.signature_editor_text_color),
        confirmText = stringResource(android.R.string.cancel),
        onConfirmClick = onDismiss,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("signature_editor_color_dialog"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
        ) {
            TEXT_COLORS.forEach { (hex, color) ->
                Box(
                    modifier = Modifier
                        .size(COLOR_SWATCH_DP.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, BoltTheme.colors.outline, CircleShape)
                        .clickable { onPick(hex) }
                        .testTag("signature_editor_color_$hex"),
                )
            }
        }
    }
}

@Composable
internal fun SignatureFontSizeDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.signature_editor_font_size),
        confirmText = stringResource(android.R.string.cancel),
        onConfirmClick = onDismiss,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("signature_editor_font_size_dialog"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half)) {
            FONT_SIZES.forEach { (labelRes, value) ->
                ButtonText(
                    text = stringResource(labelRes),
                    onClick = { onPick(value) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signature_editor_font_size_$value"),
                )
            }
        }
    }
}

@Composable
internal fun SignatureFontFamilyDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.signature_editor_font_family),
        confirmText = stringResource(android.R.string.cancel),
        onConfirmClick = onDismiss,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("signature_editor_font_family_dialog"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half)) {
            FONT_FAMILIES.forEach { family ->
                ButtonText(
                    text = family,
                    onClick = { onPick(family) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signature_editor_font_family_$family"),
                )
            }
        }
    }
}

private const val COLOR_SWATCH_DP = 36

// Six-character hex only — Outlook legacy renderers reject #rgb shorthand.
@Suppress("MagicNumber")
private val TEXT_COLORS = listOf(
    "#1A1A1A" to ComposeColor(0xFF1A1A1A),
    "#B3261E" to ComposeColor(0xFFB3261E),
    "#0B57D0" to ComposeColor(0xFF0B57D0),
    "#0F7B3F" to ComposeColor(0xFF0F7B3F),
    "#7B2D8E" to ComposeColor(0xFF7B2D8E),
    "#B06000" to ComposeColor(0xFFB06000),
)

// execCommand fontSize uses 1–7; map to labels users understand.
private val FONT_SIZES = listOf(
    R.string.signature_editor_font_size_small to "2",
    R.string.signature_editor_font_size_normal to "3",
    R.string.signature_editor_font_size_large to "5",
    R.string.signature_editor_font_size_huge to "6",
)

// Web-safe stacks that Gmail / Outlook / Apple Mail all honor.
private val FONT_FAMILIES = listOf(
    "Arial",
    "Helvetica",
    "Georgia",
    "Times New Roman",
    "Courier New",
    "Verdana",
)
