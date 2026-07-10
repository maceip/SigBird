package com.fsck.k9.ui.compose

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import app.k9mail.legacy.di.DI
import com.fsck.k9.activity.MessageCompose
import com.fsck.k9.helper.toCrLf
import com.fsck.k9.helper.toLf
import com.fsck.k9.message.html.SignatureContent
import com.fsck.k9.ui.R
import com.fsck.k9.ui.helper.DisplayHtmlUiFactory
import com.fsck.k9.ui.identity.SignatureHtmlEditor
import com.fsck.k9.view.MessageWebView
import com.fsck.k9.view.WebViewConfigProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider

/**
 * Renders the compose signature like destination email clients would, and opens the
 * isolated WYSIWYG editor on double-tap.
 *
 * Signature edits are reported immediately via [onSignatureWrite] so the host can
 * persist draft diffs on every write (never lose a composition).
 */
fun interface SignatureWriteListener {
    fun onSignatureWrite(signature: String, changed: Boolean)
}

class SignatureComposeController(
    private val messageCompose: MessageCompose,
    private val onSignatureWrite: SignatureWriteListener,
) {
    private val displayHtml = DI.get(DisplayHtmlUiFactory::class.java).createForMessageCompose()
    private val webViewConfigProvider = DI.get(WebViewConfigProvider::class.java)
    private val themeProvider = DI.get(FeatureThemeProvider::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val upperEdit: View = messageCompose.findViewById(R.id.upper_signature)
    private val lowerEdit: View = messageCompose.findViewById(R.id.lower_signature)
    private val upperHtml: MessageWebView = messageCompose.findViewById(R.id.upper_signature_html)
    private val lowerHtml: MessageWebView = messageCompose.findViewById(R.id.lower_signature_html)

    private var activeEdit: View = lowerEdit
    private var activeHtml: MessageWebView = lowerHtml
    private var composeSignature: String = ""
    private var signatureChanged: Boolean = false
    private var signatureUse: Boolean = false
    private var editorDialog: AlertDialog? = null

    init {
        configurePreview(upperHtml)
        configurePreview(lowerHtml)
        attachDoubleTap(upperHtml)
        attachDoubleTap(lowerHtml)
        attachDoubleTap(upperEdit)
        attachDoubleTap(lowerEdit)
    }

    fun bindPosition(signatureBeforeQuotedText: Boolean) {
        if (signatureBeforeQuotedText) {
            activeEdit = upperEdit
            activeHtml = upperHtml
            lowerEdit.visibility = View.GONE
            lowerHtml.visibility = View.GONE
        } else {
            activeEdit = lowerEdit
            activeHtml = lowerHtml
            upperEdit.visibility = View.GONE
            upperHtml.visibility = View.GONE
        }
    }

    fun updateFromIdentity(identity: Identity) {
        signatureUse = identity.signatureUse
        if (!signatureChanged) {
            composeSignature = identity.signature.orEmpty()
        }
        render()
    }

    fun setComposeSignature(signature: String, changed: Boolean) {
        composeSignature = signature
        signatureChanged = changed
        render()
    }

    fun getComposeSignature(): String = composeSignature

    fun isSignatureChanged(): Boolean = signatureChanged

    fun resolveSignatureForSend(): String {
        if (!signatureUse) return ""
        if (!signatureChanged && SignatureContent.isHtml(composeSignature)) {
            return composeSignature
        }
        if (signatureChanged) {
            return if (SignatureContent.isHtml(composeSignature)) {
                SignatureContent.sanitizeForStorage(composeSignature).orEmpty()
            } else {
                composeSignature.toCrLf().orEmpty()
            }
        }
        return if (SignatureContent.isHtml(composeSignature)) {
            composeSignature
        } else {
            composeSignature.toCrLf().orEmpty()
        }
    }

    fun hide() {
        upperEdit.visibility = View.GONE
        lowerEdit.visibility = View.GONE
        upperHtml.visibility = View.GONE
        lowerHtml.visibility = View.GONE
    }

    private fun render() {
        if (!signatureUse) {
            hide()
            return
        }

        val signature = composeSignature
        if (SignatureContent.isHtml(signature) || looksLikeRichHtml(signature)) {
            activeEdit.visibility = View.GONE
            activeHtml.visibility = View.VISIBLE
            val fragment = SignatureContent.toHtmlFragment(signature)
            activeHtml.displayHtmlContentWithInlineAttachments(
                displayHtml.wrapMessageContent(fragment),
                null,
                null,
            )
        } else {
            activeHtml.visibility = View.GONE
            activeEdit.visibility = View.VISIBLE
            if (activeEdit is android.widget.EditText) {
                val editText = activeEdit as android.widget.EditText
                val display = signature.toLf().orEmpty()
                if (editText.text?.toString() != display) {
                    editText.setText(display)
                }
            }
        }
    }

    private fun looksLikeRichHtml(signature: String): Boolean {
        return signature.contains("<") && signature.contains(">")
    }

    private fun configurePreview(webView: MessageWebView) {
        webView.configure(webViewConfigProvider.createForMessageCompose())
        webView.setWebViewClient(
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
            },
        )
        webView.contentDescription = "compose_signature_preview"
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDoubleTap(view: View) {
        val detector = GestureDetector(
            messageCompose,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    openEditor()
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    // Single tap on HTML preview also opens editor for discoverability.
                    if (view is MessageWebView && view.visibility == View.VISIBLE) {
                        openEditor()
                        return true
                    }
                    return false
                }
            },
        )
        view.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            // Consume touches on HTML preview so links never navigate.
            view is MessageWebView
        }
        if (view is android.widget.EditText) {
            view.addTextChangedListener(
                object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: android.text.Editable?) {
                        if (view !== activeEdit || view.visibility != View.VISIBLE) return
                        val text = s?.toString().orEmpty()
                        if (text == composeSignature || composeSignature.toLf() == text) return
                        composeSignature = text
                        signatureChanged = true
                        onSignatureWrite.onSignatureWrite(composeSignature, true)
                    }
                },
            )
        }
    }

    private fun openEditor() {
        if (editorDialog?.isShowing == true) return

        val draftHtml = when {
            SignatureContent.isHtml(composeSignature) -> composeSignature
            composeSignature.isBlank() -> ""
            else -> composeSignature
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>")
        }
        var latestWrite = draftHtml
        var wroteDuringEdit = false

        val composeView = ComposeView(messageCompose).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                themeProvider.WithTheme {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(BoltTheme.spacings.double),
                    ) {
                        SignatureHtmlEditor(
                            html = draftHtml,
                            onHtmlChange = { html ->
                                latestWrite = html
                                wroteDuringEdit = true
                                // Persist on every write so process death / dialog dismiss never loses work.
                                composeSignature = html
                                signatureChanged = true
                                onSignatureWrite.onSignatureWrite(html, true)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp),
                        )
                        ButtonText(
                            text = messageCompose.getString(R.string.edit_identity_save),
                            onClick = {
                                composeSignature = SignatureContent.sanitizeForStorage(latestWrite).orEmpty()
                                signatureChanged = true
                                wroteDuringEdit = true
                                onSignatureWrite.onSignatureWrite(composeSignature, true)
                                render()
                                editorDialog?.dismiss()
                            },
                            modifier = Modifier.padding(top = BoltTheme.spacings.default),
                        )
                    }
                }
            }
        }

        editorDialog = MaterialAlertDialogBuilder(messageCompose)
            .setTitle(R.string.edit_identity_signature_label)
            .setView(composeView)
            .setOnDismissListener {
                // Keep last on-write value even if the dialog is yanked by the user or system.
                if (wroteDuringEdit) {
                    composeSignature = SignatureContent.sanitizeForStorage(latestWrite).orEmpty()
                    signatureChanged = true
                    onSignatureWrite.onSignatureWrite(composeSignature, true)
                }
                render()
                editorDialog = null
            }
            .create()
            .also { it.show() }
    }

    fun destroy() {
        editorDialog?.dismiss()
        editorDialog = null
        mainHandler.removeCallbacksAndMessages(null)
    }
}
