package net.thunderbird.feature.account.settings.impl.ui.notifications

import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel
import net.thunderbird.core.ui.setting.SettingValue.Select.SelectOption
import net.thunderbird.core.ui.setting.Settings
import net.thunderbird.feature.notification.NotificationVibration

internal interface NotificationSettingsContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val subtitle: String = "",
        val notifyNewMail: Boolean = true,
        val notifySelf: Boolean = true,
        val notifyContactsOnly: Boolean = false,
        val ignoreChatMessages: Boolean = false,
        val notifySync: Boolean = true,
        val ringtoneSummary: String = "",
        val ringtone: String? = null,
        val notificationLight: SelectOption? = null,
        val vibrationSummary: String = "",
        val vibration: NotificationVibration = NotificationVibration.DEFAULT,
        val hasVibrator: Boolean = true,
        val showNotificationChannels: Boolean = false,
    )

    sealed interface Event {
        data object OnBackPressed : Event
        data class OnNotifyNewMailToggle(val enabled: Boolean) : Event
        data class OnNotifySelfToggle(val enabled: Boolean) : Event
        data class OnNotifyContactsOnlyToggle(val enabled: Boolean) : Event
        data class OnIgnoreChatMessagesToggle(val enabled: Boolean) : Event
        data class OnNotifySyncToggle(val enabled: Boolean) : Event
        data object OnRingtoneClick : Event
        data class OnRingtoneSelected(val ringtone: String?) : Event
        data class OnNotificationLightChange(val option: SelectOption) : Event
        data object OnVibrationClick : Event
        data class OnVibrationChange(val vibration: NotificationVibration) : Event
        data object OnMessagesChannelClick : Event
        data object OnMiscellaneousChannelClick : Event
    }

    sealed interface Effect {
        data object NavigateBack : Effect
        data class LaunchRingtonePicker(val currentRingtone: String?) : Effect
        data object ShowVibrationDialog : Effect
        data class LaunchNotificationChannel(val channelId: String) : Effect
    }

    fun interface SettingsBuilder {
        fun build(
            state: State,
            onEvent: (Event) -> Unit,
        ): Settings
    }
}
