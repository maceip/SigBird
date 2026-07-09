package net.thunderbird.feature.account.settings.impl.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.thunderbird.feature.account.profile.AccountProfileRepository
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.AccountProfileSummary
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase

internal class GetAllAccountProfiles(
    private val repository: AccountProfileRepository,
) : UseCase.GetAllAccountProfiles {
    override fun invoke(): Flow<List<AccountProfileSummary>> {
        return repository.getAll().map { profiles ->
            profiles.map { profile ->
                AccountProfileSummary(
                    accountId = profile.id,
                    name = profile.name,
                )
            }
        }
    }
}
