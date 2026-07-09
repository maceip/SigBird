package net.thunderbird.feature.account.settings.impl.ui.folders

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
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase
import net.thunderbird.feature.mail.folder.api.FolderType
import net.thunderbird.feature.mail.folder.api.SpecialFolderSelection

private const val TAG = "FolderSettingsViewModel"

@Suppress("LongMethod", "CyclomaticComplexMethod")
internal class FolderSettingsViewModel(
    private val accountId: AccountId,
    private val getAccountName: UseCase.GetAccountName,
    private val getLegacyAccount: UseCase.GetLegacyAccount,
    private val getRemoteFolderSettings: UseCase.GetRemoteFolderSettings,
    private val getAccountCapabilities: UseCase.GetAccountCapabilities,
    private val updateFolderSettings: UseCase.UpdateFolderSettings,
    private val folderOptionsMapper: FolderOptionsMapper,
    private val logger: Logger,
) : BaseViewModel<FolderSettingsContract.State, FolderSettingsContract.Event, FolderSettingsContract.Effect>(
    FolderSettingsContract.State(),
),
    FolderSettingsContract.ViewModel {

    init {
        observeAccountName()
        loadSettings()
    }

    override fun event(event: FolderSettingsContract.Event) {
        when (event) {
            FolderSettingsContract.Event.OnBackPressed -> emitEffect(FolderSettingsContract.Effect.NavigateBack)
            is FolderSettingsContract.Event.OnAutoExpandFolderChange -> updateFolder(
                AccountSettingsDomainContract.UpdateFolderSettingsCommand.UpdateAutoExpandFolder(
                    folderId = FolderOptionEncoding.folderId(event.option.id),
                ),
                onSuccess = { updateState { it.copy(autoExpandFolder = event.option) } },
            )
            is FolderSettingsContract.Event.OnSubscribedFoldersOnlyToggle -> updateFolder(
                AccountSettingsDomainContract.UpdateFolderSettingsCommand.UpdateSubscribedFoldersOnly(event.enabled),
                onSuccess = { updateState { it.copy(subscribedFoldersOnly = event.enabled) } },
            )
            is FolderSettingsContract.Event.OnArchiveFolderChange -> updateSpecialFolder(
                event.option,
                FolderType.ARCHIVE,
                AccountSettingsDomainContract.UpdateFolderSettingsCommand::UpdateArchiveFolder,
            )
            is FolderSettingsContract.Event.OnDraftsFolderChange -> updateSpecialFolder(
                event.option,
                FolderType.DRAFTS,
                AccountSettingsDomainContract.UpdateFolderSettingsCommand::UpdateDraftsFolder,
            )
            is FolderSettingsContract.Event.OnSentFolderChange -> updateSpecialFolder(
                event.option,
                FolderType.SENT,
                AccountSettingsDomainContract.UpdateFolderSettingsCommand::UpdateSentFolder,
            )
            is FolderSettingsContract.Event.OnSpamFolderChange -> updateSpecialFolder(
                event.option,
                FolderType.SPAM,
                AccountSettingsDomainContract.UpdateFolderSettingsCommand::UpdateSpamFolder,
            )
            is FolderSettingsContract.Event.OnTrashFolderChange -> updateSpecialFolder(
                event.option,
                FolderType.TRASH,
                AccountSettingsDomainContract.UpdateFolderSettingsCommand::UpdateTrashFolder,
            )
        }
    }

    private fun updateSpecialFolder(
        option: SelectOption,
        folderType: FolderType,
        commandFactory: (Long?, SpecialFolderSelection) -> AccountSettingsDomainContract.UpdateFolderSettingsCommand,
    ) {
        val selection = if (FolderOptionEncoding.isAutomatic(option.id)) {
            SpecialFolderSelection.AUTOMATIC
        } else {
            SpecialFolderSelection.MANUAL
        }
        val folderId = FolderOptionEncoding.folderId(option.id)
        val command = commandFactory(folderId, selection)
        updateFolder(command) {
            val stateKey = when (folderType) {
                FolderType.ARCHIVE -> { state: FolderSettingsContract.State -> state.copy(archiveFolder = option) }
                FolderType.DRAFTS -> { state: FolderSettingsContract.State -> state.copy(draftsFolder = option) }
                FolderType.SENT -> { state: FolderSettingsContract.State -> state.copy(sentFolder = option) }
                FolderType.SPAM -> { state: FolderSettingsContract.State -> state.copy(spamFolder = option) }
                FolderType.TRASH -> { state: FolderSettingsContract.State -> state.copy(trashFolder = option) }
                else -> return@updateFolder
            }
            updateState(stateKey)
        }
    }

    private fun updateFolder(
        command: AccountSettingsDomainContract.UpdateFolderSettingsCommand,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            updateFolderSettings(accountId = accountId, command = command).handle(
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
            val capabilities = getAccountCapabilities(accountId).fold(
                onSuccess = { it },
                onFailure = {
                    handleError(it)
                    return@launch
                },
            )
            val folderInfo = getRemoteFolderSettings(accountId).fold(
                onSuccess = { it },
                onFailure = {
                    handleError(it)
                    return@launch
                },
            )
            val account = getLegacyAccount(accountId).fold(
                onSuccess = { it },
                onFailure = {
                    handleError(it)
                    return@launch
                },
            )

            val autoExpandOptions = folderOptionsMapper.buildAutoExpandOptions(folderInfo)
            val archiveOptions = folderOptionsMapper.buildSpecialFolderOptions(folderInfo, FolderType.ARCHIVE)
            val draftsOptions = folderOptionsMapper.buildSpecialFolderOptions(folderInfo, FolderType.DRAFTS)
            val sentOptions = folderOptionsMapper.buildSpecialFolderOptions(folderInfo, FolderType.SENT)
            val spamOptions = folderOptionsMapper.buildSpecialFolderOptions(folderInfo, FolderType.SPAM)
            val trashOptions = folderOptionsMapper.buildSpecialFolderOptions(folderInfo, FolderType.TRASH)

            updateState { state ->
                state.copy(
                    isLoading = false,
                    supportsFolderSubscriptions = capabilities.supportsFolderSubscriptions,
                    isMoveCapable = capabilities.isMoveCapable,
                    subscribedFoldersOnly = account.isSubscribedFoldersOnly,
                    autoExpandFolderOptions = autoExpandOptions,
                    archiveFolderOptions = archiveOptions,
                    draftsFolderOptions = draftsOptions,
                    sentFolderOptions = sentOptions,
                    spamFolderOptions = spamOptions,
                    trashFolderOptions = trashOptions,
                    autoExpandFolder = folderOptionsMapper.optionForAutoExpandFolder(
                        account.autoExpandFolderId,
                        autoExpandOptions,
                    ),
                    archiveFolder = folderOptionsMapper.optionForSpecialFolder(
                        account.archiveFolderId,
                        account.archiveFolderSelection,
                        FolderType.ARCHIVE,
                        folderInfo,
                        archiveOptions,
                    ),
                    draftsFolder = folderOptionsMapper.optionForSpecialFolder(
                        account.draftsFolderId,
                        account.draftsFolderSelection,
                        FolderType.DRAFTS,
                        folderInfo,
                        draftsOptions,
                    ),
                    sentFolder = folderOptionsMapper.optionForSpecialFolder(
                        account.sentFolderId,
                        account.sentFolderSelection,
                        FolderType.SENT,
                        folderInfo,
                        sentOptions,
                    ),
                    spamFolder = folderOptionsMapper.optionForSpecialFolder(
                        account.spamFolderId,
                        account.spamFolderSelection,
                        FolderType.SPAM,
                        folderInfo,
                        spamOptions,
                    ),
                    trashFolder = folderOptionsMapper.optionForSpecialFolder(
                        account.trashFolderId,
                        account.trashFolderSelection,
                        FolderType.TRASH,
                        folderInfo,
                        trashOptions,
                    ),
                )
            }
        }
    }

    private fun handleError(error: AccountSettingsDomainContract.AccountSettingError) {
        logger.error(tag = TAG, message = { error.toString() })
    }
}
