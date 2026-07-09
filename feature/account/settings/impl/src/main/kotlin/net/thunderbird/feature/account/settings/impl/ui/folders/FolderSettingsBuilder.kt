package net.thunderbird.feature.account.settings.impl.ui.folders

import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.ui.setting.Setting
import net.thunderbird.core.ui.setting.SettingValue
import net.thunderbird.core.ui.setting.Settings
import net.thunderbird.feature.account.settings.R

internal class FolderSettingsBuilder(
    private val resources: StringsResourceManager,
) : FolderSettingsContract.SettingsBuilder {
    override fun build(
        state: FolderSettingsContract.State,
        onEvent: (FolderSettingsContract.Event) -> Unit,
    ): Settings {
        if (state.isLoading) {
            return kotlinx.collections.immutable.persistentListOf()
        }

        val settings = mutableListOf<Setting>()

        state.autoExpandFolder?.let { value ->
            settings += folderSelect(
                id = FolderSettingId.AUTO_EXPAND_FOLDER,
                titleRes = R.string.account_settings_auto_select_folder,
                value = value,
                options = state.autoExpandFolderOptions,
            )
        }

        if (state.supportsFolderSubscriptions) {
            settings += SettingValue.Switch(
                id = FolderSettingId.SUBSCRIBED_FOLDERS_ONLY,
                title = { resources.stringResource(R.string.account_settings_subscribed_folders_only) },
                value = state.subscribedFoldersOnly,
            )
        }

        if (state.isMoveCapable) {
            state.archiveFolder?.let {
                settings += folderSelect(
                    id = FolderSettingId.ARCHIVE_FOLDER,
                    titleRes = R.string.account_settings_archive_folder,
                    value = it,
                    options = state.archiveFolderOptions,
                )
            }
            state.draftsFolder?.let {
                settings += folderSelect(
                    id = FolderSettingId.DRAFTS_FOLDER,
                    titleRes = R.string.account_settings_drafts_folder,
                    value = it,
                    options = state.draftsFolderOptions,
                )
            }
            state.sentFolder?.let {
                settings += folderSelect(
                    id = FolderSettingId.SENT_FOLDER,
                    titleRes = R.string.account_settings_sent_folder,
                    value = it,
                    options = state.sentFolderOptions,
                )
            }
            state.spamFolder?.let {
                settings += folderSelect(
                    id = FolderSettingId.SPAM_FOLDER,
                    titleRes = R.string.account_settings_spam_folder,
                    value = it,
                    options = state.spamFolderOptions,
                )
            }
            state.trashFolder?.let {
                settings += folderSelect(
                    id = FolderSettingId.TRASH_FOLDER,
                    titleRes = R.string.account_settings_trash_folder,
                    value = it,
                    options = state.trashFolderOptions,
                )
            }
        }

        return settings.toImmutableList()
    }

    private fun folderSelect(
        id: String,
        titleRes: Int,
        value: SettingValue.Select.SelectOption,
        options: kotlinx.collections.immutable.ImmutableList<SettingValue.Select.SelectOption>,
    ): Setting = SettingValue.Select(
        id = id,
        title = { resources.stringResource(titleRes) },
        displayValueAsSecondaryText = true,
        value = value,
        options = options,
    )
}
