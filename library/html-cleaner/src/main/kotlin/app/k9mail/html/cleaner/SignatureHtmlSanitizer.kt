package app.k9mail.html.cleaner

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

/**
 * Sanitizes HTML email signatures for outbound use.
 *
 * The allow-list is intentionally aligned with formatting that modern
 * Outlook (Windows), Gmail, and Apple Mail reliably support:
 * text styles (bold/italic/underline/strike), web-safe fonts and sizes,
 * text/background colors, lists, alignment, links, tables, horizontal rules,
 * and PNG/JPEG images. Scripts, stylesheets, remote tracking pixels via
 * unsupported schemes, and exotic markup are removed.
 */
class SignatureHtmlSanitizer {
    private val cleaner = Cleaner(createSafelist())

    fun sanitize(html: String): String {
        val dirtyDocument = Jsoup.parseBodyFragment(html)
        val cleanedDocument = cleaner.clean(dirtyDocument)
        normalizeImages(cleanedDocument)
        stripDisallowedInlineStyles(cleanedDocument)
        return bodyHtml(cleanedDocument)
    }

    private fun normalizeImages(document: Document) {
        document.select("img").forEach { img ->
            val src = img.attr("src").trim()
            if (!isAllowedImageSource(src)) {
                img.remove()
                return@forEach
            }
            // Keep only attributes email clients commonly honor.
            val alt = img.attr("alt")
            val width = img.attr("width")
            val height = img.attr("height")
            img.clearAttributes()
            img.attr("src", src)
            if (alt.isNotEmpty()) img.attr("alt", alt)
            if (width.isNotEmpty()) img.attr("width", width)
            if (height.isNotEmpty()) img.attr("height", height)
        }
    }

    private fun stripDisallowedInlineStyles(document: Document) {
        document.select("[style]").forEach { element ->
            val allowed = filterInlineStyle(element.attr("style"))
            if (allowed.isEmpty()) {
                element.removeAttr("style")
            } else {
                element.attr("style", allowed)
            }
        }
    }

    private fun filterInlineStyle(style: String): String {
        return style.split(';')
            .map { it.trim() }
            .filter { declaration ->
                val property = declaration.substringBefore(':').trim().lowercase()
                property in ALLOWED_STYLE_PROPERTIES
            }
            .joinToString("; ")
    }

    private fun bodyHtml(document: Document): String {
        document.outputSettings()
            .prettyPrint(false)
            .indentAmount(0)
        return document.body().html().trim()
    }

    private fun isAllowedImageSource(src: String): Boolean {
        val lower = src.lowercase()
        return when {
            lower.startsWith("cid:") -> true
            lower.startsWith("data:image/png;base64,") -> true
            lower.startsWith("data:image/jpeg;base64,") -> true
            lower.startsWith("data:image/jpg;base64,") -> true
            else -> false
        }
    }

    companion object {
        private val ALLOWED_STYLE_PROPERTIES = setOf(
            "color",
            "background-color",
            "font-family",
            "font-size",
            "font-weight",
            "font-style",
            "text-align",
            "text-decoration",
            "line-height",
            "margin",
            "margin-top",
            "margin-right",
            "margin-bottom",
            "margin-left",
            "padding",
            "padding-top",
            "padding-right",
            "padding-bottom",
            "padding-left",
            "width",
            "height",
            "max-width",
            "border",
            "border-width",
            "border-style",
            "border-color",
        )

        private fun createSafelist(): Safelist {
            return Safelist.none()
                .addTags(
                    "div", "span", "p", "br", "hr",
                    "b", "strong", "i", "em", "u", "s", "strike", "sub", "sup",
                    "a", "img",
                    "ul", "ol", "li",
                    "font",
                    "table", "tbody", "thead", "tr", "td", "th",
                )
                .addAttributes("a", "href", "title")
                .addAttributes("img", "src", "alt", "width", "height")
                .addAttributes("font", "color", "face", "size")
                .addAttributes("td", "align", "valign", "width", "height", "colspan", "rowspan")
                .addAttributes("th", "align", "valign", "width", "height", "colspan", "rowspan")
                .addAttributes("table", "width", "border", "cellpadding", "cellspacing", "align")
                .addAttributes("div", "align")
                .addAttributes("p", "align")
                .addAttributes(":all", "class", "style", "dir")
                .addProtocols("a", "href", "http", "https", "mailto")
                .addProtocols("img", "src", "cid", "data")
        }
    }
}
