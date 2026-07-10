package net.thunderbird.feature.account.settings

import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.feature.account.settings.api.AccountSettingsNavigation
import net.thunderbird.feature.account.settings.impl.DefaultAccountSettingsNavigation
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase
import net.thunderbird.feature.account.settings.impl.domain.usecase.GetAccountCapabilities
import net.thunderbird.feature.account.settings.impl.domain.usecase.GetAccountName
import net.thunderbird.feature.account.settings.impl.domain.usecase.GetAccountProfile
import net.thunderbird.feature.account.settings.impl.domain.usecase.GetAllAccountProfiles
import net.thunderbird.feature.account.settings.impl.domain.usecase.GetLegacyAccount
import net.thunderbird.feature.account.settings.impl.domain.usecase.GetRemoteFolderSettings
import net.thunderbird.feature.account.settings.impl.domain.usecase.UpdateAvatarImage
import net.thunderbird.feature.account.settings.impl.domain.usecase.UpdateCompositionMailSettings
import net.thunderbird.feature.account.settings.impl.domain.usecase.UpdateCryptoSettings
import net.thunderbird.feature.account.settings.impl.domain.usecase.UpdateFetchingMailSettings
import net.thunderbird.feature.account.settings.impl.domain.usecase.UpdateFolderSettings
import net.thunderbird.feature.account.settings.impl.domain.usecase.UpdateGeneralSettings
import net.thunderbird.feature.account.settings.impl.domain.usecase.UpdateNotificationSettings
import net.thunderbird.feature.account.settings.impl.domain.usecase.UpdateReadEmailSettings
import net.thunderbird.feature.account.settings.impl.domain.usecase.UpdateSearchSettings
import net.thunderbird.feature.account.settings.impl.domain.usecase.ValidateAccountName
import net.thunderbird.feature.account.settings.impl.domain.usecase.ValidateAvatarMonogram
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsBuilder
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsContract
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsOptionsMapper
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsViewModel
import net.thunderbird.feature.account.settings.impl.ui.crypto.CryptoSettingsBuilder
import net.thunderbird.feature.account.settings.impl.ui.crypto.CryptoSettingsContract
import net.thunderbird.feature.account.settings.impl.ui.crypto.CryptoSettingsViewModel
import net.thunderbird.feature.account.settings.impl.ui.fetchingMail.FetchingMailSettingsBuilder
import net.thunderbird.feature.account.settings.impl.ui.fetchingMail.FetchingMailSettingsContract
import net.thunderbird.feature.account.settings.impl.ui.fetchingMail.FetchingMailSettingsOptionsMapper
import net.thunderbird.feature.account.settings.impl.ui.fetchingMail.FetchingMailSettingsViewModel
import net.thunderbird.feature.account.settings.impl.ui.folders.FolderDisplayNameFormatter
import net.thunderbird.feature.account.settings.impl.ui.folders.FolderOptionsMapper
import net.thunderbird.feature.account.settings.impl.ui.folders.FolderSettingsBuilder
import net.thunderbird.feature.account.settings.impl.ui.folders.FolderSettingsContract
import net.thunderbird.feature.account.settings.impl.ui.folders.FolderSettingsViewModel
import net.thunderbird.feature.account.settings.impl.ui.general.GeneralSettingsBuilder
import net.thunderbird.feature.account.settings.impl.ui.general.GeneralSettingsContract
import net.thunderbird.feature.account.settings.impl.ui.general.GeneralSettingsValidator
import net.thunderbird.feature.account.settings.impl.ui.general.GeneralSettingsViewModel
import net.thunderbird.feature.account.settings.impl.ui.hub.HubSettingsBuilder
import net.thunderbird.feature.account.settings.impl.ui.hub.HubSettingsContract
import net.thunderbird.feature.account.settings.impl.ui.hub.HubSettingsViewModel
import net.thunderbird.feature.account.settings.impl.ui.notifications.NotificationSettingsBuilder
import net.thunderbird.feature.account.settings.impl.ui.notifications.NotificationSettingsContract
import net.thunderbird.feature.account.settings.impl.ui.notifications.NotificationSettingsOptionsMapper
import net.thunderbird.feature.account.settings.impl.ui.notifications.NotificationSettingsViewModel
import net.thunderbird.feature.account.settings.impl.ui.notifications.RingtoneSummaryFormatter
import net.thunderbird.feature.account.settings.impl.ui.notifications.VibrationSummaryFormatter
import net.thunderbird.feature.account.settings.impl.ui.readingMail.ReadingMailSettingsBuilder
import net.thunderbird.feature.account.settings.impl.ui.readingMail.ReadingMailSettingsContract
import net.thunderbird.feature.account.settings.impl.ui.readingMail.ReadingMailSettingsViewModel
import net.thunderbird.feature.account.settings.impl.ui.search.SearchSettingBuilder
import net.thunderbird.feature.account.settings.impl.ui.search.SearchSettingsContract
import net.thunderbird.feature.account.settings.impl.ui.search.SearchSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val featureAccountSettingsModule = module {
    single<AccountSettingsNavigation> { DefaultAccountSettingsNavigation() }

    factory<UseCase.GetAccountName> {
        GetAccountName(
            repository = get(),
        )
    }

    factory<UseCase.UpdateReadMailSettings> {
        UpdateReadEmailSettings(
            repository = get(),
        )
    }

    factory<UseCase.UpdateCompositionMailSettings> {
        UpdateCompositionMailSettings(
            repository = get(),
        )
    }

    factory<UseCase.UpdateFetchingMailSettings> {
        UpdateFetchingMailSettings(
            repository = get(),
        )
    }

    factory<UseCase.UpdateSearchSettings> {
        UpdateSearchSettings(
            repository = get(),
        )
    }

    factory<UseCase.GetAllAccountProfiles> {
        GetAllAccountProfiles(
            repository = get(),
        )
    }

    factory<UseCase.GetAccountCapabilities> {
        GetAccountCapabilities(
            capabilities = get(),
        )
    }

    factory<UseCase.GetRemoteFolderSettings> {
        GetRemoteFolderSettings(
            folderProvider = get(),
        )
    }

    factory<UseCase.UpdateFolderSettings> {
        UpdateFolderSettings(
            repository = get(),
            folderRefresh = get(),
        )
    }

    factory<UseCase.UpdateNotificationSettings> {
        UpdateNotificationSettings(
            repository = get(),
            notificationBridge = get(),
        )
    }

    factory<UseCase.UpdateCryptoSettings> {
        UpdateCryptoSettings(
            repository = get(),
        )
    }

    factory<UseCase.GetAccountProfile> {
        GetAccountProfile(
            repository = get(),
        )
    }

    factory<UseCase.GetLegacyAccount> {
        GetLegacyAccount(
            repository = get(),
        )
    }

    factory<UseCase.UpdateAvatarImage> {
        UpdateAvatarImage(
            repository = get(),
            mimeTypeResolver = get(),
        )
    }

    factory<UseCase.UpdateGeneralSettings> {
        UpdateGeneralSettings(
            repository = get(),
        )
    }

    factory<GeneralSettingsContract.Validator> {
        GeneralSettingsValidator(
            accountNameValidator = ValidateAccountName(),
            avatarMonogramValidator = ValidateAvatarMonogram(),
        )
    }

    factory<GeneralSettingsContract.SettingsBuilder> {
        GeneralSettingsBuilder(
            resources = get<StringsResourceManager>(),
            accountColors = get<ImmutableList<Int>>(named("AccountColors")),
            monogramCreator = get(),
            validator = get(),
            featureFlagProvider = get(),
            iconCatalog = get(),
        )
    }

    viewModel { params ->
        GeneralSettingsViewModel(
            accountId = params.get(),
            getAccountName = get(),
            getAccountProfile = get(),
            updateGeneralSettings = get(),
            updateAvatarImage = get(),
            logger = get(),
        )
    }

    factory<ReadingMailSettingsContract.SettingsBuilder> {
        ReadingMailSettingsBuilder(
            resources = get<StringsResourceManager>(),
        )
    }

    factory<CompositionMailSettingsOptionsMapper> {
        CompositionMailSettingsOptionsMapper(
            resources = get<StringsResourceManager>(),
        )
    }

    factory<CompositionMailSettingsContract.SettingsBuilder> {
        CompositionMailSettingsBuilder(
            resources = get<StringsResourceManager>(),
            optionsMapper = get(),
        )
    }

    viewModel { params ->
        CompositionMailSettingsViewModel(
            accountId = params.get(),
            getAccountName = get(),
            getLegacyAccount = get(),
            updateCompositionMailSettings = get(),
            resources = get(),
            logger = get(),
        )
    }

    factory<FetchingMailSettingsOptionsMapper> {
        FetchingMailSettingsOptionsMapper(
            resources = get<StringsResourceManager>(),
        )
    }

    factory<FetchingMailSettingsContract.SettingsBuilder> {
        FetchingMailSettingsBuilder(
            resources = get<StringsResourceManager>(),
            fetchingMailSettingsOptionsMapper = get<FetchingMailSettingsOptionsMapper>(),
        )
    }

    factory<SearchSettingsContract.SettingsBuilder> {
        SearchSettingBuilder(
            resources = get<StringsResourceManager>(),
        )
    }

    viewModel { params ->
        ReadingMailSettingsViewModel(
            accountId = params.get(),
            getAccountName = get(),
            getLegacyAccount = get(),
            updateReadMailSettings = get(),
            resources = get(),
            logger = get(),
        )
    }

    viewModel { params ->
        FetchingMailSettingsViewModel(
            accountId = params.get(),
            logger = get(),
            getAccountName = get(),
            getLegacyAccount = get(),
            updateFetchingMailSettings = get(),
            fetchingMailSettingsOptionsMapper = get(),
        )
    }

    viewModel { params ->
        SearchSettingsViewModel(
            accountId = params.get(),
            getAccountName = get(),
            getLegacyAccount = get(),
            updateSearchSettings = get(),
            logger = get(),
            resources = get(),
        )
    }

    factory<HubSettingsContract.SettingsBuilder> {
        HubSettingsBuilder(
            resources = get<StringsResourceManager>(),
        )
    }

    viewModel { params ->
        HubSettingsViewModel(
            accountId = params.get(),
            getAccountName = get(),
            getAllAccountProfiles = get(),
            logger = get(),
        )
    }

    factory {
        FolderOptionsMapper(
            resources = get<StringsResourceManager>(),
            folderNameFormatter = get<FolderDisplayNameFormatter>(),
        )
    }

    factory<FolderSettingsContract.SettingsBuilder> {
        FolderSettingsBuilder(
            resources = get<StringsResourceManager>(),
        )
    }

    viewModel { params ->
        FolderSettingsViewModel(
            accountId = params.get(),
            getAccountName = get(),
            getLegacyAccount = get(),
            getRemoteFolderSettings = get(),
            getAccountCapabilities = get(),
            updateFolderSettings = get(),
            folderOptionsMapper = get(),
            logger = get(),
        )
    }

    factory {
        NotificationSettingsOptionsMapper(
            resources = get<StringsResourceManager>(),
        )
    }

    factory<NotificationSettingsContract.SettingsBuilder> {
        NotificationSettingsBuilder(
            resources = get<StringsResourceManager>(),
            optionsMapper = get(),
        )
    }

    viewModel { params ->
        NotificationSettingsViewModel(
            accountId = params.get(),
            getAccountName = get(),
            getLegacyAccount = get(),
            getAccountCapabilities = get(),
            updateNotificationSettings = get(),
            notificationBridge = get(),
            optionsMapper = get(),
            ringtoneSummaryFormatter = get(),
            vibrationSummaryFormatter = get(),
            logger = get(),
        )
    }

    factory<CryptoSettingsContract.SettingsBuilder> {
        CryptoSettingsBuilder(
            resources = get<StringsResourceManager>(),
        )
    }

    viewModel { params ->
        CryptoSettingsViewModel(
            accountId = params.get(),
            getAccountName = get(),
            getLegacyAccount = get(),
            updateCryptoSettings = get(),
            providerSummaryProvider = get(),
            resources = get(),
            logger = get(),
        )
    }
}
