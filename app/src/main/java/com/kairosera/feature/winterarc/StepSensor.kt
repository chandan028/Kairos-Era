package com.kairosera.feature.winterarc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.kairosera.R
import com.kairosera.core.diagnostics.SafeLog
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.ui.appContainer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.time.LocalDate

/**
 * The phone's own step counter (a legitimate system sensor, read on the device only). It counts
 * steps since the phone started; WinterArcPrefs turns that into steps today. Read only while the
 * app is on screen, so no background service is needed.
 */
object StepSensor {
    fun available(context: Context): Boolean =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun permitted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun readings(context: Context) = callbackFlow {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sm == null || sensor == null) { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) { trySend(event.values[0]) }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sm.unregisterListener(listener) }
    }.conflate()
}

/** While Winter Arc is on screen and the step sensor is switched on, keep today's steps current. */
@Composable
fun StepSensorEffect(enabled: Boolean) {
    val context = LocalContext.current
    val c = appContainer()
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(enabled) {
        if (!enabled || !StepSensor.available(context) || !StepSensor.permitted(context)) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            StepSensor.readings(context).collect { counter ->
                runCatching {
                    val today = LocalDate.now()
                    val steps = c.winterPrefs.stepsToday(today.toEpochDay(), counter)
                    c.winterArc.sensorSteps(today, steps)
                }.onFailure { SafeLog.error("step_sensor_failed", it) }
            }
        }
    }
}

/** The switch in the Steps tracker and in Winter Arc settings. */
@Composable
fun StepSensorRow(vm: ArcViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val available = remember { StepSensor.available(context) }
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        denied = !ok
        if (ok) vm.setStepSensor(true)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.wa_steps_sensor), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(
                    when {
                        !available -> R.string.wa_steps_sensor_none
                        denied -> R.string.wa_steps_sensor_denied
                        else -> R.string.wa_steps_sensor_summary
                    },
                ),
                style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = settings?.stepSensor == true && available,
            enabled = available,
            onCheckedChange = { on ->
                when {
                    !on -> vm.setStepSensor(false)
                    StepSensor.permitted(context) -> vm.setStepSensor(true)
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            },
        )
    }
}
