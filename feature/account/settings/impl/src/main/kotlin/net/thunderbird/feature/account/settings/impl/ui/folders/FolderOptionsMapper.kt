package net.thunderbird.feature.account.settings.impl.ui.folders

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.ui.setting.SettingValue.Select.SelectOption
import net.thunderbird.feature.account.settings.R
import net.thunderbird.feature.account.settings.api.RemoteFolderSettingsInfo
import net.thunderbird.feature.mail.folder.api.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder
import net.thunderbird.feature.mail.folder.api.SpecialFolderSelection

internal class FolderOptionsMapper(
    private val resources: StringsResourceManager,
    private val folderNameFormatter: FolderDisplayNameFormatter,
) {
    fun buildAutoExpandOptions(
        folderInfo: RemoteFolderSettingsInfo,
    ): ImmutableList<SelectOption> {
        return buildManualFolderOptions(folderInfo.folders)
    }

    fun buildSpecialFolderOptions(
        folderInfo: RemoteFolderSettingsInfo,
        folderType: FolderType,
    ): ImmutableList<SelectOption> {
        val automaticFolder = folderInfo.automaticSpecialFolders[folderType]
        val automaticName = automaticFolder?.let { folderNameFormatter.displayName(it) }
            ?: resources.stringResource(R.string.account_settings_no_folder_selected)

        val options = mutableListOf<SelectOption>()
        options += SelectOption(FolderOptionEncoding.automatic(automaticFolder?.id)) {
            resources.stringResource(R.string.account_settings_automatic_special_folder, automaticName)
        }
        options += SelectOption(FolderOptionEncoding.manual(null)) {
            resources.stringResource(R.string.account_settings_no_folder_selected)
        }
        options += folderInfo.folders.map { folder ->
            SelectOption(FolderOptionEncoding.manual(folder.id)) {
                folderNameFormatter.displayName(folder)
            }
        }
        return options.toImmutableList()
    }

    fun optionForAutoExpandFolder(
        folderId: Long?,
        options: ImmutableList<SelectOption>,
    ): SelectOption? {
        val encoded = FolderOptionEncoding.manual(folderId)
        return options.find { it.id == encoded } ?: options.firstOrNull()
    }

    fun optionForSpecialFolder(
        folderId: Long?,
        selection: SpecialFolderSelection,
        folderType: FolderType,
        folderInfo: RemoteFolderSettingsInfo,
        options: ImmutableList<SelectOption>,
    ): SelectOption? {
        val encoded = if (selection == SpecialFolderSelection.AUTOMATIC) {
            FolderOptionEncoding.automatic(folderInfo.automaticSpecialFolders[folderType]?.id)
        } else {
            FolderOptionEncoding.manual(folderId)
        }
        return options.find { it.id == encoded } ?: options.firstOrNull()
    }

    private fun buildManualFolderOptions(folders: List<RemoteFolder>): ImmutableList<SelectOption> {
        val options = mutableListOf<SelectOption>()
        options += SelectOption(FolderOptionEncoding.manual(null)) {
            resources.stringResource(R.string.account_settings_no_folder_selected)
        }
        options += folders.map { folder ->
            SelectOption(FolderOptionEncoding.manual(folder.id)) {
                folderNameFormatter.displayName(folder)
            }
        }
        return options.toImmutableList()
    }
}
