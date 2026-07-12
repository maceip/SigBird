package app.k9mail.library.signatureeditor

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import java.security.MessageDigest
import java.util.Base64
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end client flow against a mock gateway, proving holder-key custody:
 * the mint request carries only a public key, no seed is ever accepted from
 * the wire, and the upload presentation verifies under the key the client
 * generated locally.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SignatureImageHostClientMockTest {
    private val origin = "sigbird-signature-upload"
    private val nonce = ByteArray(32) { it.toByte() }
    private val tokenBytes = ByteArray(96) { (it * 3).toByte() }

    @Test
    fun `holder key is generated on device and signs the presentation`() {
        var mintRequest: JSONObject? = null
        var uploadRequest: JSONObject? = null

        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path == "/v1/sessions" -> jsonResponse(
                            JSONObject()
                                .put("session_id", "s1")
                                .put("origin", origin)
                                .put("presentation_nonce_b64", b64Url(nonce)),
                        )

                        path.endsWith("/assisted-mint") -> {
                            val body = JSONObject(request.body.readUtf8())
                            mintRequest = body
                            jsonResponse(
                                JSONObject()
                                    .put("token_b64", b64Url(tokenBytes))
                                    .put("holder_alg", "ed25519")
                                    .put("holder_pub_b64", body.getString("holder_pub_b64")),
                            )
                        }

                        path == "/v1/uploads" -> {
                            uploadRequest = JSONObject(request.body.readUtf8())
                            jsonResponse(
                                JSONObject()
                                    .put("upload_url", server.url("/v1/dev-put/sig/k.webp").toString())
                                    .put("public_url", "https://d2emmektbjgoev.cloudfront.net/sig/k.webp"),
                            )
                        }

                        path.startsWith("/v1/dev-put/") -> MockResponse().setResponseCode(204)
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            server.start()

            val client = SignatureImageHostClient(
                baseUrl = server.url("/").toString(),
                challengePrefix = origin,
            )
            val publicUrl = client.uploadWebp(ByteArray(512) { 7 })
            assertThat(publicUrl).isEqualTo("https://d2emmektbjgoev.cloudfront.net/sig/k.webp")
        }

        val mint = requireNotNull(mintRequest)
        val upload = requireNotNull(uploadRequest)

        val holderPub = b64UrlDecode(mint.getString("holder_pub_b64"))
        assertThat(holderPub.size).isEqualTo(32)
        assertThat(mint.has("holder_seed_b64")).isFalse()

        val message = SignatureImageHostClient.privateIdentityPresentationMessage(
            origin = origin,
            nonce = nonce,
            tokenDigest = MessageDigest.getInstance("SHA-256").digest(tokenBytes),
            issuedAt = upload.getLong("issued_at"),
        )
        val signature = b64UrlDecode(upload.getString("signature_b64"))
        assertThat(verifyEd25519(holderPub, message, signature)).isTrue()
    }

    @Test
    fun `client rejects a gateway that returns a holder seed`() {
        var failure: Throwable? = null

        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path == "/v1/sessions" -> jsonResponse(
                            JSONObject()
                                .put("session_id", "s1")
                                .put("origin", origin)
                                .put("presentation_nonce_b64", b64Url(nonce)),
                        )

                        path.endsWith("/assisted-mint") -> {
                            val body = JSONObject(request.body.readUtf8())
                            jsonResponse(
                                JSONObject()
                                    .put("token_b64", b64Url(tokenBytes))
                                    .put("holder_pub_b64", body.getString("holder_pub_b64"))
                                    .put("holder_seed_b64", b64Url(ByteArray(32))),
                            )
                        }

                        else -> MockResponse().setResponseCode(500)
                    }
                }
            }
            server.start()

            val client = SignatureImageHostClient(
                baseUrl = server.url("/").toString(),
                challengePrefix = origin,
            )
            failure = runCatching { client.uploadWebp(ByteArray(16) { 1 }) }.exceptionOrNull()
        }

        assertThat(failure).isNotNull()
    }

    private fun jsonResponse(body: JSONObject): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("content-type", "application/json")
            .setBody(body.toString())

    private fun verifyEd25519(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        val spec = EdDSANamedCurveTable.getByName("Ed25519") ?: error("Ed25519 curve missing")
        val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
        engine.initVerify(EdDSAPublicKey(EdDSAPublicKeySpec(publicKey, spec)))
        engine.update(message)
        return engine.verify(signature)
    }

    private fun b64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun b64UrlDecode(value: String): ByteArray =
        Base64.getUrlDecoder().decode(value)
}
