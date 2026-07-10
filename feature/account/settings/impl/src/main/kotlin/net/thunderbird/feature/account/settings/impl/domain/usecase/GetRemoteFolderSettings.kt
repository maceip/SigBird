package net.thunderbird.feature.account.settings.impl.domain.usecase

import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsFolderProvider
import net.thunderbird.feature.account.settings.api.RemoteFolderSettingsInfo
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase

internal class GetRemoteFolderSettings(
    private val folderProvider: AccountSettingsFolderProvider,
) : UseCase.GetRemoteFolderSettings {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun invoke(
        accountId: AccountId,
    ): Outcome<RemoteFolderSettingsInfo, AccountSettingsDomainContract.AccountSettingError> {
        return try {
            Outcome.success(folderProvider.getRemoteFolders(accountId))
        } catch (e: Exception) {
            Outcome.failure(
                AccountSettingsDomainContract.AccountSettingError.StorageError(
                    message = e.message ?: "Failed to load folders",
                ),
            )
        }
    }
}
