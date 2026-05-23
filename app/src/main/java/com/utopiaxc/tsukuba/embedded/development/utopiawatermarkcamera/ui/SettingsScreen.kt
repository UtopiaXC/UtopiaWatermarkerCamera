package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.R
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.data.SettingsRepository
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components.SettingsGroup
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components.SettingsNavigationItem
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components.SettingsSelectionDialog
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components.SettingsSwitchItem
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components.SettingsTextInputItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Watermark settings
    val enableWatermark by settingsRepository.enableWatermarkFlow.collectAsState(initial = true)
    val showLocation by settingsRepository.showLocationFlow.collectAsState(initial = true)
    val showAltitude by settingsRepository.showAltitudeFlow.collectAsState(initial = true)
    val showPressure by settingsRepository.showPressureFlow.collectAsState(initial = false)
    val showDirection by settingsRepository.showDirectionFlow.collectAsState(initial = true)
    val showDeviceInfo by settingsRepository.showDeviceInfoFlow.collectAsState(initial = true)
    val showCameraInfo by settingsRepository.showCameraInfoFlow.collectAsState(initial = true)
    val watermarkColor by settingsRepository.watermarkColorFlow.collectAsState(initial = 0)
    val authorName by settingsRepository.authorNameFlow.collectAsState(initial = "")
    
    // Camera settings
    val photoRatio by settingsRepository.photoRatioFlow.collectAsState(initial = 0)
    val photoResolution by settingsRepository.photoResolutionFlow.collectAsState(initial = 0)
    val gridMode by settingsRepository.gridModeFlow.collectAsState(initial = 0)
    val horizonLeveler by settingsRepository.horizonLevelerFlow.collectAsState(initial = false)
    val shakeToCapture by settingsRepository.shakeToCaptureFlow.collectAsState(initial = false)

    // Dialog states
    var showRatioDialog by remember { mutableStateOf(false) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var showGridDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val appLanguage by settingsRepository.appLanguageFlow.collectAsState(initial = 0)

    // Localized option lists
    val languageOptions = listOf(
        stringResource(R.string.lang_system),
        stringResource(R.string.lang_zh_cn),
        stringResource(R.string.lang_zh_tw),
        stringResource(R.string.lang_en),
        stringResource(R.string.lang_ja)
    )
    val ratioOptions = listOf(
        stringResource(R.string.ratio_4_3),
        stringResource(R.string.ratio_16_9),
        stringResource(R.string.ratio_1_1),
        stringResource(R.string.ratio_full)
    )
    val resolutionOptions = listOf(
        stringResource(R.string.resolution_highest),
        stringResource(R.string.resolution_high),
        stringResource(R.string.resolution_medium)
    )
    val gridOptions = listOf(
        stringResource(R.string.grid_off),
        stringResource(R.string.grid_thirds),
        stringResource(R.string.grid_golden)
    )
    val colorOptions = listOf(
        stringResource(R.string.color_light),
        stringResource(R.string.color_dark)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ══════════════════════════════════════
            // General Settings Group
            // ══════════════════════════════════════
            item {
                SettingsGroup(title = "General") {
                    SettingsNavigationItem(
                        icon = Icons.Filled.Language,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.app_language),
                        currentValue = languageOptions.getOrNull(appLanguage) ?: languageOptions[0],
                        onClick = { showLanguageDialog = true },
                        showDivider = false
                    )
                }
            }

            // ══════════════════════════════════════
            // Camera Settings Group
            // ══════════════════════════════════════
            item {
                SettingsGroup(title = stringResource(R.string.camera_settings)) {
                    SettingsNavigationItem(
                        icon = Icons.Filled.AspectRatio,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.photo_ratio),
                        currentValue = ratioOptions[photoRatio],
                        onClick = { showRatioDialog = true }
                    )
                    SettingsNavigationItem(
                        icon = Icons.Filled.HighQuality,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        title = stringResource(R.string.photo_resolution),
                        currentValue = resolutionOptions[photoResolution],
                        onClick = { showResolutionDialog = true }
                    )
                    SettingsNavigationItem(
                        icon = Icons.Filled.Grid4x4,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(R.string.grid_lines),
                        currentValue = gridOptions[gridMode],
                        onClick = { showGridDialog = true }
                    )
                    SettingsSwitchItem(
                        icon = Icons.Filled.Straighten,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        title = stringResource(R.string.horizon_leveler),
                        checked = horizonLeveler,
                        onCheckedChange = {
                            coroutineScope.launch {
                                settingsRepository.updateBooleanSetting(SettingsRepository.HORIZON_LEVELER, it)
                            }
                        }
                    )
                    SettingsSwitchItem(
                        icon = Icons.Filled.Vibration,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.shake_to_capture),
                        checked = shakeToCapture,
                        onCheckedChange = {
                            coroutineScope.launch {
                                settingsRepository.updateBooleanSetting(SettingsRepository.SHAKE_TO_CAPTURE, it)
                            }
                        },
                        showDivider = false
                    )
                }
            }

            // ══════════════════════════════════════
            // Watermark Settings Group
            // ══════════════════════════════════════
            item {
                SettingsGroup(title = stringResource(R.string.watermark_settings)) {
                    SettingsSwitchItem(
                        icon = Icons.AutoMirrored.Filled.BrandingWatermark,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.enable_watermark),
                        checked = enableWatermark,
                        onCheckedChange = {
                            coroutineScope.launch {
                                settingsRepository.updateBooleanSetting(SettingsRepository.ENABLE_WATERMARK, it)
                            }
                        },
                        showDivider = enableWatermark
                    )

                    if (enableWatermark) {
                        SettingsSwitchItem(
                            icon = Icons.Filled.LocationOn,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            title = stringResource(R.string.show_location),
                            subtitle = stringResource(R.string.show_location_desc),
                            checked = showLocation,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    settingsRepository.updateBooleanSetting(SettingsRepository.SHOW_LOCATION, it)
                                }
                            }
                        )
                        SettingsSwitchItem(
                            icon = Icons.Filled.Terrain,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = stringResource(R.string.show_altitude),
                            subtitle = stringResource(R.string.show_altitude_desc),
                            checked = showAltitude,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    settingsRepository.updateBooleanSetting(SettingsRepository.SHOW_ALTITUDE, it)
                                }
                            }
                        )
                        SettingsSwitchItem(
                            icon = Icons.Filled.Speed,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            title = stringResource(R.string.show_pressure),
                            subtitle = stringResource(R.string.show_pressure_desc),
                            checked = showPressure,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    settingsRepository.updateBooleanSetting(SettingsRepository.SHOW_PRESSURE, it)
                                }
                            }
                        )
                        SettingsSwitchItem(
                            icon = Icons.Filled.Explore,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = stringResource(R.string.show_direction),
                            subtitle = stringResource(R.string.show_direction_desc),
                            checked = showDirection,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    settingsRepository.updateBooleanSetting(SettingsRepository.SHOW_DIRECTION, it)
                                }
                            }
                        )
                        SettingsSwitchItem(
                            icon = Icons.Filled.PhoneAndroid,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = stringResource(R.string.show_device_info),
                            subtitle = stringResource(R.string.show_device_info_desc),
                            checked = showDeviceInfo,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    settingsRepository.updateBooleanSetting(SettingsRepository.SHOW_DEVICE_INFO, it)
                                }
                            }
                        )
                        SettingsSwitchItem(
                            icon = Icons.Filled.CameraAlt,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            title = stringResource(R.string.show_camera_info),
                            subtitle = stringResource(R.string.show_camera_info_desc),
                            checked = showCameraInfo,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    settingsRepository.updateBooleanSetting(SettingsRepository.SHOW_CAMERA_INFO, it)
                                }
                            }
                        )
                        SettingsNavigationItem(
                            icon = Icons.Filled.Palette,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = stringResource(R.string.watermark_color),
                            currentValue = colorOptions[watermarkColor],
                            onClick = { showColorDialog = true }
                        )
                        SettingsTextInputItem(
                            icon = Icons.Filled.Person,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = stringResource(R.string.author_name),
                            subtitle = stringResource(R.string.author_name_desc),
                            currentValue = authorName,
                            placeholder = stringResource(R.string.author_name_hint),
                            onValueChange = {
                                coroutineScope.launch {
                                    settingsRepository.updateStringSetting(SettingsRepository.AUTHOR_NAME, it)
                                }
                            },
                            showDivider = false
                        )
                    }
                }
            }

            // ══════════════════════════════════════
            // About Group
            // ══════════════════════════════════════
            item {
                SettingsGroup(title = stringResource(R.string.about)) {
                    SettingsNavigationItem(
                        icon = Icons.Filled.Info,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(R.string.version),
                        currentValue = "1.0",
                        onClick = { showAboutDialog = true },
                        showDivider = false
                    )
                }
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // ══════════════════════════════════════
    // Dialogs
    // ══════════════════════════════════════

    if (showRatioDialog) {
        SettingsSelectionDialog(
            title = stringResource(R.string.photo_ratio),
            options = ratioOptions,
            selectedIndex = photoRatio,
            onSelect = {
                coroutineScope.launch { settingsRepository.updateIntSetting(SettingsRepository.PHOTO_RATIO, it) }
            },
            onDismiss = { showRatioDialog = false }
        )
    }

    if (showResolutionDialog) {
        SettingsSelectionDialog(
            title = stringResource(R.string.photo_resolution),
            options = resolutionOptions,
            selectedIndex = photoResolution,
            onSelect = {
                coroutineScope.launch { settingsRepository.updateIntSetting(SettingsRepository.PHOTO_RESOLUTION, it) }
            },
            onDismiss = { showResolutionDialog = false }
        )
    }

    if (showGridDialog) {
        SettingsSelectionDialog(
            title = stringResource(R.string.grid_lines),
            options = gridOptions,
            selectedIndex = gridMode,
            onSelect = {
                coroutineScope.launch { settingsRepository.updateIntSetting(SettingsRepository.GRID_MODE, it) }
            },
            onDismiss = { showGridDialog = false }
        )
    }

    if (showColorDialog) {
        SettingsSelectionDialog(
            title = stringResource(R.string.watermark_color),
            options = colorOptions,
            selectedIndex = watermarkColor,
            onSelect = {
                coroutineScope.launch {
                    settingsRepository.updateIntSetting(SettingsRepository.WATERMARK_COLOR, it)
                }
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false }
        )
    }

    if (showLanguageDialog) {
        SettingsSelectionDialog(
            title = stringResource(R.string.app_language),
            options = languageOptions,
            selectedIndex = appLanguage,
            onSelect = {
                coroutineScope.launch {
                    settingsRepository.updateIntSetting(SettingsRepository.APP_LANGUAGE, it)
                }
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    // About dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.about_dialog_title),
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v1.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.about_developer),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.about_course),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/UtopiaXC/UtopiaWatermarkerCamera"))
                            context.startActivity(intent)
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            Icons.Filled.Code,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.about_github),
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}