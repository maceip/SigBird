package com.fsck.k9.activity

import app.k9mail.library.signatureeditor.SignatureEditorView
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import sun.misc.Unsafe

class MessageComposeTest : RobolectricTest() {
    @Test
    fun `updateSignature should preserve draft signatureChanged flag`() {
        // Arrange
        val testSubject = allocateMessageComposeWithoutConstructor()
        val signatureView = SignatureEditorView(RuntimeEnvironment.getApplication())

        setField(testSubject, "identity", Identity(signature = "Draft signature", signatureUse = true))
        setField(testSubject, "signatureView", signatureView)
        setField(testSubject, "signatureChanged", true)

        // Act
        invokePrivateMethod(testSubject, "updateSignature")

        // Assert
        assertThat(signatureView.getSignaturePlainText()).isEqualTo("Draft signature")
        assertThat(getField<Boolean>(testSubject, "signatureChanged")).isTrue()
    }

    @Test
    fun `updateSignature should preserve unchanged html signature when listener is attached`() {
        // Arrange
        val testSubject = allocateMessageComposeWithoutConstructor()
        val signatureView = SignatureEditorView(RuntimeEnvironment.getApplication())
        val htmlSignature = "<div>Hello<br>World</div>"

        setField(testSubject, "identity", Identity(signature = htmlSignature, signatureUse = true))
        setField(testSubject, "signatureView", signatureView)
        setField(testSubject, "signatureChanged", false)

        val signChangeListener = createSignatureChangeListener(testSubject)
        signatureView.setOnSignatureChangeListener(signChangeListener)

        // Act
        invokePrivateMethod(testSubject, "updateSignature")
        val resolvedSignature = invokePrivateMethodWithResult<String>(testSubject, "resolveSignatureForSend")

        // Assert
        assertThat(getField<Boolean>(testSubject, "signatureChanged")).isFalse()
        assertThat(resolvedSignature).isEqualTo(htmlSignature)
    }

    @Test
    fun `user edits should still mark signature as changed`() {
        // Arrange
        val testSubject = allocateMessageComposeWithoutConstructor()
        val signatureView = SignatureEditorView(RuntimeEnvironment.getApplication())
        val htmlSignature = "<div><b>Jane Doe</b></div>"

        setField(testSubject, "identity", Identity(signature = htmlSignature, signatureUse = true))
        setField(testSubject, "signatureView", signatureView)
        setField(testSubject, "signatureChanged", false)

        val signChangeListener = createSignatureChangeListener(testSubject)
        signatureView.setOnSignatureChangeListener(signChangeListener)
        invokePrivateMethod(testSubject, "updateSignature")

        // Act
        signatureView.append(" edited")

        // Assert
        assertThat(getField<Boolean>(testSubject, "signatureChanged")).isTrue()
        assertThat(invokePrivateMethodWithResult<String>(testSubject, "resolveSignatureForSend"))
            .contains("edited")
    }

    private fun createSignatureChangeListener(testSubject: MessageCompose) =
        SignatureEditorView.OnSignatureChangeListener {
            if (!getField<Boolean>(testSubject, "updatingSignature")) {
                setField(testSubject, "signatureChanged", true)
            }
        }

    private fun allocateMessageComposeWithoutConstructor(): MessageCompose {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply {
            isAccessible = true
        }.get(null) as Unsafe
        @Suppress("UNCHECKED_CAST")
        return unsafe.allocateInstance(MessageCompose::class.java) as MessageCompose
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
        }.invoke(target)
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
