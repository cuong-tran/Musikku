package com.maxrave.simpmusic.utils.update

import com.maxrave.logger.Logger
import com.maxrave.simpmusic.expect.installUpdate
import com.maxrave.simpmusic.expect.needsInstallPermission
import com.maxrave.simpmusic.expect.updateDownloadDir
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile

sealed interface AppUpdateState {
    data object Idle : AppUpdateState

    /** [total] <= 0 when the size is not known. */
    data class Downloading(
        val downloaded: Long,
        val total: Long,
    ) : AppUpdateState

    /** Downloaded, but the system must first let this app install packages. */
    data class NeedsPermission(
        val file: File,
    ) : AppUpdateState

    data class Installing(
        val file: File,
    ) : AppUpdateState

    data class Failed(
        val message: String,
    ) : AppUpdateState
}

/** Downloads a Musikku release asset and hands it to the platform installer. One update at a time. */
object AppUpdater {
    private const val TAG = "Update"
    private const val PROGRESS_INTERVAL_MS = 100L

    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO +
                CoroutineExceptionHandler { _, e -> Logger.e(TAG, "Update job failed: ${e.message}", e) },
        )

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val _release = MutableStateFlow<AppRelease?>(null)

    /**
     * The release being updated to, held while [state] is not Idle. The activity, and the view model
     * holding the offer, are rebuilt on rotation or a theme change while this keeps going, so the
     * dialog reads the release from here to come back.
     */
    val release: StateFlow<AppRelease?> = _release.asStateFlow()

    private val lock = ReentrantLock()

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    private var downloadJob: Job? = null
    private var installJob: Job? = null

    /** The connection of the running download, closed by [cancel] so a blocked read ends at once. */
    @Volatile
    private var connection: HttpURLConnection? = null

    /**
     * Downloads [release]'s asset, then installs it. Ignored while a download or install is already under way.
     * A complete earlier download of the same file is installed without fetching it again: Android
     * 11+ kills the app when the install permission is granted, which loses the state that held it.
     */
    fun start(release: AppRelease) {
        val asset = release.asset ?: return
        locked {
            val current = _state.value
            if (current is AppUpdateState.Downloading || current is AppUpdateState.Installing) return
            // A cancelled download may still be unwinding; it must be gone before this one clears the folder.
            val previous = downloadJob
            _release.value = release
            _state.value = AppUpdateState.Downloading(0, asset.size)
            downloadJob =
                scope.launch {
                    previous?.cancelAndJoin()
                    val file =
                        try {
                            finishedDownload(asset) ?: download(asset)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // A cancel closes the connection, which surfaces here as an I/O error.
                            currentCoroutineContext().ensureActive()
                            Logger.w(TAG, "Download of ${asset.name} failed: ${e.message}")
                            publishIfActive(AppUpdateState.Failed(e.message ?: e.toString()))
                            return@launch
                        }
                    proceed(file)
                }
        }
    }

    /** Called when the user comes back from the permission screen: installs once the permission is there. */
    fun continueAfterPermission() {
        val current = _state.value as? AppUpdateState.NeedsPermission ?: return
        if (needsInstallPermission()) {
            Logger.i(TAG, "Install permission still missing")
            return
        }
        launchInstall(current.file)
    }

    /** Opens the installer again for a file already downloaded, after the user backed out of it. */
    fun installAgain() {
        val current = _state.value as? AppUpdateState.Installing ?: return
        if (needsInstallPermission()) {
            _state.value = AppUpdateState.NeedsPermission(current.file)
            return
        }
        launchInstall(current.file)
    }

    /** Stops a running download and deletes what it wrote. */
    fun cancel() {
        val open =
            locked {
                val running = _state.value is AppUpdateState.Downloading
                if (running) {
                    Logger.i(TAG, "Download cancelled")
                    downloadJob?.cancel()
                }
                _state.value = AppUpdateState.Idle
                _release.value = null
                if (running) connection else null
            }
        // disconnect() can block behind a stalled read, so it runs off the caller's thread and lock.
        if (open != null) scope.launch { open.disconnect() }
    }

    fun reset() = cancel()

    /**
     * Sets [state] unless this download was cancelled. Checked under the same lock [cancel] takes, so a
     * progress update racing a cancel cannot put the dialog back into Downloading after it went Idle.
     */
    private suspend fun publishIfActive(state: AppUpdateState) {
        val self = currentCoroutineContext()[Job]
        locked {
            if (self?.isActive != false) _state.value = state
        }
    }

    private suspend fun proceed(file: File) {
        val self = currentCoroutineContext()[Job]
        locked {
            if (self?.isActive == false) return
            if (needsInstallPermission()) {
                Logger.i(TAG, "Downloaded ${file.name}, waiting for install permission")
                _state.value = AppUpdateState.NeedsPermission(file)
            } else {
                launchInstall(file)
            }
        }
    }

    private fun launchInstall(file: File) {
        locked {
            if (installJob?.isActive == true) return
            _state.value = AppUpdateState.Installing(file)
            installJob =
                scope.launch {
                    try {
                        Logger.i(TAG, "Installing ${file.name}")
                        installUpdate(file)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.w(TAG, "Install of ${file.name} failed: ${e.message}")
                        _state.value = AppUpdateState.Failed(e.message ?: e.toString())
                    }
                }
        }
    }

    /** The folder is emptied before each download; a link there could point that at anything. */
    private fun requireNotSymlink(dir: File) {
        if (Files.isSymbolicLink(dir.toPath())) throw IOException("${dir.path} is a symbolic link.")
    }

    /** The checked file name [asset] is saved under. */
    private fun fileNameOf(asset: ReleaseAsset): String {
        require(asset.downloadUrl.startsWith(MUSIKKU_RELEASE_DOWNLOAD_PREFIX)) {
            "The update is not hosted on Musikku's releases page."
        }
        val name = asset.name.substringAfterLast('/')
        require(name.isNotBlank() && '/' !in name && '\\' !in name && ".." !in name) {
            "The update has an invalid file name."
        }
        return name
    }

    /** [asset] as an earlier download left it, when that download finished; null otherwise. */
    private fun finishedDownload(asset: ReleaseAsset): File? {
        val name = fileNameOf(asset)
        val dir = updateDownloadDir()
        requireNotSymlink(dir)
        val file = File(dir, name)
        if (asset.size <= 0 || !file.isFile || file.length() != asset.size) return null
        Logger.i(TAG, "Reusing the finished download ${file.name}")
        return file
    }

    private suspend fun download(asset: ReleaseAsset): File {
        val name = fileNameOf(asset)
        val dir = updateDownloadDir()
        requireNotSymlink(dir)
        // Only cleared when actually downloading, so a finished download survives for finishedDownload.
        dir.deleteRecursively()
        if (!dir.mkdirs() && !dir.isDirectory) throw IOException("Cannot create ${dir.path}")
        val part = File(dir, "$name.part")
        val target = File(dir, name)
        Logger.i(TAG, "Downloading ${asset.name} (${asset.size} bytes)")
        val connection = URL(asset.downloadUrl).openConnection() as HttpURLConnection
        this.connection = connection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", UPDATE_USER_AGENT)
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("GitHub answered HTTP $code")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.size
            var downloaded = 0L
            var lastPublishAt = 0L
            var lastPublishedPercent = -1L
            publishIfActive(AppUpdateState.Downloading(0, total))
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        val percent = if (total > 0) downloaded * 100 / total else -1
                        if (now - lastPublishAt >= PROGRESS_INTERVAL_MS || (percent >= 0 && percent != lastPublishedPercent)) {
                            lastPublishAt = now
                            lastPublishedPercent = percent
                            publishIfActive(AppUpdateState.Downloading(downloaded, total))
                        }
                    }
                }
            }
            publishIfActive(AppUpdateState.Downloading(downloaded, total))
            if (asset.size > 0 && part.length() != asset.size) {
                throw IOException("The download is incomplete (${part.length()} of ${asset.size} bytes).")
            }
            if (!part.renameTo(target)) throw IOException("Cannot move the download into place.")
            Logger.i(TAG, "Downloaded ${target.name}")
            return target
        } catch (e: Throwable) {
            part.delete()
            throw e
        } finally {
            this.connection = null
            connection.disconnect()
        }
    }
}
