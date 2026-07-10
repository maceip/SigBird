package net.thunderbird.app.common.account

import com.fsck.k9.controller.MessagingController
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsCapabilities

internal class DefaultAccountSettingsCapabilities(
    private val accountManager: LegacyAccountDtoManager,
    private val messagingController: MessagingController,
    private val vibrator: com.fsck.k9.ui.settings.account.Vibrator,
) : AccountSettingsCapabilities {
    override suspend fun supportsFolderSubscriptions(accountId: AccountId): Boolean {
        val account = accountManager.getAccount("${accountId.value}") ?: return false
        return messagingController.supportsFolderSubscriptions(account)
    }

    override suspend fun isMoveCapable(accountId: AccountId): Boolean {
        val account = accountManager.getAccount("${accountId.value}") ?: return false
        return messagingController.isMoveCapable(account)
    }

    override fun hasVibrator(): Boolean = vibrator.hasVibrator
}
