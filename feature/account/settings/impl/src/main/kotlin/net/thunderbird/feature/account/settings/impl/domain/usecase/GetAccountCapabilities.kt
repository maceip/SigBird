package net.thunderbird.feature.account.settings.impl.domain.usecase

import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsCapabilities
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.AccountCapabilities
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase

internal class GetAccountCapabilities(
    private val capabilities: AccountSettingsCapabilities,
) : UseCase.GetAccountCapabilities {
    override suspend fun invoke(
        accountId: AccountId,
    ): Outcome<AccountCapabilities, AccountSettingsDomainContract.AccountSettingError> {
        return Outcome.success(
            AccountCapabilities(
                supportsFolderSubscriptions = capabilities.supportsFolderSubscriptions(accountId),
                isMoveCapable = capabilities.isMoveCapable(accountId),
                hasVibrator = capabilities.hasVibrator(),
            ),
        )
    }
}
