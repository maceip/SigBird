package net.thunderbird.app.common.account

import android.content.Context
import app.k9mail.legacy.ui.folder.FolderNameFormatter
import net.thunderbird.feature.account.settings.impl.ui.folders.FolderDisplayNameFormatter
import net.thunderbird.feature.mail.folder.api.RemoteFolder

internal class DefaultFolderDisplayNameFormatter(
    context: Context,
) : FolderDisplayNameFormatter {
    private val formatter = FolderNameFormatter(context.resources)

    override fun displayName(folder: RemoteFolder): String = formatter.displayName(folder)
}
