package io.github.aedev.flow.utils

import android.os.Build
import android.util.Log
import io.github.aedev.flow.network.AppProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Serializable
data class UpdateInfo(
    @SerialName("version") val version: String, // e.g., "v1.2.0"
    @SerialName("changelog") val changelog: String, // The release notes
    @SerialName("download_url") val downloadUrl: String, // Link to the .apk or the release page
    @SerialName("is_newer") val isNewer: Boolean,
) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Cached alongside the policy so the gate survives a cold start with no network. */
        fun fromJson(raw: String): UpdateInfo? =
            try {
                json.decodeFromString<UpdateInfo>(raw)
            } catch (e: Exception) {
                null
            }
    }
}

/**
 * An [UpdateInfo] paired with the build it was detected on.
 *
 * [UpdateInfo.isNewer] is decided once, against whichever version was installed at check time. A
 * cached copy therefore stops being true the moment the user installs it, and replaying it blindly
 * would tell an already-updated app to install the version it is running — a gate with no way out.
 */
@Serializable
data class CachedUpdateInfo(
    @SerialName("installed_version_name") val installedVersionName: String,
    @SerialName("info") val info: UpdateInfo,
) {
    fun isFor(versionName: String): Boolean = installedVersionName == versionName

    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(raw: String): CachedUpdateInfo? =
            try {
                json.decodeFromString<CachedUpdateInfo>(raw)
            } catch (e: Exception) {
                null
            }
    }
}

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

    // Read from the default branch rather than a release asset so the owner can change the
    // supported-version floor by editing one file, with no app release in between.
    internal const val POLICY_URL = "https://raw.githubusercontent.com/$GITHUB_REPO/main/update-policy.json"

    /**
     * Fetches the owner-controlled update policy. Returns null on any failure so the caller can
     * fall back to the last cached policy or to not blocking at all: a network problem must never
     * be what locks a user out.
     */
    suspend fun fetchPolicy(): UpdatePolicy? =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url(POLICY_URL)
                        .addHeader("Accept", "application/json")
                        .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Policy fetch returned HTTP ${response.code}")
                        return@withContext null
                    }
                    UpdatePolicy.fromJson(response.body.string())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Policy fetch failed", e)
                null
            }
        }

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
