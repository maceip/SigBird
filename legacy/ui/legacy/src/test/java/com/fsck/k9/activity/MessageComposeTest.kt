package com.fsck.k9.activity

import android.content.Context
import android.text.TextWatcher
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isFalse
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

    @Test
    fun `updateSignature should preserve unchanged html signature when watcher is attached`() {
        // Arrange
        val testSubject = MessageCompose()
        val signatureView = EditText(ApplicationProvider.getApplicationContext<Context>())
        val htmlSignature = "<div>Hello<br>World</div>"

        setField(testSubject, "identity", Identity(signature = htmlSignature, signatureUse = true))
        setField(testSubject, "signatureView", signatureView)
        setField(testSubject, "signatureChanged", false)

        val signTextWatcher = getField<TextWatcher>(testSubject, "signTextWatcher")
        signatureView.addTextChangedListener(signTextWatcher)

        // Act
        invokePrivateMethod(testSubject, "updateSignature")
        val resolvedSignature = invokePrivateMethodWithResult<String>(testSubject, "resolveSignatureForSend")

        // Assert
        assertThat(getField<Boolean>(testSubject, "signatureChanged")).isFalse()
        assertThat(resolvedSignature).isEqualTo(htmlSignature)
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
    private fun <T> invokePrivateMethodWithResult(target: Any, name: String): T {
        return target.javaClass.getDeclaredMethod(name).apply {
            isAccessible = true
        }.invoke(target) as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(target: Any, name: String): T {
        return target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }.get(target) as T
    }
}
