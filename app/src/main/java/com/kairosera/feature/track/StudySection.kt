package com.kairosera.feature.track

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import com.kairosera.core.ui.components.GhostAddButton
import com.kairosera.core.ui.components.KProgressBar
import com.kairosera.core.ui.components.TaskCheck
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.Space
import com.kairosera.core.ui.theme.Tone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.domain.tracker.StudyProgress
import com.kairosera.domain.tracker.StudyTopic
import com.kairosera.domain.tracker.TopicStatus
import java.time.LocalDate

@Composable
fun topicStatusLabel(s: TopicStatus): String = stringResource(
    when (s) {
        TopicStatus.NOT_STARTED -> R.string.topic_not_started
        TopicStatus.LEARNING -> R.string.topic_learning
        TopicStatus.REVIEWING -> R.string.topic_reviewing
        TopicStatus.DONE -> R.string.topic_done
    },
)

@Composable
fun StudySection(vm: TrackerDetailViewModel, date: LocalDate, tone: Tone, gain: String) {
    val topics by vm.topics.collectAsStateWithLifecycle()
    val targets by vm.targets.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<StudyTopic?>(null) }
    var addingTarget by rememberSaveable { mutableStateOf(false) }
    val summary = StudyProgress.summarize(topics)
    val tree = StudyProgress.flatten(topics)
    val names = topics.associate { it.id to it.title }

    Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
        // OVERALL
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            SectionLabel(stringResource(R.string.study_overall))
            KCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(summary.fraction * 100).toInt()}%", style = MaterialTheme.typography.displaySmall, color = tone.strong)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.study_completed_of, summary.done, summary.leafTopics),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Kairos.colors.muted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                KProgressBar(summary.fraction.toFloat(), color = tone.strong)
            }
        }
        // TODAY
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.study_plan_for_day), Modifier.weight(1f))
                Text("${targets.count { it.done }}/${targets.size}", style = MaterialTheme.typography.labelLarge, color = tone.strong)
            }
            KCard(padding = 4.dp) {
                if (targets.isEmpty()) {
                    Text(stringResource(R.string.study_plan_note), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted, modifier = Modifier.padding(16.dp))
                }
                targets.forEach { target ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { vm.setTargetDone(target.id, !target.done) }) {
                        TaskCheck(checked = target.done, onCheckedChange = { vm.setTargetDone(target.id, it) }, label = target.title)
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(
                                target.title,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (target.done) TextDecoration.LineThrough else null,
                                color = if (target.done) Kairos.colors.muted else MaterialTheme.colorScheme.onSurface,
                            )
                            target.topicId?.let { names[it] }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                            }
                        }
                        IconButton(onClick = { vm.deleteTarget(target.id) }) { Icon(Icons.Outlined.Close, stringResource(R.string.remove_target), tint = Kairos.colors.muted) }
                    }
                }
                TextButton(onClick = { addingTarget = true }, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_study_target))
                }
            }
        }
        if (gain.isNotBlank()) GainCard(gain, tone)
        // TOPICS
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            SectionLabel(stringResource(R.string.study_topics))
            KCard(padding = 8.dp) {
                if (topics.isEmpty()) {
                    Text(stringResource(R.string.study_topics_empty), style = MaterialTheme.typography.bodyMedium, color = Kairos.colors.muted, modifier = Modifier.padding(12.dp))
                }
                tree.forEach { (topic, depth) ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { editing = topic }.padding(start = (8 + depth * 20).dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TopicStatusDot(topic.status, tone)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(topic.title, style = if (depth == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge)
                            val bits = buildList {
                                add(topicStatusLabel(topic.status))
                                if (topic.confidence > 0) add("★".repeat(topic.confidence))
                                if (topic.practiceDone > 0) add(stringResource(R.string.topic_practice_count, topic.practiceDone))
                            }
                            Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Kairos.colors.muted)
                        }
                    }
                }
            }
            GhostAddButton(stringResource(R.string.add_topic), onClick = { editing = StudyTopic(trackerId = vm.trackerId, title = "") }, icon = Icons.Outlined.Add)
        }
    }

    editing?.let { topic ->
        TopicDialog(
            topic = topic,
            candidates = topics.filter { it.id != topic.id },
            onDismiss = { editing = null },
            onSave = { vm.saveTopic(it); editing = null },
            onDelete = if (topic.id != 0L) ({ vm.deleteTopic(topic.id); editing = null }) else null,
        )
    }
    if (addingTarget) {
        TargetDialog(topics = topics, onDismiss = { addingTarget = false }, onAdd = { title, topicId -> vm.addTarget(title, topicId); addingTarget = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicDialog(
    topic: StudyTopic,
    candidates: List<StudyTopic>,
    onDismiss: () -> Unit,
    onSave: (StudyTopic) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var draft by remember(topic.id) { mutableStateOf(topic) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (topic.id == 0L) R.string.add_topic else R.string.edit_topic)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.title, onValueChange = { draft = draft.copy(title = it.take(200)) },
                    label = { Text(stringResource(R.string.field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                ParentPicker(draft.parentId, candidates) { draft = draft.copy(parentId = it) }
                Text(stringResource(R.string.topic_status), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopicStatus.entries.forEach { s ->
                        FilterChip(selected = draft.status == s, onClick = { draft = draft.copy(status = s) }, label = { Text(topicStatusLabel(s)) })
                    }
                }
                Text(stringResource(R.string.topic_confidence), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { n ->
                        FilterChip(
                            selected = draft.confidence == n,
                            onClick = { draft = draft.copy(confidence = if (draft.confidence == n) 0 else n) },
                            label = { Text(n.toString()) },
                        )
                    }
                }
                OutlinedTextField(draft.notes, { draft = draft.copy(notes = it.take(4000)) }, label = { Text(stringResource(R.string.field_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.resources, { draft = draft.copy(resources = it.take(2000)) }, label = { Text(stringResource(R.string.topic_resources)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.questions, { draft = draft.copy(questions = it.take(2000)) }, label = { Text(stringResource(R.string.topic_questions)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) {
                    OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete_topic), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft.copy(title = draft.title.trim())) }, enabled = draft.title.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.delete_topic_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete_topic), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ParentPicker(parentId: Long?, candidates: List<StudyTopic>, onPick: (Long?) -> Unit) {
    if (candidates.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val none = stringResource(R.string.topic_no_parent)
    Column {
        AssistChip(
            onClick = { open = true },
            label = { Text(stringResource(R.string.topic_parent, candidates.firstOrNull { it.id == parentId }?.title ?: none)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(none) }, onClick = { open = false; onPick(null) })
            StudyProgress.flatten(candidates).forEach { (t, depth) ->
                DropdownMenuItem(text = { Text("  ".repeat(depth) + t.title) }, onClick = { open = false; onPick(t.id) })
            }
        }
    }
}

@Composable
private fun TargetDialog(topics: List<StudyTopic>, onDismiss: () -> Unit, onAdd: (String, Long?) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var topicId by rememberSaveable { mutableStateOf<Long?>(null) }
    var open by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_study_target)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(200) }, label = { Text(stringResource(R.string.study_target_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (topics.isNotEmpty()) {
                    Column {
                        AssistChip(
                            onClick = { open = true },
                            label = { Text(stringResource(R.string.study_target_topic, topics.firstOrNull { it.id == topicId }?.title ?: stringResource(R.string.topic_no_parent))) },
                        )
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.topic_no_parent)) }, onClick = { open = false; topicId = null })
                            StudyProgress.flatten(topics).forEach { (t, depth) ->
                                DropdownMenuItem(text = { Text("  ".repeat(depth) + t.title) }, onClick = { open = false; topicId = t.id })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(title, topicId) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Filled = done, half = learning, ring with dot = reviewing, empty ring = not started. Label is always shown beside it. */
@Composable
private fun TopicStatusDot(status: TopicStatus, tone: Tone) {
    val ring = Kairos.colors.line
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val r = size.minDimension / 2f
        when (status) {
            TopicStatus.DONE -> drawCircle(tone.strong, r)
            TopicStatus.LEARNING -> {
                drawCircle(ring, r - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawArc(tone.strong, -90f, 180f, useCenter = true)
            }
            TopicStatus.REVIEWING -> {
                drawCircle(tone.strong, r - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawCircle(tone.strong, r * 0.35f)
            }
            TopicStatus.NOT_STARTED -> drawCircle(ring, r - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
    }
}
