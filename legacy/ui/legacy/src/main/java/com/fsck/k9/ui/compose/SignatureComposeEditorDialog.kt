package com.fsck.k9.ui.compose

import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.fsck.k9.message.html.SignatureContent
import com.fsck.k9.ui.R
import com.fsck.k9.ui.identity.SignatureHtmlEditor
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider

internal class SignatureComposeEditorDialog(
    private val activity: AppCompatActivity,
    private val themeProvider: FeatureThemeProvider,
    private val onPersist: (signature: String) -> Unit,
    private val onDismiss: () -> Unit,
) {
    private var editorDialog: AlertDialog? = null

    fun show(composeSignature: String) {
        if (editorDialog?.isShowing == true) return

        val draftHtml = toDraftHtml(composeSignature)
        var latestWrite = draftHtml
        var wroteDuringEdit = false

        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                themeProvider.WithTheme {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(BoltTheme.spacings.double),
                    ) {
                        SignatureHtmlEditor(
                            html = draftHtml,
                            onHtmlChange = { html ->
                                latestWrite = html
                                wroteDuringEdit = true
                                onPersist(html)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp),
                        )
                        ButtonText(
                            text = activity.getString(R.string.edit_identity_save),
                            onClick = {
                                onPersist(SignatureContent.sanitizeForStorage(latestWrite).orEmpty())
                                wroteDuringEdit = true
                                editorDialog?.dismiss()
                            },
                            modifier = Modifier.padding(top = BoltTheme.spacings.default),
                        )
                    }
                }
            }
        }

        editorDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.edit_identity_signature_label)
            .setView(composeView)
            .setOnDismissListener {
                if (wroteDuringEdit) {
                    onPersist(SignatureContent.sanitizeForStorage(latestWrite).orEmpty())
                }
                onDismiss()
                editorDialog = null
            }
            .create()
            .apply { show() }
    }

    fun dismiss() {
        editorDialog?.dismiss()
        editorDialog = null
    }

    private fun toDraftHtml(composeSignature: String): String = when {
        SignatureContent.isHtml(composeSignature) -> composeSignature
        composeSignature.isBlank() -> ""
        else -> composeSignature
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
    }
}

internal fun EditText.attachPlainTextSignatureListener(
    isActive: () -> Boolean,
    currentSignature: () -> String,
    onSignatureChanged: (signature: String) -> Unit,
) {
    addTextChangedListener(
        object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isActive()) return
                val text = s?.toString().orEmpty()
                val signature = currentSignature()
                if (text == signature || signature.replace("\r\n", "\n") == text) return
                onSignatureChanged(text)
            }
        },
    )
}
