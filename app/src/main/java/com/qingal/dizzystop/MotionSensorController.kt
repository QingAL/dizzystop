package com.qingal.dizzystop

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.exp
import kotlin.math.sqrt

class MotionSensorController(
    context: Context,
    private val onAccelerationChanged: (screenX: Float, screenYUp: Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val preferences = MotionCueSettings.preferences(context)

    private val linearAcceleration =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val gravity = FloatArray(3)
    private var settings = MotionCueSettings.read(preferences)
    private var hasGravitySensorReading = false
    private var filteredX = 0f
    private var filteredY = 0f
    private var lastTimestamp = 0L
    private var suppressMotionUntilTimestamp = 0L
    private var registered = false

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, _ ->
            settings = MotionCueSettings.read(sharedPreferences)
        }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun start(): Boolean {
        if (registered) return true
        if (linearAcceleration == null && accelerometer == null) return false

        val motionSensor = linearAcceleration ?: accelerometer ?: return false
        sensorManager.registerListener(this, motionSensor, SensorManager.SENSOR_DELAY_GAME)

        if (gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
        } else if (linearAcceleration != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }

        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        registered = true
        return true
    }

    fun stop() {
        if (registered) {
            sensorManager.unregisterListener(this)
            registered = false
        }
        filteredX = 0f
        filteredY = 0f
        lastTimestamp = 0L
        suppressMotionUntilTimestamp = 0L
        onAccelerationChanged(0f, 0f)
    }

    fun release() {
        stop()
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                event.values.copyInto(gravity, endIndex = 3)
                hasGravitySensorReading = true
                return
            }

            Sensor.TYPE_GYROSCOPE -> {
                handleGyroscope(event)
                return
            }

            Sensor.TYPE_ACCELEROMETER -> {
                updateFallbackGravity(event.values)
                if (linearAcceleration != null) return
            }
        }

        val acceleration = FloatArray(3)
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            event.values.copyInto(acceleration, endIndex = 3)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            for (index in 0..2) {
                acceleration[index] = event.values[index] - gravity[index]
            }
        } else {
            return
        }

        val dtSeconds = calculateDeltaSeconds(event.timestamp)
        val (screenX, screenY) = if (event.timestamp < suppressMotionUntilTimestamp) {
            0f to 0f
        } else {
            projectToScreenAxes(acceleration)
        }

        val alpha = 1f - exp(-dtSeconds / settings.filterTimeSeconds)
        filteredX += alpha * (screenX - filteredX)
        filteredY += alpha * (screenY - filteredY)

        onAccelerationChanged(
            applyDeadZone(filteredX, settings.deadZone),
            applyDeadZone(filteredY, settings.deadZone)
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun handleGyroscope(event: SensorEvent) {
        val rotationSpeed = sqrt(
            event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2]
        )
        if (rotationSpeed >= settings.rotationThreshold) {
            suppressMotionUntilTimestamp = event.timestamp +
                (settings.rotationSettleSeconds * NANOS_PER_SECOND).toLong()
        }
    }

    private fun calculateDeltaSeconds(timestamp: Long): Float {
        val delta = if (lastTimestamp == 0L) {
            DEFAULT_SAMPLE_SECONDS
        } else {
            ((timestamp - lastTimestamp) / NANOS_PER_SECOND).coerceIn(0.001f, 0.1f)
        }
        lastTimestamp = timestamp
        return delta
    }

    private fun updateFallbackGravity(values: FloatArray) {
        if (hasGravitySensorReading) return

        if (gravity.all { it == 0f }) {
            values.copyInto(gravity, endIndex = 3)
            return
        }

        for (index in 0..2) {
            gravity[index] =
                FALLBACK_GRAVITY_ALPHA * gravity[index] +
                    (1f - FALLBACK_GRAVITY_ALPHA) * values[index]
        }
    }

    private fun projectToScreenAxes(acceleration: FloatArray): Pair<Float, Float> {
        val screenAcceleration = remapForDisplayRotation(acceleration)
        val screenGravity = remapForDisplayRotation(gravity)

        // Screen X is the lateral cue axis. The longitudinal axis is
        // perpendicular to both gravity and screen X, so Z contributes when
        // the phone is held upright.
        val longitudinalY = screenGravity[2]
        val longitudinalZ = -screenGravity[1]
        val axisLength = sqrt(
            longitudinalY * longitudinalY + longitudinalZ * longitudinalZ
        )
        val screenY = if (axisLength > MIN_AXIS_LENGTH) {
            screenAcceleration[1] * longitudinalY / axisLength +
                screenAcceleration[2] * longitudinalZ / axisLength
        } else {
            screenAcceleration[1]
        }
        return screenAcceleration[0] to screenY
    }

    private fun remapForDisplayRotation(values: FloatArray): FloatArray {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        @Suppress("DEPRECATION")
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> floatArrayOf(-y, x, z)
            Surface.ROTATION_180 -> floatArrayOf(-x, -y, z)
            Surface.ROTATION_270 -> floatArrayOf(y, -x, z)
            else -> floatArrayOf(x, y, z)
        }
    }

    private fun applyDeadZone(value: Float, deadZone: Float): Float = when {
        value > deadZone -> value - deadZone
        value < -deadZone -> value + deadZone
        else -> 0f
    }

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000f
        private const val DEFAULT_SAMPLE_SECONDS = 0.02f
        private const val FALLBACK_GRAVITY_ALPHA = 0.97f
        private const val MIN_AXIS_LENGTH = 0.5f
    }
}
