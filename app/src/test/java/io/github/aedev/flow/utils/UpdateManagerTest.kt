package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateManagerTest {
    private val splitAssets =
        listOf(
            ReleaseAsset("flow-armeabi-v7a.apk", "https://example.test/armv7"),
            ReleaseAsset("flow-foss-arm64-v8a.apk", "https://example.test/foss-arm64"),
            ReleaseAsset("flow-arm64-v8a.apk", "https://example.test/arm64"),
        )

    @Test
    fun selectApkDownloadUrl_arm64Device_selectsArm64GithubApk() {
        val url =
            UpdateManager.selectApkDownloadUrl(
                assets = splitAssets,
                supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
            )

        assertThat(url).isEqualTo("https://example.test/arm64")
    }

    @Test
    fun selectApkDownloadUrl_arm32Device_selectsArmv7GithubApk() {
        val url =
            UpdateManager.selectApkDownloadUrl(
                assets = splitAssets,
                supportedAbis = listOf("armeabi-v7a", "armeabi"),
            )

        assertThat(url).isEqualTo("https://example.test/armv7")
    }

    @Test
    fun selectApkDownloadUrl_unpublishedAbi_returnsNull() {
        val url =
            UpdateManager.selectApkDownloadUrl(
                assets = splitAssets,
                supportedAbis = listOf("x86_64", "x86"),
            )

        assertThat(url).isNull()
    }

    @Test
    fun selectApkDownloadUrl_legacyRelease_prefersGithubApk() {
        val url =
            UpdateManager.selectApkDownloadUrl(
                assets =
                    listOf(
                        ReleaseAsset("flow-foss.apk", "https://example.test/foss"),
                        ReleaseAsset("flow.apk", "https://example.test/github"),
                    ),
                supportedAbis = listOf("arm64-v8a"),
            )

        assertThat(url).isEqualTo("https://example.test/github")
    }

    /**
     * The updater must never consult upstream. Upstream publishes the same applicationId
     * (io.github.aedev.flow) signed with a different key, so downloading its APK makes Android
     * reject the update with INSTALL_FAILED_UPDATE_INCOMPATIBLE on every existing install.
     */
    @Test
    fun updateEndpoint_pointsAtThisFork() {
        assertThat(UpdateManager.GITHUB_REPO).isEqualTo("mukti-69/Flow420")
        assertThat(UpdateManager.API_URL)
            .isEqualTo("https://api.github.com/repos/mukti-69/Flow420/releases/latest")
        assertThat(UpdateManager.API_URL).doesNotContain("A-EDev")
    }

    @Test
    fun isNewer_detectsHigherVersions() {
        assertThat(UpdateManager.isNewer("2.2.2", "2.2.1")).isTrue()
        assertThat(UpdateManager.isNewer("2.3.0", "2.2.9")).isTrue()
        assertThat(UpdateManager.isNewer("3.0", "2.9.9")).isTrue()
        assertThat(UpdateManager.isNewer("2.2.10", "2.2.9")).isTrue()
    }

    @Test
    fun isNewer_rejectsSameOrOlderVersions() {
        assertThat(UpdateManager.isNewer("2.2.1", "2.2.1")).isFalse()
        assertThat(UpdateManager.isNewer("2.2.0", "2.2.1")).isFalse()
        assertThat(UpdateManager.isNewer("1.9.9", "2.0.0")).isFalse()
        assertThat(UpdateManager.isNewer("2.2.9", "2.2.10")).isFalse()
    }

    @Test
    fun isNewer_ignoresBuildTypeSuffixes() {
        assertThat(UpdateManager.isNewer("2.2.2-nightly", "2.2.1")).isTrue()
        assertThat(UpdateManager.isNewer("2.2.1-nightly", "2.2.1")).isFalse()
    }
}
