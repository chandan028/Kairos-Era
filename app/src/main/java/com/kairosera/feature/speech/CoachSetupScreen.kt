package com.kairosera.feature.speech

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.ai.AiBackend
import com.kairosera.ai.DeviceFit
import com.kairosera.ai.EngineState
import com.kairosera.ai.GemmaModel
import com.kairosera.ai.ImportState
import com.kairosera.ai.ModelRequirements
import com.kairosera.core.settings.SpeechCoachSettings
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.feature.more.SubScreen
import com.kairosera.feature.winterarc.Arc
import com.kairosera.feature.winterarc.ArcButton
import com.kairosera.feature.winterarc.ArcCard
import com.kairosera.ui.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Where the model is, so the screen can say exactly what is installed. */
private data class ModelStatus(val file: File?, val usable: Boolean)

/**
 * Coach settings: import the Gemma model through the system file picker (Kairos cannot read AI
 * Edge Gallery's private copy), test that it loads, pick GPU or CPU, and choose whether
 * recordings are kept.
 */
@Composable
fun CoachSetupScreen(onBack: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val importState by c.modelImporter.state.collectAsStateWithLifecycle()
    val engineState by c.gemma.state.collectAsStateWithLifecycle()
    val settings by c.speechPrefs.settings.collectAsStateWithLifecycle(initialValue = SpeechCoachSettings())
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<ModelStatus?>(null) }
    var tested by remember { mutableStateOf(false) }

    LaunchedEffect(refresh, importState) {
        status = withContext(Dispatchers.IO) {
            val f = c.modelStore.installedFile()
            ModelStatus(f, c.modelStore.getGemmaModelPath() != null)
        }
    }
    // A test load holds gigabytes; free it when leaving unless the speak screen loads it again.
    DisposableEffect(Unit) { onDispose { if (tested) c.appScope.launch { c.gemma.release() } } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) c.appScope.launch { c.modelImporter.import(context.applicationContext, uri) }
    }

    SubScreen(stringResource(R.string.sc_setup_title), onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp)) {
            Text(stringResource(R.string.sc_setup_intro), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted)
            Spacer(Modifier.height(16.dp))

            Section(stringResource(R.string.sc_model_section))
            ArcCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                val s = status
                val file = s?.file
                when {
                    s == null -> Unit
                    file == null -> Text(stringResource(R.string.sc_model_none))
                    s.usable -> {
                        Text(stringResource(R.string.sc_model_installed, file.name), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.sc_model_size, gb(file.length())), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                    }
                    else -> Text(stringResource(R.string.sc_model_bad), color = MaterialTheme.colorScheme.error)
                }
                when (val st = importState) {
                    is ImportState.Copying -> {
                        Spacer(Modifier.height(10.dp))
                        val total = st.totalBytes
                        if (total != null && total > 0) {
                            LinearProgressIndicator(progress = { (st.copiedBytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Arc.primary)
                            Text(stringResource(R.string.sc_model_copying, gb(st.copiedBytes), gb(total)), style = MaterialTheme.typography.bodySmall)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Arc.primary)
                            Text(stringResource(R.string.sc_model_copying_unknown, gb(st.copiedBytes)), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { c.modelImporter.cancel() }) { Text(stringResource(R.string.sc_cancel)) }
                    }
                    is ImportState.Failed -> {
                        Spacer(Modifier.height(8.dp))
                        Text(coachErrorText(st.error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    ImportState.Done -> {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.sc_model_ready), color = Arc.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                    ImportState.Idle -> Unit
                }
                if (importState !is ImportState.Copying) {
                    Spacer(Modifier.height(12.dp))
                    ArcButton(
                        stringResource(if (s?.file != null) R.string.sc_model_replace else R.string.sc_model_import),
                        { c.modelImporter.reset(); picker.launch(arrayOf("*/*")) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.sc_model_import_hint), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                    c.modelStore.adbTarget?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.sc_model_adb, it.absolutePath), style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted)
                    }
                    if (s?.usable == true) {
                        Row {
                            TextButton(onClick = { tested = true; c.appScope.launch { c.gemma.warmUp() } }, enabled = engineState !is EngineState.Loading) {
                                Text(stringResource(R.string.sc_model_test))
                            }
                            if (c.modelStore.importTarget.isFile) {
                                TextButton(onClick = {
                                    c.appScope.launch { c.gemma.release(); c.modelStore.remove(); c.modelImporter.reset(); refresh++ }
                                }) { Text(stringResource(R.string.sc_model_remove)) }
                            }
                        }
                        EngineLine(engineState)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            Section(stringResource(R.string.sc_backend_section))
            ArcCard(Modifier.fillMaxWidth().selectableGroup(), padding = 4.dp) {
                BackendRow(AiBackend.GPU, R.string.sc_backend_gpu, R.string.sc_backend_gpu_summary, settings.backend) { c.appScope.launch { c.speechPrefs.setBackend(it) } }
                BackendRow(AiBackend.CPU, R.string.sc_backend_cpu, R.string.sc_backend_cpu_summary, settings.backend) { c.appScope.launch { c.speechPrefs.setBackend(it) } }
            }
            Spacer(Modifier.height(20.dp))

            Section(stringResource(R.string.sc_device_section))
            ArcCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                val ram = remember { c.deviceInfo.totalRamBytes() }
                if (ram > 0) Text(stringResource(R.string.sc_device_ram, gb(ram)), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        when (ModelRequirements.fit(ram)) {
                            DeviceFit.OK -> R.string.sc_device_ok
                            DeviceFit.BELOW_RECOMMENDED -> R.string.sc_device_low
                            DeviceFit.TOO_SMALL -> R.string.sc_device_too_small
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted,
                )
            }
            Spacer(Modifier.height(20.dp))

            ArcCard(Modifier.fillMaxWidth(), onClick = { c.appScope.launch { c.speechPrefs.setKeepRecordings(!settings.keepRecordings) } }, padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sc_keep_recordings), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.sc_keep_recordings_summary), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = settings.keepRecordings, onCheckedChange = { on -> c.appScope.launch { c.speechPrefs.setKeepRecordings(on) } })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(GemmaModel.MODEL_ID, style = MaterialTheme.typography.labelSmall, color = Kairos.colors.muted)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
}

@Composable
private fun EngineLine(state: EngineState) {
    val text = when (state) {
        EngineState.Idle -> return
        is EngineState.Loading -> stringResource(R.string.sc_model_loading, state.backend.name)
        is EngineState.Ready -> stringResource(R.string.sc_model_test_ok, state.backend.name, String.format(Locale.getDefault(), "%.1f", state.loadMillis / 1000.0)) +
            if (state.fellBackToCpu) "\n" + stringResource(R.string.sc_model_fell_back) else ""
        is EngineState.Failed -> coachErrorText(state.error)
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = if (state is EngineState.Failed) MaterialTheme.colorScheme.error else Kairos.colors.muted)
}

@Composable
private fun BackendRow(value: AiBackend, title: Int, summary: Int, selected: AiBackend, onSelect: (AiBackend) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) }).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
        }
    }
}

private fun gb(bytes: Long): String = String.format(Locale.getDefault(), "%.2f", ModelRequirements.gb(bytes))
