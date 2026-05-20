package com.utopiaxc.tsukuba.embedded.development.utopiawatermarkcamera.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

data class SensorData(
    val altitude: Float? = null,
    val pressure: Float? = null,  // Raw pressure in hPa
    val azimuth: Float? = null,   // 0-360 degrees
    val pitch: Float? = null,
    val roll: Float? = null
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
    
    // Shake detection parameters
    private var acceleration = 10f
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var lastAcceleration = SensorManager.GRAVITY_EARTH

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
                
                // Backup shake detection using basic accelerometer if linear acc is missing
                detectShakeWithAccelerometer(event.values[0], event.values[1], event.values[2])
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magneticValues, 0, event.values.size)
                hasMagnetic = true
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                // Precise shake detection
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt((x*x + y*y + z*z).toDouble()).toFloat()
                if (magnitude > 12f) { // Shake threshold
                    val now = System.currentTimeMillis()
                    if (now - _shakeEvent.value > 2000) { // Cooldown
                        _shakeEvent.value = now
                    }
                }
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

    private fun detectShakeWithAccelerometer(x: Float, y: Float, z: Float) {
        if (linearAcceleration != null) return // Use linear if available
        lastAcceleration = currentAcceleration
        currentAcceleration = sqrt((x*x + y*y + z*z).toDouble()).toFloat()
        val delta = currentAcceleration - lastAcceleration
        acceleration = acceleration * 0.9f + delta
        if (acceleration > 12) {
            val now = System.currentTimeMillis()
            if (now - _shakeEvent.value > 2000) {
                _shakeEvent.value = now
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}