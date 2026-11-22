package com.maxrave.simpmusic.expect

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.BuildKonfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File
import java.util.zip.ZipFile

private const val TAG = "Update"
private const val VIVO_PACKAGE = "com.spotify.music"
private const val UPSTREAM_UPDATE_CHANNEL = "upstream_update_channel"
private const val UPSTREAM_UPDATE_NOTIFICATION_ID = 0x5D7A

private fun appContext(): Context = getKoin().get()

private val assetSuffix: String? by lazy { computeAssetSuffix(appContext()) }

private fun computeAssetSuffix(context: Context): String? {
    val packageName = context.packageName
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    // A dev build installs as app.musikku.dev and is signed with a debug key: a release APK would
    // either land as a second app or fail to install over it.
    if (packageName.endsWith(".dev") || debuggable) return null
    if (packageName == VIVO_PACKAGE) return "-full-vivo-arm64-v8a.apk"
    val flavor = if (BuildKonfig.isFullBuild) "full" else "foss"
    return "-$flavor-${installedAbi(context)}.apk"
}

/** The ABI split this APK was built for, read from its own lib/ folders; "universal" when it holds several or none. */
private fun installedAbi(context: Context): String =
    try {
        ZipFile(context.applicationInfo.sourceDir).use { zip ->
            val abis =
                zip
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .filter { it.startsWith("lib/") }
                    .mapNotNull { it.removePrefix("lib/").substringBefore('/', "").takeIf { abi -> abi.isNotEmpty() } }
                    .toSet()
            abis.singleOrNull() ?: "universal"
        }
    } catch (e: Exception) {
        Logger.w(TAG, "Could not read the installed APK's ABIs: ${e.message}")
        "universal"
    }

actual fun updateAssetSuffix(): String? = assetSuffix

actual fun updateDownloadDir(): File = File(appContext().cacheDir, "updates")

actual fun needsInstallPermission(): Boolean = !appContext().packageManager.canRequestPackageInstalls()

actual fun openInstallPermissionSettings() {
    val context = appContext()
    try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: ActivityNotFoundException) {
        Logger.w(TAG, "No screen for install permission, opening security settings")
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

actual suspend fun installUpdate(file: File) {
    val context = appContext()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    val intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    withContext(Dispatchers.Main) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            throw IllegalStateException("No app on this device can install the update.", e)
        }
    }
}

actual fun postUpstreamUpdateNotification(
    title: String,
    message: String,
    url: String,
) {
    val context = appContext()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        Logger.i(TAG, "Upstream update notification skipped: notifications are not allowed")
        return
    }
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    // Creating a channel that already exists only updates its name, so this is safe on every call.
    manager.createNotificationChannel(
        NotificationChannel(UPSTREAM_UPDATE_CHANNEL, "Upstream updates", NotificationManager.IMPORTANCE_DEFAULT),
    )
    val pendingIntent =
        PendingIntent.getActivity(
            context,
            UPSTREAM_UPDATE_NOTIFICATION_ID,
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    val notification =
        NotificationCompat
            .Builder(context, UPSTREAM_UPDATE_CHANNEL)
            .setSmallIcon(com.maxrave.media3.R.drawable.mono)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    try {
        manager.notify(UPSTREAM_UPDATE_NOTIFICATION_ID, notification)
    } catch (e: SecurityException) {
        Logger.w(TAG, "Upstream update notification refused: ${e.message}")
    }
}
