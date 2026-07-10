package app.k9mail.library.signatureeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.io.ByteArrayOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SignatureInlineImagesTest {
    @Test
    fun `encodeBytes keeps images within two megabyte budget and max dimension`() {
        val jpeg = createNoisyJpeg(width = 800, height = 600)
        assertThat(jpeg.size).isLessThanOrEqualTo(SignatureInlineImages.MAX_ENCODED_BYTES)

        val dataUri = SignatureInlineImages.encodeBytes(jpeg, "image/jpeg")

        assertThat(dataUri).isNotNull()
        val encoded = requireNotNull(dataUri)
        assertThat(encoded).contains("data:image/jpeg;base64,")
        val decoded = Base64.decode(encoded.substringAfter("base64,"), Base64.DEFAULT)
        assertThat(decoded.size).isEqualTo(jpeg.size)
    }

    @Test
    fun `encodeBytes downscales oversized dimensions under two megabyte budget`() {
        val hugeJpeg = createNoisyJpeg(width = 4000, height = 3000)

        val dataUri = SignatureInlineImages.encodeBytes(hugeJpeg, "image/jpeg")

        assertThat(dataUri).isNotNull()
        val encoded = requireNotNull(dataUri)
        val decoded = Base64.decode(encoded.substringAfter("base64,"), Base64.DEFAULT)
        assertThat(decoded.size).isLessThanOrEqualTo(SignatureInlineImages.MAX_ENCODED_BYTES)
        assertThat(decoded.size).isLessThan(hugeJpeg.size)

        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.size, bounds)
        assertThat(maxOf(bounds.outWidth, bounds.outHeight))
            .isLessThanOrEqualTo(SignatureInlineImages.MAX_DIMENSION_PX)
    }

    @Test
    fun `encodeBytes rejects empty and unsupported mime`() {
        assertThat(SignatureInlineImages.encodeBytes(ByteArray(0), "image/jpeg")).isNull()
        assertThat(SignatureInlineImages.encodeBytes(byteArrayOf(1, 2, 3), "image/gif")).isNull()
    }

    @Test
    fun `optimizeHtml rewrites images that exceed max dimension`() {
        val hugeJpeg = createNoisyJpeg(width = 4000, height = 3000)
        val hugeBase64 = Base64.encodeToString(hugeJpeg, Base64.NO_WRAP)
        val html = """<div>Hi<img src="data:image/jpeg;base64,$hugeBase64" alt="x"></div>"""

        val optimized = SignatureInlineImages.optimizeHtml(html)

        assertThat(optimized.length).isLessThan(html.length)
        assertThat(optimized).contains("data:image/")
        assertThat(optimized.contains("Hi")).isTrue()
    }

    @Test
    fun `optimizeHtml leaves images within limits unchanged`() {
        val jpeg = createNoisyJpeg(width = 800, height = 600)
        val base64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val html = """<div>Small<img src="data:image/jpeg;base64,$base64" alt="x"></div>"""

        assertThat(SignatureInlineImages.optimizeHtml(html)).isEqualTo(html)
    }

    private fun createNoisyJpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        for (i in 0 until 400) {
            paint.color = Color.rgb((i * 37) % 256, (i * 91) % 256, (i * 17) % 256)
            val left = ((i * 47) % width).toFloat()
            val top = ((i * 29) % height).toFloat()
            canvas.drawRect(left, top, left + 80f, top + 60f, paint)
        }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        bitmap.recycle()
        return output.toByteArray()
    }
}
