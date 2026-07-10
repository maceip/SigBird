package net.thunderbird.feature.account.settings.impl.ui.crypto

import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel
import net.thunderbird.core.ui.setting.Settings

internal interface CryptoSettingsContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val subtitle: String = "",
        val isOpenPgpEnabled: Boolean = false,
        val openPgpProviderSummary: String = "",
        val openPgpKeySummary: String = "",
        val hasOpenPgpKey: Boolean = false,
        val autocryptPreferEncrypt: Boolean = false,
        val hideSignOnly: Boolean = false,
        val encryptSubject: Boolean = false,
        val encryptAllDrafts: Boolean = false,
    )

    sealed interface Event {
        data object OnBackPressed : Event
        data object OnOpenPgpProviderClick : Event
        data object OnOpenPgpKeyClick : Event
        data class OnAutocryptPreferEncryptToggle(val enabled: Boolean) : Event
        data class OnHideSignOnlyToggle(val enabled: Boolean) : Event
        data class OnEncryptSubjectToggle(val enabled: Boolean) : Event
        data class OnEncryptAllDraftsToggle(val enabled: Boolean) : Event
        data object OnAutocryptTransferClick : Event
        data object OnRefreshCryptoState : Event
    }

    sealed interface Effect {
        data object NavigateBack : Effect
        data object LaunchOpenPgpProviderChooser : Effect
        data object LaunchOpenPgpKeySelector : Effect
        data object LaunchAutocryptTransfer : Effect
    }

    fun interface SettingsBuilder {
        fun build(
            state: State,
            onEvent: (Event) -> Unit,
        ): Settings
    }
}
