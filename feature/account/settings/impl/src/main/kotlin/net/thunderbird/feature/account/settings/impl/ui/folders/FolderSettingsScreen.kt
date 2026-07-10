package net.thunderbird.feature.account.settings.impl.ui.folders

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import net.thunderbird.core.common.provider.AppNameProvider
import net.thunderbird.core.ui.contract.mvi.observe
import net.thunderbird.core.ui.setting.SettingViewProvider
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.BackgroundAccountRemover
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun FolderSettingsScreen(
    accountId: AccountId,
    onBack: () -> Unit,
    viewModel: FolderSettingsContract.ViewModel = koinViewModel<FolderSettingsViewModel> {
        parametersOf(accountId)
    },
    provider: SettingViewProvider = koinInject(),
    builder: FolderSettingsContract.SettingsBuilder = koinInject(),
    appNameProvider: AppNameProvider = koinInject(),
    accountRemover: BackgroundAccountRemover = koinInject(),
) {
    val activity = LocalActivity.current as ComponentActivity
    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            FolderSettingsContract.Effect.NavigateBack -> onBack()
        }
    }

    BackHandler(onBack = onBack)

    FolderSettingsContent(
        state = state.value,
        onEvent = { dispatch(it) },
        provider = provider,
        builder = builder,
        appNameProvider = appNameProvider,
        onAccountRemove = {
            activity.setResult(Activity.RESULT_OK)
            accountRemover.removeAccountAsync("${accountId.value}")
            activity.finish()
        },
    )
}
