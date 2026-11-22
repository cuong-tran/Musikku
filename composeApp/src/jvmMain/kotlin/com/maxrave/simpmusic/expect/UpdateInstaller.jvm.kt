package com.maxrave.simpmusic.expect

import com.maxrave.common.desktopDataPath
import com.maxrave.domain.notification.DesktopNotificationManager
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

private const val TAG = "Update"
private const val UPSTREAM_UPDATE_NOTIFICATION_ID = "upstream_update"

private enum class DesktopOs { LINUX, MAC, WINDOWS, OTHER }

private val os: DesktopOs by lazy {
    val name = System.getProperty("os.name").orEmpty().lowercase()
    when {
        "linux" in name -> DesktopOs.LINUX
        "mac" in name || "darwin" in name -> DesktopOs.MAC
        "windows" in name -> DesktopOs.WINDOWS
        else -> DesktopOs.OTHER
    }
}

private val arch: String by lazy { System.getProperty("os.arch").orEmpty().lowercase() }

private val isDevRun: Boolean get() = System.getProperty("simpmusic.dev") == "true"

/** The AppImage this process was started from, when it was started from one. */
private fun runningAppImage(): File? =
    System
        .getenv("APPIMAGE")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.isFile }

/** The Musikku.app bundle holding the running executable, or null outside a bundle. */
private fun runningMacBundle(): File? {
    val command = ProcessHandle.current().info().command().orElse(null) ?: return null
    var dir: File? = File(command).absoluteFile.parentFile
    while (dir != null && !dir.name.endsWith(".app")) dir = dir.parentFile
    return dir?.takeIf { it.isDirectory && File(it, "Contents").isDirectory }
}

actual fun updateAssetSuffix(): String? {
    if (isDevRun) return null
    return when (os) {
        DesktopOs.LINUX ->
            if (runningAppImage() != null && (arch == "amd64" || arch == "x86_64")) "-linux-x86_64.AppImage" else null

        DesktopOs.MAC ->
            when {
                runningMacBundle() == null -> null
                arch == "aarch64" || arch == "arm64" -> "-mac-aarch64.zip"
                else -> "-mac-amd64.zip"
            }

        // Only x64 is shipped; Windows on ARM runs it under emulation.
        DesktopOs.WINDOWS -> "-windows-x64-installer.zip"

        DesktopOs.OTHER -> null
    }
}

// The user's own data folder, on every OS. The temp folder is shared by every user on Linux and
// macOS (/tmp), so a fixed name there could be created ahead of us, or swapped for a link, by
// someone else. On Windows the MSIX package also virtualizes writes under AppData, temp included,
// and install.bat elevates itself into a process outside the package that might not see them; the
// data folder sits in the user's home, which is not virtualized.
actual fun updateDownloadDir(): File = File(desktopDataPath("update"))

actual fun needsInstallPermission(): Boolean = false

actual fun openInstallPermissionSettings() {}

actual suspend fun installUpdate(file: File) {
    withContext(Dispatchers.IO) {
        when (os) {
            DesktopOs.LINUX -> installAppImage(file)
            DesktopOs.MAC -> installMacBundle(file)
            DesktopOs.WINDOWS -> runWindowsInstaller(file)
            DesktopOs.OTHER -> throw IllegalStateException("This system cannot install updates by itself.")
        }
    }
}

