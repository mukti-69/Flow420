package io.github.aedev.flow.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import io.github.aedev.flow.network.AppProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Progress of an in-app update, from download through handing the APK to the installer. */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Downloading(
        val percent: Int,
    ) : UpdateDownloadState

    data object Verifying : UpdateDownloadState

    data object Installing : UpdateDownloadState

    data class Failed(
        val reason: UpdateFailure,
    ) : UpdateDownloadState
}

enum class UpdateFailure {
    DOWNLOAD,
    SIGNATURE_MISMATCH,
    INSTALLER_UNAVAILABLE,
}

/**
 * Downloads a release APK and hands it to the system package installer, so an update can be
 * applied without leaving the app.
 *
 * The APK is only offered to the installer when its signing certificate matches the installed
 * app. Android refuses a same-package update signed with a different key
 * (INSTALL_FAILED_UPDATE_INCOMPATIBLE), and that failure surfaces as an opaque "App not
 * installed" with no hint that the build came from a different publisher.
 */
object ApkUpdateInstaller {
    private const val TAG = "ApkUpdateInstaller"
    private const val MIME_APK = "application/vnd.android.package-archive"
    private const val UPDATE_DIR = "updates"
    private const val UPDATE_FILE = "tutube-update.apk"

    private val client: OkHttpClient
        get() = AppProxyManager.applyTo(OkHttpClient.Builder()).build()

    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        onState: (UpdateDownloadState) -> Unit,
    ) {
        suspend fun emit(state: UpdateDownloadState) = withContext(Dispatchers.Main.immediate) { onState(state) }

        emit(UpdateDownloadState.Downloading(0))

        val apk =
            try {
                withContext(Dispatchers.IO) {
                    download(context, downloadUrl) { percent ->
                        emit(UpdateDownloadState.Downloading(percent))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update download failed", e)
                emit(UpdateDownloadState.Failed(UpdateFailure.DOWNLOAD))
                return
            }

        emit(UpdateDownloadState.Verifying)
        val signedByUs = withContext(Dispatchers.IO) { isSignedByInstalledApp(context, apk) }
        if (!signedByUs) {
            Log.e(TAG, "Downloaded APK is signed by a different key than the installed app")
            apk.delete()
            emit(UpdateDownloadState.Failed(UpdateFailure.SIGNATURE_MISMATCH))
            return
        }

        emit(UpdateDownloadState.Installing)
        if (!launchInstaller(context, apk)) {
            emit(UpdateDownloadState.Failed(UpdateFailure.INSTALLER_UNAVAILABLE))
        }
    }

    private suspend fun download(
        context: Context,
        url: String,
        onProgress: suspend (Int) -> Unit,
    ): File {
        val dir = File(context.cacheDir, UPDATE_DIR)
        if (!dir.exists() && !dir.mkdirs()) error("Could not create ${dir.absolutePath}")
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, UPDATE_FILE)

        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Empty response body")
            val total = body.contentLength()

            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = ((copied * 100) / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }

        if (target.length() == 0L) error("Downloaded file is empty")
        return target
    }

    /** Compares raw certificate bytes; no digest is computed, so nothing crypto-related is reimplemented. */
    fun isSignedByInstalledApp(
        context: Context,
        apk: File,
    ): Boolean {
        val installed = signaturesOfInstalledPackage(context) ?: return false
        val candidate = signaturesOfArchive(context, apk.absolutePath) ?: return false
        return installed.any { installedSigner ->
            candidate.any { candidateSigner -> installedSigner.contentEquals(candidateSigner) }
        }
    }

    private fun signaturesOfInstalledPackage(context: Context): List<ByteArray>? =
        runCatching {
            signersOf(context.packageManager.getPackageInfo(context.packageName, signatureFlags()))
        }.getOrNull()

    private fun signaturesOfArchive(
        context: Context,
        apkPath: String,
    ): List<ByteArray>? =
        runCatching {
            val info =
                context.packageManager.getPackageArchiveInfo(apkPath, signatureFlags())
                    ?: error("Not a readable APK: $apkPath")
            signersOf(info)
        }.getOrNull()

    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun signersOf(info: PackageInfo): List<ByteArray>? {
        val signatures =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
        return signatures?.map { it.toByteArray() }
    }

    private fun launchInstaller(
        context: Context,
        apk: File,
    ): Boolean =
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updater", apk)
            val intent =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, MIME_APK)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch the package installer", e)
            false
        }
}
