package net.thunderbird.app.common.account

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.feature.account.settings.R
import net.thunderbird.feature.account.settings.impl.ui.notifications.RingtoneSummaryFormatter
import net.thunderbird.feature.account.settings.impl.ui.notifications.VibrationSummaryFormatter
import net.thunderbird.feature.notification.NotificationVibration
import net.thunderbird.feature.notification.VibratePattern

internal class DefaultRingtoneSummaryFormatter(
    private val context: Context,
    private val resources: StringsResourceManager,
) : RingtoneSummaryFormatter {
    override fun format(ringtone: String?): String {
        if (ringtone.isNullOrEmpty()) {
            return resources.stringResource(R.string.account_settings_ringtone_silent)
        }
        val uri = Uri.parse(ringtone)
        val title = RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        return title ?: resources.stringResource(R.string.account_settings_ringtone_default)
    }
}

internal class DefaultVibrationSummaryFormatter(
    private val resources: StringsResourceManager,
) : VibrationSummaryFormatter {
    override fun format(vibration: NotificationVibration): String {
        if (!vibration.isEnabled) {
            return resources.stringResource(R.string.account_settings_ringtone_silent)
        }
        val patternLabel = when (vibration.pattern) {
            VibratePattern.Default -> resources.stringResource(R.string.account_settings_vibrate_pattern_default)
            VibratePattern.Pattern1 -> resources.stringResource(R.string.account_settings_vibrate_pattern_1)
            VibratePattern.Pattern2 -> resources.stringResource(R.string.account_settings_vibrate_pattern_2)
            VibratePattern.Pattern3 -> resources.stringResource(R.string.account_settings_vibrate_pattern_3)
            VibratePattern.Pattern4 -> resources.stringResource(R.string.account_settings_vibrate_pattern_4)
            VibratePattern.Pattern5 -> resources.stringResource(R.string.account_settings_vibrate_pattern_5)
        }
        return patternLabel
    }
}
