package net.thunderbird.feature.account.settings.impl.ui.compositionMail

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
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsContract.Event
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsContract.SettingsBuilder
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsContract.State

@Composable
internal fun CompositionMailSettingsContent(
    state: State,
    onEvent: (Event) -> Unit,
    onAccountRemove: () -> Unit,
    provider: SettingViewProvider,
    builder: SettingsBuilder,
    appNameProvider: AppNameProvider,
    modifier: Modifier = Modifier,
) {
    val settings = remember(state, builder, onEvent) {
        builder.build(state = state, onEvent = onEvent)
    }

    var showDialog by remember { mutableStateOf(false) }

    provider.SettingView(
        title = stringResource(R.string.account_settings_composition),
        subtitle = state.subtitle,
        settings = settings,
        onSettingValueChange = { setting -> handleSettingChange(setting, onEvent) },
        onBack = { onEvent(Event.OnBackPressed) },
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
                    showDialog = true
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

    if (showDialog) {
        AlertDialog(
            title = stringResource(R.string.account_settings_account_delete_dlg_title),
            text = stringResource(
                R.string.account_settings_account_delete_dlg_instructions_fmt,
                state.subtitle.toString(),
                appNameProvider.appName,
            ),
            confirmText = stringResource(R.string.account_settings_okay_action),
            dismissText = stringResource(R.string.account_settings_cancel_action),
            onConfirmClick = {
                showDialog = false
                onAccountRemove()
            },
            onDismissClick = { showDialog = false },
            onDismissRequest = { showDialog = false },
        )
    }
}

@Suppress("CyclomaticComplexMethod")
private fun handleSettingChange(
    setting: Setting,
    onEvent: (Event) -> Unit,
) {
    when (setting) {
        is SettingValue.Switch -> when (setting.id) {
            CompositionMailSettingId.ALWAYS_SHOW_CC_BCC.name ->
                onEvent(Event.OnAlwaysShowCcBccToggle(setting.value))

            CompositionMailSettingId.MESSAGE_READ_RECEIPT.name ->
                onEvent(Event.OnMessageReadReceiptToggle(setting.value))

            CompositionMailSettingId.DEFAULT_QUOTED_TEXT_SHOWN.name ->
                onEvent(Event.OnDefaultQuotedTextShownToggle(setting.value))

            CompositionMailSettingId.REPLY_AFTER_QUOTE.name ->
                onEvent(Event.OnReplyAfterQuoteToggle(setting.value))

            CompositionMailSettingId.STRIP_SIGNATURE.name ->
                onEvent(Event.OnStripSignatureToggle(setting.value))

            CompositionMailSettingId.UPLOAD_SENT_MESSAGES.name ->
                onEvent(Event.OnUploadSentMessagesToggle(setting.value))

            else -> Unit
        }

        is SettingValue.Select -> when (setting.id) {
            CompositionMailSettingId.MESSAGE_FORMAT.name ->
                onEvent(Event.OnMessageFormatChange(setting.value))

            CompositionMailSettingId.QUOTE_STYLE.name ->
                onEvent(Event.OnQuoteStyleChange(setting.value))

            else -> Unit
        }

        is SettingValue.Text -> when (setting.id) {
            CompositionMailSettingId.QUOTE_PREFIX.name ->
                onEvent(Event.OnQuotePrefixChange(setting.value))

            else -> Unit
        }

        else -> Unit
    }
}
