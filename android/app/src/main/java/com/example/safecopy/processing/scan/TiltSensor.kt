package com.example.safecopy.processing.scan

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.safecopy.domain.scan.LowPassFilter
import com.example.safecopy.domain.scan.Tilt
import com.example.safecopy.domain.scan.TiltReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/** Индикатор наклона по акселерометру (SensorManager, TYPE_ACCELEROMETER), как требует ТЗ. */
class TiltSensor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isAvailable: Boolean get() = accelerometer != null

    /** Поток показаний, пока на него есть подписчик. Без акселерометра поток пустой. */
    fun readings(): Flow<TiltReading> = callbackFlow {
        val sensor = accelerometer
        if (sensor == null || sensorManager == null) {
            close()
            return@callbackFlow
        }
        val filter = LowPassFilter()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = filter.apply(event.values[0], event.values[1], event.values[2])
                trySend(Tilt.fromGravity(x, y, z))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
