package net.thunderbird.feature.account.settings.impl.ui.compositionMail

import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.core.android.account.MessageFormat
import net.thunderbird.core.android.account.QuoteStyle
import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel
import net.thunderbird.core.ui.setting.SettingValue.Select.SelectOption
import net.thunderbird.core.ui.setting.Settings
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsContract.Event
import net.thunderbird.feature.account.settings.impl.ui.compositionMail.CompositionMailSettingsContract.State

internal interface CompositionMailSettingsContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect>

    data class State(
        val subtitle: String = "",
        val messageFormat: SelectOption = SelectOption(MessageFormat.AUTO.name) { "" },
        val alwaysShowCcBcc: Boolean = false,
        val messageReadReceipt: Boolean = false,
        val quoteStyle: SelectOption = SelectOption(QuoteStyle.PREFIX.name) { "" },
        val defaultQuotedTextShown: Boolean = true,
        val replyAfterQuote: Boolean = false,
        val stripSignature: Boolean = true,
        val quotePrefix: String = "",
        val uploadSentMessages: Boolean = true,
        val supportsUploadSentMessages: Boolean = true,
    )

    sealed interface Event {
        data object OnBackPressed : Event
        data object OnCompositionDefaultsClick : Event
        data object OnManageIdentitiesClick : Event
        data object OnOutgoingServerClick : Event
        data class OnMessageFormatChange(val messageFormat: SelectOption) : Event
        data class OnAlwaysShowCcBccToggle(val enabled: Boolean) : Event
        data class OnMessageReadReceiptToggle(val enabled: Boolean) : Event
        data class OnQuoteStyleChange(val quoteStyle: SelectOption) : Event
        data class OnDefaultQuotedTextShownToggle(val enabled: Boolean) : Event
        data class OnReplyAfterQuoteToggle(val enabled: Boolean) : Event
        data class OnStripSignatureToggle(val enabled: Boolean) : Event
        data class OnQuotePrefixChange(val quotePrefix: String) : Event
        data class OnUploadSentMessagesToggle(val enabled: Boolean) : Event
    }

    sealed interface Effect {
        data object NavigateBack : Effect
        data object NavigateToCompositionDefaults : Effect
        data object NavigateToManageIdentities : Effect
        data object NavigateToOutgoingServer : Effect
    }

    fun interface SettingsBuilder {
        fun build(
            state: State,
            onEvent: (Event) -> Unit,
        ): Settings
    }
}
