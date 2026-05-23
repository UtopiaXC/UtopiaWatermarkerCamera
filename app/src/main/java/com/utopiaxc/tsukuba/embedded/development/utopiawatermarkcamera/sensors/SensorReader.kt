package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

enum class DeviceOrientation {
    PORTRAIT,
    LANDSCAPE,
    FLAT
}

data class SensorData(
    val altitude: Float? = null,
    val pressure: Float? = null,  // Raw pressure in hPa
    val azimuth: Float? = null,   // 0-360 degrees
    val pitch: Float? = null,
    val roll: Float? = null,
    val gravityX: Float? = null,
    val gravityY: Float? = null,
    val gravityZ: Float? = null,
    val deviceOrientation: DeviceOrientation = DeviceOrientation.PORTRAIT
)

class SensorReader(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData.asStateFlow()

    private val _shakeEvent = MutableStateFlow(0L)
    val shakeEvent: StateFlow<Long> = _shakeEvent.asStateFlow()

    private var gravityValues = FloatArray(3)
    private var magneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false
    
    // 4-swing shake detection state
    // We track directional reversals on X-axis (left-right)
    // Need 4 reversals (left→right→left→right) within a time window
    private var lastSignificantX = 0f
    private var lastDirection = 0 // -1 = left, 1 = right, 0 = unknown
    private var swingCount = 0
    private var firstSwingTime = 0L
    private val SWING_THRESHOLD = 6f       // Minimum acceleration to count as significant movement
    private val SWING_TIME_WINDOW = 2500L  // Must complete 4 swings within 2.5 seconds
    private val SHAKE_COOLDOWN = 3000L     // Cooldown after a successful shake event

    fun start() {
        pressureSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        linearAcceleration?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        var currentData = _sensorData.value

        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                val pressure = event.values[0]
                val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)
                currentData = currentData.copy(altitude = altitude, pressure = pressure)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityValues, 0, event.values.size)
                hasGravity = true
                
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                
                val newOrientation = detectOrientation(gx, gy, gz, currentData.deviceOrientation)
                currentData = currentData.copy(
                    gravityX = gx,
                    gravityY = gy,
                    gravityZ = gz,
                    deviceOrientation = newOrientation
                )
                
                // Fallback shake detection using accelerometer if linear acc is missing
                if (linearAcceleration == null) {
                    // Subtract gravity approximation for shake detection
                    detectSwingPattern(gx)
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magneticValues, 0, event.values.size)
                hasMagnetic = true
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                // Use X-axis for left-right swing detection
                val x = event.values[0]
                detectSwingPattern(x)
            }
        }

        if (hasGravity && hasMagnetic) {
            val rotationMatrix = FloatArray(9)
            if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, magneticValues)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                
                var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f
                val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                
                currentData = currentData.copy(azimuth = azimuth, pitch = pitch, roll = roll)
            }
        }

        _sensorData.value = currentData
    }

    /**
     * Detect a 4-swing pattern: the phone must reverse direction 4 times
     * (e.g., left→right→left→right) within a time window.
     * This prevents accidental single-movement triggers.
     */
    private fun detectSwingPattern(xAcceleration: Float) {
        val now = System.currentTimeMillis()
        
        // Check cooldown
        if (now - _shakeEvent.value < SHAKE_COOLDOWN) return
        
        // Only consider significant movements
        if (abs(xAcceleration) < SWING_THRESHOLD) return
        
        val currentDirection = if (xAcceleration > 0) 1 else -1
        
        // Detect direction reversal
        if (lastDirection != 0 && currentDirection != lastDirection) {
            if (swingCount == 0) {
                firstSwingTime = now
            }
            
            // Check if we're still within the time window
            if (now - firstSwingTime < SWING_TIME_WINDOW) {
                swingCount++
                
                if (swingCount >= 4) {
                    // 4 swings detected! Trigger shake event
                    _shakeEvent.value = now
                    swingCount = 0
                    lastDirection = 0
                    return
                }
            } else {
                // Time window expired, start over with this as the first swing
                swingCount = 1
                firstSwingTime = now
            }
        }
        
        lastDirection = currentDirection
    }

    private fun detectOrientation(gx: Float, gy: Float, gz: Float, current: DeviceOrientation): DeviceOrientation {
        val ax = abs(gx)
        val ay = abs(gy)
        val az = abs(gz)
        
        // Hysteresis threshold: requires the new dominant axis to exceed the current dominant axis by 1.5 m/s^2
        val threshold = 1.5f

        return when (current) {
            DeviceOrientation.PORTRAIT -> {
                if (ax > ay + threshold && ax > az) DeviceOrientation.LANDSCAPE
                else if (az > ay + threshold && az > ax) DeviceOrientation.FLAT
                else DeviceOrientation.PORTRAIT
            }
            DeviceOrientation.LANDSCAPE -> {
                if (ay > ax + threshold && ay > az) DeviceOrientation.PORTRAIT
                else if (az > ax + threshold && az > ay) DeviceOrientation.FLAT
                else DeviceOrientation.LANDSCAPE
            }
            DeviceOrientation.FLAT -> {
                if (ay > az + threshold && ay > ax) DeviceOrientation.PORTRAIT
                else if (ax > az + threshold && ax > ay) DeviceOrientation.LANDSCAPE
                else DeviceOrientation.FLAT
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}