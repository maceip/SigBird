package net.thunderbird.feature.account.settings.api

import net.thunderbird.feature.account.AccountId

/**
 * Provides account-specific capability checks for account settings screens.
 */
interface AccountSettingsCapabilities {
    suspend fun supportsFolderSubscriptions(accountId: AccountId): Boolean

    suspend fun isMoveCapable(accountId: AccountId): Boolean

    fun hasVibrator(): Boolean
}
