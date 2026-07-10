package app.k9mail.library.signatureeditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Encodes and downscales inline signature images so the WYSIWYG editor stays responsive.
 *
 * Large data-URI images (often phone photos) previously caused multi-second freezes,
 * Binder transaction failures when editing identities, and unusable typing lag because
 * every keystroke re-serialized megabytes of base64 through the WebView bridge.
 */
internal object SignatureInlineImages {
    const val MAX_DIMENSION_PX = 480
    const val MAX_ENCODED_BYTES = 120_000
    const val MAX_SOURCE_BYTES = 8_000_000
    const val JPEG_QUALITY = 72
    private const val JPEG_RETRY_QUALITY = 57
    private const val MAX_ABSOLUTE_ENCODED_BYTES = MAX_ENCODED_BYTES * 2

    private val DATA_URI_REGEX = Regex(
        pattern = """(?i)(data:image/(?:png|jpe?g);base64,)([A-Za-z0-9+/=]+)""",
    )

    @Suppress("ReturnCount")
    fun encodeBytes(bytes: ByteArray, mimeHint: String?): String? {
        if (bytes.isEmpty() || bytes.size > MAX_SOURCE_BYTES) {
            return null
        }

        val normalizedMime = mimeHint?.lowercase()
        val canKeepOriginal = bytes.size <= MAX_ENCODED_BYTES && !exceedsMaxDimension(bytes)
        val originalDataUri = when {
            !canKeepOriginal -> null

            normalizedMime == "image/png" -> toDataUri("image/png", bytes)

            normalizedMime == "image/jpeg" || normalizedMime == "image/jpg" ->
                toDataUri("image/jpeg", bytes)

            else -> null
        }
        return originalDataUri ?: compressBitmap(bytes)
    }

    /**
     * Re-encodes oversized data-URI images already present in stored signature HTML.
     */
    fun optimizeHtml(html: String): String {
        if (html.length < MAX_ENCODED_BYTES || !html.contains("data:image", ignoreCase = true)) {
            return html
        }

        return DATA_URI_REGEX.replace(html) { match ->
            val base64 = match.groupValues[2]
            val decoded = try {
                Base64.decode(base64, Base64.DEFAULT)
            } catch (_: IllegalArgumentException) {
                return@replace match.value
            }

            if (decoded.size <= MAX_ENCODED_BYTES && !exceedsMaxDimension(decoded)) {
                return@replace match.value
            }

            compressBitmap(decoded) ?: match.value
        }
    }

    private fun exceedsMaxDimension(bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return false
        }
        return bounds.outWidth > MAX_DIMENSION_PX || bounds.outHeight > MAX_DIMENSION_PX
    }

    @Suppress("ReturnCount")
    private fun compressBitmap(bytes: ByteArray): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: return null

        val scaled = scaleToMaxDimension(decoded, MAX_DIMENSION_PX)
        if (scaled !== decoded) {
            decoded.recycle()
        }

        val output = ByteArrayOutputStream()
        val hasAlpha = scaled.hasAlpha()
        val compressed = if (hasAlpha) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
        } else {
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
        scaled.recycle()
        if (!compressed) {
            return null
        }

        var encoded = output.toByteArray()
        if (!hasAlpha && encoded.size > MAX_ENCODED_BYTES) {
            encoded = recompressSmaller(encoded, output) ?: return null
        }

        if (encoded.size > MAX_ABSOLUTE_ENCODED_BYTES) {
            // Still enormous (e.g. complex PNG); drop rather than freeze the UI.
            return null
        }

        val mime = if (hasAlpha) "image/png" else "image/jpeg"
        return toDataUri(mime, encoded)
    }

    private fun recompressSmaller(encoded: ByteArray, output: ByteArrayOutputStream): ByteArray? {
        output.reset()
        val retry = BitmapFactory.decodeByteArray(encoded, 0, encoded.size) ?: return null
        val smaller = scaleToMaxDimension(retry, MAX_DIMENSION_PX / 2)
        if (smaller !== retry) {
            retry.recycle()
        }
        smaller.compress(Bitmap.CompressFormat.JPEG, JPEG_RETRY_QUALITY, output)
        smaller.recycle()
        return output.toByteArray()
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
