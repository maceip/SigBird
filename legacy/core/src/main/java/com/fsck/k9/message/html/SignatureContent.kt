package com.fsck.k9.message.html

import app.k9mail.html.cleaner.SignatureHtmlSanitizer

/**
 * Helpers for identity signatures that may be stored as plain text or HTML.
 *
 * HTML signatures are detected by a leading HTML marker or common tags. Plain-text
 * signatures continue to use the historical `-- ` delimiter conversion path.
 */
object SignatureContent {
    private val htmlSanitizer = SignatureHtmlSanitizer()

    private val HTML_TAG_REGEX = Regex(
        pattern = """(?i)^\s*(?:<!DOCTYPE\s+html\b|<html\b|<body\b|<div\b|<p\b|<span\b|<br\b|<img\b|<table\b|<b\b|<i\b|<u\b|<strong\b|<em\b|<a\b|<font\b)""",
    )

    @JvmStatic
    fun isHtml(signature: String?): Boolean {
        if (signature.isNullOrBlank()) return false
        return HTML_TAG_REGEX.containsMatchIn(signature)
    }

    /**
     * Returns sanitized HTML suitable for inclusion in an outbound HTML body.
     * Plain-text signatures are converted with [HtmlConverter.textToHtmlFragment].
     * HTML signatures are sanitized and wrapped in the signature class when needed.
     */
    @JvmStatic
    fun toHtmlFragment(signature: String?): String {
        if (signature.isNullOrBlank()) return ""

        return if (isHtml(signature)) {
            val sanitized = htmlSanitizer.sanitize(signature)
            wrapWithSignatureClass(sanitized)
        } else {
            HtmlConverter.textToHtmlFragment(signature)
        }
    }

    /**
     * Returns plain text suitable for a text/plain body part.
     */
    @JvmStatic
    fun toPlainText(signature: String?): String {
        if (signature.isNullOrBlank()) return ""
        return if (isHtml(signature)) {
            HtmlConverter.htmlToText(signature)
        } else {
            signature
        }
    }

    /**
     * Sanitizes an HTML signature for persistence. Plain text is returned unchanged.
     */
    @JvmStatic
    fun sanitizeForStorage(signature: String?): String? {
        if (signature == null) return null
        if (signature.isBlank()) return signature
        return if (isHtml(signature)) {
            htmlSanitizer.sanitize(signature)
        } else {
            signature
        }
    }

    private fun wrapWithSignatureClass(html: String): String {
        if (html.contains("k9mail-signature", ignoreCase = true)) {
            return html
        }
        return """<div class="k9mail-signature">$html</div>"""
    }
}
