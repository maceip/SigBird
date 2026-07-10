package app.k9mail.library.signatureeditor

import app.k9mail.html.cleaner.SignatureHtmlSanitizer
import kotlin.jvm.JvmStatic

/**
 * Helpers for identity signatures that may be stored as plain text or HTML.
 */
object SignatureStorage {
    private val htmlSanitizer = SignatureHtmlSanitizer()

    /**
     * Detects HTML markup (including unsafe tags like script). Detection is intentionally
     * broader than the sanitizer allow-list so entirely-unsafe HTML is still classified as
     * HTML and can be rejected after sanitization empties it.
     *
     * Requires a tag-like boundary (`\s`, `/`, or `>`) so plain `user@example.com` in angle
     * brackets is not treated as HTML.
     */
    private val HTML_TAG_REGEX = Regex(
        pattern = """(?i)^\s*(?:<!DOCTYPE\s+html\b|<!--|</?[a-zA-Z][a-zA-Z0-9]*(?:\s|/|>))""",
    )

    @JvmStatic
    fun isHtml(signature: String?): Boolean {
        if (signature.isNullOrBlank()) return false
        return HTML_TAG_REGEX.containsMatchIn(signature)
    }

    @JvmStatic
    fun sanitizeForStorage(signature: String?): String? = when {
        signature == null -> null
        signature.isBlank() -> signature
        isHtml(signature) -> htmlSanitizer.sanitize(signature)
        else -> signature
    }

    /**
     * Downscales oversized inline images then sanitizes. Use when loading a signature into
     * an editor so previously-saved phone photos do not freeze the UI.
     */
    @JvmStatic
    fun prepareForEditing(signature: String?): String = when {
        signature.isNullOrBlank() -> signature.orEmpty()
        !isHtml(signature) -> signature
        else -> htmlSanitizer.sanitize(SignatureInlineImages.optimizeHtml(signature))
    }

    @JvmStatic
    fun toPlainText(signature: String?): String {
        if (signature.isNullOrBlank()) return ""
        return if (isHtml(signature)) {
            signature
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</p>"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        } else {
            signature
        }
    }
}
