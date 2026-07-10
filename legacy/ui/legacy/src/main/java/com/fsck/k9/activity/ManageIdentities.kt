package com.fsck.k9.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.fsck.k9.Preferences
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.BaseActivity
import com.fsck.k9.ui.identity.IdentityFormatter
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.organism.TopAppBar
import net.thunderbird.components.ui.bolt.template.Scaffold
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.android.ext.android.inject

/**
 * Modern Compose identity management screen.
 *
 * Replaces the legacy ListView-based ManageIdentities activity while keeping the
 * same Intent contract used by account settings.
 */
class ManageIdentities : BaseActivity() {
    private val themeProvider: FeatureThemeProvider by inject()
    private val identityFormatter: IdentityFormatter by inject()

    private lateinit var account: LegacyAccountDto
    private var identitiesChanged = false

    private val editIdentityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // EditIdentity saves directly; refresh from preferences.
        reloadAccount()
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accountUuid = intent.getStringExtra(ChooseIdentity.EXTRA_ACCOUNT)
            ?: error("Missing account UUID")
        account = Preferences.getPreferences().getAccount(accountUuid)
            ?: error("Couldn't find account")
        render()
    }

    override fun onStop() {
        saveIdentitiesIfNeeded()
        super.onStop()
    }

    private fun reloadAccount() {
        val uuid = account.uuid
        account = Preferences.getPreferences().getAccount(uuid) ?: account
    }

    private fun render() {
        setContent {
            themeProvider.WithTheme {
                ManageIdentitiesScreen(
                    identities = account.identities.toImmutableList(),
                    identityFormatter = identityFormatter,
                    onBack = { finish() },
                    onAdd = { launchEditIdentity(identityIndex = -1, identity = null) },
                    onEdit = { index, identity -> launchEditIdentity(index, identity) },
                    onMoveUp = { index -> moveIdentity(index, index - 1) },
                    onMoveDown = { index -> moveIdentity(index, index + 1) },
                    onMakeDefault = { index -> moveIdentity(index, 0) },
                    onRemove = { index -> removeIdentity(index) },
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                )
            }
        }
    }

    private fun launchEditIdentity(identityIndex: Int, identity: Identity?) {
        val intent = Intent(this, EditIdentity::class.java).apply {
            putExtra(EditIdentity.EXTRA_ACCOUNT, account.uuid)
            if (identityIndex >= 0 && identity != null) {
                putExtra(EditIdentity.EXTRA_IDENTITY, identity)
                putExtra(EditIdentity.EXTRA_IDENTITY_INDEX, identityIndex)
            }
        }
        editIdentityLauncher.launch(intent)
    }

    private fun moveIdentity(from: Int, to: Int) {
        val identities = account.identities
        if (from !in identities.indices || to !in identities.indices) return
        val identity = identities.removeAt(from)
        identities.add(to, identity)
        identitiesChanged = true
        render()
    }

    private fun removeIdentity(index: Int) {
        val identities = account.identities
        if (identities.size <= 1) {
            Toast.makeText(this, R.string.no_removable_identity, Toast.LENGTH_LONG).show()
            return
        }
        if (index !in identities.indices) return
        identities.removeAt(index)
        identitiesChanged = true
        render()
    }

    private fun saveIdentitiesIfNeeded() {
        if (identitiesChanged) {
            Preferences.getPreferences().saveAccount(account)
            identitiesChanged = false
        }
    }

    companion object {
        @JvmStatic
        fun start(activity: Activity, accountUuid: String) {
            val intent = Intent(activity, ManageIdentities::class.java)
            intent.putExtra(ChooseIdentity.EXTRA_ACCOUNT, accountUuid)
            activity.startActivity(intent)
        }
    }
}

@Composable
private fun ManageIdentitiesScreen(
    identities: ImmutableList<Identity>,
    identityFormatter: IdentityFormatter,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Int, Identity) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onMakeDefault: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag("manage_identities_screen"),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.manage_identities_title),
                navigationIcon = {
                    ButtonIcon(
                        onClick = onBack,
                        imageVector = Icons.Outlined.ArrowBack,
                    )
                },
                actions = {
                    ButtonIcon(
                        onClick = onAdd,
                        imageVector = Icons.Outlined.Add,
                        modifier = Modifier.testTag("manage_identities_add"),
                    )
                },
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("manage_identities_list"),
                contentPadding = PaddingValues(vertical = BoltTheme.spacings.default),
                verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
            ) {
                itemsIndexed(identities) { index, identity ->
                    IdentityListItem(
                        title = identityFormatter.getDisplayName(identity),
                        subtitle = identityFormatter.getEmailDisplayName(identity),
                        isDefault = index == 0,
                        canMoveUp = index > 0,
                        canMoveDown = index < identities.lastIndex,
                        canRemove = identities.size > 1,
                        onClick = { onEdit(index, identity) },
                        onMoveUp = { onMoveUp(index) },
                        onMoveDown = { onMoveDown(index) },
                        onMakeDefault = { onMakeDefault(index) },
                        onRemove = { onRemove(index) },
                        modifier = Modifier.testTag("manage_identities_item_$index"),
                    )
                }
            }
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun IdentityListItem(
    title: String,
    subtitle: String,
    isDefault: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMakeDefault: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = BoltTheme.spacings.double, vertical = BoltTheme.spacings.default),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TextBodyLarge(text = title)
                TextBodySmall(text = subtitle)
                if (isDefault) {
                    TextBodySmall(text = stringResource(R.string.manage_identities_default_badge))
                }
            }
            ButtonIcon(
                onClick = { showActions = !showActions },
                imageVector = Icons.Outlined.MoreVert,
            )
        }

        if (showActions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = BoltTheme.spacings.half),
                horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
            ) {
                if (canMoveUp) {
                    ButtonText(
                        text = stringResource(R.string.manage_identities_move_up_action),
                        onClick = {
                            showActions = false
                            onMoveUp()
                        },
                    )
                }
                if (canMoveDown) {
                    ButtonText(
                        text = stringResource(R.string.manage_identities_move_down_action),
                        onClick = {
                            showActions = false
                            onMoveDown()
                        },
                    )
                }
                if (!isDefault) {
                    ButtonText(
                        text = stringResource(R.string.manage_identities_move_top_action),
                        onClick = {
                            showActions = false
                            onMakeDefault()
                        },
                    )
                }
                if (canRemove) {
                    ButtonText(
                        text = stringResource(R.string.manage_identities_remove_action),
                        onClick = {
                            showActions = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}
