package com.fsck.k9.activity

import android.content.Intent
import assertk.assertThat
import assertk.assertions.isFalse
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.Preferences
import java.util.UUID
import net.thunderbird.core.android.account.Identity
import org.junit.Test
import org.robolectric.Robolectric

class EditIdentityTest : K9RobolectricTest() {
    @Test
    fun `recreating edit identity screen should not require saved identity state`() {
        val identity = Identity(
            description = "Work",
            name = "Alice Example",
            email = "alice@example.com",
            signature = "Regards",
            signatureUse = false,
            replyTo = "reply@example.com",
        )
        val preferences = Preferences.getPreferences()
        val accountUuid = UUID.randomUUID().toString()
        preferences.clearAccounts()
        val account = preferences.newAccount(accountUuid).apply {
            identities = mutableListOf(identity)
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
        controller.recreate()

        assertThat(controller.get().isFinishing).isFalse()
    }
}
