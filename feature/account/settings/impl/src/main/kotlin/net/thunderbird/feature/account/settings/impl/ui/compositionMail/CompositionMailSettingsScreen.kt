package net.thunderbird.feature.account.settings.impl.ui.compositionMail

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.k9mail.feature.launcher.FeatureLauncherActivity
import app.k9mail.feature.launcher.FeatureLauncherTarget
import net.thunderbird.core.common.provider.AppNameProvider
import net.thunderbird.core.ui.contract.mvi.observe
import net.thunderbird.core.ui.setting.SettingViewProvider
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsLegacyNavigation
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsContract.Effect
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@SuppressLint("ContextCastToActivity")
@Composable
internal fun CompositionMailSettingsScreen(
    accountId: AccountId,
    onBack: () -> Unit,
    viewModel: CompositionMailSettingsContract.ViewModel = koinViewModel<CompositionMailSettingsViewModel> {
        parametersOf(accountId)
    },
    provider: SettingViewProvider = koinInject(),
    builder: CompositionMailSettingsContract.SettingsBuilder = koinInject(),
    appNameProvider: AppNameProvider = koinInject(),
    legacyNavigation: AccountSettingsLegacyNavigation = koinInject(),
) {
    val context = LocalContext.current
    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            Effect.NavigateBack -> onBack()
            Effect.NavigateToCompositionDefaults ->
                legacyNavigation.launchCompositionDefaults(context, accountId)

            Effect.NavigateToManageIdentities ->
                legacyNavigation.launchManageIdentities(context, accountId)

            Effect.NavigateToOutgoingServer -> {
                FeatureLauncherActivity.launch(
                    context = context,
                    target = FeatureLauncherTarget.AccountEditOutgoingSettings(accountUuid = "${accountId.value}"),
                )
            }
        }
    }
    val activity = LocalActivity.current as ComponentActivity
    BackHandler(onBack = onBack)

    CompositionMailSettingsContent(
        state = state.value,
        onEvent = { dispatch(it) },
        provider = provider,
        builder = builder,
        appNameProvider = appNameProvider,
        onAccountRemove = {
            activity.setResult(Activity.RESULT_OK)
            activity.finish()
        },
    )
}
