package com.fsck.k9.activity

import android.content.Intent
import android.os.Bundle
import assertk.assertThat
import assertk.assertions.isFalse
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.Preferences
import net.thunderbird.core.android.account.Identity
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
            signatureUse = true,
            replyTo = "reply@example.com",
        )
        val account = Preferences.getPreferences().run {
            clearAccounts()
            newAccount("account-uuid").apply {
                identities = mutableListOf(identity)
            }
        }
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
}
