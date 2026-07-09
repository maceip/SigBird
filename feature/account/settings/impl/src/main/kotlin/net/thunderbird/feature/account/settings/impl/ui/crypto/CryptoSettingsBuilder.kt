package net.thunderbird.feature.account.settings.impl.ui.crypto

import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.ui.setting.Setting
import net.thunderbird.core.ui.setting.SettingValue
import net.thunderbird.core.ui.setting.Settings
import net.thunderbird.feature.account.settings.R

internal class CryptoSettingsBuilder(
    private val resources: StringsResourceManager,
) : CryptoSettingsContract.SettingsBuilder {
    override fun build(
        state: CryptoSettingsContract.State,
        onEvent: (CryptoSettingsContract.Event) -> Unit,
    ): Settings {
        val settings = mutableListOf<Setting>()

        settings += SettingValue.ActionText(
            id = CryptoSettingId.OPENPGP_PROVIDER,
            title = { resources.stringResource(R.string.account_settings_crypto_app) },
            description = { state.openPgpProviderSummary },
            value = "",
            onClick = { onEvent(CryptoSettingsContract.Event.OnOpenPgpProviderClick) },
        )

        if (state.isOpenPgpEnabled) {
            settings += SettingValue.ActionText(
                id = CryptoSettingId.OPENPGP_KEY,
                title = { resources.stringResource(R.string.account_settings_crypto_key) },
                description = { state.openPgpKeySummary },
                value = "",
                onClick = { onEvent(CryptoSettingsContract.Event.OnOpenPgpKeyClick) },
            )
        }

        if (state.hasOpenPgpKey) {
            settings += SettingValue.Switch(
                id = CryptoSettingId.AUTOCRYPT_PREFER_ENCRYPT,
                title = { resources.stringResource(R.string.account_settings_crypto_prefer_encrypt) },
                value = state.autocryptPreferEncrypt,
            )
            settings += SettingValue.Switch(
                id = CryptoSettingId.HIDE_SIGN_ONLY,
                title = { resources.stringResource(R.string.account_settings_crypto_hide_sign_only) },
                description = {
                    if (state.hideSignOnly) {
                        resources.stringResource(R.string.account_settings_crypto_hide_sign_only_on)
                    } else {
                        resources.stringResource(R.string.account_settings_crypto_hide_sign_only_off)
                    }
                },
                value = state.hideSignOnly,
            )
            settings += SettingValue.Switch(
                id = CryptoSettingId.ENCRYPT_SUBJECT,
                title = { resources.stringResource(R.string.account_settings_crypto_encrypt_subject) },
                description = { resources.stringResource(R.string.account_settings_crypto_encrypt_subject_subtitle) },
                value = state.encryptSubject,
            )
            settings += SettingValue.Switch(
                id = CryptoSettingId.ENCRYPT_ALL_DRAFTS,
                title = { resources.stringResource(R.string.account_settings_crypto_encrypt_all_drafts) },
                description = {
                    if (state.encryptAllDrafts) {
                        resources.stringResource(R.string.account_settings_crypto_encrypt_all_drafts_on)
                    } else {
                        resources.stringResource(R.string.account_settings_crypto_encrypt_all_drafts_off)
                    }
                },
                value = state.encryptAllDrafts,
            )
            settings += SettingValue.ActionText(
                id = CryptoSettingId.AUTOCRYPT_TRANSFER,
                title = { resources.stringResource(R.string.account_settings_autocrypt_transfer_title) },
                description = { resources.stringResource(R.string.account_settings_autocrypt_transfer_summary) },
                value = "",
                onClick = { onEvent(CryptoSettingsContract.Event.OnAutocryptTransferClick) },
            )
        }

        return settings.toImmutableList()
    }
}
