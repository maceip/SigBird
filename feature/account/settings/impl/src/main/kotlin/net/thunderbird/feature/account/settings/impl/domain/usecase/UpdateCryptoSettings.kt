package net.thunderbird.feature.account.settings.impl.domain.usecase

import kotlinx.coroutines.flow.firstOrNull
import net.thunderbird.core.android.account.AccountDefaultsProvider.Companion.NO_OPENPGP_KEY
import net.thunderbird.core.android.account.LegacyAccountRepository
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UpdateCryptoSettingsCommand
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase

internal class UpdateCryptoSettings(
    private val repository: LegacyAccountRepository,
) : UseCase.UpdateCryptoSettings {
    override suspend fun invoke(
        accountId: AccountId,
        command: UpdateCryptoSettingsCommand,
    ): Outcome<Unit, AccountSettingsDomainContract.AccountSettingError> {
        return repository.getById(accountId).firstOrNull()?.let { account ->
            val updatedAccount = when (command) {
                is UpdateCryptoSettingsCommand.UpdateOpenPgpProvider -> {
                    if (command.providerPackage == null) {
                        account.copy(openPgpProvider = null, openPgpKey = NO_OPENPGP_KEY)
                    } else {
                        account.copy(openPgpProvider = command.providerPackage)
                    }
                }

                is UpdateCryptoSettingsCommand.UpdateOpenPgpKey -> {
                    account.copy(openPgpKey = command.keyId)
                }

                is UpdateCryptoSettingsCommand.UpdateAutocryptPreferEncrypt -> {
                    account.copy(autocryptPreferEncryptMutual = command.value)
                }

                is UpdateCryptoSettingsCommand.UpdateHideSignOnly -> {
                    account.copy(isOpenPgpHideSignOnly = command.value)
                }

                is UpdateCryptoSettingsCommand.UpdateEncryptSubject -> {
                    account.copy(isOpenPgpEncryptSubject = command.value)
                }

                is UpdateCryptoSettingsCommand.UpdateEncryptAllDrafts -> {
                    account.copy(isOpenPgpEncryptAllDrafts = command.value)
                }
            }
            repository.update(updatedAccount)
            Outcome.success(Unit)
        } ?: Outcome.failure(AccountSettingsDomainContract.AccountSettingError.NotFound("Account not found"))
    }
}
