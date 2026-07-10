package app.k9mail.feature.account.setup.domain.usecase

import app.k9mail.feature.account.setup.domain.usecase.ValidateEmailSignature.ValidateEmailSignatureError
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.core.validation.ValidationError
import org.junit.Test

class ValidateEmailSignatureTest {

    private val testSubject = ValidateEmailSignature()

    @Test
    fun `should succeed when email signature is set`() {
        val result = testSubject.execute("email signature")

        assertThat(result).isInstanceOf<Outcome.Success<Unit>>()
    }

    @Test
    fun `should succeed when email signature is empty`() {
        val result = testSubject.execute("")

        assertThat(result).isInstanceOf<Outcome.Success<Unit>>()
    }

    @Test
    fun `should fail when email signature is blank`() {
        val result = testSubject.execute(" ")

        assertThat(result).isInstanceOf<Outcome.Failure<ValidationError>>()
            .prop(Outcome.Failure<ValidationError>::error)
            .isInstanceOf<ValidateEmailSignatureError.BlankEmailSignature>()
    }

    @Test
    fun `should succeed when html signature is safe`() {
        val result = testSubject.execute("<div><b>Jane Doe</b></div>")

        assertThat(result).isInstanceOf<Outcome.Success<Unit>>()
    }

    @Test
    fun `should succeed when html signature contains inline image`() {
        val result = testSubject.execute(
            """<div><img src="data:image/png;base64,iVBORw0KGgo=" alt="Logo">Jane</div>""",
        )

        assertThat(result).isInstanceOf<Outcome.Success<Unit>>()
    }

    @Test
    fun `should fail when html signature is entirely unsafe`() {
        val result = testSubject.execute(
            """<script>alert(1)</script><img src="https://evil.example/track.png">""",
        )

        assertThat(result).isInstanceOf<Outcome.Failure<ValidationError>>()
            .prop(Outcome.Failure<ValidationError>::error)
            .isInstanceOf<ValidateEmailSignatureError.InvalidEmailSignature>()
    }
}
