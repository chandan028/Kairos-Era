package com.kairosera.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.Tone
import com.kairosera.domain.model.Category
import com.kairosera.domain.tracker.TrackerTemplate

/*
 * Meaning → color, in one place. Learning is purple, activity coral, reading/motivation amber,
 * habits and personal growth green, work and projects blue.
 */

@Composable
@ReadOnlyComposable
fun toneFor(template: TrackerTemplate): Tone = when (template) {
    TrackerTemplate.STUDY, TrackerTemplate.LEARNING -> Kairos.colors.learning
    TrackerTemplate.FITNESS, TrackerTemplate.HEALTH -> Kairos.colors.activity
    TrackerTemplate.READING -> Kairos.colors.motivation
    TrackerTemplate.HABIT, TrackerTemplate.PERSONAL -> Kairos.colors.success
    TrackerTemplate.PROJECT, TrackerTemplate.FINANCE, TrackerTemplate.CUSTOM -> Kairos.colors.info
}

/** Categories carry an icon key chosen at creation; it decides the semantic tone. */
@Composable
@ReadOnlyComposable
fun toneFor(category: Category?): Tone = when (category?.icon) {
    "school" -> Kairos.colors.learning
    "fitness" -> Kairos.colors.activity
    "book" -> Kairos.colors.motivation
    "work" -> Kairos.colors.info
    "person" -> Kairos.colors.success
    else -> Kairos.colors.info
}

/** A person's chosen emoji on a soft semantic circle (trackers keep their personal emoji). */
@Composable
fun EmojiBadge(emoji: String, tone: Tone, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(tone.soft), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (size.value * 0.45f).sp)
    }
}
