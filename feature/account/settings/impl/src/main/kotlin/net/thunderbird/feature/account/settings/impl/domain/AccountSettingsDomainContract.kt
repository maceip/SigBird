package net.thunderbird.feature.account.settings.impl.domain

import com.eygraber.uri.Uri
import kotlinx.coroutines.flow.Flow
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.core.validation.ValidationError
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.avatar.Avatar
import net.thunderbird.feature.account.profile.AccountProfile
import net.thunderbird.feature.account.settings.api.RemoteFolderSettingsInfo
import net.thunderbird.feature.mail.folder.api.SpecialFolderSelection
import net.thunderbird.feature.notification.NotificationLight
import net.thunderbird.feature.notification.NotificationVibration

internal interface AccountSettingsDomainContract {

    interface UseCase {
        fun interface GetAccountName {
            operator fun invoke(accountId: AccountId): Flow<Outcome<String, AccountSettingError>>
        }

        fun interface GetAccountProfile {
            operator fun invoke(accountId: AccountId): Flow<Outcome<AccountProfile, AccountSettingError>>
        }

        fun interface GetLegacyAccount {
            suspend operator fun invoke(accountId: AccountId): Outcome<LegacyAccount, AccountSettingError>
        }

        fun interface UpdateGeneralSettings {
            suspend operator fun invoke(
                accountId: AccountId,
                command: UpdateGeneralSettingCommand,
            ): Outcome<Unit, AccountSettingError>
        }

        fun interface UpdateReadMailSettings {
            suspend operator fun invoke(
                accountId: AccountId,
                command: UpdateReadMessageSettingsCommand,
            ): Outcome<Unit, AccountSettingError>
        }

        fun interface UpdateSearchSettings {
            suspend operator fun invoke(
                accountId: AccountId,
                command: UpdateSearchSettingsCommand,
            ): Outcome<Unit, AccountSettingError>
        }

        fun interface UpdateFetchingMailSettings {
            suspend operator fun invoke(
                accountId: AccountId,
                command: UpdateFetchingMailSettingsCommand,
            ): Outcome<Unit, AccountSettingError>
        }

        fun interface UpdateCompositionMailSettings {
            suspend operator fun invoke(
                accountId: AccountId,
                command: UpdateCompositionMailSettingsCommand,
            ): Outcome<Unit, AccountSettingError>
        }

        fun interface GetRemoteFolderSettings {
            suspend operator fun invoke(accountId: AccountId): Outcome<RemoteFolderSettingsInfo, AccountSettingError>
        }

        fun interface GetAccountCapabilities {
            suspend operator fun invoke(accountId: AccountId): Outcome<AccountCapabilities, AccountSettingError>
        }

        fun interface UpdateFolderSettings {
            suspend operator fun invoke(
                accountId: AccountId,
                command: UpdateFolderSettingsCommand,
            ): Outcome<Unit, AccountSettingError>
        }

        fun interface UpdateNotificationSettings {
            suspend operator fun invoke(
                accountId: AccountId,
                command: UpdateNotificationSettingsCommand,
            ): Outcome<Unit, AccountSettingError>
        }

        fun interface UpdateCryptoSettings {
            suspend operator fun invoke(
                accountId: AccountId,
                command: UpdateCryptoSettingsCommand,
            ): Outcome<Unit, AccountSettingError>
        }

        fun interface GetAllAccountProfiles {
            operator fun invoke(): Flow<List<AccountProfileSummary>>
        }

        fun interface UpdateAvatarImage {
            suspend operator fun invoke(
                accountId: AccountId,
                imageUri: Uri,
            ): Outcome<Avatar.Image, AccountSettingError>
        }

        fun interface ValidateAccountName {
            operator fun invoke(name: String): Outcome<Unit, ValidateAccountNameError>
        }

        fun interface ValidateAvatarMonogram {
            operator fun invoke(monogram: String): Outcome<Unit, ValidateMonogramError>
        }
    }

    sealed interface UpdateGeneralSettingCommand {
        data class UpdateName(val value: String) : UpdateGeneralSettingCommand
        data class UpdateColor(val value: Int) : UpdateGeneralSettingCommand
        data class UpdateAvatar(val value: Avatar) : UpdateGeneralSettingCommand
    }

    sealed interface UpdateReadMessageSettingsCommand {
        data class UpdateShowPictures(val value: String) : UpdateReadMessageSettingsCommand
        data class UpdateIsMarkMessageAsReadOnView(val value: Boolean) : UpdateReadMessageSettingsCommand
    }

    sealed interface UpdateSearchSettingsCommand {
        data class UpdateServerSearchLimit(val value: Int) : UpdateSearchSettingsCommand
    }

