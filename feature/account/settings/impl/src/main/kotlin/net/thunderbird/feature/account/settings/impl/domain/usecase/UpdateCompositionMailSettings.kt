package net.thunderbird.feature.account.settings.impl.domain.usecase

import kotlinx.coroutines.flow.firstOrNull
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountRepository
import net.thunderbird.core.android.account.MessageFormat
import net.thunderbird.core.android.account.QuoteStyle
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UpdateCompositionMailSettingsCommand
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase

internal class UpdateCompositionMailSettings(
    private val repository: LegacyAccountRepository,
) : UseCase.UpdateCompositionMailSettings {
    override suspend fun invoke(
        accountId: AccountId,
        command: UpdateCompositionMailSettingsCommand,
    ): Outcome<Unit, AccountSettingsDomainContract.AccountSettingError> {
        return repository.getById(accountId).firstOrNull()?.let { account: LegacyAccount ->
            val updated = when (command) {
                is UpdateCompositionMailSettingsCommand.UpdateMessageFormat ->
                    account.copy(messageFormat = MessageFormat.valueOf(command.value))

                is UpdateCompositionMailSettingsCommand.UpdateAlwaysShowCcBcc ->
                    account.copy(isAlwaysShowCcBcc = command.value)

                is UpdateCompositionMailSettingsCommand.UpdateMessageReadReceipt ->
                    account.copy(isMessageReadReceipt = command.value)

                is UpdateCompositionMailSettingsCommand.UpdateQuoteStyle ->
                    account.copy(quoteStyle = QuoteStyle.valueOf(command.value))

                is UpdateCompositionMailSettingsCommand.UpdateDefaultQuotedTextShown ->
                    account.copy(isDefaultQuotedTextShown = command.value)

                is UpdateCompositionMailSettingsCommand.UpdateReplyAfterQuote ->
                    account.copy(isReplyAfterQuote = command.value)

                is UpdateCompositionMailSettingsCommand.UpdateStripSignature ->
                    account.copy(isStripSignature = command.value)

                is UpdateCompositionMailSettingsCommand.UpdateQuotePrefix ->
                    account.copy(quotePrefix = command.value)

                is UpdateCompositionMailSettingsCommand.UpdateUploadSentMessages ->
                    account.copy(isUploadSentMessages = command.value)
            }
            repository.update(updated)
            Outcome.success(Unit)
        } ?: Outcome.failure(AccountSettingsDomainContract.AccountSettingError.NotFound("Account not found"))
    }
}
