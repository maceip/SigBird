package net.thunderbird.app.common.account

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.fsck.k9.notification.NotificationChannelManager
import com.fsck.k9.notification.NotificationSettingsUpdater
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountNotificationChannelType
import net.thunderbird.feature.account.settings.api.AccountSettingsNotificationBridge

internal class DefaultAccountSettingsNotificationBridge(
    private val accountManager: LegacyAccountDtoManager,
    private val notificationChannelManager: NotificationChannelManager,
    private val notificationSettingsUpdater: NotificationSettingsUpdater,
) : AccountSettingsNotificationBridge {
    override suspend fun syncNotificationSettings(accountId: AccountId) {
        val account = accountManager.getAccount("${accountId.value}") ?: return
        notificationSettingsUpdater.updateNotificationSettings(account)
    }

    override suspend fun getNotificationChannelId(
        accountId: AccountId,
        channelType: AccountNotificationChannelType,
    ): String {
        val account = checkNotNull(accountManager.getAccount("${accountId.value}")) {
            "Account not found: $accountId"
        }
        val legacyChannelType = when (channelType) {
            AccountNotificationChannelType.MESSAGES -> NotificationChannelManager.ChannelType.MESSAGES
            AccountNotificationChannelType.MISCELLANEOUS -> NotificationChannelManager.ChannelType.MISCELLANEOUS
        }
        return notificationChannelManager.getChannelIdFor(account, legacyChannelType)
    }

    override fun openNotificationChannelSettings(context: Context, channelId: String) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }
}
