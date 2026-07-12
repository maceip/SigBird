package com.fsck.k9.activity

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class EditIdentitySignatureViewModelTest {
    @Test
    fun `initialize does not overwrite an in-progress signature draft`() {
        // Arrange
        val testSubject = EditIdentitySignatureViewModel()
        testSubject.initialize("<p>saved</p>")
        testSubject.updateSignature("<p>draft</p>")

        // Act
        testSubject.initialize("<p>saved again</p>")

        // Assert
        assertThat(testSubject.signature).isEqualTo("<p>draft</p>")
    }
}
