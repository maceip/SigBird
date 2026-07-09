package app.k9mail.library.signatureeditor

import com.fsck.k9.message.html.SignatureContent

/**
 * Helpers for identity signatures that may be stored as plain text or HTML.
 */
object SignatureStorage {
    fun isHtml(signature: String?): Boolean = SignatureContent.isHtml(signature)

    fun sanitizeForStorage(signature: String?): String? = SignatureContent.sanitizeForStorage(signature)

    fun toPlainText(signature: String?): String = SignatureContent.toPlainText(signature)
}
