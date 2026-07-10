package net.thunderbird.feature.account.settings.impl.ui.compositionMail

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.thunderbird.core.android.account.MessageFormat
import net.thunderbird.core.android.account.QuoteStyle
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.logging.Logger
import net.thunderbird.core.outcome.handle
import net.thunderbird.core.ui.contract.mvi.BaseViewModel
import net.thunderbird.core.ui.setting.SettingValue.Select.SelectOption
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.R
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsContract.Effect
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsContract.Event
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsContract.State

private const val TAG = "CompositionMailSettingsViewModel"

@Suppress("LongMethod", "CyclomaticComplexMethod")
internal class CompositionMailSettingsViewModel(
    private val accountId: AccountId,
    private val getAccountName: UseCase.GetAccountName,
    private val getLegacyAccount: UseCase.GetLegacyAccount,
    private val updateCompositionMailSettings: UseCase.UpdateCompositionMailSettings,
    private val resources: StringsResourceManager,
    private val logger: Logger,
) : BaseViewModel<State, Event, Effect>(State()), CompositionMailSettingsContract.ViewModel {

    init {
        observeAccountName()
        observeCompositionMailSettings()
    }

    override fun event(event: Event) {
        when (event) {
            Event.OnBackPressed -> emitEffect(Effect.NavigateBack)

            Event.OnCompositionDefaultsClick -> emitEffect(Effect.NavigateToCompositionDefaults)

            Event.OnManageIdentitiesClick -> emitEffect(Effect.NavigateToManageIdentities)

            Event.OnOutgoingServerClick -> emitEffect(Effect.NavigateToOutgoingServer)

            is Event.OnMessageFormatChange -> updateSetting(
                command = AccountSettingsDomainContract.UpdateCompositionMailSettingsCommand.UpdateMessageFormat(
                    event.messageFormat.id,
                ),
                onSuccess = { updateState { it.copy(messageFormat = event.messageFormat) } },
            )

            is Event.OnAlwaysShowCcBccToggle -> updateSetting(
                command = AccountSettingsDomainContract.UpdateCompositionMailSettingsCommand.UpdateAlwaysShowCcBcc(
                    event.enabled,
                ),
                onSuccess = { updateState { it.copy(alwaysShowCcBcc = event.enabled) } },
            )

            is Event.OnMessageReadReceiptToggle -> updateSetting(
                command = AccountSettingsDomainContract.UpdateCompositionMailSettingsCommand.UpdateMessageReadReceipt(
                    event.enabled,
                ),
                onSuccess = { updateState { it.copy(messageReadReceipt = event.enabled) } },
            )

            is Event.OnQuoteStyleChange -> updateSetting(
                command = AccountSettingsDomainContract.UpdateCompositionMailSettingsCommand.UpdateQuoteStyle(
                    event.quoteStyle.id,
                ),
                onSuccess = { updateState { it.copy(quoteStyle = event.quoteStyle) } },
            )

            is Event.OnDefaultQuotedTextShownToggle -> {
                val command =
                    AccountSettingsDomainContract.UpdateCompositionMailSettingsCommand
                        .UpdateDefaultQuotedTextShown(event.enabled)
                updateSetting(
                    command = command,
                    onSuccess = { updateState { it.copy(defaultQuotedTextShown = event.enabled) } },
                )
            }

            is Event.OnReplyAfterQuoteToggle -> updateSetting(
                command = AccountSettingsDomainContract.UpdateCompositionMailSettingsCommand.UpdateReplyAfterQuote(
                    event.enabled,
                ),
                onSuccess = { updateState { it.copy(replyAfterQuote = event.enabled) } },
            )

            is Event.OnStripSignatureToggle -> updateSetting(
                command = AccountSettingsDomainContract.UpdateCompositionMailSettingsCommand.UpdateStripSignature(
                    event.enabled,
                ),
                onSuccess = { updateState { it.copy(stripSignature = event.enabled) } },
            )

            is Event.OnQuotePrefixChange -> updateSetting(
                command = AccountSettingsDomainContract.UpdateCompositionMailSettingsCommand.UpdateQuotePrefix(
                    event.quotePrefix,
                ),
                onSuccess = { updateState { it.copy(quotePrefix = event.quotePrefix) } },
            )

            is Event.OnUploadSentMessagesToggle -> updateSetting(
                command = AccountSettingsDomainContract.UpdateCompositionMailSettingsCommand.UpdateUploadSentMessages(
                    event.enabled,
                ),
                onSuccess = { updateState { it.copy(uploadSentMessages = event.enabled) } },
            )
        }
    }

    private fun updateSetting(
        command: AccountSettingsDomainContract.UpdateCompositionMailSettingsCommand,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            updateCompositionMailSettings(accountId = accountId, command = command).handle(
                onSuccess = { onSuccess() },
                onFailure = { handleError(it) },
            )
        }
    }

    private fun observeAccountName() {
        getAccountName(accountId)
            .onEach { outcome ->
                outcome.handle(
                    onSuccess = { updateState { state -> state.copy(subtitle = it) } },
                    onFailure = { handleError(it) },
                )
            }.launchIn(viewModelScope)
    }

    private fun observeCompositionMailSettings() {
        viewModelScope.launch {
            getLegacyAccount(accountId).handle(
                onSuccess = { account ->
                    updateState { state ->
                        state.copy(
                            messageFormat = messageFormatOption(account.messageFormat),
                            alwaysShowCcBcc = account.isAlwaysShowCcBcc,
                            messageReadReceipt = account.isMessageReadReceipt,
                            quoteStyle = quoteStyleOption(account.quoteStyle),
                            defaultQuotedTextShown = account.isDefaultQuotedTextShown,
                            replyAfterQuote = account.isReplyAfterQuote,
                            stripSignature = account.isStripSignature,
                            quotePrefix = account.quotePrefix.orEmpty(),
                            uploadSentMessages = account.isUploadSentMessages,
                            supportsUploadSentMessages = true,
                        )
                    }
                },
                onFailure = { handleError(it) },
            )
        }
    }

    private fun messageFormatOption(messageFormat: MessageFormat): SelectOption {
        return when (messageFormat) {
            MessageFormat.TEXT -> SelectOption(MessageFormat.TEXT.name) {
                resources.stringResource(R.string.account_settings_message_format_text)
            }

            MessageFormat.HTML -> SelectOption(MessageFormat.HTML.name) {
                resources.stringResource(R.string.account_settings_message_format_html)
            }

            MessageFormat.AUTO -> SelectOption(MessageFormat.AUTO.name) {
                resources.stringResource(R.string.account_settings_message_format_auto)
            }
        }
    }

    private fun quoteStyleOption(quoteStyle: QuoteStyle): SelectOption {
        return when (quoteStyle) {
            QuoteStyle.PREFIX -> SelectOption(QuoteStyle.PREFIX.name) {
                resources.stringResource(R.string.account_settings_quote_style_prefix)
            }

            QuoteStyle.HEADER -> SelectOption(QuoteStyle.HEADER.name) {
                resources.stringResource(R.string.account_settings_quote_style_header)
            }
        }
    }

    private fun handleError(error: AccountSettingsDomainContract.AccountSettingError) {
        when (error) {
            is AccountSettingsDomainContract.AccountSettingError.NotFound ->
                logger.error(tag = TAG, message = { error.message })

            is AccountSettingsDomainContract.AccountSettingError.StorageError ->
                logger.error(tag = TAG, message = { error.message })

            is AccountSettingsDomainContract.AccountSettingError.UnsupportedFormat ->
                logger.error(tag = TAG, message = { error.message })
        }
    }
}
