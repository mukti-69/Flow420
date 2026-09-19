package io.github.aedev.flow.ui.screens.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.MarkdownChangelogText
import io.github.aedev.flow.utils.UpdateDownloadState
import io.github.aedev.flow.utils.UpdateFailure
import io.github.aedev.flow.utils.UpdateInfo

/**
 * Shown instead of the app when the owner's update policy declares the installed build too old to
 * keep using. Deliberately has no dismiss affordance: the only ways out are installing the update
 * or leaving the app. It carries no navigation, so it cannot be backed out of into the UI.
 */
@Composable
fun UpdateRequiredScreen(
    updateInfo: UpdateInfo,
    message: String?,
    downloadState: UpdateDownloadState,
    onUpdate: () -> Unit,
) {
    val busy = downloadState is UpdateDownloadState.Downloading

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(88.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_notification_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.update_required_title),
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message ?: stringResource(R.string.update_required_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.ui_version, updateInfo.version),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            val changelog = remember(updateInfo.changelog) { updateInfo.changelog }
            if (changelog.isNotBlank()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    MarkdownChangelogText(
                        markdown = changelog,
                        textColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            UpdateRequiredProgress(downloadState)

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onUpdate,
                enabled = !busy,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                shape = RoundedCornerShape(28.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.update_flow),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.update_required_how_to_update),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun UpdateRequiredProgress(state: UpdateDownloadState) {
    val label =
        when (state) {
            is UpdateDownloadState.Downloading -> {
                stringResource(R.string.update_downloading_percent, state.percent)
            }

            UpdateDownloadState.Verifying -> {
                stringResource(R.string.update_verifying_signature)
            }

            UpdateDownloadState.Installing -> {
                stringResource(R.string.update_opening_installer)
            }

            is UpdateDownloadState.Failed -> {
                when (state.reason) {
                    UpdateFailure.DOWNLOAD -> stringResource(R.string.update_failed_download)
                    UpdateFailure.SIGNATURE_MISMATCH -> stringResource(R.string.update_failed_signature)
                    UpdateFailure.INSTALLER_UNAVAILABLE -> stringResource(R.string.update_failed_installer)
                }
            }

            UpdateDownloadState.Idle -> {
                null
            }
        } ?: return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color =
                if (state is UpdateDownloadState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                },
        )
        if (state is UpdateDownloadState.Downloading) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
