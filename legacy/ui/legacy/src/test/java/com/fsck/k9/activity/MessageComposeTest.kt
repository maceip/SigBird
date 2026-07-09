package com.fsck.k9.activity

import android.content.Context
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test

class MessageComposeTest : RobolectricTest() {
    @Test
    fun `updateSignature should preserve draft signatureChanged flag`() {
        // Arrange
        val testSubject = MessageCompose()
        val signatureView = EditText(ApplicationProvider.getApplicationContext<Context>())

        setField(testSubject, "identity", Identity(signature = "Draft signature", signatureUse = true))
        setField(testSubject, "signatureView", signatureView)
        setField(testSubject, "signatureChanged", true)

        // Act
        invokePrivateMethod(testSubject, "updateSignature")

        // Assert
        assertThat(signatureView.text.toString()).isEqualTo("Draft signature")
        assertThat(getField<Boolean>(testSubject, "signatureChanged")).isTrue()
    }

    private fun setField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private fun invokePrivateMethod(target: Any, name: String) {
        target.javaClass.getDeclaredMethod(name).apply {
            isAccessible = true
            invoke(target)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(target: Any, name: String): T {
        return target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }.get(target) as T
    }
}
