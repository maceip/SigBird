package net.thunderbird.feature.account.settings.api

import android.content.Context
import net.thunderbird.feature.account.AccountId

enum class AccountNotificationChannelType {
    MESSAGES,
    MISCELLANEOUS,
}

/**
 * Bridges legacy notification channel infrastructure for account notification settings.
 */
interface AccountSettingsNotificationBridge {
    suspend fun syncNotificationSettings(accountId: AccountId)

    suspend fun getNotificationChannelId(
        accountId: AccountId,
        channelType: AccountNotificationChannelType,
    ): String

    fun openNotificationChannelSettings(context: Context, channelId: String)
}
