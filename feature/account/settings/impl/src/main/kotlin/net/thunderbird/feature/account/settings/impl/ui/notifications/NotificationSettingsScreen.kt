package net.thunderbird.feature.account.settings.impl.ui.notifications

import android.app.Activity
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import net.thunderbird.core.common.provider.AppNameProvider
import net.thunderbird.core.ui.contract.mvi.observe
import net.thunderbird.core.ui.setting.SettingViewProvider
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.api.AccountSettingsNotificationBridge
import net.thunderbird.feature.account.settings.api.BackgroundAccountRemover
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun NotificationSettingsScreen(
    accountId: AccountId,
    onBack: () -> Unit,
    viewModel: NotificationSettingsContract.ViewModel = koinViewModel<NotificationSettingsViewModel> {
        parametersOf(accountId)
    },
    provider: SettingViewProvider = koinInject(),
    builder: NotificationSettingsContract.SettingsBuilder = koinInject(),
    appNameProvider: AppNameProvider = koinInject(),
    accountRemover: BackgroundAccountRemover = koinInject(),
    notificationBridge: AccountSettingsNotificationBridge = koinInject(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as ComponentActivity
    var showVibrationDialog by remember { mutableStateOf(false) }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            viewModel.event(NotificationSettingsContract.Event.OnRingtoneSelected(uri?.toString()))
        }
    }

    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            NotificationSettingsContract.Effect.NavigateBack -> onBack()
            is NotificationSettingsContract.Effect.LaunchRingtonePicker -> {
                val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    .putExtra(
                        RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    )
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    .putExtra(
                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        effect.currentRingtone?.let(Uri::parse),
                    )
                ringtonePicker.launch(intent)
            }
            NotificationSettingsContract.Effect.ShowVibrationDialog -> {
                showVibrationDialog = true
            }
            is NotificationSettingsContract.Effect.LaunchNotificationChannel -> {
                notificationBridge.openNotificationChannelSettings(context, effect.channelId)
            }
        }
    }

    BackHandler(onBack = onBack)

    NotificationSettingsContent(
        state = state.value,
        onEvent = { dispatch(it) },
        provider = provider,
        builder = builder,
        appNameProvider = appNameProvider,
        showVibrationDialog = showVibrationDialog,
        onDismissVibrationDialog = { showVibrationDialog = false },
        onAccountRemove = {
            activity.setResult(Activity.RESULT_OK)
            accountRemover.removeAccountAsync("${accountId.value}")
            activity.finish()
        },
    )
}
