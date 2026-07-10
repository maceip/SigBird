package net.thunderbird.feature.account.settings.impl.ui.notifications

import android.os.Build
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.thunderbird.core.logging.Logger
import net.thunderbird.core.outcome.fold
import net.thunderbird.core.outcome.handle
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.core.ui.setting.SettingValue.Select.SelectOption
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountNotificationChannelType
import net.thunderbird.feature.account.settings.api.AccountSettingsNotificationBridge
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase
import net.thunderbird.feature.notification.NotificationLight
import net.thunderbird.feature.notification.NotificationVibration
import net.thunderbird.feature.notification.VibratePattern

private const val TAG = "NotificationSettingsViewModel"

@Suppress("LongMethod", "CyclomaticComplexMethod")
internal class NotificationSettingsViewModel(
    private val accountId: AccountId,
    private val getAccountName: UseCase.GetAccountName,
    private val getLegacyAccount: UseCase.GetLegacyAccount,
    private val getAccountCapabilities: UseCase.GetAccountCapabilities,
    private val updateNotificationSettings: UseCase.UpdateNotificationSettings,
    private val notificationBridge: AccountSettingsNotificationBridge,
    private val optionsMapper: NotificationSettingsOptionsMapper,
    private val ringtoneSummaryFormatter: RingtoneSummaryFormatter,
    private val vibrationSummaryFormatter: VibrationSummaryFormatter,
    private val logger: Logger,
) : BaseViewModel<
    NotificationSettingsContract.State,
    NotificationSettingsContract.Event,
    NotificationSettingsContract.Effect,
    >(
    NotificationSettingsContract.State(),
),
    NotificationSettingsContract.ViewModel {

    init {
        observeAccountName()
        loadSettings()
    }

    override fun event(event: NotificationSettingsContract.Event) {
        when (event) {
            NotificationSettingsContract.Event.OnBackPressed -> emitEffect(
                NotificationSettingsContract.Effect.NavigateBack,
            )

            is NotificationSettingsContract.Event.OnNotifyNewMailToggle -> updateSetting(
                AccountSettingsDomainContract.UpdateNotificationSettingsCommand.UpdateNotifyNewMail(event.enabled),
            ) { updateState { it.copy(notifyNewMail = event.enabled) } }

            is NotificationSettingsContract.Event.OnNotifySelfToggle -> updateSetting(
                AccountSettingsDomainContract.UpdateNotificationSettingsCommand.UpdateNotifySelf(event.enabled),
            ) { updateState { it.copy(notifySelf = event.enabled) } }

            is NotificationSettingsContract.Event.OnNotifyContactsOnlyToggle -> updateSetting(
                AccountSettingsDomainContract.UpdateNotificationSettingsCommand.UpdateNotifyContactsOnly(event.enabled),
            ) { updateState { it.copy(notifyContactsOnly = event.enabled) } }

            is NotificationSettingsContract.Event.OnIgnoreChatMessagesToggle -> updateSetting(
                AccountSettingsDomainContract.UpdateNotificationSettingsCommand.UpdateIgnoreChatMessages(event.enabled),
            ) { updateState { it.copy(ignoreChatMessages = event.enabled) } }

            is NotificationSettingsContract.Event.OnNotifySyncToggle -> updateSetting(
                AccountSettingsDomainContract.UpdateNotificationSettingsCommand.UpdateNotifySync(event.enabled),
            ) { updateState { it.copy(notifySync = event.enabled) } }

            NotificationSettingsContract.Event.OnRingtoneClick -> emitEffect(
                NotificationSettingsContract.Effect.LaunchRingtonePicker(state.value.ringtone),
            )

            is NotificationSettingsContract.Event.OnRingtoneSelected -> updateSetting(
                AccountSettingsDomainContract.UpdateNotificationSettingsCommand.UpdateRingtone(event.ringtone),
            ) {
                updateState {
                    it.copy(
                        ringtone = event.ringtone,
                        ringtoneSummary = ringtoneSummaryFormatter.format(event.ringtone),
                    )
                }
            }

            is NotificationSettingsContract.Event.OnNotificationLightChange -> {
                val light = NotificationLight.valueOf(event.option.id)
                updateSetting(
                    AccountSettingsDomainContract.UpdateNotificationSettingsCommand.UpdateNotificationLight(light),
                ) { updateState { it.copy(notificationLight = event.option) } }
            }

            NotificationSettingsContract.Event.OnVibrationClick -> emitEffect(
                NotificationSettingsContract.Effect.ShowVibrationDialog,
            )

            is NotificationSettingsContract.Event.OnVibrationChange -> updateSetting(
                AccountSettingsDomainContract.UpdateNotificationSettingsCommand.UpdateVibration(event.vibration),
            ) {
                updateState {
                    it.copy(
                        vibration = event.vibration,
                        vibrationSummary = vibrationSummaryFormatter.format(event.vibration),
                    )
                }
            }

            NotificationSettingsContract.Event.OnMessagesChannelClick -> launchChannel(
                AccountNotificationChannelType.MESSAGES,
            )

            NotificationSettingsContract.Event.OnMiscellaneousChannelClick -> launchChannel(
                AccountNotificationChannelType.MISCELLANEOUS,
            )
        }
    }

    private fun launchChannel(channelType: AccountNotificationChannelType) {
        viewModelScope.launch {
            val channelId = notificationBridge.getNotificationChannelId(accountId, channelType)
            emitEffect(NotificationSettingsContract.Effect.LaunchNotificationChannel(channelId))
        }
    }

    private fun updateSetting(
        command: AccountSettingsDomainContract.UpdateNotificationSettingsCommand,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            updateNotificationSettings(accountId = accountId, command = command).handle(
                onSuccess = { onSuccess() },
                onFailure = { handleError(it) },
            )
        }
    }

    private fun observeAccountName() {
        getAccountName(accountId)
            .onEach { outcome ->
                outcome.handle(
                    onSuccess = { updateState { state -> state.copy(subtitle = it) } },
                    onFailure = { handleError(it) },
                )
            }.launchIn(viewModelScope)
    }

    private fun loadSettings() {
        viewModelScope.launch {
            notificationBridge.syncNotificationSettings(accountId)

            val account = getLegacyAccount(accountId).fold(
                onSuccess = { it },
                onFailure = {
                    handleError(it)
                    return@launch
                },
            )
            val capabilities = getAccountCapabilities(accountId).fold(
                onSuccess = { it },
                onFailure = {
                    handleError(it)
                    return@launch
                },
            )

            val notificationSettings = account.notificationSettings
            val lightOption = optionsMapper.notificationLightOption(notificationSettings.light)

            updateState { state ->
                state.copy(
                    notifyNewMail = account.isNotifyNewMail,
                    notifySelf = account.isNotifySelfNewMail,
                    notifyContactsOnly = account.isNotifyContactsMailOnly,
                    ignoreChatMessages = account.isIgnoreChatMessages,
                    notifySync = account.isNotifySync,
                    ringtone = notificationSettings.ringtone,
                    ringtoneSummary = ringtoneSummaryFormatter.format(notificationSettings.ringtone),
                    notificationLight = lightOption,
                    vibration = notificationSettings.vibration,
                    vibrationSummary = vibrationSummaryFormatter.format(notificationSettings.vibration),
                    hasVibrator = capabilities.hasVibrator,
                    showNotificationChannels = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
                )
            }
        }
    }

    private fun handleError(error: AccountSettingsDomainContract.AccountSettingError) {
        logger.error(tag = TAG, message = { error.toString() })
    }
}

fun interface RingtoneSummaryFormatter {
    fun format(ringtone: String?): String
}

fun interface VibrationSummaryFormatter {
    fun format(vibration: NotificationVibration): String
}
