package net.thunderbird.app.common.net

import android.content.Context
import android.net.ConnectivityManager
import org.minidns.dnsserverlookup.AbstractDnsServerLookupMechanism
import org.minidns.dnsserverlookup.AndroidUsingExec

/**
 * Provides MiniDNS with the DNS servers of the active network.
 *
 * MiniDNS' built-in lookup mechanisms (`getprop net.dns1`, reflection on system properties,
 * `/etc/resolv.conf`) all come up empty on Android 8+, causing it to silently fall back to
 * hardcoded public resolvers (8.8.8.8, …). On networks that block direct UDP port 53 traffic to
 * the internet, every MiniDNS query then blackholes until its timeout. The MX lookup during
 * account auto-discovery uses MiniDNS, so without this mechanism the "Looking up configuration"
 * step can stall for a very long time on such networks.
 */
class AndroidLinkPropertiesDnsServerLookup(
    context: Context,
) : AbstractDnsServerLookupMechanism(NAME, PRIORITY) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isAvailable(): Boolean = true

    override fun getDnsServerAddresses(): List<String>? {
        val dnsServers = connectivityManager.activeNetwork
            ?.let { network -> connectivityManager.getLinkProperties(network) }
            ?.dnsServers
            .orEmpty()

        return toListOfStrings(dnsServers).takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val NAME = "AndroidLinkProperties"

        // Run before MiniDNS' built-in Android mechanisms, which don't work on Android 8+.
        private val PRIORITY = AndroidUsingExec.PRIORITY - 1
    }
}
