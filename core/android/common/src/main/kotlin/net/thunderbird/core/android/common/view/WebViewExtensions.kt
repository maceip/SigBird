package net.thunderbird.core.android.common.view

import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

fun WebView.showInDarkMode() = setupThemeMode(darkTheme = true)
fun WebView.showInLightMode() = setupThemeMode(darkTheme = false)

private fun WebView.setupThemeMode(darkTheme: Boolean) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        try {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(
                this.settings,
                darkTheme,
            )
        } catch (_: UnsupportedOperationException) {
            // The WebView provider can advertise the feature and still reject the AndroidX
            // dark-mode WebSettings API (Robolectric does this in unit tests).
        }
    }
}
