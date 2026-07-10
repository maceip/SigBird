package net.thunderbird.feature.account.settings.api

import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.mail.folder.api.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder

data class RemoteFolderSettingsInfo(
    val folders: List<RemoteFolder>,
    val automaticSpecialFolders: Map<FolderType, RemoteFolder?>,
)

/**
 * Loads remote folder information for account folder settings.
 */
interface AccountSettingsFolderProvider {
    suspend fun getRemoteFolders(accountId: AccountId): RemoteFolderSettingsInfo
}
