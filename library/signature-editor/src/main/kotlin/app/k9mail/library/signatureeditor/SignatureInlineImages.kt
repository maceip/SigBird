package app.k9mail.library.signatureeditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Encodes signature images as lossy WebP at or under [MAX_ENCODED_BYTES] (256 KiB).
 *
 * Accepts PNG / JPEG / GIF (first frame) / WebP input. Aggressively reduces quality
 * and dimensions until the budget is met (or returns null).
 */
internal object SignatureInlineImages {
    /** Maximum encoded WebP payload (262144 bytes) — matches the upload gateway. */
    const val MAX_ENCODED_BYTES = 262_144

    /** Reject source files larger than this before attempting decode. */
    const val MAX_SOURCE_BYTES = 12_000_000

    /** Starting longest edge before the quality/dimension shrink loop. */
    const val MAX_DIMENSION_PX = 1600

    private const val MIN_DIMENSION_PX = 64
    private const val INITIAL_QUALITY = 82
    private const val MIN_QUALITY = 40
    private const val QUALITY_STEP = 8
    private const val DIMENSION_SHRINK_NUMERATOR = 3
    private const val DIMENSION_SHRINK_DENOMINATOR = 4

    private val DATA_URI_REGEX = Regex(
        pattern = """(?i)(data:image/(?:png|jpe?g|webp|gif);base64,)([A-Za-z0-9+/=]+)""",
    )

    private val SUPPORTED_MIME = setOf(
        "image/png",
        "image/jpeg",
        "image/jpg",
        "image/gif",
        "image/webp",
    )

    /**
     * Encodes [bytes] to a WebP payload ≤ [MAX_ENCODED_BYTES].
     * Returns null if empty, unsupported, undecodable, or impossible under budget.
     */
    @Suppress("ReturnCount")
    fun encodeToWebp(bytes: ByteArray, mimeHint: String?): ByteArray? {
        if (bytes.isEmpty() || bytes.size > MAX_SOURCE_BYTES) {
            return null
        }
        val normalizedMime = mimeHint?.lowercase()
        if (normalizedMime != null && normalizedMime !in SUPPORTED_MIME) {
            return null
        }
        return compressToWebpBudget(bytes)
    }

    /**
     * Data-URI form of [encodeToWebp] for HTML that still embeds images locally
     * (legacy signatures / offline). Prefer hosted URLs for new inserts.
     */
    fun encodeBytes(bytes: ByteArray, mimeHint: String?): String? {
        val webp = encodeToWebp(bytes, mimeHint) ?: return null
        return toDataUri("image/webp", webp)
    }

    /**
     * Re-encodes oversized data-URI images in HTML to WebP under the 256 KiB budget.
     */
    fun optimizeHtml(html: String): String {
        if (!html.contains("data:image", ignoreCase = true)) {
            return html
        }
        return DATA_URI_REGEX.replace(html) { match ->
            val base64 = match.groupValues[2]
            val decoded = try {
                Base64.decode(base64, Base64.DEFAULT)
            } catch (_: IllegalArgumentException) {
                return@replace match.value
            }
            if (decoded.size <= MAX_ENCODED_BYTES &&
                match.groupValues[1].contains("webp", ignoreCase = true) &&
                !exceedsMaxDimension(decoded)
            ) {
                return@replace match.value
            }
            encodeBytes(decoded, null) ?: match.value
        }
    }

    @Suppress("ReturnCount", "ComplexCondition")
    private fun compressToWebpBudget(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        var maxDim = MAX_DIMENSION_PX
        while (maxDim >= MIN_DIMENSION_PX) {
            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                ?: return null
            val scaled = scaleToMaxDimension(decoded, maxDim)
            if (scaled !== decoded) {
                decoded.recycle()
            }

            var quality = INITIAL_QUALITY
            while (quality >= MIN_QUALITY) {
                val encoded = encodeWebp(scaled, quality)
                if (encoded != null && encoded.size <= MAX_ENCODED_BYTES) {
                    scaled.recycle()
                    return encoded
                }
                quality -= QUALITY_STEP
            }
            scaled.recycle()
            maxDim = (maxDim * DIMENSION_SHRINK_NUMERATOR) / DIMENSION_SHRINK_DENOMINATOR
        }
        return null
    }

    private fun encodeWebp(bitmap: Bitmap, quality: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        if (!bitmap.compress(format, quality, output)) {
            return null
        }
        return output.toByteArray()
    }

    private fun exceedsMaxDimension(bytes: ByteArray): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                false
            } else {
                bounds.outWidth > MAX_DIMENSION_PX || bounds.outHeight > MAX_DIMENSION_PX
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun scaleToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) {
            return bitmap
        }
        val scale = maxDimension.toFloat() / largest.toFloat()
        val width = max(1, (bitmap.width * scale).roundToInt())
        val height = max(1, (bitmap.height * scale).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / sample >= maxDimension && halfHeight / sample >= maxDimension) {
            sample *= 2
        }
        return sample
    }

    private fun toDataUri(mimeType: String, bytes: ByteArray): String {
        return "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
