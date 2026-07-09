package net.thunderbird.feature.account.settings.impl.ui.compositionMail

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.android.account.MessageFormat
import net.thunderbird.core.android.account.QuoteStyle
import net.thunderbird.core.common.resources.StringsResourceManager
import net.thunderbird.core.ui.setting.Setting
import net.thunderbird.core.ui.setting.SettingValue
import net.thunderbird.core.ui.setting.SettingValue.Select.SelectOption
import net.thunderbird.core.ui.setting.Settings
import net.thunderbird.feature.account.settings.R

internal class CompositionMailSettingsOptionsMapper(
    private val resources: StringsResourceManager,
) {
    fun messageFormatOptions(): ImmutableList<SelectOption> = persistentListOf(
        SelectOption(MessageFormat.TEXT.name) {
            resources.stringResource(R.string.account_settings_message_format_text)
        },
        SelectOption(MessageFormat.HTML.name) {
            resources.stringResource(R.string.account_settings_message_format_html)
        },
        SelectOption(MessageFormat.AUTO.name) {
            resources.stringResource(R.string.account_settings_message_format_auto)
        },
    )

    fun quoteStyleOptions(): ImmutableList<SelectOption> = persistentListOf(
        SelectOption(QuoteStyle.PREFIX.name) {
            resources.stringResource(R.string.account_settings_quote_style_prefix)
        },
        SelectOption(QuoteStyle.HEADER.name) {
            resources.stringResource(R.string.account_settings_quote_style_header)
        },
    )
}

