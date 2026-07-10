package net.thunderbird.feature.account.settings.impl.domain.usecase

import kotlinx.coroutines.flow.firstOrNull
import net.thunderbird.core.android.account.LegacyAccountRepository
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsNotificationBridge
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UpdateNotificationSettingsCommand
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase

internal class UpdateNotificationSettings(
    private val repository: LegacyAccountRepository,
    private val notificationBridge: AccountSettingsNotificationBridge,
) : UseCase.UpdateNotificationSettings {
    override suspend fun invoke(
        accountId: AccountId,
        command: UpdateNotificationSettingsCommand,
    ): Outcome<Unit, AccountSettingsDomainContract.AccountSettingError> {
        return repository.getById(accountId).firstOrNull()?.let { account ->
            val updatedAccount = when (command) {
                is UpdateNotificationSettingsCommand.UpdateNotifyNewMail -> {
                    account.copy(isNotifyNewMail = command.value)
                }

                is UpdateNotificationSettingsCommand.UpdateNotifySelf -> {
                    account.copy(isNotifySelfNewMail = command.value)
                }

                is UpdateNotificationSettingsCommand.UpdateNotifyContactsOnly -> {
                    account.copy(isNotifyContactsMailOnly = command.value)
                }

                is UpdateNotificationSettingsCommand.UpdateIgnoreChatMessages -> {
                    account.copy(isIgnoreChatMessages = command.value)
                }

                is UpdateNotificationSettingsCommand.UpdateNotifySync -> {
                    account.copy(isNotifySync = command.value)
                }

                is UpdateNotificationSettingsCommand.UpdateRingtone -> {
                    account.copy(
                        notificationSettings = account.notificationSettings.copy(
                            isRingEnabled = command.value != null,
                            ringtone = command.value,
                        ),
                    )
                }

                is UpdateNotificationSettingsCommand.UpdateNotificationLight -> {
                    account.copy(
                        notificationSettings = account.notificationSettings.copy(
                            light = command.value,
                        ),
                    )
                }

                is UpdateNotificationSettingsCommand.UpdateVibration -> {
                    account.copy(
                        notificationSettings = account.notificationSettings.copy(
                            vibration = command.value,
                        ),
                    )
                }
            }
            repository.update(updatedAccount)
            notificationBridge.syncNotificationSettings(accountId)
            Outcome.success(Unit)
        } ?: Outcome.failure(AccountSettingsDomainContract.AccountSettingError.NotFound("Account not found"))
    }
}