    sealed interface UpdateFetchingMailSettingsCommand {
        data class UpdateLocalFolderSize(val value: Int) : UpdateFetchingMailSettingsCommand
        data class UpdateSyncMessageFrom(val value: Int) : UpdateFetchingMailSettingsCommand
        data class UpdateFetchMessageUpTo(val value: Int) : UpdateFetchingMailSettingsCommand
        data class UpdateFolderPollFrequency(val value: Int) : UpdateFetchingMailSettingsCommand
        data class UpdateSyncServerDeletions(val value: Boolean) : UpdateFetchingMailSettingsCommand
        data class UpdateMarkAsReadWhenDeleted(val value: Boolean) : UpdateFetchingMailSettingsCommand
        data class UpdateDeletePolicy(val value: String) : UpdateFetchingMailSettingsCommand
        data class UpdateExpungePolicy(val value: String) : UpdateFetchingMailSettingsCommand
        data class UpdateMaxPushFolders(val value: Int) : UpdateFetchingMailSettingsCommand
        data class UpdateIdleRefreshMinutes(val value: Int) : UpdateFetchingMailSettingsCommand
    }

    sealed interface UpdateCompositionMailSettingsCommand {
        data class UpdateMessageFormat(val value: String) : UpdateCompositionMailSettingsCommand
        data class UpdateAlwaysShowCcBcc(val value: Boolean) : UpdateCompositionMailSettingsCommand
        data class UpdateMessageReadReceipt(val value: Boolean) : UpdateCompositionMailSettingsCommand
        data class UpdateQuoteStyle(val value: String) : UpdateCompositionMailSettingsCommand
        data class UpdateDefaultQuotedTextShown(val value: Boolean) : UpdateCompositionMailSettingsCommand
        data class UpdateReplyAfterQuote(val value: Boolean) : UpdateCompositionMailSettingsCommand
        data class UpdateStripSignature(val value: Boolean) : UpdateCompositionMailSettingsCommand
        data class UpdateQuotePrefix(val value: String) : UpdateCompositionMailSettingsCommand
        data class UpdateUploadSentMessages(val value: Boolean) : UpdateCompositionMailSettingsCommand
    }

    data class AccountProfileSummary(
        val accountId: AccountId,
        val name: String,
    )

    data class AccountCapabilities(
        val supportsFolderSubscriptions: Boolean,
        val isMoveCapable: Boolean,
        val hasVibrator: Boolean,
    )

    sealed interface UpdateFolderSettingsCommand {
        data class UpdateAutoExpandFolder(val folderId: Long?) : UpdateFolderSettingsCommand
        data class UpdateSubscribedFoldersOnly(val value: Boolean) : UpdateFolderSettingsCommand
        data class UpdateArchiveFolder(
            val folderId: Long?,
            val selection: SpecialFolderSelection,
        ) : UpdateFolderSettingsCommand
        data class UpdateDraftsFolder(
            val folderId: Long?,
            val selection: SpecialFolderSelection,
        ) : UpdateFolderSettingsCommand
        data class UpdateSentFolder(
            val folderId: Long?,
            val selection: SpecialFolderSelection,
        ) : UpdateFolderSettingsCommand
        data class UpdateSpamFolder(
            val folderId: Long?,
            val selection: SpecialFolderSelection,
        ) : UpdateFolderSettingsCommand
        data class UpdateTrashFolder(
            val folderId: Long?,
            val selection: SpecialFolderSelection,
        ) : UpdateFolderSettingsCommand
    }

    sealed interface UpdateNotificationSettingsCommand {
        data class UpdateNotifyNewMail(val value: Boolean) : UpdateNotificationSettingsCommand
        data class UpdateNotifySelf(val value: Boolean) : UpdateNotificationSettingsCommand
        data class UpdateNotifyContactsOnly(val value: Boolean) : UpdateNotificationSettingsCommand
        data class UpdateIgnoreChatMessages(val value: Boolean) : UpdateNotificationSettingsCommand
        data class UpdateNotifySync(val value: Boolean) : UpdateNotificationSettingsCommand
        data class UpdateRingtone(val value: String?) : UpdateNotificationSettingsCommand
        data class UpdateNotificationLight(val value: NotificationLight) : UpdateNotificationSettingsCommand
        data class UpdateVibration(val value: NotificationVibration) : UpdateNotificationSettingsCommand
    }

    sealed interface UpdateCryptoSettingsCommand {
        data class UpdateOpenPgpProvider(val providerPackage: String?) : UpdateCryptoSettingsCommand
        data class UpdateOpenPgpKey(val keyId: Long) : UpdateCryptoSettingsCommand
        data class UpdateAutocryptPreferEncrypt(val value: Boolean) : UpdateCryptoSettingsCommand
        data class UpdateHideSignOnly(val value: Boolean) : UpdateCryptoSettingsCommand
        data class UpdateEncryptSubject(val value: Boolean) : UpdateCryptoSettingsCommand
        data class UpdateEncryptAllDrafts(val value: Boolean) : UpdateCryptoSettingsCommand
    }

    sealed interface AccountSettingError {
        data class NotFound(
            val message: String,
        ) : AccountSettingError

        data class StorageError(
            val message: String,
        ) : AccountSettingError

        data class UnsupportedFormat(
            val message: String,
        ) : AccountSettingError
    }

    sealed interface ValidateAccountNameError : ValidationError {
        data object EmptyName : ValidateAccountNameError
        data object TooLongName : ValidateAccountNameError
    }

    sealed interface ValidateMonogramError : ValidationError {
        data object EmptyMonogram : ValidateMonogramError
        data object TooLongMonogram : ValidateMonogramError
    }
}
