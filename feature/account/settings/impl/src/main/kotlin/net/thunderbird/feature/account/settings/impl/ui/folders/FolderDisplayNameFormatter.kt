package net.thunderbird.feature.account.settings.impl.ui.folders

import net.thunderbird.feature.mail.folder.api.RemoteFolder

/**
 * Formats remote folder names for display in folder settings.
 */
fun interface FolderDisplayNameFormatter {
    fun displayName(folder: RemoteFolder): String
}
