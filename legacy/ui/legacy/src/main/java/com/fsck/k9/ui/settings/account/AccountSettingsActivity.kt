package com.fsck.k9.ui.settings.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartScreenCallback
import androidx.preference.PreferenceScreen
import app.k9mail.feature.launcher.FeatureLauncherActivity
import app.k9mail.feature.launcher.FeatureLauncherTarget
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.BaseActivity
import com.fsck.k9.ui.base.extensions.fragmentTransaction
import com.fsck.k9.ui.base.extensions.fragmentTransactionWithBackStack
import com.fsck.k9.ui.base.livedata.observeNotNull
import net.thunderbird.core.logging.legacy.Log
import org.koin.androidx.viewmodel.ext.android.viewModel

class AccountSettingsActivity : BaseActivity(), OnPreferenceStartScreenCallback {
    private val accountViewModel: AccountSettingsViewModel by viewModel()
    private lateinit var accountUuid: String
    private var startScreenKey: String? = null
    private var legacyOnly = false
    private var fragmentAdded = false

    private lateinit var accountSpinner: AccountSelectionSpinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!decodeArguments()) {
            Log.d("Invalid arguments")
            finish()
            return
        }

        if (!legacyOnly && redirectToComposeScreen(startScreenKey)) {
            finish()
            return
        }

        setLayout(R.layout.activity_account_settings)

        accountSpinner = findViewById(R.id.accountSpinner)

        initializeActionBar()

        loadAccount()
    }

    private fun redirectToComposeScreen(screenKey: String?): Boolean {
        val target = when (screenKey) {
            PREFERENCE_FOLDERS -> FeatureLauncherTarget.AccountFolderSettings(accountUuid)
            PREFERENCE_NOTIFICATIONS -> FeatureLauncherTarget.AccountNotificationSettings(accountUuid)
            PREFERENCE_OPENPGP -> FeatureLauncherTarget.AccountCryptoSettings(accountUuid)
            null, PREFERENCE_MAIN -> FeatureLauncherTarget.AccountSettingsHub(accountUuid)
            else -> return false
        }
        FeatureLauncherActivity.launch(context = this, target = target)
        return true
    }

    private fun initializeActionBar() {
        val actionBar = supportActionBar ?: throw RuntimeException("getSupportActionBar() == null")
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setDisplayShowTitleEnabled(false)

        accountSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                onAccountSelected(selectedAccountUuid = accountSpinner.selection.uuid)
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }

        accountViewModel.accounts.observeNotNull(this) { accounts ->
            accountSpinner.setAccounts(accounts)
        }
    }

    private fun onAccountSelected(selectedAccountUuid: String) {
        if (selectedAccountUuid != accountUuid && !isFinishing) {
            FeatureLauncherActivity.launch(
                context = this,
                target = FeatureLauncherTarget.AccountSettingsHub(selectedAccountUuid),
            )
            finish()
        }
    }

    private fun decodeArguments(): Boolean {
        accountUuid = intent.getStringExtra(ARG_ACCOUNT_UUID) ?: return false
        startScreenKey = intent.getStringExtra(ARG_START_SCREEN_KEY)
        legacyOnly = intent.getBooleanExtra(ARG_LEGACY_ONLY, false)
        return true
    }

    private fun loadAccount() {
        accountViewModel.getAccount(accountUuid).observe(this) { account ->
            if (account == null) {
                Log.w("Account with UUID %s not found", accountUuid)
                finish()
                return@observe
            }

            accountSpinner.selection = account
            addAccountSettingsFragment()
        }
    }

    private fun addAccountSettingsFragment() {
        val needToAddFragment = supportFragmentManager.findFragmentById(R.id.accountSettingsContainer) == null
        if (needToAddFragment && !fragmentAdded) {
            fragmentAdded = true
            fragmentTransaction {
                add(R.id.accountSettingsContainer, AccountSettingsFragment.create(accountUuid, startScreenKey))
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onPreferenceStartScreen(
        caller: PreferenceFragmentCompat,
        preferenceScreen: PreferenceScreen,
    ): Boolean {
        if (!legacyOnly && redirectToComposeScreen(preferenceScreen.key)) {
            return true
        }

        fragmentTransactionWithBackStack {
            replace(R.id.accountSettingsContainer, AccountSettingsFragment.create(accountUuid, preferenceScreen.key))
        }

        return true
    }

    override fun setTitle(title: CharSequence) {
        super.setTitle(title)
        accountSpinner.setTitle(title)
    }

    companion object {
        private const val ARG_ACCOUNT_UUID = "accountUuid"
        private const val ARG_START_SCREEN_KEY = "startScreen"
        private const val ARG_LEGACY_ONLY = "legacyOnly"
        private const val PREFERENCE_MAIN = "main"
        private const val PREFERENCE_FOLDERS = "folders"
        private const val PREFERENCE_NOTIFICATIONS = "notifications"
        private const val PREFERENCE_OPENPGP = "openpgp"

        @JvmStatic
        @JvmOverloads
        fun start(context: Context, accountUuid: String, startScreenKey: String? = null) {
            val intent = Intent(context, AccountSettingsActivity::class.java).apply {
                putExtra(ARG_ACCOUNT_UUID, accountUuid)
                startScreenKey?.let { putExtra(ARG_START_SCREEN_KEY, it) }
            }
            context.startActivity(intent)
        }

        @JvmStatic
        fun startCryptoSettings(context: Context, accountUuid: String) {
            FeatureLauncherActivity.launch(
                context = context,
                target = FeatureLauncherTarget.AccountCryptoSettings(accountUuid),
            )
        }

        @JvmStatic
        fun startLegacyOpenPgpKeySelector(context: Context, accountUuid: String) {
            val intent = Intent(context, AccountSettingsActivity::class.java).apply {
                putExtra(ARG_ACCOUNT_UUID, accountUuid)
                putExtra(ARG_START_SCREEN_KEY, PREFERENCE_OPENPGP)
                putExtra(ARG_LEGACY_ONLY, true)
            }
            context.startActivity(intent)
        }
    }
}