internal class CompositionMailSettingsBuilder(
    private val resources: StringsResourceManager,
    private val optionsMapper: CompositionMailSettingsOptionsMapper,
) : CompositionMailSettingsContract.SettingsBuilder {
    override fun build(
        state: CompositionMailSettingsContract.State,
        onEvent: (CompositionMailSettingsContract.Event) -> Unit,
    ): Settings {
        val settings = mutableListOf<Setting>()
        settings += compositionDefaults(onEvent)
        settings += manageIdentities(onEvent)
        settings += outgoingServer(onEvent)
        settings += messageFormat(state.messageFormat)
        settings += alwaysShowCcBcc(state.alwaysShowCcBcc)
        settings += messageReadReceipt(state.messageReadReceipt)
        settings += quoteStyle(state.quoteStyle)
        settings += defaultQuotedTextShown(state.defaultQuotedTextShown)
        if (state.quoteStyle.id == QuoteStyle.HEADER.name) {
            settings += replyAfterQuote(state.replyAfterQuote)
        }
        settings += stripSignature(state.stripSignature)
        if (state.quoteStyle.id == QuoteStyle.PREFIX.name) {
            settings += quotePrefix(state.quotePrefix)
        }
        if (state.supportsUploadSentMessages) {
            settings += uploadSentMessages(state.uploadSentMessages)
        }
        return settings.toImmutableList()
    }

    private fun compositionDefaults(onEvent: (CompositionMailSettingsContract.Event) -> Unit) = SettingValue.ActionText(
        id = CompositionMailSettingId.COMPOSITION_DEFAULTS.name,
        title = { resources.stringResource(R.string.account_settings_composition_label) },
        description = { resources.stringResource(R.string.account_settings_composition_summary) },
        icon = { null },
        value = "",
        onClick = { onEvent(CompositionMailSettingsContract.Event.OnCompositionDefaultsClick) },
    )

    private fun manageIdentities(onEvent: (CompositionMailSettingsContract.Event) -> Unit) = SettingValue.ActionText(
        id = CompositionMailSettingId.MANAGE_IDENTITIES.name,
        title = { resources.stringResource(R.string.account_settings_identities_label) },
        description = { resources.stringResource(R.string.account_settings_identities_summary) },
        icon = { null },
        value = "",
        onClick = { onEvent(CompositionMailSettingsContract.Event.OnManageIdentitiesClick) },
    )

    private fun outgoingServer(onEvent: (CompositionMailSettingsContract.Event) -> Unit) = SettingValue.ActionText(
        id = CompositionMailSettingId.OUTGOING_SERVER.name,
        title = { resources.stringResource(R.string.account_settings_outgoing_label) },
        description = { resources.stringResource(R.string.account_settings_outgoing_summary) },
        icon = { null },
        value = "",
        onClick = { onEvent(CompositionMailSettingsContract.Event.OnOutgoingServerClick) },
    )

    private fun messageFormat(value: SelectOption) = SettingValue.Select(
        id = CompositionMailSettingId.MESSAGE_FORMAT.name,
        title = { resources.stringResource(R.string.account_settings_message_format_label) },
        description = { null },
        icon = { null },
        displayValueAsSecondaryText = true,
        value = value,
        options = optionsMapper.messageFormatOptions(),
    )

    private fun alwaysShowCcBcc(value: Boolean) = SettingValue.Switch(
        id = CompositionMailSettingId.ALWAYS_SHOW_CC_BCC.name,
        title = { resources.stringResource(R.string.account_settings_always_show_cc_bcc_label) },
        description = { null },
        value = value,
    )

    private fun messageReadReceipt(value: Boolean) = SettingValue.Switch(
        id = CompositionMailSettingId.MESSAGE_READ_RECEIPT.name,
        title = { resources.stringResource(R.string.account_settings_message_read_receipt_label) },
        description = { resources.stringResource(R.string.account_settings_message_read_receipt_summary) },
        value = value,
    )

    private fun quoteStyle(value: SelectOption) = SettingValue.Select(
        id = CompositionMailSettingId.QUOTE_STYLE.name,
        title = { resources.stringResource(R.string.account_settings_quote_style_label) },
        description = { null },
        icon = { null },
        displayValueAsSecondaryText = true,
        value = value,
        options = optionsMapper.quoteStyleOptions(),
    )

    private fun defaultQuotedTextShown(value: Boolean) = SettingValue.Switch(
        id = CompositionMailSettingId.DEFAULT_QUOTED_TEXT_SHOWN.name,
        title = { resources.stringResource(R.string.account_settings_default_quoted_text_shown_label) },
        description = { resources.stringResource(R.string.account_settings_default_quoted_text_shown_summary) },
        value = value,
    )

    private fun replyAfterQuote(value: Boolean) = SettingValue.Switch(
        id = CompositionMailSettingId.REPLY_AFTER_QUOTE.name,
        title = { resources.stringResource(R.string.account_settings_reply_after_quote_label) },
        description = { resources.stringResource(R.string.account_settings_reply_after_quote_summary) },
        value = value,
    )

    private fun stripSignature(value: Boolean) = SettingValue.Switch(
        id = CompositionMailSettingId.STRIP_SIGNATURE.name,
        title = { resources.stringResource(R.string.account_settings_strip_signature_label) },
        description = { resources.stringResource(R.string.account_settings_strip_signature_summary) },
        value = value,
    )

    private fun quotePrefix(value: String) = SettingValue.Text(
        id = CompositionMailSettingId.QUOTE_PREFIX.name,
        title = { resources.stringResource(R.string.account_settings_quote_prefix_label) },
        description = { null },
        icon = { null },
        value = value,
    )

    private fun uploadSentMessages(value: Boolean) = SettingValue.Switch(
        id = CompositionMailSettingId.UPLOAD_SENT_MESSAGES.name,
        title = { resources.stringResource(R.string.account_settings_upload_sent_messages_label) },
        description = { resources.stringResource(R.string.account_settings_upload_sent_messages_summary) },
        value = value,
    )
}

internal enum class CompositionMailSettingId {
    COMPOSITION_DEFAULTS,
    MANAGE_IDENTITIES,
    OUTGOING_SERVER,
    MESSAGE_FORMAT,
    ALWAYS_SHOW_CC_BCC,
    MESSAGE_READ_RECEIPT,
    QUOTE_STYLE,
    DEFAULT_QUOTED_TEXT_SHOWN,
    REPLY_AFTER_QUOTE,
    STRIP_SIGNATURE,
    QUOTE_PREFIX,
    UPLOAD_SENT_MESSAGES,
}
