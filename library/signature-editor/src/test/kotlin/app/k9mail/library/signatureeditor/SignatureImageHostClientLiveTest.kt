package app.k9mail.library.signatureeditor

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import java.io.ByteArrayOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Live upload against tokens.public.computer (no issuer keys in the app).
 * Skips when the gateway is unreachable so offline CI stays green.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SignatureImageHostClientLiveTest {
    @Test
    fun `upload webp then save reopen keeps public_url`() {
        assumeTrue("gateway reachable", gatewayReachable())

        val webp = createTinyWebp()
        assertThat(webp.size).isLessThanOrEqualTo(SignatureInlineImages.MAX_ENCODED_BYTES)

        val client = SignatureImageHostClient(
            baseUrl = BuildConfig.SIGNATURE_GATEWAY_BASE,
            challengePrefix = BuildConfig.SIGNATURE_CHALLENGE_PREFIX,
        )
        val publicUrl = client.uploadWebp(webp)

        assertThat(publicUrl).startsWith("https://")
        assertThat(
            publicUrl.contains("tokens.public.computer/") ||
                publicUrl.contains("d2emmektbjgoev.cloudfront.net/"),
        ).isTrue()
        assertThat(publicUrl).contains(".webp")
        assertThat(SignatureImageHostClient.isAllowedHostedImageUrl(publicUrl)).isTrue()

        val html = """<div>Live<img src="$publicUrl" alt="sig"></div>"""
        val saved = SignatureStorage.sanitizeForStorage(html).orEmpty()
        val reopened = SignatureStorage.prepareForEditing(saved)

        assertThat(saved).contains(publicUrl)
        assertThat(reopened).contains(publicUrl)
    }

    private fun gatewayReachable(): Boolean {
        return runCatching {
            val request = okhttp3.Request.Builder()
                .url(BuildConfig.SIGNATURE_GATEWAY_BASE.trimEnd('/') + "/healthz")
                .get()
                .build()
            okhttp3.OkHttpClient().newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun createTinyWebp(): ByteArray {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val output = ByteArrayOutputStream()
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        check(bitmap.compress(format, 70, output))
        bitmap.recycle()
        return output.toByteArray()
    }
}
