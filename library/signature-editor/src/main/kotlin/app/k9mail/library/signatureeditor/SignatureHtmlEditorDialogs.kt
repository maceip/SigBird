package app.k9mail.library.signatureeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlined
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.components.ui.bolt.theme.BoltTheme

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

// execCommand fontSize uses 1–7; map to labels users understand.
private val FONT_SIZES = listOf(
    R.string.signature_editor_font_size_small to "2",
    R.string.signature_editor_font_size_normal to "3",
    R.string.signature_editor_font_size_large to "5",
    R.string.signature_editor_font_size_huge to "6",
)
