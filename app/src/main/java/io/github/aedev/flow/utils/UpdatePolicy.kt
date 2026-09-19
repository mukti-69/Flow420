package io.github.aedev.flow.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Remote ceiling on how old an installed build may be before it is held at the update gate.
 *
 * The project owner edits `update-policy.json` on the default branch to raise
 * [minSupportedVersionCode]. Keeping this out of the APK is the only way to retire an old build
 * from the outside: any client that predates the gate still has to be fetched by a client that
 * already speaks it, so the policy has to be readable without shipping a release first.
 */
@Serializable
data class UpdatePolicy(
    @SerialName("min_supported_version_code") val minSupportedVersionCode: Int = 0,
    @SerialName("min_supported_version_name") val minSupportedVersionName: String = "",
    @SerialName("message") val message: String? = null,
) {
    fun requiresUpdate(installedVersionCode: Int): Boolean = installedVersionCode < minSupportedVersionCode

    /** Round-trips through [fromJson] so the last known policy can be cached for offline launches. */
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * A malformed policy yields null rather than a default, because callers fail open and a
         * bad edit to the JSON must not lock every user out of the app.
         */
        fun fromJson(raw: String): UpdatePolicy? =
            try {
                val policy = json.decodeFromString<UpdatePolicy>(raw)
                if (policy.minSupportedVersionCode < 0) {
                    null
                } else {
                    policy.copy(message = policy.message?.takeIf { it.isNotBlank() })
                }
            } catch (e: Exception) {
                null
            }
    }
}

/**
 * Whether to replace the app with the update gate.
 *
 * [updateAvailable] is required for blocking: if the policy demands a version that has not been
 * published yet there is nothing to install, and blocking would strand the user on a screen with
 * no way forward. A policy raised ahead of its release therefore fails open until the release
 * exists, which also keeps a typo in `update-policy.json` from bricking every install.
 */
internal fun shouldBlockLaunch(
    policy: UpdatePolicy?,
    installedVersionCode: Int,
    updateAvailable: Boolean,
): Boolean = policy != null && updateAvailable && policy.requiresUpdate(installedVersionCode)
