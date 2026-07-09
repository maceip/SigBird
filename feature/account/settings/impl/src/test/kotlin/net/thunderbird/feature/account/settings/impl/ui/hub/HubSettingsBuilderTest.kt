package net.thunderbird.feature.account.settings.impl.ui.hub

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.ui.setting.SettingValue
import net.thunderbird.core.ui.setting.SettingValue.Select.SelectOption
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.AccountProfileSummary

class HubSettingsBuilderTest {

    private val resources = object : StringsResourceManager {
        override fun stringResource(resourceId: Int): String = "string_$resourceId"

        override fun stringResource(resourceId: Int, vararg formatArgs: Any?): String = "string_$resourceId"
    }

    private val testSubject = HubSettingsBuilder(resources)

    @Test
    fun `build should include account selector and all hub sections`() {
        val accountId = AccountIdFactory.of("account-1")
        val state = HubSettingsContract.State(
            subtitle = "Test",
            accounts = persistentListOf(
                AccountProfileSummary(accountId = accountId, name = "Test"),
                AccountProfileSummary(accountId = AccountIdFactory.of("account-2"), name = "Other"),
            ),
            selectedAccount = SelectOption(accountId.value) { "Test" },
        )

        val settings = testSubject.build(state) { }

        assertThat(settings).hasSize(9)
        assertThat(settings.first()).isInstanceOf<SettingValue.Select>()
        assertThat((settings[1] as SettingValue.ActionText).id).isEqualTo(HubSettingId.GENERAL)
    }
}
