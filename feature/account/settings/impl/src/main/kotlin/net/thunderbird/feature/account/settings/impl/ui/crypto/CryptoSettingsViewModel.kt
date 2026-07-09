package net.thunderbird.feature.account.settings.impl.ui.crypto

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.logging.Logger
import net.thunderbird.core.outcome.fold
import net.thunderbird.core.outcome.handle
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsCryptoBridge
import net.thunderbird.feature.account.settings.api.OpenPgpProviderSummaryProvider
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase
import net.thunderbird.feature.account.settings.R

private const val TAG = "CryptoSettingsViewModel"

internal class CryptoSettingsViewModel(
    private val accountId: AccountId,
    private val getAccountName: UseCase.GetAccountName,
    private val getLegacyAccount: UseCase.GetLegacyAccount,
    private val updateCryptoSettings: UseCase.UpdateCryptoSettings,
    private val providerSummaryProvider: OpenPgpProviderSummaryProvider,
    private val resources: StringsResourceManager,
    private val logger: Logger,
) : BaseViewModel<CryptoSettingsContract.State, CryptoSettingsContract.Event, CryptoSettingsContract.Effect>(
    CryptoSettingsContract.State(),
),
    CryptoSettingsContract.ViewModel {

    init {
        observeAccountName()
        refreshCryptoState()
    }

    override fun event(event: CryptoSettingsContract.Event) {
        when (event) {
            CryptoSettingsContract.Event.OnBackPressed -> emitEffect(CryptoSettingsContract.Effect.NavigateBack)
            CryptoSettingsContract.Event.OnOpenPgpProviderClick -> emitEffect(
                CryptoSettingsContract.Effect.LaunchOpenPgpProviderChooser,
            )
            CryptoSettingsContract.Event.OnOpenPgpKeyClick -> emitEffect(
                CryptoSettingsContract.Effect.LaunchOpenPgpKeySelector,
            )
            is CryptoSettingsContract.Event.OnAutocryptPreferEncryptToggle -> updateSetting(
                AccountSettingsDomainContract.UpdateCryptoSettingsCommand.UpdateAutocryptPreferEncrypt(event.enabled),
            ) { updateState { it.copy(autocryptPreferEncrypt = event.enabled) } }
            is CryptoSettingsContract.Event.OnHideSignOnlyToggle -> updateSetting(
                AccountSettingsDomainContract.UpdateCryptoSettingsCommand.UpdateHideSignOnly(event.enabled),
            ) { updateState { it.copy(hideSignOnly = event.enabled) } }
            is CryptoSettingsContract.Event.OnEncryptSubjectToggle -> updateSetting(
                AccountSettingsDomainContract.UpdateCryptoSettingsCommand.UpdateEncryptSubject(event.enabled),
            ) { updateState { it.copy(encryptSubject = event.enabled) } }
            is CryptoSettingsContract.Event.OnEncryptAllDraftsToggle -> updateSetting(
                AccountSettingsDomainContract.UpdateCryptoSettingsCommand.UpdateEncryptAllDrafts(event.enabled),
            ) { updateState { it.copy(encryptAllDrafts = event.enabled) } }
            CryptoSettingsContract.Event.OnAutocryptTransferClick -> emitEffect(
                CryptoSettingsContract.Effect.LaunchAutocryptTransfer,
            )
            CryptoSettingsContract.Event.OnRefreshCryptoState -> refreshCryptoState()
        }
    }

    private fun updateSetting(
        command: AccountSettingsDomainContract.UpdateCryptoSettingsCommand,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            updateCryptoSettings(accountId = accountId, command = command).handle(
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

    private fun refreshCryptoState() {
        viewModelScope.launch {
            val account = getLegacyAccount(accountId).fold(
                onSuccess = { it },
                onFailure = {
                    handleError(it)
                    return@launch
                },
            )

            val providerSummary = providerSummaryProvider.getProviderSummary(account.openPgpProvider)

            updateState { state ->
                state.copy(
                    isOpenPgpEnabled = account.isOpenPgpProviderConfigured(),
                    openPgpProviderSummary = providerSummary,
                    hasOpenPgpKey = account.hasOpenPgpKey(),
                    openPgpKeySummary = if (account.hasOpenPgpKey()) {
                        resources.stringResource(R.string.account_settings_crypto_key_configured)
                    } else {
                        resources.stringResource(R.string.account_settings_crypto_key_not_configured)
                    },
                    autocryptPreferEncrypt = account.autocryptPreferEncryptMutual,
                    hideSignOnly = account.isOpenPgpHideSignOnly,
                    encryptSubject = account.isOpenPgpEncryptSubject,
                    encryptAllDrafts = account.isOpenPgpEncryptAllDrafts,
                )
            }
        }
    }

    private fun handleError(error: AccountSettingsDomainContract.AccountSettingError) {
        logger.error(tag = TAG, message = { error.toString() })
    }
}
