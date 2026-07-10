package net.thunderbird.feature.account.settings.impl.ui.folders

import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel
import net.thunderbird.core.ui.setting.SettingValue.Select.SelectOption
import net.thunderbird.core.ui.setting.Settings

internal interface FolderSettingsContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val subtitle: String = "",
        val isLoading: Boolean = true,
        val supportsFolderSubscriptions: Boolean = true,
        val isMoveCapable: Boolean = true,
        val autoExpandFolder: SelectOption? = null,
        val subscribedFoldersOnly: Boolean = false,
        val archiveFolder: SelectOption? = null,
        val draftsFolder: SelectOption? = null,
        val sentFolder: SelectOption? = null,
        val spamFolder: SelectOption? = null,
        val trashFolder: SelectOption? = null,
        val autoExpandFolderOptions: ImmutableList<SelectOption> = kotlinx.collections.immutable.persistentListOf(),
        val archiveFolderOptions: ImmutableList<SelectOption> = kotlinx.collections.immutable.persistentListOf(),
        val draftsFolderOptions: ImmutableList<SelectOption> = kotlinx.collections.immutable.persistentListOf(),
        val sentFolderOptions: ImmutableList<SelectOption> = kotlinx.collections.immutable.persistentListOf(),
        val spamFolderOptions: ImmutableList<SelectOption> = kotlinx.collections.immutable.persistentListOf(),
        val trashFolderOptions: ImmutableList<SelectOption> = kotlinx.collections.immutable.persistentListOf(),
    )

    sealed interface Event {
        data object OnBackPressed : Event
        data class OnAutoExpandFolderChange(val option: SelectOption) : Event
        data class OnSubscribedFoldersOnlyToggle(val enabled: Boolean) : Event
        data class OnArchiveFolderChange(val option: SelectOption) : Event
        data class OnDraftsFolderChange(val option: SelectOption) : Event
        data class OnSentFolderChange(val option: SelectOption) : Event
        data class OnSpamFolderChange(val option: SelectOption) : Event
        data class OnTrashFolderChange(val option: SelectOption) : Event
    }

    sealed interface Effect {
        data object NavigateBack : Effect
    }

    fun interface SettingsBuilder {
        fun build(
            state: State,
            onEvent: (Event) -> Unit,
        ): Settings
    }
}
