package net.thunderbird.app.common.account

import com.fsck.k9.controller.MessagingController
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsFolderRefresh

internal class DefaultAccountSettingsFolderRefresh(
    private val messagingController: MessagingController,
    private val accountManager: net.thunderbird.core.android.account.LegacyAccountDtoManager,
) : AccountSettingsFolderRefresh {
    override fun refreshFolderList(accountId: AccountId) {
        val account = accountManager.getAccount("${accountId.value}") ?: return
        messagingController.refreshFolderList(account)
    }
}
