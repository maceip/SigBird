package net.thunderbird.feature.account.settings.api

import android.content.Context
import net.thunderbird.feature.account.AccountId

/**
 * Launches legacy account settings screens that are not yet available via deep links.
 */
interface AccountSettingsLegacyNavigation {
    fun launchCompositionDefaults(context: Context, accountId: AccountId)
    fun launchManageIdentities(context: Context, accountId: AccountId)
}