/** Replaces the running AppImage's file, then starts it again once this process has exited. */
private fun installAppImage(download: File) {
    val target = runningAppImage() ?: throw IllegalStateException("Musikku is not running from an AppImage.")
    val dir = target.absoluteFile.parentFile
    if (dir == null || !Files.isWritable(dir.toPath())) {
        throw IllegalStateException("Cannot write to ${dir ?: target}. Move the AppImage to a folder you own and try again.")
    }
    val staged = File(dir, "${target.name}.update")
    try {
        Files.copy(download.toPath(), staged.toPath(), StandardCopyOption.REPLACE_EXISTING)
        if (!staged.setExecutable(true, false)) {
            throw IllegalStateException("Cannot make ${staged.path} executable.")
        }
        // A running AppImage keeps its own open file, so replacing the name under it is safe.
        Files.move(staged.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) {
        staged.delete()
        throw if (e is IllegalStateException) e else IllegalStateException("Could not replace ${target.path}: ${e.message}", e)
    }
    Logger.i(TAG, "AppImage replaced, restarting")
    // Waits for this process to exit: until then it holds the single-instance lock, and the new
    // AppImage would quit as a second instance.
    val builder =
        ProcessBuilder(
            "sh",
            "-c",
            "while kill -0 \"\$1\" 2>/dev/null; do sleep 0.5; done; exec \"\$0\"",
            target.absolutePath,
            ProcessHandle.current().pid().toString(),
        )
            .redirectInput(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
    // The new AppImage sets these itself; inheriting them would point it at this one's mount.
    builder.environment().apply {
        remove("APPDIR")
        remove("APPIMAGE")
        remove("ARGV0")
        remove("OWD")
    }
    startOrFail(builder, "restart Musikku")
    exitProcess(0)
}

/** Unpacks the new bundle, then hands the swap to a script that waits for this process to exit. */
private fun installMacBundle(zip: File) {
    val oldBundle = runningMacBundle() ?: throw IllegalStateException("Musikku is not running from an app bundle.")
    val parent = oldBundle.absoluteFile.parentFile
    if (parent == null || !Files.isWritable(parent.toPath())) {
        throw IllegalStateException("Cannot write to ${parent ?: oldBundle}. Move Musikku to a folder you own and try again.")
    }
    val work = File(zip.parentFile, "unpacked")
    work.deleteRecursively()
    work.mkdirs()
    // ditto keeps the bundle's symlinks and signature intact; java.util.zip would not.
    runOrFail(ProcessBuilder("/usr/bin/ditto", "-x", "-k", zip.absolutePath, work.absolutePath), "unpack the update")
    val newBundle =
        work.listFiles()?.singleOrNull { it.isDirectory && it.name.endsWith(".app") }
            ?: throw IllegalStateException("The update does not contain an app bundle.")
    val script = File(zip.parentFile, "swap.sh")
    script.writeText(
        """
        #!/bin/sh
        PID="${'$'}1"; OLD="${'$'}2"; NEW="${'$'}3"
        while kill -0 "${'$'}PID" 2>/dev/null; do sleep 0.5; done
        rm -rf "${'$'}OLD.old"
        if mv "${'$'}OLD" "${'$'}OLD.old"; then
            if mv "${'$'}NEW" "${'$'}OLD"; then
                rm -rf "${'$'}OLD.old"
            else
                mv "${'$'}OLD.old" "${'$'}OLD"
            fi
        fi
        open "${'$'}OLD"
        """.trimIndent() + "\n",
    )
    val builder =
        ProcessBuilder(
            "/bin/sh",
            script.absolutePath,
            ProcessHandle.current().pid().toString(),
            oldBundle.absolutePath,
            newBundle.absolutePath,
        ).redirectInput(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
    startOrFail(builder, "start the update")
    Logger.i(TAG, "App bundle staged, quitting so it can be swapped")
    exitProcess(0)
}

/** Unpacks the installer bundle and starts install.bat, which closes and relaunches the app itself. */
private fun runWindowsInstaller(zip: File) {
    val target = File(zip.parentFile, "installer")
    target.deleteRecursively()
    target.mkdirs()
    val root = target.canonicalFile
    ZipInputStream(zip.inputStream().buffered()).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            val out = File(root, entry.name).canonicalFile
            if (out != root && !out.path.startsWith(root.path + File.separator)) {
                throw IllegalStateException("The update holds a file outside its folder: ${entry.name}")
            }
            if (entry.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                out.outputStream().use { input.copyTo(it) }
            }
        }
    }
    val bat =
        root.walkTopDown().firstOrNull { it.isFile && it.name.equals("install.bat", ignoreCase = true) }
            ?: throw IllegalStateException("The update does not contain install.bat.")
    Logger.i(TAG, "Starting the Windows installer")
    startOrFail(
        ProcessBuilder("cmd", "/c", "start", "\"\"", bat.absolutePath).directory(bat.parentFile),
        "start the installer",
    )
}

private fun startOrFail(
    builder: ProcessBuilder,
    what: String,
): Process =
    try {
        builder.start()
    } catch (e: Exception) {
        throw IllegalStateException("Could not $what: ${e.message}", e)
    }

private fun runOrFail(
    builder: ProcessBuilder,
    what: String,
) {
    val process = startOrFail(builder.redirectErrorStream(true), what)
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val code = process.waitFor()
    if (code != 0) {
        throw IllegalStateException("Could not $what (exit $code): ${output.trim().take(200)}")
    }
}

actual fun postUpstreamUpdateNotification(
    title: String,
    message: String,
    url: String,
): Boolean =
    // The native notification carries no click action, so it names the release and nothing more.
    try {
        getKoin().get<DesktopNotificationManager>().post(UPSTREAM_UPDATE_NOTIFICATION_ID, title, message)
        true
    } catch (e: Exception) {
        Logger.w(TAG, "Upstream update notification failed: ${e.message}")
        false
    }
