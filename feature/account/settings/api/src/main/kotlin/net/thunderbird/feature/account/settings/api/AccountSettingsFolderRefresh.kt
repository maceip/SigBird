package net.thunderbird.feature.account.settings.api

import net.thunderbird.feature.account.AccountId

/**
 * Triggers a remote folder list refresh for an account.
 */
fun interface AccountSettingsFolderRefresh {
    fun refreshFolderList(accountId: AccountId)
}
