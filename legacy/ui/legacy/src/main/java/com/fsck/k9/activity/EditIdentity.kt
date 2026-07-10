package com.fsck.k9.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import com.fsck.k9.EmailAddressValidator
import com.fsck.k9.Preferences
import com.fsck.k9.message.html.SignatureContent
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.BaseActivity
import com.fsck.k9.ui.identity.SignatureHtmlEditor
import net.thunderbird.components.ui.bolt.atom.Checkbox
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.text.TextBodySmall
import net.thunderbird.components.ui.bolt.molecule.input.TextInput
import net.thunderbird.components.ui.bolt.organism.TopAppBar
import net.thunderbird.components.ui.bolt.template.Scaffold
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import org.koin.android.ext.android.inject

class EditIdentity : BaseActivity() {
    private val emailAddressValidator: EmailAddressValidator by inject()
    private val themeProvider: FeatureThemeProvider by inject()

    private lateinit var account: LegacyAccountDto
    private var identityIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        identityIndex = intent.getIntExtra(EXTRA_IDENTITY_INDEX, -1)
        val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT) ?: error("Missing account UUID")
        account = Preferences.getPreferences().getAccount(accountUuid) ?: error("Couldn't find account")

        val intentIdentity = when {
            identityIndex != -1 -> {
                IntentCompat.getParcelableExtra(intent, EXTRA_IDENTITY, Identity::class.java)
                    ?: error("Missing argument")
            }

            else -> Identity()
        }
        val initialIdentity = savedInstanceState?.let {
            BundleCompat.getParcelable(it, EXTRA_IDENTITY, Identity::class.java)
        } ?: intentIdentity

        setContent {
            themeProvider.WithTheme {
                EditIdentityScreen(
                    initialIdentity = initialIdentity,
                    emailAddressValidator = emailAddressValidator,
                    onBack = { finish() },
                    onSave = { identity ->
                        saveIdentity(identity)
                        finish()
                    },
                    modifier = Modifier.semantics { testTagsAsResourceId = true },
                )
            }
        }
    }

    private fun saveIdentity(identity: Identity) {
        val identities = account.identities
        if (identityIndex == -1) {
            identities.add(identity)
        } else {
            identities.removeAt(identityIndex)
            identities.add(identityIndex, identity)
        }
        Preferences.getPreferences().saveAccount(account)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Identity edits are held in Compose state; nothing additional to persist here.
    }

    companion object {
        const val EXTRA_IDENTITY = "com.fsck.k9.EditIdentity_identity"
        const val EXTRA_IDENTITY_INDEX = "com.fsck.k9.EditIdentity_identity_index"
        const val EXTRA_ACCOUNT = "com.fsck.k9.EditIdentity_account"
    }
}

@Suppress("LongMethod")
@Composable
private fun EditIdentityScreen(
    initialIdentity: Identity,
    emailAddressValidator: EmailAddressValidator,
    onBack: () -> Unit,
    onSave: (Identity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var description by rememberSaveable { mutableStateOf(initialIdentity.description.orEmpty()) }
    var name by rememberSaveable { mutableStateOf(initialIdentity.name.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(initialIdentity.email.orEmpty()) }
    var replyTo by rememberSaveable { mutableStateOf(initialIdentity.replyTo.orEmpty()) }
    var useSignature by rememberSaveable { mutableStateOf(initialIdentity.signatureUse) }
    var signature by rememberSaveable { mutableStateOf(initialIdentity.signature.orEmpty()) }

    val canSave = remember(email, replyTo) {
        emailAddressValidator.isValidAddressOnly(email.trim()) &&
            (replyTo.isBlank() || emailAddressValidator.isValidAddressOnly(replyTo.trim()))
    }

    Scaffold(
        modifier = modifier.testTag("edit_identity_screen"),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.edit_identity_title),
                navigationIcon = {
                    ButtonIcon(
                        onClick = onBack,
                        imageVector = Icons.Outlined.ArrowBack,
                    )
                },
                actions = {
                    ButtonText(
                        enabled = canSave,
                        onClick = {
                            onSave(
                                initialIdentity.copy(
                                    description = description.takeUnless { it.isBlank() },
                                    name = name.takeUnless { it.isBlank() },
                                    email = email.trim(),
                                    replyTo = replyTo.trim().takeUnless { it.isBlank() },
                                    signatureUse = useSignature,
                                    signature = SignatureContent.sanitizeForStorage(signature),
                                ),
                            )
                        },
                        text = stringResource(R.string.edit_identity_save),
                        modifier = Modifier.testTag("edit_identity_save"),
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = BoltTheme.spacings.double),
                verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.double),
            ) {
                TextInput(
                    text = description,
                    onTextChange = { description = it },
                    label = stringResource(R.string.edit_identity_description_label),
                )
                TextInput(
                    text = name,
                    onTextChange = { name = it },
                    label = stringResource(R.string.edit_identity_name_label),
                )
                TextInput(
                    text = email,
                    onTextChange = { email = it },
                    label = stringResource(R.string.edit_identity_email_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                TextInput(
                    text = replyTo,
                    onTextChange = { replyTo = it },
                    label = stringResource(R.string.edit_identity_reply_to_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_identity_use_signature_row")
                        .clickable { useSignature = !useSignature }
                        .padding(horizontal = BoltTheme.spacings.double),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = useSignature,
                        onCheckedChange = { useSignature = it },
                        modifier = Modifier.testTag("edit_identity_use_signature"),
                    )
                    TextBodySmall(
                        text = stringResource(R.string.account_settings_signature_use_label),
                    )
                }

                if (useSignature) {
                    SignatureHtmlEditor(
                        html = signature,
                        onHtmlChange = { signature = it },
                        modifier = Modifier.testTag("edit_identity_signature_editor"),
                    )
                }
            }
        }
    }
}
