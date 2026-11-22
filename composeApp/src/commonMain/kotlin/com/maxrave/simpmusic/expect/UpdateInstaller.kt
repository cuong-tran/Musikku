package com.maxrave.simpmusic.expect

import java.io.File

/** Suffix of the release asset this build updates itself with, or null when this build cannot self-update. */
expect fun updateAssetSuffix(): String?

/** Folder the update is downloaded into (a temp/cache location). */
expect fun updateDownloadDir(): File

/** True when the OS needs the user to grant this app permission to install packages first. */
expect fun needsInstallPermission(): Boolean

/** Opens the system screen where that permission is granted. */
expect fun openInstallPermissionSettings()

/**
 * Installs the downloaded file. Returns normally on Android (the system installer takes over); on
 * Desktop it may exit the process. Throws on failure with a message fit to show the user.
 */
expect suspend fun installUpdate(file: File)

/**
 * Posts a notification saying upstream SimpMusic has a release this fork has not caught up with.
 * It is information only: nothing in it installs. [url] is that release's page.
 */
expect fun postUpstreamUpdateNotification(
    title: String,
    message: String,
    url: String,
)
