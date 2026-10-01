package com.kairosera.data.speech

import com.kairosera.core.database.SpeechDao
import com.kairosera.core.database.SpeechSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/** One analysed speech, as the report and progress screens show it. */
data class SpeechSession(
    val id: Long = 0,
    val date: LocalDate,
    val createdAt: Instant,
    val topic: String,
    val speakingSessionId: Long?,
    val durationSeconds: Int,
    val overallScore: Double?,
    val modelOverallScore: Int?,
    val clarityScore: Int?,
    val structureScore: Int?,
    val vocabularyScore: Int?,
    val grammarScore: Int?,
    val concisenessScore: Int?,
    val fillerWords: List<String>?,
    val strengths: List<String>,
    val improvements: List<String>,
    val nextExercise: String,
    val summary: String,
    val transcript: String,
    val wordsPerMinute: Int?,
    val fillerSounds: Int?,
    val longPauses: Int?,
    val voicedSeconds: Double?,
    val backend: String,
    val audioPath: String? = null,
)

/** Local only: the reports live in the app's Room database, like everything else in Kairos. */
interface SpeechSessionRepository {
    suspend fun save(session: SpeechSession): Long
    fun observeAll(): Flow<List<SpeechSession>>
    fun observe(id: Long): Flow<SpeechSession?>
    suspend fun get(id: Long): SpeechSession?
    suspend fun setAudioPath(id: Long, path: String?)
    suspend fun delete(id: Long)
}

class RoomSpeechSessionRepository(private val dao: SpeechDao) : SpeechSessionRepository {
    override suspend fun save(session: SpeechSession): Long = dao.insert(session.toEntity())
    override fun observeAll(): Flow<List<SpeechSession>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override fun observe(id: Long): Flow<SpeechSession?> = dao.observe(id).map { it?.toDomain() }
    override suspend fun get(id: Long): SpeechSession? = dao.byId(id)?.toDomain()
    override suspend fun setAudioPath(id: Long, path: String?) = dao.setAudioPath(id, path)
    override suspend fun delete(id: Long) = dao.delete(id)
}

private val listSerializer = ListSerializer(String.serializer())
private val json = Json { ignoreUnknownKeys = true }

private fun encode(list: List<String>): String = json.encodeToString(listSerializer, list)
private fun decode(s: String?): List<String>? = s?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }

internal fun SpeechSession.toEntity() = SpeechSessionEntity(
    id = id, date = date.toEpochDay(), createdAt = createdAt.toEpochMilli(), topic = topic, speakingSessionId = speakingSessionId,
    durationSeconds = durationSeconds, overallScore = overallScore, modelOverallScore = modelOverallScore,
    clarityScore = clarityScore, structureScore = structureScore, vocabularyScore = vocabularyScore,
    grammarScore = grammarScore, concisenessScore = concisenessScore,
    fillerWords = fillerWords?.let(::encode), strengths = encode(strengths), improvements = encode(improvements),
    nextExercise = nextExercise, summary = summary, transcript = transcript, wordsPerMinute = wordsPerMinute,
    fillerSounds = fillerSounds, longPauses = longPauses, voicedSeconds = voicedSeconds, backend = backend, audioPath = audioPath,
)

internal fun SpeechSessionEntity.toDomain() = SpeechSession(
    id = id, date = LocalDate.ofEpochDay(date), createdAt = Instant.ofEpochMilli(createdAt), topic = topic, speakingSessionId = speakingSessionId,
    durationSeconds = durationSeconds, overallScore = overallScore, modelOverallScore = modelOverallScore,
    clarityScore = clarityScore, structureScore = structureScore, vocabularyScore = vocabularyScore,
    grammarScore = grammarScore, concisenessScore = concisenessScore,
    fillerWords = decode(fillerWords), strengths = decode(strengths).orEmpty(), improvements = decode(improvements).orEmpty(),
    nextExercise = nextExercise, summary = summary, transcript = transcript, wordsPerMinute = wordsPerMinute,
    fillerSounds = fillerSounds, longPauses = longPauses, voicedSeconds = voicedSeconds, backend = backend, audioPath = audioPath,
)
