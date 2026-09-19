package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdatePolicyTest {
    @Test
    fun fromJson_validPolicy_parsesAllFields() {
        val policy =
            UpdatePolicy.fromJson(
                """{"min_supported_version_code": 20201, "min_supported_version_name": "2.2.1", "message": "Please update"}""",
            )

        assertThat(policy).isNotNull()
        assertThat(policy!!.minSupportedVersionCode).isEqualTo(20201)
        assertThat(policy.minSupportedVersionName).isEqualTo("2.2.1")
        assertThat(policy.message).isEqualTo("Please update")
    }

    @Test
    fun fromJson_missingMessage_yieldsNullMessage() {
        val policy = UpdatePolicy.fromJson("""{"min_supported_version_code": 20201, "min_supported_version_name": "2.2.1"}""")

        assertThat(policy).isNotNull()
        assertThat(policy!!.message).isNull()
    }

    @Test
    fun fromJson_blankMessage_yieldsNullMessage() {
        val policy =
            UpdatePolicy.fromJson(
                """{"min_supported_version_code": 0, "min_supported_version_name": "2.2.1", "message": "   "}""",
            )

        assertThat(policy!!.message).isNull()
    }

    @Test
    fun fromJson_missingVersionCode_defaultsToZeroAndDoesNotBlock() {
        val policy = UpdatePolicy.fromJson("""{"min_supported_version_name": "2.2.1"}""")

        assertThat(policy).isNotNull()
        assertThat(policy!!.minSupportedVersionCode).isEqualTo(0)
        assertThat(policy.requiresUpdate(installedVersionCode = 18)).isFalse()
    }

    // A typo in update-policy.json must not be able to lock every install out of the app.
    @Test
    fun fromJson_malformedJson_returnsNull() {
        assertThat(UpdatePolicy.fromJson("not json at all")).isNull()
        assertThat(UpdatePolicy.fromJson("{")).isNull()
    }

    @Test
    fun fromJson_negativeVersionCode_returnsNull() {
        assertThat(UpdatePolicy.fromJson("""{"min_supported_version_code": -5}""")).isNull()
    }

    @Test
    fun requiresUpdate_olderThanFloor_isTrue() {
        val policy = UpdatePolicy(minSupportedVersionCode = 20202, minSupportedVersionName = "2.2.2", message = null)

        assertThat(policy.requiresUpdate(installedVersionCode = 20201)).isTrue()
    }

    @Test
    fun requiresUpdate_equalToFloor_isFalse() {
        val policy = UpdatePolicy(minSupportedVersionCode = 20202, minSupportedVersionName = "2.2.2", message = null)

        assertThat(policy.requiresUpdate(installedVersionCode = 20202)).isFalse()
    }

    @Test
    fun toJson_roundTripsThroughFromJson() {
        val original = UpdatePolicy(minSupportedVersionCode = 20202, minSupportedVersionName = "2.2.2", message = "Update now")

        val restored = UpdatePolicy.fromJson(original.toJson())

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun toJson_roundTripsNullMessage() {
        val original = UpdatePolicy(minSupportedVersionCode = 0, minSupportedVersionName = "2.2.1", message = null)

        assertThat(UpdatePolicy.fromJson(original.toJson())).isEqualTo(original)
    }

    @Test
    fun shouldBlockLaunch_noPolicy_doesNotBlock() {
        assertThat(
            shouldBlockLaunch(policy = null, installedVersionCode = 18, updateAvailable = true),
        ).isFalse()
    }

    @Test
    fun shouldBlockLaunch_belowFloorWithUpdateAvailable_blocks() {
        val policy = UpdatePolicy(minSupportedVersionCode = 20202, minSupportedVersionName = "2.2.2", message = null)

        assertThat(
            shouldBlockLaunch(policy = policy, installedVersionCode = 20201, updateAvailable = true),
        ).isTrue()
    }

    @Test
    fun shouldBlockLaunch_belowFloorButNothingToInstall_doesNotBlock() {
        val policy = UpdatePolicy(minSupportedVersionCode = 20202, minSupportedVersionName = "2.2.2", message = null)

        // The floor was raised before the release was published. Blocking here would strand the
        // user on a screen with nothing to install, so the gate has to fail open.
        assertThat(
            shouldBlockLaunch(policy = policy, installedVersionCode = 20201, updateAvailable = false),
        ).isFalse()
    }

    @Test
    fun shouldBlockLaunch_atOrAboveFloor_doesNotBlock() {
        val policy = UpdatePolicy(minSupportedVersionCode = 20202, minSupportedVersionName = "2.2.2", message = null)

        assertThat(
            shouldBlockLaunch(policy = policy, installedVersionCode = 20202, updateAvailable = true),
        ).isFalse()
    }

    @Test
    fun shouldBlockLaunch_defaultPolicy_doesNotBlockCurrentRelease() {
        // Guards the shipped default: raising the floor ships in a later release, so the file that
        // goes out with a build must never gate that same build.
        val shippedDefault = UpdatePolicy(minSupportedVersionCode = 0, minSupportedVersionName = "2.2.1", message = null)

        assertThat(
            shouldBlockLaunch(policy = shippedDefault, installedVersionCode = 20202, updateAvailable = true),
        ).isFalse()
    }

    @Test
    fun cachedUpdateInfo_sameInstalledVersion_isForThatVersion() {
        val cached = CachedUpdateInfo(installedVersionName = "2.2.1", info = sampleInfo())

        assertThat(cached.isFor("2.2.1")).isTrue()
    }

    // After the user installs the update the app runs a new version, and the cached entry was
    // recorded against the old one. Replaying it would send them back to install the build they
    // are already on -- a gate with no exit.
    @Test
    fun cachedUpdateInfo_differentInstalledVersion_isNotForThatVersion() {
        val cached = CachedUpdateInfo(installedVersionName = "2.2.1", info = sampleInfo())

        assertThat(cached.isFor("2.2.2")).isFalse()
    }

    @Test
    fun cachedUpdateInfo_roundTripsThroughJson() {
        val cached = CachedUpdateInfo(installedVersionName = "2.2.1", info = sampleInfo())

        assertThat(CachedUpdateInfo.fromJson(cached.toJson())).isEqualTo(cached)
    }

    @Test
    fun cachedUpdateInfo_malformedJson_returnsNull() {
        assertThat(CachedUpdateInfo.fromJson("nonsense")).isNull()
    }

    private fun sampleInfo() =
        UpdateInfo(
            version = "2.2.2",
            changelog = "Fixes and branding",
            downloadUrl = "https://example.test/arm64",
            isNewer = true,
        )
}
