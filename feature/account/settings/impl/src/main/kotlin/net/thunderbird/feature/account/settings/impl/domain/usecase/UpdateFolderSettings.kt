package net.thunderbird.feature.account.settings.impl.domain.usecase

import kotlinx.coroutines.flow.firstOrNull
import net.thunderbird.core.android.account.LegacyAccountRepository
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsFolderRefresh
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UpdateFolderSettingsCommand
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase
import net.thunderbird.feature.mail.folder.api.SpecialFolderSelection

internal class UpdateFolderSettings(
    private val repository: LegacyAccountRepository,
    private val folderRefresh: AccountSettingsFolderRefresh,
) : UseCase.UpdateFolderSettings {
    override suspend fun invoke(
        accountId: AccountId,
        command: UpdateFolderSettingsCommand,
    ): Outcome<Unit, AccountSettingsDomainContract.AccountSettingError> {
        return repository.getById(accountId).firstOrNull()?.let { account ->
            val updatedAccount = when (command) {
                is UpdateFolderSettingsCommand.UpdateAutoExpandFolder -> {
                    account.copy(autoExpandFolderId = command.folderId)
                }

                is UpdateFolderSettingsCommand.UpdateSubscribedFoldersOnly -> {
                    if (account.isSubscribedFoldersOnly != command.value) {
                        folderRefresh.refreshFolderList(accountId)
                    }
                    account.copy(isSubscribedFoldersOnly = command.value)
                }

                is UpdateFolderSettingsCommand.UpdateArchiveFolder -> {
                    account.copy(
                        archiveFolderId = command.folderId,
                        archiveFolderSelection = command.selection,
                    )
                }

                is UpdateFolderSettingsCommand.UpdateDraftsFolder -> {
                    account.copy(
                        draftsFolderId = command.folderId,
                        draftsFolderSelection = command.selection,
                    )
                }

                is UpdateFolderSettingsCommand.UpdateSentFolder -> {
                    account.copy(
                        sentFolderId = command.folderId,
                        sentFolderSelection = command.selection,
                    )
                }

                is UpdateFolderSettingsCommand.UpdateSpamFolder -> {
                    account.copy(
                        spamFolderId = command.folderId,
                        spamFolderSelection = command.selection,
                    )
                }

                is UpdateFolderSettingsCommand.UpdateTrashFolder -> {
                    account.copy(
                        trashFolderId = command.folderId,
                        trashFolderSelection = command.selection,
                    )
                }
            }
            repository.update(updatedAccount)
            Outcome.success(Unit)
        } ?: Outcome.failure(AccountSettingsDomainContract.AccountSettingError.NotFound("Account not found"))
    }
}
