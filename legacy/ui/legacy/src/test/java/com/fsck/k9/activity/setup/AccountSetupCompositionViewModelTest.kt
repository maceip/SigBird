package com.fsck.k9.activity.setup

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.EmailAddressValidator
import com.fsck.k9.activity.setup.AccountSetupCompositionContract.Event
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import com.fsck.k9.message.html.SignatureContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.storage.profile.AvatarDto
import net.thunderbird.feature.account.storage.profile.AvatarTypeDto
import net.thunderbird.feature.account.storage.profile.ProfileDto
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSetupCompositionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `SavePressed uses the latest captured signature html`() = runTest {
        // Arrange
        val initialSignature = "<p>stale</p>"
        val latestSignature = "<p>fresh</p>"
        val account = createLegacyAccount(signature = initialSignature)
        val accountManager = FakeLegacyAccountManager(account)
        val testSubject = AccountSetupCompositionViewModel(
            legacyAccountManager = accountManager,
            resources = FakeStringsResourceManager(),
            emailAddressValidator = EmailAddressValidator(),
            accountUuid = account.uuid,
        )

        // Act
        testSubject.event(Event.SavePressed(signature = latestSignature))
        advanceUntilIdle()

        // Assert
        assertThat(accountManager.account.signature).isEqualTo(
            SignatureContent.sanitizeForStorage(latestSignature),
        )
    }

    private fun createLegacyAccount(signature: String): LegacyAccount {
        val accountId = AccountIdFactory.create()
        val identity = Identity(
            name = "Alice Example",
            email = "alice@example.com",
            signature = signature,
            signatureUse = true,
        )

        return LegacyAccount(
            id = accountId,
            name = "Example",
            email = "alice@example.com",
            profile = createProfile(accountId),
            incomingServerSettings = createServerSettings(type = "imap", port = 993),
            outgoingServerSettings = createServerSettings(type = "smtp", port = 587),
            identities = listOf(identity),
        )
    }

    private fun createProfile(accountId: AccountId): ProfileDto {
        return ProfileDto(
            id = accountId,
            name = "Example",
            color = 0xFF00FF,
            avatar = AvatarDto(
                avatarType = AvatarTypeDto.MONOGRAM,
                avatarMonogram = "ae",
                avatarImageUri = null,
                avatarIconName = null,
            ),
        )
    }

    private fun createServerSettings(type: String, port: Int): ServerSettings {
        return ServerSettings(
            type = type,
            host = "$type.example.com",
            port = port,
            connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
            authenticationType = AuthType.PLAIN,
            username = "alice",
            password = "password",
            clientCertificateAlias = null,
        )
    }
}

private class FakeLegacyAccountManager(
    initialAccount: LegacyAccount,
) : LegacyAccountManager {
    var account: LegacyAccount = initialAccount
        private set

    override fun getAll(): Flow<List<LegacyAccount>> = flowOf(listOf(account))

    override fun getById(id: AccountId): Flow<LegacyAccount?> = flowOf(account.takeIf { it.id == id })

    override suspend fun update(account: LegacyAccount) {
        this.account = account
    }

    override fun getByIdSync(id: AccountId): LegacyAccount? = account.takeIf { it.id == id }

    override fun updateSync(account: LegacyAccount) {
        this.account = account
    }

    override fun getAccounts(): List<LegacyAccount> = listOf(account)

    override fun getAccountsFlow(): Flow<List<LegacyAccount>> = flowOf(listOf(account))

    override fun getAccount(accountUuid: String): LegacyAccount? = account.takeIf { it.uuid == accountUuid }

    override fun getAccountFlow(accountUuid: String): Flow<LegacyAccount?> = flowOf(getAccount(accountUuid))

    override fun moveAccount(account: LegacyAccount, newPosition: Int) = Unit

    override fun saveAccount(account: LegacyAccount) {
        this.account = account
    }
}

private class FakeStringsResourceManager : StringsResourceManager {
    override fun stringResource(resourceId: Int): String = resourceId.toString()

    override fun stringResource(resourceId: Int, vararg formatArgs: Any?): String = resourceId.toString()
}
