package com.fsck.k9.ui.identity

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.k9mail.library.signatureeditor.SignatureHtmlEditor as LibrarySignatureHtmlEditor
import com.fsck.k9.ui.R

/**
 * Legacy re-export of the shared signature editor with identity-settings strings.
 */
@Composable
fun SignatureHtmlEditor(
    html: String,
    onHtmlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LibrarySignatureHtmlEditor(
        html = html,
        onHtmlChange = onHtmlChange,
        modifier = modifier,
        label = stringResource(R.string.edit_identity_signature_label),
    )
}
