package app.k9mail.feature.account.setup.domain.usecase

import app.k9mail.autodiscovery.api.AuthenticationType
import app.k9mail.autodiscovery.api.AutoDiscoveryResult
import app.k9mail.autodiscovery.api.AutoDiscoveryService
import app.k9mail.autodiscovery.api.ImapServerSettings
import app.k9mail.autodiscovery.api.SmtpServerSettings
import app.k9mail.autodiscovery.demo.DemoServerSettings
import app.k9mail.feature.account.setup.domain.DomainContract
import java.net.SocketTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull
import net.thunderbird.core.common.mail.toUserEmailAddress
import net.thunderbird.core.common.oauth.OAuthConfigurationProvider

internal class GetAutoDiscovery(
    private val service: AutoDiscoveryService,
    private val oauthProvider: OAuthConfigurationProvider,
    private val timeout: Duration = DISCOVERY_TIMEOUT,
) : DomainContract.UseCase.GetAutoDiscovery {
    override suspend fun execute(emailAddress: String): AutoDiscoveryResult {
        val email = emailAddress.toUserEmailAddress()

        // Individual discovery attempts can stall for a very long time, e.g. when DNS queries are
        // silently dropped by the network. Cap the total lookup time so the UI can always recover.
        val result = withTimeoutOrNull(timeout) {
            service.discover(email)
        } ?: AutoDiscoveryResult.NetworkError(
            SocketTimeoutException("Auto-discovery did not complete within $timeout"),
        )

        return if (result is AutoDiscoveryResult.Settings) {
            if (result.incomingServerSettings is DemoServerSettings) {
                return result
            } else {
                validateOAuthSupport(result)
            }
        } else {
            result
        }
    }

    private fun validateOAuthSupport(settings: AutoDiscoveryResult.Settings): AutoDiscoveryResult {
        if (settings.incomingServerSettings !is ImapServerSettings ||
            settings.outgoingServerSettings !is SmtpServerSettings
        ) {
            return AutoDiscoveryResult.NoUsableSettingsFound
        }

        val incomingServerSettings = settings.incomingServerSettings as ImapServerSettings
        val outgoingServerSettings = settings.outgoingServerSettings as SmtpServerSettings

        val incomingAuthenticationTypes = cleanAuthenticationTypes(
            authenticationTypes = incomingServerSettings.authenticationTypes,
            hostname = incomingServerSettings.hostname.value,
        )
        val outgoingAuthenticationTypes = cleanAuthenticationTypes(
            authenticationTypes = outgoingServerSettings.authenticationTypes,
            hostname = outgoingServerSettings.hostname.value,
        )

        return if (incomingAuthenticationTypes.isNotEmpty() && outgoingAuthenticationTypes.isNotEmpty()) {
            settings.copy(
                incomingServerSettings = incomingServerSettings.copy(
                    authenticationTypes = incomingAuthenticationTypes,
                ),
                outgoingServerSettings = outgoingServerSettings.copy(
                    authenticationTypes = outgoingAuthenticationTypes,
                ),
            )
        } else {
            AutoDiscoveryResult.NoUsableSettingsFound
        }
    }

    private fun cleanAuthenticationTypes(
        authenticationTypes: List<AuthenticationType>,
        hostname: String,
    ): List<AuthenticationType> {
        return if (AuthenticationType.OAuth2 in authenticationTypes && !isOAuthSupportedFor(hostname)) {
            // OAuth2 is not supported for this hostname; remove it from the list of supported authentication types
            authenticationTypes.filter { it != AuthenticationType.OAuth2 }
        } else {
            authenticationTypes
        }
    }

    private fun isOAuthSupportedFor(hostname: String): Boolean {
        return oauthProvider.getConfiguration(hostname) != null
    }

    companion object {
        private val DISCOVERY_TIMEOUT = 30.seconds
    }
}
