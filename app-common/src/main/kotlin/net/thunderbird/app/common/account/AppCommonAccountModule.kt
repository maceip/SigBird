package net.thunderbird.app.common.account

import androidx.compose.ui.graphics.vector.ImageVector
import app.k9mail.feature.account.setup.AccountSetupExternalContract
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.app.common.account.data.DefaultAccountProfileLocalDataSource
import net.thunderbird.app.common.account.data.DefaultLegacyAccountManager
import net.thunderbird.app.common.account.data.DefaultLegacyAccountRepository
import net.thunderbird.core.android.account.AccountDefaultsProvider
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.core.android.account.LegacyAccountRepository
import net.thunderbird.feature.account.avatar.AvatarIcon
import net.thunderbird.feature.account.avatar.AvatarIconCatalog
import net.thunderbird.feature.account.avatar.AvatarMonogramCreator
import net.thunderbird.feature.account.avatar.DefaultAvatarIconCatalog
import net.thunderbird.feature.account.avatar.DefaultAvatarMonogramCreator
import net.thunderbird.feature.account.core.AccountCoreExternalContract.AccountProfileLocalDataSource
import net.thunderbird.feature.account.core.featureAccountCoreModule
import net.thunderbird.feature.account.settings.api.AccountSettingsCryptoBridge
import net.thunderbird.feature.account.settings.api.AccountSettingsCapabilities
import net.thunderbird.feature.account.settings.api.AccountSettingsFolderProvider
import net.thunderbird.feature.account.settings.api.AccountSettingsFolderRefresh
import net.thunderbird.feature.account.settings.api.AccountSettingsLegacyNavigation
import net.thunderbird.feature.account.settings.api.AccountSettingsNotificationBridge
import net.thunderbird.feature.account.settings.api.OpenPgpProviderSummaryProvider
import net.thunderbird.feature.account.settings.impl.ui.folders.FolderDisplayNameFormatter
import net.thunderbird.feature.account.settings.impl.ui.notifications.RingtoneSummaryFormatter
import net.thunderbird.feature.account.settings.impl.ui.notifications.VibrationSummaryFormatter
import net.thunderbird.feature.account.storage.legacy.featureAccountStorageLegacyModule
import net.thunderbird.feature.mail.account.api.AccountManager
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.binds
import org.koin.dsl.module
import app.k9mail.core.ui.legacy.theme2.common.R as ThemeCommonR

internal val appCommonAccountModule = module {
    includes(
        featureAccountCoreModule,
        featureAccountStorageLegacyModule,
    )

    single<AccountSettingsLegacyNavigation> {
        DefaultAccountSettingsLegacyNavigation()
    }

    single<AccountSettingsCapabilities> {
        DefaultAccountSettingsCapabilities(
            accountManager = get(),
            messagingController = get(),
            vibrator = get(),
        )
    }

    single<AccountSettingsFolderProvider> {
        DefaultAccountSettingsFolderProvider(
            accountManager = get(),
            folderRepository = get(),
            specialFolderSelectionStrategy = get(),
        )
    }

    single<AccountSettingsFolderRefresh> {
        DefaultAccountSettingsFolderRefresh(
            messagingController = get(),
            accountManager = get(),
        )
    }

    single<AccountSettingsNotificationBridge> {
        DefaultAccountSettingsNotificationBridge(
            accountManager = get(),
            notificationChannelManager = get(),
            notificationSettingsUpdater = get(),
        )
    }

    single<AccountSettingsCryptoBridge> {
        DefaultAccountSettingsCryptoBridge()
    }

    factory<FolderDisplayNameFormatter> {
        DefaultFolderDisplayNameFormatter(
            context = androidContext(),
        )
    }

    factory<RingtoneSummaryFormatter> {
        DefaultRingtoneSummaryFormatter(
            context = androidContext(),
            resources = get(),
        )
    }

    factory<VibrationSummaryFormatter> {
        DefaultVibrationSummaryFormatter(
            resources = get(),
        )
    }

    factory<OpenPgpProviderSummaryProvider> {
        DefaultOpenPgpProviderSummaryProvider(
            resources = get(),
            packageManager = androidContext().packageManager,
        )
    }

    single<AccountManager<LegacyAccount>> {
        DefaultLegacyAccountManager(
            accountManager = get(),
            accountDataMapper = get(),
        )
    } binds arrayOf(LegacyAccountManager::class)

    single<AccountProfileLocalDataSource> {
        DefaultAccountProfileLocalDataSource(
            accountManager = get(),
            dataMapper = get(),
        )
    }

    single<AccountDefaultsProvider> {
        DefaultAccountDefaultsProvider(
            resourceProvider = get(),
            featureFlagProvider = get(),
        )
    }

    factory<ImmutableList<Int>>(named("AccountColors")) {
        androidContext().resources.getIntArray(
            ThemeCommonR.array.account_colors,
        ).toList().toImmutableList()
    }

    factory {
        AccountColorPicker(
            repository = get(),
            accountColors = get(named("AccountColors")),
        )
    }

    single<AvatarIconCatalog<AvatarIcon<ImageVector>>> {
        DefaultAvatarIconCatalog()
    }

    factory<AvatarMonogramCreator> {
        DefaultAvatarMonogramCreator()
    }

    factory<LegacyAccountRepository> {
        DefaultLegacyAccountRepository(
            accountManager = get(),
        )
    }

    factory<AccountSetupExternalContract.AccountCreator> {
        AccountCreator(
            accountColorPicker = get(),
            localFoldersCreator = get(),
            preferences = get(),
            context = androidApplication(),
            deletePolicyProvider = get(),
            messagingController = get(),
            avatarMonogramCreator = get(),
            unifiedInboxConfigurator = get(),
        )
    }
}
