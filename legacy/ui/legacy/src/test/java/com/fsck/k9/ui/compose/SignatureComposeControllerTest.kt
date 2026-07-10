package com.fsck.k9.ui.compose

import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.ui.R
import net.thunderbird.core.android.account.Identity
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

class SignatureComposeControllerTest : K9RobolectricTest() {
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var activity: AppCompatActivity
    private lateinit var testSubject: SignatureComposeController

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        activity = activityController.get()
        activity.setTheme(R.style.Theme_Legacy_Test)
        activity.setContentView(R.layout.message_compose_content)
        testSubject = SignatureComposeController(activity) { _, _ -> }
        testSubject.bindPosition(signatureBeforeQuotedText = false)
    }

    @Test
    fun `updateFromIdentity should preserve draft signatureChanged flag`() {
        testSubject.setComposeSignature("Draft signature", changed = true)

        testSubject.updateFromIdentity(Identity(signature = "Identity signature", signatureUse = true))

        assertThat(testSubject.getComposeSignature()).isEqualTo("Draft signature")
        assertThat(testSubject.isSignatureChanged()).isTrue()
    }

    @Test
    fun `resolveSignatureForSend should preserve unchanged html signature`() {
        val htmlSignature = "<div>Hello<br>World</div>"
        testSubject.updateFromIdentity(Identity(signature = htmlSignature, signatureUse = true))

        val resolvedSignature = testSubject.resolveSignatureForSend()

        assertThat(testSubject.isSignatureChanged()).isFalse()
        assertThat(resolvedSignature).isEqualTo(htmlSignature)
    }

    @Test
    fun `plain text edits should mark signature as changed`() {
        testSubject.updateFromIdentity(Identity(signature = "Plain signature", signatureUse = true))
        val signatureView = activity.findViewById<EditText>(R.id.lower_signature)

        signatureView.setText("Plain signature edited")

        assertThat(testSubject.isSignatureChanged()).isTrue()
        assertThat(testSubject.resolveSignatureForSend()).contains("edited")
    }
}
