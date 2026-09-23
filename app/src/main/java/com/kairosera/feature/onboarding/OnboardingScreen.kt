package com.kairosera.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kairosera.R
import com.kairosera.core.ui.components.PrimaryWideButton
import com.kairosera.domain.model.Priority
import com.kairosera.domain.model.Task
import com.kairosera.ui.appContainer
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** Three short steps, then straight into the app. Every choice can be changed later in Settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen() {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var focus by rememberSaveable { mutableStateOf(setOf<String>()) }
    var firstTask by rememberSaveable { mutableStateOf("") }
    var examples by rememberSaveable { mutableStateOf(true) }
    var finishing by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            runCatching {
                if (examples) c.addSampleContent()
                if (firstTask.isNotBlank()) {
                    c.saveTask(Task(title = firstTask.trim(), date = LocalDate.now(), priority = Priority.MEDIUM, createdAt = Instant.now()))
                }
            }
            c.settings.completeOnboarding(name, focus)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                when (step) {
                    0 -> {
                        Box(
                            Modifier.size(104.dp).align(Alignment.CenterHorizontally).clip(CircleShape)
                                .background(Brush.verticalGradient(listOf(Color(0xFF1B2F57), Color(0xFF34548C)))),
                        ) {
                            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(stringResource(R.string.app_name_display), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.tagline), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        Spacer(Modifier.height(24.dp))
                        Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(24.dp))
                        Promise(Icons.Outlined.Lock, R.string.onboarding_private)
                        Promise(Icons.Outlined.PhoneAndroid, R.string.onboarding_local)
                        Promise(Icons.Outlined.NoAccounts, R.string.onboarding_no_account)
                        Promise(Icons.Outlined.CloudOff, R.string.onboarding_no_cloud)
                        Spacer(Modifier.height(32.dp))
                        PrimaryWideButton(stringResource(R.string.onboarding_begin), onClick = { step = 1 })
                    }
                    1 -> {
                        Text(stringResource(R.string.onboarding_about_you), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            label = { Text(stringResource(R.string.onboarding_name_label)) },
                            supportingText = { Text(stringResource(R.string.optional)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.onboarding_focus), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "learning" to R.string.focus_learning, "health" to R.string.focus_health, "reading" to R.string.focus_reading,
                                "work" to R.string.focus_work, "personal" to R.string.focus_personal, "custom" to R.string.focus_custom,
                            ).forEach { (key, label) ->
                                FilterChip(selected = key in focus, onClick = { focus = if (key in focus) focus - key else focus + key }, label = { Text(stringResource(label)) })
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = firstTask,
                            onValueChange = { firstTask = it.take(200) },
                            label = { Text(stringResource(R.string.onboarding_first_task)) },
                            supportingText = { Text(stringResource(R.string.onboarding_first_task_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = examples, onCheckedChange = { examples = it })
                            Text(stringResource(R.string.onboarding_examples), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(24.dp))
                        PrimaryWideButton(stringResource(R.string.next), onClick = { step = 2 })
                        TextButton(onClick = { step = 0 }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.back)) }
                    }
                    else -> {
                        Text(stringResource(R.string.onboarding_reminders_title), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.onboarding_reminders_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(32.dp))
                        PrimaryWideButton(stringResource(R.string.onboarding_allow_reminders), enabled = !finishing, onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            finish()
                        })
                        TextButton(onClick = { finish() }, enabled = !finishing, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
                            Text(stringResource(R.string.onboarding_not_now))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Promise(icon: ImageVector, text: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}
