package app.k9mail.feature.account.setup.domain.usecase

import app.k9mail.feature.account.setup.domain.DomainContract
import app.k9mail.feature.account.setup.domain.usecase.ValidateEmailSignature.ValidateEmailSignatureError.BlankEmailSignature
import app.k9mail.feature.account.setup.domain.usecase.ValidateEmailSignature.ValidateEmailSignatureError.InvalidEmailSignature
import app.k9mail.library.signatureeditor.SignatureStorage
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.core.validation.ValidationError
import net.thunderbird.core.validation.ValidationOutcome
import net.thunderbird.core.validation.ValidationSuccess

internal class ValidateEmailSignature : DomainContract.UseCase.ValidateEmailSignature {

    override fun execute(emailSignature: String): ValidationOutcome {
        return when {
            emailSignature.isEmpty() -> ValidationSuccess
            emailSignature.isBlank() -> Outcome.Failure(error = BlankEmailSignature)
            SignatureStorage.isHtml(emailSignature) -> validateHtmlSignature(emailSignature)
            else -> ValidationSuccess
        }
    }

    private fun validateHtmlSignature(emailSignature: String): ValidationOutcome {
        val sanitized = SignatureStorage.sanitizeForStorage(emailSignature).orEmpty()
        return if (sanitized.isBlank()) {
            Outcome.Failure(error = InvalidEmailSignature)
        } else {
            ValidationSuccess
        }
    }

    sealed interface ValidateEmailSignatureError : ValidationError {
        data object BlankEmailSignature : ValidateEmailSignatureError
        data object InvalidEmailSignature : ValidateEmailSignatureError
    }
}
