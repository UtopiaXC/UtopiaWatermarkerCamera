package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.R
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.data.SettingsRepository
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components.SettingsGroup
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components.SettingsNavigationItem
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components.SettingsSelectionDialog
import com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.ui.components.SettingsSwitchItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Watermark settings
    val enableWatermark by settingsRepository.enableWatermarkFlow.collectAsState(initial = true)
    val showLocation by settingsRepository.showLocationFlow.collectAsState(initial = true)
    val showAltitude by settingsRepository.showAltitudeFlow.collectAsState(initial = true)
    val showPressure by settingsRepository.showPressureFlow.collectAsState(initial = false)
    val showDirection by settingsRepository.showDirectionFlow.collectAsState(initial = true)
    val showDeviceInfo by settingsRepository.showDeviceInfoFlow.collectAsState(initial = true)
    val showCameraInfo by settingsRepository.showCameraInfoFlow.collectAsState(initial = true)
    val watermarkPosition by settingsRepository.watermarkPositionFlow.collectAsState(initial = 0)
    val watermarkColor by settingsRepository.watermarkColorFlow.collectAsState(initial = 0)
    
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
    var showPositionDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    // Localized option lists
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
    val positionOptions = listOf(
        stringResource(R.string.position_bottom),
        stringResource(R.string.position_top),
        stringResource(R.string.position_left),
        stringResource(R.string.position_right)
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
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
            // Camera Settings Group
            // ══════════════════════════════════════
            item {
                SettingsGroup(title = stringResource(R.string.camera_settings)) {
                    SettingsNavigationItem(
                        icon = Icons.Filled.AspectRatio,
                        iconTint = Color(0xFF5C6BC0),
                        title = stringResource(R.string.photo_ratio),
                        currentValue = ratioOptions[photoRatio],
                        onClick = { showRatioDialog = true }
                    )
                    SettingsNavigationItem(
                        icon = Icons.Filled.HighQuality,
                        iconTint = Color(0xFF26A69A),
                        title = stringResource(R.string.photo_resolution),
                        currentValue = resolutionOptions[photoResolution],
                        onClick = { showResolutionDialog = true }
                    )
                    SettingsNavigationItem(
                        icon = Icons.Filled.Grid4x4,
                        iconTint = Color(0xFF78909C),
                        title = stringResource(R.string.grid_lines),
                        currentValue = gridOptions[gridMode],
                        onClick = { showGridDialog = true }
                    )
                    SettingsSwitchItem(
                        icon = Icons.Filled.Straighten,
                        iconTint = Color(0xFF8D6E63),
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
                        iconTint = Color(0xFFEF5350),
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
                        iconTint = Color(0xFF42A5F5),
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
                            iconTint = Color(0xFF66BB6A),
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
                            iconTint = Color(0xFF8D6E63),
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
                            iconTint = Color(0xFF7E57C2),
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
                            iconTint = Color(0xFFFF7043),
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
                            iconTint = Color(0xFF26C6DA),
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
                            iconTint = Color(0xFFAB47BC),
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
                            icon = Icons.Filled.VerticalAlignBottom,
                            iconTint = Color(0xFF5C6BC0),
                            title = stringResource(R.string.watermark_position),
                            currentValue = positionOptions[watermarkPosition],
                            onClick = { showPositionDialog = true }
                        )
                        SettingsNavigationItem(
                            icon = Icons.Filled.Palette,
                            iconTint = Color(0xFF78909C),
                            title = stringResource(R.string.watermark_color),
                            currentValue = colorOptions[watermarkColor],
                            onClick = { showColorDialog = true },
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
                        iconTint = Color(0xFF78909C),
                        title = stringResource(R.string.version),
                        currentValue = "1.0",
                        onClick = { },
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

    if (showPositionDialog) {
        SettingsSelectionDialog(
            title = stringResource(R.string.watermark_position),
            options = positionOptions,
            selectedIndex = watermarkPosition,
            onSelect = {
                coroutineScope.launch { settingsRepository.updateIntSetting(SettingsRepository.WATERMARK_POSITION, it) }
            },
            onDismiss = { showPositionDialog = false }
        )
    }

    if (showColorDialog) {
        SettingsSelectionDialog(
            title = stringResource(R.string.watermark_color),
            options = colorOptions,
            selectedIndex = watermarkColor,
            onSelect = {
                coroutineScope.launch { settingsRepository.updateIntSetting(SettingsRepository.WATERMARK_COLOR, it) }
            },
            onDismiss = { showColorDialog = false }
        )
    }
}