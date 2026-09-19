package io.github.aedev.flow.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDonations: () -> Unit,
) {
    val context = LocalContext.current
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showDeviceInfoDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    // version info
    val packageInfo =
        remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }
        }
    val versionName = packageInfo?.versionName ?: context.getString(R.string.unknown)
    val versionCode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode?.toString() ?: "0"
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toString() ?: "0"
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.about_title),
                onBack = onNavigateBack,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_notification_logo),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 3.sp,
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.v_version_template, versionName, versionCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { HorizontalDivider() }

            item { AboutSectionLabel(stringResource(R.string.section_app)) }
            item {
                AboutRow(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.about_changelog),
                    subtitle = stringResource(R.string.whats_new_in_flow),
                    onClick = { showChangelogDialog = true },
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRow(
                    icon = Icons.Outlined.VolunteerActivism,
                    title = stringResource(R.string.donate_item_title),
                    subtitle = stringResource(R.string.support_dev_subtitle),
                    onClick = onNavigateToDonations,
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

            item { AboutSectionLabel(stringResource(R.string.section_contact)) }
            item {
                AboutRow(
                    icon = Icons.Outlined.Public,
                    title = stringResource(R.string.about_website),
                    subtitle = "github.com/mukti-69/Flow420",
                    onClick = { openUrl(context, "https://github.com/mukti-69/Flow420") },
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRowWithPainter(
                    iconPainter = painterResource(id = R.drawable.ic_github),
                    title = stringResource(R.string.github_label),
                    subtitle = stringResource(R.string.github_subtitle),
                    onClick = { openUrl(context, "https://github.com/mukti-69/Flow420") },
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRow(
                    icon = Icons.Outlined.Person,
                    title = stringResource(R.string.about_creator),
                    subtitle = "mukti-69",
                    onClick = { openUrl(context, "https://github.com/mukti-69") },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

            item { AboutSectionLabel(stringResource(R.string.section_legal)) }
            item {
                AboutRow(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.about_license),
                    subtitle = "GNU GPL v3",
                    onClick = { showLicenseDialog = true },
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRow(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.newpipe_extractor_title),
                    subtitle = stringResource(R.string.newpipe_extractor_subtitle),
                    onClick = { openUrl(context, "https://github.com/TeamNewPipe/NewPipeExtractor") },
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

            item { AboutSectionLabel(stringResource(R.string.section_device)) }
            item {
                AboutRow(
                    icon = Icons.Outlined.Smartphone,
                    title = stringResource(R.string.about_device_info),
                    subtitle = "${Build.MANUFACTURER} ${Build.MODEL}",
                    onClick = { showDeviceInfoDialog = true },
                )
            }
        }
    }

    if (showLicenseDialog) LicenseDialog(onDismiss = { showLicenseDialog = false })
    if (showDeviceInfoDialog) DeviceInfoDialog(onDismiss = { showDeviceInfoDialog = false })
    if (showChangelogDialog) ChangelogDialog(onDismiss = { showChangelogDialog = false })
}

@Composable
private fun AboutSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutRowWithPainter(
    iconPainter: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
fun LicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var licenseText by remember { mutableStateOf(context.getString(R.string.loading_ellipsis)) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                context.assets.open("license.txt").bufferedReader().use {
                    licenseText = it.readText()
                }
            } catch (e: Exception) {
                licenseText = context.getString(R.string.error_license_load)
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gnu_license_full_title)) },
        text = {
            Box(Modifier.heightIn(max = 400.dp)) {
                LazyColumn {
                    item {
                        Text(
                            text = licenseText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_ok)) }
        },
    )
}

@Composable
fun DeviceInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val deviceInfo =
        remember {
            buildString {
                append(context.getString(R.string.manufacturer_label, Build.MANUFACTURER) + "\n")
                append(context.getString(R.string.model_label, Build.MODEL) + "\n")
                append(context.getString(R.string.board_label, Build.BOARD) + "\n")
                append(context.getString(R.string.arch_label, Build.SUPPORTED_ABIS.joinToString(", ")) + "\n")
                append(context.getString(R.string.android_sdk_label, Build.VERSION.SDK_INT.toString()) + "\n")
                append(context.getString(R.string.os_label, Build.VERSION.RELEASE) + "\n")
                append(
                    context.getString(
                        R.string.density_label,
                        android.content.res.Resources
                            .getSystem()
                            .displayMetrics.density
                            .toString(),
                    ) +
                        "\n",
                )
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_device_info)) },
        text = {
            Text(
                text = deviceInfo,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_ok)) }
        },
        dismissButton = {
            TextButton(onClick = {
                clipboardManager.setText(AnnotatedString(deviceInfo))
            }) { Text(stringResource(R.string.btn_copy)) }
        },
    )
}

@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var changelogText by remember { mutableStateOf(context.getString(R.string.loading_ellipsis)) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val assetManager = context.assets
                val files = assetManager.list("changelog") ?: emptyArray()
                val latestFile =
                    files
                        .filter { it.endsWith(".txt") }
                        .sortedWith(compareByDescending { it })
                        .firstOrNull()

                if (latestFile != null) {
                    assetManager.open("changelog/$latestFile").bufferedReader().use {
                        changelogText = it.readText()
                    }
                } else {
                    changelogText = context.getString(R.string.no_changelog_found_message)
                }
            } catch (e: Exception) {
                changelogText = context.getString(R.string.error_changelog_load)
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_changelog)) },
        text = {
            Box(Modifier.heightIn(max = 400.dp)) {
                LazyColumn {
                    item {
                        Text(
                            text = changelogText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_ok)) }
        },
    )
}

private fun openUrl(
    context: Context,
    url: String,
) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
