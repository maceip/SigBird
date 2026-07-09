package net.thunderbird.app.common.account

import app.k9mail.legacy.mailstore.FolderRepository
import com.fsck.k9.mailstore.SpecialFolderSelectionStrategy
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsFolderProvider
import net.thunderbird.feature.account.settings.api.RemoteFolderSettingsInfo
import net.thunderbird.feature.mail.folder.api.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder

internal class DefaultAccountSettingsFolderProvider(
    private val accountManager: LegacyAccountDtoManager,
    private val folderRepository: FolderRepository,
    private val specialFolderSelectionStrategy: SpecialFolderSelectionStrategy,
) : AccountSettingsFolderProvider {
    override suspend fun getRemoteFolders(accountId: AccountId): RemoteFolderSettingsInfo {
        val account = checkNotNull(accountManager.getAccount("${accountId.value}")) {
            "Account not found: $accountId"
        }

        val folders = folderRepository.getRemoteFolders(account.id)
            .sortedWith(
                compareByDescending<RemoteFolder> { it.type == FolderType.INBOX }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )

        val automaticSpecialFolders = mapOf(
            FolderType.ARCHIVE to specialFolderSelectionStrategy.selectSpecialFolder(folders, FolderType.ARCHIVE),
            FolderType.DRAFTS to specialFolderSelectionStrategy.selectSpecialFolder(folders, FolderType.DRAFTS),
            FolderType.SENT to specialFolderSelectionStrategy.selectSpecialFolder(folders, FolderType.SENT),
            FolderType.SPAM to specialFolderSelectionStrategy.selectSpecialFolder(folders, FolderType.SPAM),
            FolderType.TRASH to specialFolderSelectionStrategy.selectSpecialFolder(folders, FolderType.TRASH),
        )

        return RemoteFolderSettingsInfo(
            folders = folders,
            automaticSpecialFolders = automaticSpecialFolders,
        )
    }
}
