package app.k9mail.library.signatureeditor

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class SignatureImageHostClientTest {
    @Test
    fun `privateIdentityPresentationMessage matches tamayo wire format`() {
        val nonce = ByteArray(32) { it.toByte() }
        val digest = ByteArray(32) { (100 + it).toByte() }

        val message = SignatureImageHostClient.privateIdentityPresentationMessage(
            origin = "sigbird-signature-upload",
            nonce = nonce,
            tokenDigest = digest,
            issuedAt = 1_700_000_000L,
        )

        // Reference hex from github.com/maceip/tamayo tokenprofile.PrivateIdentityPresentationMessage
        val expected = hex(
            "6561742d706173732f7076742d706f700000000018736967626972642d7369676e61747572652d" +
                "75706c6f6164000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" +
                "6465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f80818283000000006553f100",
        )
        assertThat(message).isEqualTo(expected)
    }

    @Test
    fun `isAllowedHostedImageUrl only accepts tokens public computer https paths`() {
        assertThat(
            SignatureImageHostClient.isAllowedHostedImageUrl(
                "https://tokens.public.computer/v1/dev-get/sig/2026/07/abcd/obj.webp",
            ),
        ).isTrue()
        assertThat(
            SignatureImageHostClient.isAllowedHostedImageUrl("https://evil.example/x.webp"),
        ).isFalse()
        assertThat(
            SignatureImageHostClient.isAllowedHostedImageUrl(
                "https://user@tokens.public.computer/x.webp",
            ),
        ).isFalse()
        assertThat(
            SignatureImageHostClient.isAllowedHostedImageUrl("http://tokens.public.computer/x.webp"),
        ).isFalse()
        assertThat(
            SignatureImageHostClient.isAllowedHostedImageUrl("data:image/webp;base64,AA=="),
        ).isFalse()
    }

    private fun hex(value: String): ByteArray {
        val clean = value.replace(" ", "")
        require(clean.length % 2 == 0)
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
