package net.thunderbird.feature.account.settings.impl

import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import net.thunderbird.core.ui.navigation.deepLinkComposable
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.settings.api.AccountSettingsNavigation
import net.thunderbird.feature.account.settings.api.AccountSettingsRoute
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsScreen
import net.thunderbird.feature.account.settings.impl.ui.crypto.CryptoSettingsScreen
import net.thunderbird.feature.account.settings.impl.ui.fetchingMail.FetchingMailSettingsScreen
import net.thunderbird.feature.account.settings.impl.ui.fetchingMail.advanced.AdvancedFetchingMailSettingsScreen
import net.thunderbird.feature.account.settings.impl.ui.folders.FolderSettingsScreen
import net.thunderbird.feature.account.settings.impl.ui.general.GeneralSettingsScreen
import net.thunderbird.feature.account.settings.impl.ui.hub.HubSettingsScreen
import net.thunderbird.feature.account.settings.impl.ui.notifications.NotificationSettingsScreen
import net.thunderbird.feature.account.settings.impl.ui.readingMail.ReadingMailSettingsScreen
import net.thunderbird.feature.account.settings.impl.ui.search.SearchSettingsScreen

internal class DefaultAccountSettingsNavigation : AccountSettingsNavigation {

    @Suppress("LongMethod")
    override fun registerRoutes(
        navGraphBuilder: NavGraphBuilder,
        onBack: () -> Unit,
        onFinish: (AccountSettingsRoute) -> Unit,
    ) {
        with(navGraphBuilder) {
            deepLinkComposable<AccountSettingsRoute.Hub>(
                basePath = AccountSettingsRoute.Hub.BASE_PATH,
            ) { backStackEntry ->
                val hubRoute = backStackEntry.toRoute<AccountSettingsRoute.Hub>()
                val accountId = AccountIdFactory.of(hubRoute.accountId)

                HubSettingsScreen(
                    accountId = accountId,
                    onBack = onBack,
                )
            }
        }

        with(navGraphBuilder) {
            deepLinkComposable<AccountSettingsRoute.GeneralSettings>(
                basePath = AccountSettingsRoute.GeneralSettings.BASE_PATH,
            ) { backStackEntry ->
                val generalSettingsRoute = backStackEntry.toRoute<AccountSettingsRoute.GeneralSettings>()
                val accountId = AccountIdFactory.of(generalSettingsRoute.accountId)

                GeneralSettingsScreen(
                    accountId = accountId,
                    onBack = onBack,
                )
            }
        }

        with(navGraphBuilder) {
            deepLinkComposable<AccountSettingsRoute.ReadingMailSettings>(
                basePath = AccountSettingsRoute.ReadingMailSettings.BASE_PATH,
            ) { backStackEntry ->
                val readingMailSettingsRoute = backStackEntry.toRoute<AccountSettingsRoute.ReadingMailSettings>()
                val accountId = AccountIdFactory.of(readingMailSettingsRoute.accountId)

                ReadingMailSettingsScreen(
                    accountId = accountId,
                    onBack = onBack,
                )
            }
        }

        with(navGraphBuilder) {
            deepLinkComposable<AccountSettingsRoute.FetchingMailSettings>(
                basePath = AccountSettingsRoute.FetchingMailSettings.BASE_PATH,
            ) { backStackEntry ->
                val fetchingMailSettingsRoute = backStackEntry.toRoute<AccountSettingsRoute.FetchingMailSettings>()
                val accountId = AccountIdFactory.of(fetchingMailSettingsRoute.accountId)

                FetchingMailSettingsScreen(
                    accountId = accountId,
                    onBack = onBack,
                )
            }
        }

        with(navGraphBuilder) {
            deepLinkComposable<AccountSettingsRoute.AdvancedFetchingMailSettings>(
                basePath = AccountSettingsRoute.AdvancedFetchingMailSettings.BASE_PATH,
            ) { backStackEntry ->
                val advancedFetchingMailSettingsRoute =
                    backStackEntry.toRoute<AccountSettingsRoute.AdvancedFetchingMailSettings>()
                val accountId = AccountIdFactory.of(advancedFetchingMailSettingsRoute.accountId)

                AdvancedFetchingMailSettingsScreen(
                    accountId = accountId,
                    onBack = onBack,
                )
            }
        }

        with(navGraphBuilder) {
            deepLinkComposable<AccountSettingsRoute.CompositionMailSettings>(
                basePath = AccountSettingsRoute.CompositionMailSettings.BASE_PATH,
            ) { backStackEntry ->
                val compositionMailSettingsRoute =
                    backStackEntry.toRoute<AccountSettingsRoute.CompositionMailSettings>()
                val accountId = AccountIdFactory.of(compositionMailSettingsRoute.accountId)

                CompositionMailSettingsScreen(
                    accountId = accountId,
                    onBack = onBack,
                )
            }
        }

        with(navGraphBuilder) {
            deepLinkComposable<AccountSettingsRoute.SearchSettings>(
                basePath = AccountSettingsRoute.SearchSettings.Companion.BASE_PATH,
            ) { backStackEntry ->
                val searchSettingsRoute = backStackEntry.toRoute<AccountSettingsRoute.SearchSettings>()
                val accountId = AccountIdFactory.of(searchSettingsRoute.accountId)

                SearchSettingsScreen(
                    accountId = accountId,
                    onBack = onBack,
                )
            }
        }

        with(navGraphBuilder) {
            deepLinkComposable<AccountSettingsRoute.FolderSettings>(
                basePath = AccountSettingsRoute.FolderSettings.BASE_PATH,
            ) { backStackEntry ->
                val folderSettingsRoute = backStackEntry.toRoute<AccountSettingsRoute.FolderSettings>()
                val accountId = AccountIdFactory.of(folderSettingsRoute.accountId)

                FolderSettingsScreen(
                    accountId = accountId,
                    onBack = onBack,
                )
            }
        }

        with(navGraphBuilder) {
            deepLinkComposable<AccountSettingsRoute.NotificationSettings>(
                basePath = AccountSettingsRoute.NotificationSettings.BASE_PATH,
            ) { backStackEntry ->
                val notificationSettingsRoute = backStackEntry.toRoute<AccountSettingsRoute.NotificationSettings>()
                val accountId = AccountIdFactory.of(notificationSettingsRoute.accountId)

                NotificationSettingsScreen(
                    accountId = accountId,
                    onBack = onBack,
                )
            }
        }

        with(navGraphBuilder) {
            deepLinkComposable<AccountSettingsRoute.CryptoSettings>(
                basePath = AccountSettingsRoute.CryptoSettings.BASE_PATH,
            ) { backStackEntry ->
                val cryptoSettingsRoute = backStackEntry.toRoute<AccountSettingsRoute.CryptoSettings>()
                val accountId = AccountIdFactory.of(cryptoSettingsRoute.accountId)

                CryptoSettingsScreen(
                    accountId = accountId,
                    onBack = onBack,
                )
            }
        }
    }
}
