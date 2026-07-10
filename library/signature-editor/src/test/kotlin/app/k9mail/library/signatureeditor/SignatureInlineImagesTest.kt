package app.k9mail.library.signatureeditor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import java.io.ByteArrayOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SignatureInlineImagesTest {
    @Test
    fun `encodeBytes downscales large bitmap under size budget`() {
        val largeJpeg = createNoisyJpeg(width = 2000, height = 1500)

        val dataUri = SignatureInlineImages.encodeBytes(largeJpeg, "image/jpeg")

        assertThat(dataUri).isNotNull()
        val encoded = requireNotNull(dataUri)
        assertThat(encoded).contains("data:image/")
        val base64 = encoded.substringAfter("base64,")
        val decoded = Base64.decode(base64, Base64.DEFAULT)
        assertThat(decoded.size).isLessThan(SignatureInlineImages.MAX_ENCODED_BYTES * 2)
        assertThat(decoded.size).isLessThan(largeJpeg.size)
    }

    @Test
    fun `optimizeHtml rewrites oversized data uri images`() {
        val largeJpeg = createNoisyJpeg(width = 1600, height = 1200)
        assertThat(largeJpeg.size > SignatureInlineImages.MAX_ENCODED_BYTES).isTrue()
        val hugeBase64 = Base64.encodeToString(largeJpeg, Base64.NO_WRAP)
        val html = """<div>Hi<img src="data:image/jpeg;base64,$hugeBase64" alt="x"></div>"""

        val optimized = SignatureInlineImages.optimizeHtml(html)

        assertThat(optimized.length).isLessThan(html.length)
        assertThat(optimized).contains("data:image/")
        assertThat(optimized.contains("Hi")).isTrue()
    }

    @Test
    fun `optimizeHtml leaves small signatures unchanged`() {
        val html = """<div>Small<img src="data:image/png;base64,iVBORw0KGgo=" alt="x"></div>"""

        assertThat(SignatureInlineImages.optimizeHtml(html)).contains("iVBORw0KGgo=")
    }

    private fun createNoisyJpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        // High-frequency rectangles keep JPEG size large enough to exercise downscaling.
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
