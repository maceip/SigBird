package net.thunderbird.feature.account.settings.impl.ui.crypto

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import net.thunderbird.core.common.provider.AppNameProvider
import net.thunderbird.core.ui.contract.mvi.observe
import net.thunderbird.core.ui.setting.SettingViewProvider
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsCryptoBridge
import net.thunderbird.feature.account.settings.api.BackgroundAccountRemover
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun CryptoSettingsScreen(
    accountId: AccountId,
    onBack: () -> Unit,
    viewModel: CryptoSettingsContract.ViewModel = koinViewModel<CryptoSettingsViewModel> {
        parametersOf(accountId)
    },
    provider: SettingViewProvider = koinInject(),
    builder: CryptoSettingsContract.SettingsBuilder = koinInject(),
    appNameProvider: AppNameProvider = koinInject(),
    accountRemover: BackgroundAccountRemover = koinInject(),
    cryptoBridge: AccountSettingsCryptoBridge = koinInject(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as ComponentActivity

    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            CryptoSettingsContract.Effect.NavigateBack -> onBack()
            CryptoSettingsContract.Effect.LaunchOpenPgpProviderChooser -> {
                cryptoBridge.launchOpenPgpProviderChooser(context, accountId)
            }
            CryptoSettingsContract.Effect.LaunchOpenPgpKeySelector -> {
                cryptoBridge.launchOpenPgpKeySelector(context, accountId)
            }
            CryptoSettingsContract.Effect.LaunchAutocryptTransfer -> {
                cryptoBridge.launchAutocryptTransfer(context, accountId)
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.event(CryptoSettingsContract.Event.OnRefreshCryptoState)
    }

    BackHandler(onBack = onBack)

    CryptoSettingsContent(
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
