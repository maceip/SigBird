package app.k9mail.library.signatureeditor

import app.k9mail.html.cleaner.SignatureHtmlSanitizer

/**
 * Helpers for identity signatures that may be stored as plain text or HTML.
 */
object SignatureStorage {
    private val htmlSanitizer = SignatureHtmlSanitizer()

    private val HTML_TAG_REGEX = Regex(
        pattern = """(?i)^\s*(?:<!DOCTYPE\s+html\b|<html\b|<body\b|<div\b|<p\b|<span\b|<br\b|<img\b|<table\b|<b\b|<i\b|<u\b|<strong\b|<em\b|<a\b|<font\b)""",
    )

    fun isHtml(signature: String?): Boolean {
        if (signature.isNullOrBlank()) return false
        return HTML_TAG_REGEX.containsMatchIn(signature)
    }

    fun sanitizeForStorage(signature: String?): String? {
        if (signature == null) return null
        if (signature.isBlank()) return signature
        return if (isHtml(signature)) {
            htmlSanitizer.sanitize(signature)
        } else {
            signature
        }
    }

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
