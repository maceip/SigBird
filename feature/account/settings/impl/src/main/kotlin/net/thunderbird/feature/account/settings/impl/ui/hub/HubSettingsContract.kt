package net.thunderbird.feature.account.settings.impl.ui.hub

import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel
import net.thunderbird.core.ui.setting.SettingValue.Select.SelectOption
import net.thunderbird.core.ui.setting.Settings
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.AccountProfileSummary

internal interface HubSettingsContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val subtitle: String = "",
        val accounts: ImmutableList<AccountProfileSummary> = kotlinx.collections.immutable.persistentListOf(),
        val selectedAccount: SelectOption? = null,
    )

    sealed interface Event {
        data object OnBackPressed : Event
        data class OnAccountSelected(val account: SelectOption) : Event
        data object OnGeneralClick : Event
        data object OnReadingMailClick : Event
        data object OnFetchingMailClick : Event
        data object OnCompositionClick : Event
        data object OnFoldersClick : Event
        data object OnNotificationsClick : Event
        data object OnSearchClick : Event
        data object OnCryptoClick : Event
    }

    sealed interface Effect {
        data object NavigateBack : Effect
        data class NavigateToAccount(val accountId: String) : Effect
        data object NavigateToGeneral : Effect
        data object NavigateToReadingMail : Effect
        data object NavigateToFetchingMail : Effect
        data object NavigateToComposition : Effect
        data object NavigateToFolders : Effect
        data object NavigateToNotifications : Effect
        data object NavigateToSearch : Effect
        data object NavigateToCrypto : Effect
    }

    fun interface SettingsBuilder {
        fun build(
            state: State,
            onEvent: (Event) -> Unit,
        ): Settings
    }
}
