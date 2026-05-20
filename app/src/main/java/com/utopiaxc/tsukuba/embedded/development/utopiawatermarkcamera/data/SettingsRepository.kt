package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        // Watermark settings
        val ENABLE_WATERMARK = booleanPreferencesKey("enable_watermark")
        val SHOW_LOCATION = booleanPreferencesKey("show_location")
        val SHOW_ALTITUDE = booleanPreferencesKey("show_altitude")
        val SHOW_PRESSURE = booleanPreferencesKey("show_pressure")
        val SHOW_DIRECTION = booleanPreferencesKey("show_direction")
        val SHOW_DEVICE_INFO = booleanPreferencesKey("show_device_info")
        val SHOW_CAMERA_INFO = booleanPreferencesKey("show_camera_info")
        val WATERMARK_POSITION = intPreferencesKey("watermark_position") // 0: Bottom, 1: Top, 2: Left, 3: Right
        val WATERMARK_COLOR = intPreferencesKey("watermark_color") // 0: Light, 1: Dark

        // Camera settings
        val PHOTO_RATIO = intPreferencesKey("photo_ratio") // 0: 4:3, 1: 16:9, 2: 1:1, 3: Full
        val PHOTO_RESOLUTION = intPreferencesKey("photo_resolution") // 0: Highest, 1: High, 2: Medium
        val GRID_MODE = intPreferencesKey("grid_mode") // 0: Off, 1: Rule of Thirds, 2: Golden Ratio
        val HORIZON_LEVELER = booleanPreferencesKey("horizon_leveler")
        val SHAKE_TO_CAPTURE = booleanPreferencesKey("shake_to_capture")
        val FILTER_MODE = intPreferencesKey("filter_mode") // 0: None, 1: B&W, 2: Vintage, 3: Cool, 4: Vivid
    }

    // Watermark flows
    val enableWatermarkFlow: Flow<Boolean> = context.dataStore.data.map { it[ENABLE_WATERMARK] ?: true }
    val showLocationFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_LOCATION] ?: true }
    val showAltitudeFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_ALTITUDE] ?: true }
    val showPressureFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PRESSURE] ?: false }
    val showDirectionFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_DIRECTION] ?: true }
    val showDeviceInfoFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_DEVICE_INFO] ?: true }
    val showCameraInfoFlow: Flow<Boolean> = context.dataStore.data.map { it[SHOW_CAMERA_INFO] ?: true }
    val watermarkPositionFlow: Flow<Int> = context.dataStore.data.map { it[WATERMARK_POSITION] ?: 0 }
    val watermarkColorFlow: Flow<Int> = context.dataStore.data.map { it[WATERMARK_COLOR] ?: 0 }

    // Camera flows
    val photoRatioFlow: Flow<Int> = context.dataStore.data.map { it[PHOTO_RATIO] ?: 0 }
    val photoResolutionFlow: Flow<Int> = context.dataStore.data.map { it[PHOTO_RESOLUTION] ?: 0 }
    val gridModeFlow: Flow<Int> = context.dataStore.data.map { it[GRID_MODE] ?: 0 }
    val horizonLevelerFlow: Flow<Boolean> = context.dataStore.data.map { it[HORIZON_LEVELER] ?: false }
    val shakeToCaptureFlow: Flow<Boolean> = context.dataStore.data.map { it[SHAKE_TO_CAPTURE] ?: false }
    val filterModeFlow: Flow<Int> = context.dataStore.data.map { it[FILTER_MODE] ?: 0 }

    suspend fun updateBooleanSetting(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    suspend fun updateIntSetting(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }
}