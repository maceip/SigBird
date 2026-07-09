package net.thunderbird.feature.account.settings.impl.ui.hub

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.k9mail.feature.launcher.FeatureLauncherActivity
import app.k9mail.feature.launcher.FeatureLauncherTarget
import net.thunderbird.core.ui.contract.mvi.observe
import net.thunderbird.core.ui.setting.SettingViewProvider
import net.thunderbird.feature.account.AccountId
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun HubSettingsScreen(
    accountId: AccountId,
    onBack: () -> Unit,
    viewModel: HubSettingsContract.ViewModel = koinViewModel<HubSettingsViewModel> {
        parametersOf(accountId)
    },
    provider: SettingViewProvider = koinInject(),
    builder: HubSettingsContract.SettingsBuilder = koinInject(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as ComponentActivity

    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            HubSettingsContract.Effect.NavigateBack -> onBack()
            is HubSettingsContract.Effect.NavigateToAccount -> {
                FeatureLauncherActivity.launch(
                    context = context,
                    target = FeatureLauncherTarget.AccountSettingsHub(effect.accountId),
                )
                activity.finish()
            }
            HubSettingsContract.Effect.NavigateToGeneral -> launchSubScreen(
                context = context,
                target = FeatureLauncherTarget.AccountSettings("${accountId.value}"),
            )
            HubSettingsContract.Effect.NavigateToReadingMail -> launchSubScreen(
                context = context,
                target = FeatureLauncherTarget.AccountReadingMailSettings("${accountId.value}"),
            )
            HubSettingsContract.Effect.NavigateToFetchingMail -> launchSubScreen(
                context = context,
                target = FeatureLauncherTarget.AccountFetchingMailSettings("${accountId.value}"),
            )
            HubSettingsContract.Effect.NavigateToComposition -> launchSubScreen(
                context = context,
                target = FeatureLauncherTarget.AccountCompositionMailSettings("${accountId.value}"),
            )
            HubSettingsContract.Effect.NavigateToFolders -> launchSubScreen(
                context = context,
                target = FeatureLauncherTarget.AccountFolderSettings("${accountId.value}"),
            )
            HubSettingsContract.Effect.NavigateToNotifications -> launchSubScreen(
                context = context,
                target = FeatureLauncherTarget.AccountNotificationSettings("${accountId.value}"),
            )
            HubSettingsContract.Effect.NavigateToSearch -> launchSubScreen(
                context = context,
                target = FeatureLauncherTarget.AccountSearchSettings("${accountId.value}"),
            )
            HubSettingsContract.Effect.NavigateToCrypto -> launchSubScreen(
                context = context,
                target = FeatureLauncherTarget.AccountCryptoSettings("${accountId.value}"),
            )
        }
    }

    BackHandler(onBack = onBack)

    HubSettingsContent(
        state = state.value,
        onEvent = { dispatch(it) },
        provider = provider,
        builder = builder,
    )
}

private fun launchSubScreen(
    context: android.content.Context,
    target: FeatureLauncherTarget,
) {
    FeatureLauncherActivity.launch(context = context, target = target)
}
