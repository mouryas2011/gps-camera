package com.geostamp.camera.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SensorSnapshot(
    val compassDegrees: Int = 45,
    val compassDirection: String = "NE",
    val pitchDegrees: Int = 0,
    val rollDegrees: Int = 0,
    val magneticFieldMicroTesla: Float = 38.2f,
    val pressureHpa: Float = 1012.0f
)

class SensorHelper(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val _snapshot = MutableStateFlow(SensorSnapshot())
    val snapshot: StateFlow<SensorSnapshot> = _snapshot.asStateFlow()

    private val gravityValues = FloatArray(3)
    private val magneticValues = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false

    fun start() {
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
        if (magnetometer != null && rotationSensor != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        }
        pressureSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val azimuthRad = orientation[0]
                var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                if (azimuthDeg < 0) azimuthDeg += 360f
                val pitch = Math.toDegrees(orientation[1].toDouble()).roundToInt()
                val roll = Math.toDegrees(orientation[2].toDouble()).roundToInt()
                updateCompass(azimuthDeg.roundToInt(), pitch, roll)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityValues, 0, 3)
                hasGravity = true
                if (hasGravity && hasMagnetic) {
                    calculateOrientationFromGravityAndMagnetic()
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magneticValues, 0, 3)
                hasMagnetic = true
                val totalMagnitude = sqrt(
                    (event.values[0] * event.values[0] +
                     event.values[1] * event.values[1] +
                     event.values[2] * event.values[2]).toDouble()
                ).toFloat()
                _snapshot.value = _snapshot.value.copy(magneticFieldMicroTesla = totalMagnitude)

                if (hasGravity && hasMagnetic && rotationSensor == null) {
                    calculateOrientationFromGravityAndMagnetic()
                }
            }
            Sensor.TYPE_PRESSURE -> {
                _snapshot.value = _snapshot.value.copy(pressureHpa = event.values[0])
            }
        }
    }

    private fun calculateOrientationFromGravityAndMagnetic() {
        val r = FloatArray(9)
        val i = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, i, gravityValues, magneticValues)) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(r, orientation)
            var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuthDeg < 0) azimuthDeg += 360f
            val pitch = Math.toDegrees(orientation[1].toDouble()).roundToInt()
            val roll = Math.toDegrees(orientation[2].toDouble()).roundToInt()
            updateCompass(azimuthDeg.roundToInt(), pitch, roll)
        }
    }

    private fun updateCompass(degrees: Int, pitch: Int = 0, roll: Int = 0) {
        val normalized = (degrees % 360 + 360) % 360
        val dir = getCardinalDirection(normalized)
        _snapshot.value = _snapshot.value.copy(
            compassDegrees = normalized,
            compassDirection = dir,
            pitchDegrees = pitch,
            rollDegrees = roll
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        fun getCardinalDirection(degrees: Int): String {
            val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
            val index = ((degrees + 11.25) / 22.5).toInt() % 16
            return directions[index]
        }
    }
}
