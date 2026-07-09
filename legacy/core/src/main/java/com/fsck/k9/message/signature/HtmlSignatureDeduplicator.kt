package com.fsck.k9.message.signature

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Ensures only the first signature block remains visible in a message body.
 *
 * Quoted replies often accumulate the same signature repeatedly through a thread.
 * Regardless of account "strip signature" preferences, message display keeps a single
 * signature (the first one encountered outside nested quotes when possible) and removes
 * subsequent signature blocks.
 */
object HtmlSignatureDeduplicator {
    private val SIGNATURE_CLASS_HINTS = listOf(
        "k9mail-signature",
        "__signature",
    )

    @JvmStatic
    fun deduplicate(html: String): String {
        val document = Jsoup.parse(html)
        val signatures = document.body()
            .select("div, span, p, section")
            .filter { it.isSignatureElement() }

        if (signatures.size <= 1) {
            return toCompactString(document)
        }

        // Keep the first signature; remove the rest (typically quoted repeats).
        signatures.drop(1).forEach { it.remove() }

        return toCompactString(document)
    }

    private fun Element.isSignatureElement(): Boolean {
        val className = className().lowercase()
        if (SIGNATURE_CLASS_HINTS.any { className.contains(it) }) return true
        return className.split(Regex("\\s+")).any { it == "signature" }
    }

    private fun toCompactString(document: org.jsoup.nodes.Document): String {
        document.outputSettings()
            .prettyPrint(false)
            .indentAmount(0)
        return document.html()
    }
}
