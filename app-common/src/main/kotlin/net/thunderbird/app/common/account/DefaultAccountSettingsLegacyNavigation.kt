package net.thunderbird.app.common.account

import android.content.Context
import com.fsck.k9.activity.ManageIdentities
import com.fsck.k9.activity.setup.AccountSetupComposition
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsLegacyNavigation

internal class DefaultAccountSettingsLegacyNavigation : AccountSettingsLegacyNavigation {
    override fun launchCompositionDefaults(context: Context, accountId: AccountId) {
        AccountSetupComposition.actionEditCompositionSettings(
            context as android.app.Activity,
            "${accountId.value}",
        )
    }

    override fun launchManageIdentities(context: Context, accountId: AccountId) {
        ManageIdentities.start(context as android.app.Activity, "${accountId.value}")
    }
}
