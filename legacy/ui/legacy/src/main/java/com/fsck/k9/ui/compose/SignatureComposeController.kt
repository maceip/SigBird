package com.fsck.k9.ui.compose

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import app.k9mail.legacy.di.DI
import com.fsck.k9.helper.toCrLf
import com.fsck.k9.helper.toLf
import com.fsck.k9.message.html.SignatureContent
import com.fsck.k9.ui.R
import com.fsck.k9.ui.helper.DisplayHtmlUiFactory
import com.fsck.k9.view.MessageWebView
import com.fsck.k9.view.WebViewConfigProvider
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider

/**
 * Renders the compose signature like destination email clients would, and opens the
 * isolated WYSIWYG editor on double-tap.
 *
 * Signature edits are reported immediately via [onSignatureWrite] so the host can
 * persist draft diffs on every write (never lose a composition).
 */
@Suppress("TooManyFunctions")
class SignatureComposeController(
    activity: AppCompatActivity,
    private val onSignatureWrite: SignatureWriteListener,
) {
    private val displayHtml = DI.get(DisplayHtmlUiFactory::class.java).createForMessageCompose()
    private val webViewConfigProvider = DI.get(WebViewConfigProvider::class.java)
    private val themeProvider = DI.get(FeatureThemeProvider::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val editorDialog = SignatureComposeEditorDialog(
        activity = activity,
        themeProvider = themeProvider,
        onPersist = { signature ->
            composeSignature = signature
            signatureChanged = true
            onSignatureWrite.onSignatureWrite(signature, true)
        },
        onDismiss = { render() },
    )

    private val upperEdit: View = activity.findViewById(R.id.upper_signature)
    private val lowerEdit: View = activity.findViewById(R.id.lower_signature)
    private val upperHtml: MessageWebView = activity.findViewById(R.id.upper_signature_html)
    private val lowerHtml: MessageWebView = activity.findViewById(R.id.lower_signature_html)

    private var activeEdit: View = lowerEdit
    private var activeHtml: MessageWebView = lowerHtml
    private var composeSignature: String = ""
    private var signatureChanged: Boolean = false
    private var signatureUse: Boolean = false

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

    fun resolveSignatureForSend(): String = when {
        !signatureUse -> ""
        !signatureChanged && SignatureContent.isHtml(composeSignature) -> composeSignature
        signatureChanged && SignatureContent.isHtml(composeSignature) ->
            SignatureContent.sanitizeForStorage(composeSignature).orEmpty()
        signatureChanged -> composeSignature.toCrLf().orEmpty()
        SignatureContent.isHtml(composeSignature) -> composeSignature
        else -> composeSignature.toCrLf().orEmpty()
    }

    fun hide() {
        upperEdit.visibility = View.GONE
        lowerEdit.visibility = View.GONE
        upperHtml.visibility = View.GONE
        lowerHtml.visibility = View.GONE
    }

    fun destroy() {
        editorDialog.dismiss()
        mainHandler.removeCallbacksAndMessages(null)
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
            if (activeEdit is EditText) {
                val editText = activeEdit as EditText
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
            view.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    openEditor()
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
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
            view is MessageWebView
        }
        if (view is EditText) {
            view.attachPlainTextSignatureListener(
                isActive = { view === activeEdit && view.visibility == View.VISIBLE },
                currentSignature = { composeSignature },
                onSignatureChanged = { text ->
                    composeSignature = text
                    signatureChanged = true
                    onSignatureWrite.onSignatureWrite(composeSignature, true)
                },
            )
        }
    }

    private fun openEditor() {
        editorDialog.show(composeSignature)
    }
}
