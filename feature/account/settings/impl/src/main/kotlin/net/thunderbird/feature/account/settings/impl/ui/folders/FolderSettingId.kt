package net.thunderbird.feature.account.settings.impl.ui.folders

internal object FolderSettingId {
    const val AUTO_EXPAND_FOLDER = "auto_expand_folder"
    const val SUBSCRIBED_FOLDERS_ONLY = "subscribed_folders_only"
    const val ARCHIVE_FOLDER = "archive_folder"
    const val DRAFTS_FOLDER = "drafts_folder"
    const val SENT_FOLDER = "sent_folder"
    const val SPAM_FOLDER = "spam_folder"
    const val TRASH_FOLDER = "trash_folder"
}

internal object FolderOptionEncoding {
    const val AUTOMATIC_PREFIX = "AUTOMATIC|"
    const val MANUAL_PREFIX = "MANUAL|"
    const val NO_FOLDER_VALUE = ""

    fun automatic(folderId: Long?): String = AUTOMATIC_PREFIX + (folderId?.toString() ?: NO_FOLDER_VALUE)

    fun manual(folderId: Long?): String = MANUAL_PREFIX + (folderId?.toString() ?: NO_FOLDER_VALUE)

    fun isAutomatic(encoded: String): Boolean = encoded.startsWith(AUTOMATIC_PREFIX)

    fun folderId(encoded: String): Long? {
        val folderValue = encoded.substringAfter("|")
        return if (folderValue.isEmpty()) null else folderValue.toLongOrNull()
    }
}
