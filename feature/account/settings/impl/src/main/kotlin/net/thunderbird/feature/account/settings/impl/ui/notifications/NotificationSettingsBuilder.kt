package net.thunderbird.feature.account.settings.impl.ui.notifications

import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.ui.setting.Setting
import net.thunderbird.core.ui.setting.SettingValue
import net.thunderbird.core.ui.setting.Settings
import net.thunderbird.feature.account.settings.R

internal class NotificationSettingsBuilder(
    private val resources: StringsResourceManager,
    private val optionsMapper: NotificationSettingsOptionsMapper,
) : NotificationSettingsContract.SettingsBuilder {
    override fun build(
        state: NotificationSettingsContract.State,
        onEvent: (NotificationSettingsContract.Event) -> Unit,
    ): Settings {
        val settings = mutableListOf<Setting>()

        settings += SettingValue.Switch(
            id = NotificationSettingId.NOTIFY_NEW_MAIL,
            title = { resources.stringResource(R.string.account_settings_notify_label) },
            description = { resources.stringResource(R.string.account_settings_notify_summary) },
            value = state.notifyNewMail,
        )

        if (state.notifyNewMail) {
            settings += SettingValue.Switch(
                id = NotificationSettingId.NOTIFY_SELF,
                title = { resources.stringResource(R.string.account_settings_notify_self_label) },
                description = { resources.stringResource(R.string.account_settings_notify_self_summary) },
                value = state.notifySelf,
            )
            settings += SettingValue.Switch(
                id = NotificationSettingId.NOTIFY_CONTACTS_ONLY,
                title = { resources.stringResource(R.string.account_settings_notify_contacts_only_label) },
                description = { resources.stringResource(R.string.account_settings_notify_contacts_only_summary) },
                value = state.notifyContactsOnly,
            )
            settings += SettingValue.Switch(
                id = NotificationSettingId.IGNORE_CHAT_MESSAGES,
                title = { resources.stringResource(R.string.account_settings_ignore_chat_messages_label) },
                description = { resources.stringResource(R.string.account_settings_ignore_chat_messages_summary) },
                value = state.ignoreChatMessages,
            )
            settings += SettingValue.ActionText(
                id = NotificationSettingId.RINGTONE,
                title = { resources.stringResource(R.string.account_settings_ringtone) },
                value = state.ringtoneSummary,
                onClick = { onEvent(NotificationSettingsContract.Event.OnRingtoneClick) },
            )
            if (state.hasVibrator) {
                settings += SettingValue.ActionText(
                    id = NotificationSettingId.VIBRATION,
                    title = { resources.stringResource(R.string.account_settings_vibration) },
                    value = state.vibrationSummary,
                    onClick = { onEvent(NotificationSettingsContract.Event.OnVibrationClick) },
                )
            }
            state.notificationLight?.let { light ->
                settings += SettingValue.Select(
                    id = NotificationSettingId.NOTIFICATION_LIGHT,
                    title = { resources.stringResource(R.string.account_settings_notification_light_label) },
                    displayValueAsSecondaryText = true,
                    value = light,
                    options = optionsMapper.notificationLightOptions(),
                )
            }
        }

        settings += SettingValue.Switch(
            id = NotificationSettingId.NOTIFY_SYNC,
            title = { resources.stringResource(R.string.account_settings_notify_sync_label) },
            description = { resources.stringResource(R.string.account_settings_notify_sync_summary) },
            value = state.notifySync,
        )

        if (state.showNotificationChannels) {
            settings += SettingValue.ActionText(
                id = NotificationSettingId.MESSAGES_CHANNEL,
                title = { resources.stringResource(R.string.account_settings_notification_channel_messages) },
                description = { resources.stringResource(R.string.account_settings_notification_channel_messages_summary) },
                value = "",
                onClick = { onEvent(NotificationSettingsContract.Event.OnMessagesChannelClick) },
            )
            settings += SettingValue.ActionText(
                id = NotificationSettingId.MISCELLANEOUS_CHANNEL,
                title = { resources.stringResource(R.string.account_settings_notification_channel_miscellaneous) },
                description = {
                    resources.stringResource(R.string.account_settings_notification_channel_miscellaneous_summary)
                },
                value = "",
                onClick = { onEvent(NotificationSettingsContract.Event.OnMiscellaneousChannelClick) },
            )
        }

        return settings.toImmutableList()
    }
}
