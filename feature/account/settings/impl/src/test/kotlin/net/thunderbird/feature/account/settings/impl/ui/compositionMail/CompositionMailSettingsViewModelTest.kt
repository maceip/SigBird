package net.thunderbird.feature.account.settings.impl.ui.compositionMail

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.thunderbird.components.ui.testing.coroutines.MainDispatcherHelper
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.common.mail.Protocols
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.core.outcome.Outcome
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.storage.profile.AvatarDto
import net.thunderbird.feature.account.storage.profile.AvatarTypeDto
import net.thunderbird.feature.account.storage.profile.ProfileDto

@OptIn(ExperimentalCoroutinesApi::class)
internal class CompositionMailSettingsViewModelTest {

    private val mainDispatcher = MainDispatcherHelper()

    @BeforeTest
    fun setUp() {
        mainDispatcher.setUp()
    }

    @AfterTest
    fun tearDown() {
        mainDispatcher.tearDown()
    }

    private val resources = object : StringsResourceManager {
        override fun stringResource(resourceId: Int): String = "string_$resourceId"

        override fun stringResource(resourceId: Int, vararg formatArgs: Any?): String {
            return stringResource(resourceId)
        }
    }

    @Test
    fun `default state should hide upload sent messages until account is loaded`() {
        assertThat(CompositionMailSettingsContract.State().supportsUploadSentMessages).isFalse()
    }

    @Test
    fun `should hide upload sent messages for pop3 accounts`() = runTest {
        // Arrange
        val accountId = AccountIdFactory.create()
        val legacyAccount = createLegacyAccount(accountId, incomingProtocol = Protocols.POP3)
        val testSubject = createViewModel(accountId, legacyAccount)

        // Act
        advanceUntilIdle()

        // Assert
        assertThat(testSubject.state.value.supportsUploadSentMessages).isFalse()
    }

    @Test
    fun `should show upload sent messages for imap accounts`() = runTest {
        // Arrange
        val accountId = AccountIdFactory.create()
        val legacyAccount = createLegacyAccount(accountId, incomingProtocol = Protocols.IMAP, uploadSentMessages = true)
        val testSubject = createViewModel(accountId, legacyAccount)

        // Act
        advanceUntilIdle()

        // Assert
        assertThat(testSubject.state.value.supportsUploadSentMessages).isTrue()
        assertThat(testSubject.state.value.uploadSentMessages).isEqualTo(true)
    }

    private fun createViewModel(
        accountId: AccountId,
        account: LegacyAccount,
    ) = CompositionMailSettingsViewModel(
        accountId = accountId,
        getAccountName = { flowOf(Outcome.success("Subtitle")) },
        getLegacyAccount = { Outcome.success(account) },
        updateCompositionMailSettings = { _, _ ->
            Outcome.success(Unit)
        },
        resources = resources,
        logger = TestLogger(),
    )

    private fun createLegacyAccount(
        accountId: AccountId,
        incomingProtocol: String,
        uploadSentMessages: Boolean = false,
    ): LegacyAccount {
        return LegacyAccount(
            id = accountId,
            name = "Demo",
            email = "demo@example.com",
            isSensitiveDebugLoggingEnabled = { true },
            isUploadSentMessages = uploadSentMessages,
            profile = ProfileDto(
                id = accountId,
                name = "Demo",
                color = 0xFF0000,
                avatar = AvatarDto(
                    avatarType = AvatarTypeDto.ICON,
                    avatarMonogram = null,
                    avatarImageUri = null,
                    avatarIconName = "star",
                ),
            ),
            identities = listOf(
                Identity(
                    signatureUse = false,
                    description = "Demo Identity",
                ),
            ),
            incomingServerSettings = ServerSettings(
                type = incomingProtocol,
                host = "imap.example.com",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = "test",
                password = "pass",
                clientCertificateAlias = null,
            ),
            outgoingServerSettings = ServerSettings(
                type = "smtp",
                host = "smtp.example.com",
                port = 465,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = "test",
                password = "pass",
                clientCertificateAlias = null,
            ),
        )
    }
}
