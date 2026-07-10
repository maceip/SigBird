package com.fsck.k9.activity

import android.content.Intent
import android.os.Bundle
import assertk.assertThat
import assertk.assertions.isFalse
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.Preferences
import java.util.UUID
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.common.mail.AuthType
import net.thunderbird.core.common.mail.ConnectionSecurity
import net.thunderbird.core.common.mail.ServerSettings
import org.junit.Test
import org.robolectric.Robolectric

class EditIdentityTest : K9RobolectricTest() {
    @Test
    fun `recreating edit identity screen should not require saved identity state`() {
        // Arrange
        val identity = Identity(
            description = "Work",
            name = "Alice Example",
            email = "alice@example.com",
            signature = "Regards",
            signatureUse = false,
            replyTo = "reply@example.com",
        )
        val preferences = Preferences.getPreferences()
        val account = preferences.run {
            clearAccounts()
            newAccount(UUID.randomUUID().toString()).apply {
                incomingServerSettings = TEST_SERVER_SETTINGS
                outgoingServerSettings = TEST_SERVER_SETTINGS
                identities = mutableListOf(identity)
            }
        }
        preferences.saveAccount(account)
        val intent = Intent(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            EditIdentity::class.java,
        ).apply {
            putExtra(EditIdentity.EXTRA_ACCOUNT, account.uuid)
            putExtra(EditIdentity.EXTRA_IDENTITY, identity)
            putExtra(EditIdentity.EXTRA_IDENTITY_INDEX, 0)
        }

        val controller = Robolectric.buildActivity(EditIdentity::class.java, intent).setup()
        val outState = Bundle()
        controller.saveInstanceState(outState).pause().stop().destroy()

        // Act
        val recreatedActivity = Robolectric.buildActivity(EditIdentity::class.java, intent)
            .create(outState)
            .start()
            .resume()
            .get()

        // Assert
        assertThat(recreatedActivity.isFinishing).isFalse()
    }

    private companion object {
        private val TEST_SERVER_SETTINGS = ServerSettings(
            type = "imap",
            host = "example.test",
            port = 993,
            connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
            authenticationType = AuthType.PLAIN,
            username = "user",
            password = null,
            clientCertificateAlias = null,
        )
    }
}
