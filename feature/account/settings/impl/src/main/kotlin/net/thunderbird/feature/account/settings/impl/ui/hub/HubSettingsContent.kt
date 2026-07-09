package net.thunderbird.feature.account.settings.impl.ui.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.thunderbird.core.ui.setting.Setting
import net.thunderbird.core.ui.setting.SettingValue
import net.thunderbird.core.ui.setting.SettingViewProvider
import net.thunderbird.feature.account.settings.R

@Composable
internal fun HubSettingsContent(
    state: HubSettingsContract.State,
    onEvent: (HubSettingsContract.Event) -> Unit,
    provider: SettingViewProvider,
    builder: HubSettingsContract.SettingsBuilder,
    modifier: Modifier = Modifier,
) {
    val settings = remember(state, builder, onEvent) {
        builder.build(state = state, onEvent = onEvent)
    }

    provider.SettingView(
        title = stringResource(R.string.account_settings_hub_title),
        subtitle = state.subtitle,
        settings = settings,
        onSettingValueChange = { setting -> handleSettingChange(setting, onEvent) },
        onBack = { onEvent(HubSettingsContract.Event.OnBackPressed) },
        modifier = modifier,
    )
}

private fun handleSettingChange(
    setting: Setting,
    onEvent: (HubSettingsContract.Event) -> Unit,
) {
    when (setting) {
        is SettingValue.Select -> onEvent(HubSettingsContract.Event.OnAccountSelected(setting.value))
        else -> Unit
    }
}
