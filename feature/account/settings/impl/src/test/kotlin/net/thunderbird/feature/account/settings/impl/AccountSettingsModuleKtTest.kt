package net.thunderbird.feature.account.settings.impl

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.test.Test
import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.core.android.account.LegacyAccountRepository
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.featureflag.FeatureFlagProvider
import net.thunderbird.core.file.MimeTypeResolver
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.avatar.AvatarIcon
import net.thunderbird.feature.account.avatar.AvatarIconCatalog
import net.thunderbird.feature.account.avatar.AvatarImageRepository
import net.thunderbird.feature.account.avatar.AvatarMonogramCreator
import net.thunderbird.feature.account.profile.AccountProfileRepository
import net.thunderbird.feature.account.settings.api.AccountSettingsCapabilities
import net.thunderbird.feature.account.settings.api.AccountSettingsFolderProvider
import net.thunderbird.feature.account.settings.api.AccountSettingsFolderRefresh
import net.thunderbird.feature.account.settings.api.AccountSettingsNotificationBridge
import net.thunderbird.feature.account.settings.api.OpenPgpProviderSummaryProvider
import net.thunderbird.feature.account.settings.featureAccountSettingsModule
import net.thunderbird.feature.account.settings.impl.ui.folders.FolderDisplayNameFormatter
import net.thunderbird.feature.account.settings.impl.ui.general.GeneralSettingsContract
import net.thunderbird.feature.account.settings.impl.ui.notifications.RingtoneSummaryFormatter
import net.thunderbird.feature.account.settings.impl.ui.notifications.VibrationSummaryFormatter
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

internal class AccountSettingsModuleKtTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `should hava a valid di module`() {
        featureAccountSettingsModule.verify(
            extraTypes = listOf(
                AccountId::class,
                GeneralSettingsContract.State::class,
                Logger::class,
                StringsResourceManager::class,
                FolderDisplayNameFormatter::class,
                AccountSettingsNotificationBridge::class,
                AccountSettingsFolderRefresh::class,
                AccountSettingsFolderProvider::class,
                AccountSettingsCapabilities::class,
                OpenPgpProviderSummaryProvider::class,
                AccountProfileRepository::class,
                LegacyAccountRepository::class,
                AvatarImageRepository::class,
                MimeTypeResolver::class,
                AvatarMonogramCreator::class,
                FeatureFlagProvider::class,
                AvatarIconCatalog::class,
                AvatarIcon::class,
                ImageVector::class,
                RingtoneSummaryFormatter::class,
                VibrationSummaryFormatter::class,
                ImmutableList::class,
                Int::class,
                Context::class,
            ),
        )
    }
}
