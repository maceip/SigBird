package net.thunderbird.app.common.account

import android.app.Activity
import android.content.Context
import com.fsck.k9.ui.endtoend.AutocryptKeyTransferActivity
import com.fsck.k9.ui.settings.account.AccountSettingsActivity
import com.fsck.k9.ui.settings.account.OpenPgpAppSelectDialog
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsCryptoBridge
import net.thunderbird.feature.account.settings.api.OpenPgpProviderInfo
import org.openintents.openpgp.util.OpenPgpProviderUtil

internal class DefaultAccountSettingsCryptoBridge(
    private val accountManager: LegacyAccountDtoManager,
) : AccountSettingsCryptoBridge {
    override fun getOpenPgpProviderName(context: Context, providerPackage: String?): String? {
        if (providerPackage == null) return null
        return OpenPgpProviderUtil.getOpenPgpProviderName(context.packageManager, providerPackage)
    }

    override fun getAvailableOpenPgpProviders(context: Context): List<OpenPgpProviderInfo> {
        return OpenPgpProviderUtil.getOpenPgpProviderPackages(context).map { packageName ->
            OpenPgpProviderInfo(
                packageName = packageName,
                displayName = OpenPgpProviderUtil.getOpenPgpProviderName(context.packageManager, packageName)
                    ?: packageName,
            )
        }
    }

    override fun launchOpenPgpProviderChooser(context: Context, accountId: AccountId) {
        val activity = context as? Activity ?: return
        val account = accountManager.getAccount("${accountId.value}") ?: return
        OpenPgpAppSelectDialog.startOpenPgpChooserActivity(activity, account)
    }

    override fun launchOpenPgpKeySelector(context: Context, accountId: AccountId) {
        AccountSettingsActivity.startLegacyOpenPgpKeySelector(context, "${accountId.value}")
    }

    override fun launchAutocryptTransfer(context: Context, accountId: AccountId) {
        val intent = AutocryptKeyTransferActivity.createIntent(context, "${accountId.value}")
        context.startActivity(intent)
    }
}
