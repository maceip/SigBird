package net.thunderbird.feature.account.settings.impl.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.thunderbird.components.ui.bolt.organism.AlertDialog
import net.thunderbird.feature.account.settings.R
import net.thunderbird.feature.notification.NotificationVibration
import net.thunderbird.feature.notification.VibratePattern

@Suppress("LongMethod")
@Composable
internal fun VibrationSettingsDialog(
    vibration: NotificationVibration,
    onConfirm: (NotificationVibration) -> Unit,
    onDismiss: () -> Unit,
) {
    var isEnabled by remember(vibration) { mutableStateOf(vibration.isEnabled) }
    var selectedPattern by remember(vibration) { mutableStateOf(vibration.pattern) }
    var repeatCount by remember(vibration) { mutableIntStateOf(vibration.repeatCount) }

    AlertDialog(
        title = stringResource(R.string.account_settings_vibration),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isEnabled = !isEnabled }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.account_settings_vibration_enabled),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }

                VibratePattern.entries.forEach { pattern ->
                    val label = vibrationPatternLabel(pattern)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled) { selectedPattern = pattern }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedPattern == pattern,
                            onClick = { selectedPattern = pattern },
                            enabled = isEnabled,
                        )
                        Text(text = label)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.account_settings_vibration_times, repeatCount),
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.Slider(
                        value = (repeatCount - 1).toFloat(),
                        onValueChange = { repeatCount = it.toInt() + 1 },
                        valueRange = 0f..4f,
                        steps = 3,
                        enabled = isEnabled,
                        modifier = Modifier.weight(2f),
                    )
                }
            }
        },
        confirmText = stringResource(R.string.account_settings_okay_action),
        dismissText = stringResource(R.string.account_settings_cancel_action),
        onConfirmClick = {
            onConfirm(
                NotificationVibration(
                    isEnabled = isEnabled,
                    pattern = selectedPattern,
                    repeatCount = repeatCount,
                ),
            )
        },
        onDismissClick = onDismiss,
        onDismissRequest = onDismiss,
    )
}

@Composable
private fun vibrationPatternLabel(pattern: VibratePattern): String {
    return when (pattern) {
        VibratePattern.Default -> stringResource(R.string.account_settings_vibrate_pattern_default)
        VibratePattern.Pattern1 -> stringResource(R.string.account_settings_vibrate_pattern_1)
        VibratePattern.Pattern2 -> stringResource(R.string.account_settings_vibrate_pattern_2)
        VibratePattern.Pattern3 -> stringResource(R.string.account_settings_vibrate_pattern_3)
        VibratePattern.Pattern4 -> stringResource(R.string.account_settings_vibrate_pattern_4)
        VibratePattern.Pattern5 -> stringResource(R.string.account_settings_vibrate_pattern_5)
    }
}
