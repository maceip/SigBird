package net.thunderbird.feature.account.settings.impl.ui.hub

import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.ui.setting.Setting
import net.thunderbird.core.ui.setting.SettingValue
import net.thunderbird.core.ui.setting.Settings
import net.thunderbird.feature.account.settings.R

internal class HubSettingsBuilder(
    private val resources: StringsResourceManager,
) : HubSettingsContract.SettingsBuilder {
    override fun build(
        state: HubSettingsContract.State,
        onEvent: (HubSettingsContract.Event) -> Unit,
    ): Settings {
        val settings = mutableListOf<Setting>()

        state.selectedAccount?.let { selectedAccount ->
            if (state.accounts.size > 1) {
                settings += SettingValue.Select(
                    id = HubSettingId.ACCOUNT,
                    title = { resources.stringResource(R.string.account_settings_hub_account_label) },
                    description = { null },
                    displayValueAsSecondaryText = true,
                    value = selectedAccount,
                    options = state.accounts.map { account ->
                        SettingValue.Select.SelectOption(account.accountId.value.toString()) { account.name }
                    }.toImmutableList(),
                )
            }
        }

        settings += action(
            id = HubSettingId.GENERAL,
            titleRes = R.string.account_settings_general_title,
            onClick = { onEvent(HubSettingsContract.Event.OnGeneralClick) },
        )
        settings += action(
            id = HubSettingId.READING_MAIL,
            titleRes = R.string.account_settings_reading_mail,
            onClick = { onEvent(HubSettingsContract.Event.OnReadingMailClick) },
        )
        settings += action(
            id = HubSettingId.FETCHING_MAIL,
            titleRes = R.string.account_settings_fetching_mail,
            onClick = { onEvent(HubSettingsContract.Event.OnFetchingMailClick) },
        )
        settings += action(
            id = HubSettingId.COMPOSITION,
            titleRes = R.string.account_settings_composition,
            onClick = { onEvent(HubSettingsContract.Event.OnCompositionClick) },
        )
        settings += action(
            id = HubSettingId.FOLDERS,
            titleRes = R.string.account_settings_folders,
            onClick = { onEvent(HubSettingsContract.Event.OnFoldersClick) },
        )
        settings += action(
            id = HubSettingId.NOTIFICATIONS,
            titleRes = R.string.account_settings_notifications,
            onClick = { onEvent(HubSettingsContract.Event.OnNotificationsClick) },
        )
        settings += action(
            id = HubSettingId.SEARCH,
            titleRes = R.string.account_settings_search_action,
            onClick = { onEvent(HubSettingsContract.Event.OnSearchClick) },
        )
        settings += action(
            id = HubSettingId.CRYPTO,
            titleRes = R.string.account_settings_crypto,
            onClick = { onEvent(HubSettingsContract.Event.OnCryptoClick) },
        )

        return settings.toImmutableList()
    }

    private fun action(
        id: String,
        titleRes: Int,
        onClick: () -> Unit,
    ): Setting = SettingValue.ActionText(
        id = id,
        title = { resources.stringResource(titleRes) },
        value = "",
        onClick = onClick,
    )
}
