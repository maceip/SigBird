package net.thunderbird.feature.account.settings.impl.ui.hub

import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.thunderbird.core.logging.Logger
import net.thunderbird.core.outcome.handle
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.core.ui.setting.SettingValue.Select.SelectOption
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase

private const val TAG = "HubSettingsViewModel"

internal class HubSettingsViewModel(
    private val accountId: AccountId,
    private val getAccountName: UseCase.GetAccountName,
    private val getAllAccountProfiles: UseCase.GetAllAccountProfiles,
    private val logger: Logger,
) : BaseViewModel<HubSettingsContract.State, HubSettingsContract.Event, HubSettingsContract.Effect>(
    HubSettingsContract.State(),
),
    HubSettingsContract.ViewModel {

    init {
        observeAccountName()
        observeAccounts()
    }

    override fun event(event: HubSettingsContract.Event) {
        when (event) {
            HubSettingsContract.Event.OnBackPressed -> emitEffect(HubSettingsContract.Effect.NavigateBack)

            is HubSettingsContract.Event.OnAccountSelected -> {
                if (event.account.id != accountId.value.toString()) {
                    emitEffect(HubSettingsContract.Effect.NavigateToAccount(event.account.id))
                }
            }

            HubSettingsContract.Event.OnGeneralClick -> emitEffect(HubSettingsContract.Effect.NavigateToGeneral)

            HubSettingsContract.Event.OnReadingMailClick -> emitEffect(HubSettingsContract.Effect.NavigateToReadingMail)

            HubSettingsContract.Event.OnFetchingMailClick -> emitEffect(
                HubSettingsContract.Effect.NavigateToFetchingMail,
            )

            HubSettingsContract.Event.OnCompositionClick -> emitEffect(HubSettingsContract.Effect.NavigateToComposition)

            HubSettingsContract.Event.OnFoldersClick -> emitEffect(HubSettingsContract.Effect.NavigateToFolders)

            HubSettingsContract.Event.OnNotificationsClick -> emitEffect(
                HubSettingsContract.Effect.NavigateToNotifications,
            )

            HubSettingsContract.Event.OnSearchClick -> emitEffect(HubSettingsContract.Effect.NavigateToSearch)

            HubSettingsContract.Event.OnCryptoClick -> emitEffect(HubSettingsContract.Effect.NavigateToCrypto)
        }
    }

    private fun observeAccountName() {
        getAccountName(accountId)
            .onEach { outcome ->
                outcome.handle(
                    onSuccess = { updateState { state -> state.copy(subtitle = it) } },
                    onFailure = { logger.error(tag = TAG, message = { it.toString() }) },
                )
            }.launchIn(viewModelScope)
    }

    private fun observeAccounts() {
        getAllAccountProfiles()
            .onEach { profiles ->
                val selected = profiles.find { it.accountId == accountId }
                updateState { state ->
                    state.copy(
                        accounts = profiles.toImmutableList(),
                        selectedAccount = selected?.let {
                            SelectOption(it.accountId.value.toString()) { it.name }
                        },
                    )
                }
            }.launchIn(viewModelScope)
    }
}
