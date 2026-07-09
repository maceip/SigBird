package net.thunderbird.feature.account.settings.impl.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.components.ui.bolt.atom.DropdownMenuBox
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.core.common.provider.AppNameProvider
import net.thunderbird.core.ui.setting.Setting
import net.thunderbird.core.ui.setting.SettingValue
import net.thunderbird.core.ui.setting.SettingViewProvider
import net.thunderbird.feature.account.settings.R
import net.thunderbird.feature.notification.NotificationVibration

@Composable
internal fun NotificationSettingsContent(
    state: NotificationSettingsContract.State,
    onEvent: (NotificationSettingsContract.Event) -> Unit,
    onAccountRemove: () -> Unit,
    provider: SettingViewProvider,
    builder: NotificationSettingsContract.SettingsBuilder,
    appNameProvider: AppNameProvider,
    modifier: Modifier = Modifier,
    showVibrationDialog: Boolean = false,
    onDismissVibrationDialog: () -> Unit = {},
) {
    val settings = remember(state, builder, onEvent) {
        builder.build(state = state, onEvent = onEvent)
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

    provider.SettingView(
        title = stringResource(R.string.account_settings_notifications),
        subtitle = state.subtitle,
        settings = settings,
        onSettingValueChange = { setting -> handleSettingChange(setting, onEvent) },
        onBack = { onEvent(NotificationSettingsContract.Event.OnBackPressed) },
        modifier = modifier,
        actions = {
            var expanded by remember { mutableStateOf(false) }

            DropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { shouldExpand -> expanded = shouldExpand },
                options = persistentListOf(
                    stringResource(R.string.account_settings_remove_account_action),
                ),
                onItemSelected = {
                    showDeleteDialog = true
                    expanded = false
                },
            ) {
                ButtonIcon(
                    onClick = { expanded = true },
                    imageVector = Icons.Outlined.MoreVert,
                )
            }
        },
    )

    if (showDeleteDialog) {
        AlertDialog(
            title = stringResource(R.string.account_settings_account_delete_dlg_title),
            text = stringResource(
                R.string.account_settings_account_delete_dlg_instructions_fmt,
                state.subtitle,
                appNameProvider.appName,
            ),
            confirmText = stringResource(R.string.account_settings_okay_action),
            dismissText = stringResource(R.string.account_settings_cancel_action),
            onConfirmClick = {
                showDeleteDialog = false
                onAccountRemove()
            },
            onDismissClick = { showDeleteDialog = false },
            onDismissRequest = { showDeleteDialog = false },
        )
    }

    if (showVibrationDialog) {
        VibrationSettingsDialog(
            vibration = state.vibration,
            onConfirm = { vibration ->
                onEvent(NotificationSettingsContract.Event.OnVibrationChange(vibration))
                onDismissVibrationDialog()
            },
            onDismiss = onDismissVibrationDialog,
        )
    }
}

private fun handleSettingChange(
    setting: Setting,
    onEvent: (NotificationSettingsContract.Event) -> Unit,
) {
    when (setting) {
        is SettingValue.Switch -> when (setting.id) {
            NotificationSettingId.NOTIFY_NEW_MAIL -> onEvent(
                NotificationSettingsContract.Event.OnNotifyNewMailToggle(setting.value),
            )
            NotificationSettingId.NOTIFY_SELF -> onEvent(
                NotificationSettingsContract.Event.OnNotifySelfToggle(setting.value),
            )
            NotificationSettingId.NOTIFY_CONTACTS_ONLY -> onEvent(
                NotificationSettingsContract.Event.OnNotifyContactsOnlyToggle(setting.value),
            )
            NotificationSettingId.IGNORE_CHAT_MESSAGES -> onEvent(
                NotificationSettingsContract.Event.OnIgnoreChatMessagesToggle(setting.value),
            )
            NotificationSettingId.NOTIFY_SYNC -> onEvent(
                NotificationSettingsContract.Event.OnNotifySyncToggle(setting.value),
            )
        }
        is SettingValue.Select -> when (setting.id) {
            NotificationSettingId.NOTIFICATION_LIGHT -> onEvent(
                NotificationSettingsContract.Event.OnNotificationLightChange(setting.value),
            )
        }
        else -> Unit
    }
}
