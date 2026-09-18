package io.github.aedev.flow.utils

import android.os.Build
import android.util.Log
import io.github.aedev.flow.network.AppProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val version: String, // e.g., "v1.2.0"
    val changelog: String, // The release notes
    val downloadUrl: String, // Link to the .apk or the release page
    val isNewer: Boolean,
)

/**
 * Outcome of an update check. A plain null cannot tell "already current" apart from "the request
 * failed", which would report a network error to the user as "you're up to date".
 */
sealed interface UpdateCheckResult {
    data class Available(
        val info: UpdateInfo,
    ) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    data class Failed(
        val cause: Exception?,
    ) : UpdateCheckResult
}

internal data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

object UpdateManager {
    private const val TAG = "UpdateManager"

    private val client: OkHttpClient
        get() = AppProxyManager.applyTo(OkHttpClient.Builder()).build()

    // The fork's own repository. Pointing this at upstream (A-EDev/Flow) makes every client
    // download an APK signed with a different key, which Android then refuses to install as an
    // update.
    internal const val GITHUB_REPO = "mukti-69/Flow420"
    internal const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo? =
        when (val result = checkForUpdateResult(currentVersionName)) {
            is UpdateCheckResult.Available -> result.info
            else -> null
        }

    suspend fun checkForUpdateResult(currentVersionName: String): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url(API_URL)
                        .addHeader("Accept", "application/vnd.github.v3+json")
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext UpdateCheckResult.Failed(
                            IllegalStateException("GitHub returned HTTP ${response.code}"),
                        )
                    }

                    val json = JSONObject(response.body?.string() ?: "{}")

                    // 1. Get Remote Version
                    val remoteTag =
                        json
                            .optString("tag_name", "")
                            .removePrefix("v")
                            .split("-")
                            .first()
                    val currentTag = currentVersionName.removePrefix("v").split("-").first()

                    // 2. Get the APK matching this device, or fall back to the release page.
                    val assets = json.optJSONArray("assets")
                    val releaseAssets = mutableListOf<ReleaseAsset>()
                    if (assets != null && assets.length() > 0) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                releaseAssets +=
                                    ReleaseAsset(
                                        name = name,
                                        downloadUrl = asset.optString("browser_download_url"),
                                    )
                            }
                        }
                    }
                    val downloadUrl =
                        selectApkDownloadUrl(
                            assets = releaseAssets,
                            supportedAbis = Build.SUPPORTED_ABIS.asList(),
                        ) ?: json.optString("html_url")

                    // 3. Compare Versions
                    if (isNewer(remoteTag, currentTag)) {
                        UpdateCheckResult.Available(
                            UpdateInfo(
                                version = json.optString("tag_name"),
                                changelog = json.optString("body"),
                                downloadUrl = downloadUrl,
                                isNewer = true,
                            ),
                        )
                    } else {
                        UpdateCheckResult.UpToDate
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                UpdateCheckResult.Failed(e)
            }
        }

    /**
     * Compares two version strings (e.g., "1.2.0" vs "1.1.9").
     * Both strings should already have build-type suffixes stripped (done in checkForUpdate).
     */
    internal fun isNewer(
        remote: String,
        current: String,
    ): Boolean {
        val cleanRemote = remote.split("-").first()
        val cleanCurrent = current.split("-").first()
        val remoteParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }

        val length = maxOf(remoteParts.size, currentParts.size)

        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    internal fun selectApkDownloadUrl(
        assets: List<ReleaseAsset>,
        supportedAbis: List<String>,
    ): String? {
        val githubAssets =
            assets.filterNot {
                it.name.startsWith("flow-foss-", ignoreCase = true)
            }
        val splitAssets =
            githubAssets.filter {
                it.name.equals("flow-arm64-v8a.apk", ignoreCase = true) ||
                    it.name.equals("flow-armeabi-v7a.apk", ignoreCase = true)
            }

        if (splitAssets.isNotEmpty()) {
            val preferredNames =
                supportedAbis.mapNotNull { abi ->
                    when (abi) {
                        "arm64-v8a" -> "flow-arm64-v8a.apk"
                        "armeabi-v7a" -> "flow-armeabi-v7a.apk"
                        else -> null
                    }
                }
            return preferredNames.firstNotNullOfOrNull { preferredName ->
                splitAssets
                    .firstOrNull {
                        it.name.equals(preferredName, ignoreCase = true)
                    }?.downloadUrl
            }
        }

        return githubAssets
            .firstOrNull {
                it.name.equals("flow.apk", ignoreCase = true)
            }?.downloadUrl ?: githubAssets.firstOrNull()?.downloadUrl
    }
}
