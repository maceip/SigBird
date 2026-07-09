package net.thunderbird.app.common.account

import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.feature.account.settings.R
import net.thunderbird.feature.account.settings.api.OpenPgpProviderSummaryProvider
import org.openintents.openpgp.util.OpenPgpProviderUtil

internal class DefaultOpenPgpProviderSummaryProvider(
    private val resources: StringsResourceManager,
    private val packageManager: android.content.pm.PackageManager,
) : OpenPgpProviderSummaryProvider {
    override fun getProviderSummary(providerPackage: String?): String {
        if (providerPackage == null) {
            return resources.stringResource(R.string.account_settings_crypto_summary_off)
        }
        val providerName = OpenPgpProviderUtil.getOpenPgpProviderName(packageManager, providerPackage)
            ?: providerPackage
        return resources.stringResource(R.string.account_settings_crypto_summary_on, providerName)
    }
}
