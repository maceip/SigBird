package net.thunderbird.feature.account.settings.impl.ui.notifications

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.ui.setting.SettingValue.Select.SelectOption
import net.thunderbird.feature.account.settings.R
import net.thunderbird.feature.notification.NotificationLight

internal class NotificationSettingsOptionsMapper(
    private val resources: StringsResourceManager,
) {
    fun notificationLightOptions(): ImmutableList<SelectOption> {
        return NotificationLight.entries.map { light ->
            SelectOption(light.name) { notificationLightLabel(light) }
        }.toImmutableList()
    }

    fun notificationLightOption(light: NotificationLight): SelectOption {
        return SelectOption(light.name) { notificationLightLabel(light) }
    }

    private fun notificationLightLabel(light: NotificationLight): String {
        return when (light) {
            NotificationLight.Disabled -> resources.stringResource(
                R.string.account_settings_notification_light_disabled,
            )

            NotificationLight.AccountColor -> resources.stringResource(
                R.string.account_settings_notification_light_account_color,
            )

            NotificationLight.SystemDefaultColor -> resources.stringResource(
                R.string.account_settings_notification_light_system,
            )

            NotificationLight.White -> resources.stringResource(R.string.account_settings_notification_light_white)

            NotificationLight.Red -> resources.stringResource(R.string.account_settings_notification_light_red)

            NotificationLight.Green -> resources.stringResource(R.string.account_settings_notification_light_green)

            NotificationLight.Blue -> resources.stringResource(R.string.account_settings_notification_light_blue)

            NotificationLight.Yellow -> resources.stringResource(R.string.account_settings_notification_light_yellow)

            NotificationLight.Cyan -> resources.stringResource(R.string.account_settings_notification_light_cyan)

            NotificationLight.Magenta -> resources.stringResource(R.string.account_settings_notification_light_magenta)
        }
    }
}
