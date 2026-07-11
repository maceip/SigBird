package app.k9mail.library.signatureeditor

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Uploads a WebP signature image to the tokens gateway using a tamayo
 * private-identity token (assisted-mint in staging).
 *
 * Base URL and challenge origin come from [BuildConfig] — issuer keys stay
 * on the gateway, never in the app.
 */
class SignatureImageHostClient(
    private val baseUrl: String = BuildConfig.SIGNATURE_GATEWAY_BASE,
    private val challengePrefix: String = BuildConfig.SIGNATURE_CHALLENGE_PREFIX,
    private val httpClient: OkHttpClient = defaultClient(),
) {
    /**
     * Encodes nothing — caller passes WebP bytes ≤ 256 KiB.
     * Returns the public HTTPS URL for the uploaded object.
     */
    @Suppress("ReturnCount", "LongMethod")
    fun uploadWebp(webpBytes: ByteArray): String {
        require(webpBytes.isNotEmpty()) { "empty webp" }
        require(webpBytes.size <= SignatureInlineImages.MAX_ENCODED_BYTES) {
            "webp exceeds ${SignatureInlineImages.MAX_ENCODED_BYTES} bytes"
        }

        val session = postJson("/v1/sessions", JSONObject())
        val sessionId = session.getString("session_id")
        val origin = session.getString("origin")
        val nonceB64 = session.getString("presentation_nonce_b64")
        require(origin == challengePrefix) {
            "unexpected session origin '$origin' (expected '$challengePrefix')"
        }

        val mint = postJson("/v1/sessions/$sessionId/assisted-mint", JSONObject())
        val tokenB64 = mint.getString("token_b64")
        val holderSeedB64 = mint.getString("holder_seed_b64")

        val tokenBytes = b64Decode(tokenB64)
        val tokenDigest = sha256(tokenBytes)
        val nonce = b64Decode(nonceB64)
        require(nonce.size == DIGEST_BYTES) { "presentation nonce must be $DIGEST_BYTES bytes" }

        val issuedAt = System.currentTimeMillis() / 1000L
        val message = privateIdentityPresentationMessage(origin, nonce, tokenDigest, issuedAt)
        val signature = signEd25519(b64Decode(holderSeedB64), message)

        val shaHex = sha256(webpBytes).joinToString("") { "%02x".format(it) }
        val uploadReq = JSONObject()
            .put("session_id", sessionId)
            .put("token_b64", tokenB64)
            .put("signature_b64", b64Encode(signature))
            .put("issued_at", issuedAt)
            .put("content_sha256_hex", shaHex)
            .put("content_length", webpBytes.size)
            .put("content_type", WEBP_MIME)

        val upload = postJson("/v1/uploads", uploadReq)
        val uploadUrl = upload.getString("upload_url")
        val publicUrl = upload.getString("public_url")

        val put = Request.Builder()
            .url(uploadUrl)
            .put(webpBytes.toRequestBody(WEBP_MIME.toMediaType()))
            .header("content-type", WEBP_MIME)
            .build()
        httpClient.newCall(put).execute().use { response ->
            if (!response.isSuccessful) {
                error("PUT failed: HTTP ${response.code}")
            }
        }
        return publicUrl
    }

    private fun postJson(path: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(body.toString().toRequestBody(JSON_MIME.toMediaType()))
            .header("content-type", JSON_MIME)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("POST $path failed: HTTP ${response.code} $text")
            }
            return JSONObject(text)
        }
    }

    companion object {
        /** Fallback host string for allow-list checks outside BuildConfig. */
        const val ALLOWED_HOST = "tokens.public.computer"
        private const val WEBP_MIME = "image/webp"
        private const val JSON_MIME = "application/json"
        private const val POP_DOMAIN = "eat-pass/pvt-pop\u0000"
        private const val DIGEST_BYTES = 32
        private const val ORIGIN_LEN_BYTES = 4
        private const val ISSUED_AT_BYTES = 8
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val WRITE_TIMEOUT_SECONDS = 60L
        private const val ED25519_SEED_BYTES = 32

        fun isAllowedHostedImageUrl(src: String): Boolean {
            val lower = src.trim().lowercase()
            val hasHostPrefix = lower.startsWith("https://$ALLOWED_HOST/")
            // Block credentials / userinfo tricks.
            val hasCredentialTricks = lower.contains("@") || lower.contains("\\")
            return hasHostPrefix && !hasCredentialTricks
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        /** Wire-compatible with tamayo tokenprofile.PrivateIdentityPresentationMessage. */
        internal fun privateIdentityPresentationMessage(
            origin: String,
            nonce: ByteArray,
            tokenDigest: ByteArray,
            issuedAt: Long,
        ): ByteArray {
            require(nonce.size == DIGEST_BYTES)
            require(tokenDigest.size == DIGEST_BYTES)
            val originBytes = origin.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(
                POP_DOMAIN.length + ORIGIN_LEN_BYTES + originBytes.size +
                    DIGEST_BYTES + DIGEST_BYTES + ISSUED_AT_BYTES,
            ).order(ByteOrder.BIG_ENDIAN)
            buffer.put(POP_DOMAIN.toByteArray(Charsets.ISO_8859_1))
            buffer.putInt(originBytes.size)
            buffer.put(originBytes)
            buffer.put(nonce)
            buffer.put(tokenDigest)
            buffer.putLong(issuedAt)
            return buffer.array()
        }

        private fun signEd25519(seed: ByteArray, message: ByteArray): ByteArray {
            require(seed.size == ED25519_SEED_BYTES) { "ed25519 seed must be $ED25519_SEED_BYTES bytes" }
            val spec = EdDSANamedCurveTable.getByName("Ed25519")
                ?: error("Ed25519 curve missing")
            val privateKey = EdDSAPrivateKey(EdDSAPrivateKeySpec(seed, spec))
            val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
            engine.initSign(privateKey)
            engine.update(message)
            return engine.sign()
        }

        private fun sha256(bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun b64Encode(bytes: ByteArray): String =
            android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING,
            )

        private fun b64Decode(value: String): ByteArray {
            val flags = android.util.Base64.URL_SAFE or
                android.util.Base64.NO_WRAP or
                android.util.Base64.NO_PADDING
            return try {
                android.util.Base64.decode(value, flags)
            } catch (_: IllegalArgumentException) {
                android.util.Base64.decode(value, android.util.Base64.DEFAULT)
            }
        }
    }
}
