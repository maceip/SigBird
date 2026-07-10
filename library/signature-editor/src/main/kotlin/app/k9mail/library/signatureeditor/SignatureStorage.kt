package app.k9mail.library.signatureeditor

import com.fsck.k9.message.html.SignatureContent
import kotlin.jvm.JvmStatic

/**
 * Helpers for identity signatures that may be stored as plain text or HTML.
 */
object SignatureStorage {
    @JvmStatic
    fun isHtml(signature: String?): Boolean = SignatureContent.isHtml(signature)

    @JvmStatic
    fun sanitizeForStorage(signature: String?): String? = SignatureContent.sanitizeForStorage(signature)

    @JvmStatic
    fun toPlainText(signature: String?): String = SignatureContent.toPlainText(signature)
}
