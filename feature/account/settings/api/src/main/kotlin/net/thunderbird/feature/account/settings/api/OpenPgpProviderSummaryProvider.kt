package net.thunderbird.feature.account.settings.api

/**
 * Resolves OpenPGP provider display summaries for crypto settings.
 */
interface OpenPgpProviderSummaryProvider {
    fun getProviderSummary(providerPackage: String?): String
}
