package com.kairosera.domain.tracker

import java.time.Instant
import java.time.LocalDate

enum class TopicStatus { NOT_STARTED, LEARNING, REVIEWING, DONE }

/** A node in a study plan, e.g. Java Core > Collections > HashMap internals. */
data class StudyTopic(
    val id: Long = 0,
    val trackerId: Long,
    val parentId: Long? = null,
    val title: String,
    val status: TopicStatus = TopicStatus.NOT_STARTED,
    val notes: String = "",
    val resources: String = "",
    val questions: String = "",
    val practiceDone: Int = 0,
    /** 0 = not rated, 1..5 = how confident the user feels. */
    val confidence: Int = 0,
    val position: Int = 0,
    val updatedAt: Instant = Instant.EPOCH,
)

/** An item on one day's study plan. The plan is the user's: completing items never rewrites it. */
data class StudyTarget(
    val id: Long = 0,
    val trackerId: Long,
    val date: LocalDate,
    val topicId: Long? = null,
    val title: String,
    val done: Boolean = false,
    val position: Int = 0,
)

data class StudySummary(val leafTopics: Int, val done: Int, val inProgress: Int) {
    val fraction: Double get() = if (leafTopics == 0) 0.0 else done.toDouble() / leafTopics
}

object StudyProgress {
    /** Progress counts leaf topics only, so adding a heading never changes the percentage. */
    fun summarize(topics: List<StudyTopic>): StudySummary {
        val parents = topics.mapNotNull { it.parentId }.toSet()
        val leaves = topics.filter { it.id !in parents }
        return StudySummary(
            leafTopics = leaves.size,
            done = leaves.count { it.status == TopicStatus.DONE },
            inProgress = leaves.count { it.status == TopicStatus.LEARNING || it.status == TopicStatus.REVIEWING },
        )
    }

    /**
     * What completing a daily target does to its topic: it records practice and moves a
     * not-started topic to "learning". It never marks a topic done: only the user decides that.
     */
    fun onTargetCompleted(topic: StudyTopic, at: Instant): StudyTopic = topic.copy(
        status = if (topic.status == TopicStatus.NOT_STARTED) TopicStatus.LEARNING else topic.status,
        practiceDone = topic.practiceDone + 1,
        updatedAt = at,
    )

    fun onTargetUncompleted(topic: StudyTopic, at: Instant): StudyTopic =
        topic.copy(practiceDone = (topic.practiceDone - 1).coerceAtLeast(0), updatedAt = at)

    /** Depth-first order with depth, for showing the tree. Cycles and orphans are shown at the root, never lost. */
    fun flatten(topics: List<StudyTopic>): List<Pair<StudyTopic, Int>> {
        val byParent = topics.groupBy { it.parentId }
        val ids = topics.map { it.id }.toSet()
        val out = mutableListOf<Pair<StudyTopic, Int>>()
        val seen = HashSet<Long>()
        fun visit(t: StudyTopic, depth: Int) {
            if (!seen.add(t.id)) return
            out += t to depth
            byParent[t.id].orEmpty().sortedBy { it.position }.forEach { visit(it, depth + 1) }
        }
        topics.filter { it.parentId == null || it.parentId !in ids }.sortedBy { it.position }.forEach { visit(it, 0) }
        topics.filter { it.id !in seen }.forEach { visit(it, 0) }
        return out
    }
}
