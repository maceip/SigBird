package net.thunderbird.feature.account.settings.api

import android.content.Context
import net.thunderbird.feature.account.AccountId

data class OpenPgpProviderInfo(
    val packageName: String,
    val displayName: String,
)

/**
 * Bridges legacy OpenPGP and Autocrypt flows for account crypto settings.
 */
interface AccountSettingsCryptoBridge {
    fun getOpenPgpProviderName(context: Context, providerPackage: String?): String?

    fun getAvailableOpenPgpProviders(context: Context): List<OpenPgpProviderInfo>

    fun launchOpenPgpProviderChooser(context: Context, accountId: AccountId)

    fun launchOpenPgpKeySelector(context: Context, accountId: AccountId)

    fun launchAutocryptTransfer(context: Context, accountId: AccountId)
}
