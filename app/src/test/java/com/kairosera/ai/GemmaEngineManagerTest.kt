package com.kairosera.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GemmaEngineManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val created = mutableListOf<FakeEngine>()

    private fun manager(
        path: () -> String? = { CoachFakes.fakeModel(tmp.root).absolutePath },
        backend: AiBackend = AiBackend.GPU,
        ram: Long = 8 * CoachFakes.GB,
        factory: LlmEngineFactory = LlmEngineFactory { _, b -> FakeEngine(b).also { created += it } },
    ) = GemmaEngineManager(CoachFakes.provider(path), factory, CoachFakes.device(ram), { backend }, io = Dispatchers.Unconfined)

    private suspend fun expectError(m: GemmaEngineManager, error: CoachError) {
        try {
            m.withEngine { it.backend }
            fail("expected $error")
        } catch (e: CoachException) {
            assertEquals(error, e.error)
            assertEquals(EngineState.Failed(error), m.state.value)
        }
    }

    @Test fun modelNotFound() = runBlocking { expectError(manager(path = { null }), CoachError.MODEL_MISSING) }

    @Test fun wrongFileIsRejectedBeforeLoading() = runBlocking {
        val bad = tmp.newFile("gemma.bin").apply { writeText("nope") }
        expectError(manager(path = { bad.absolutePath }), CoachError.MODEL_INVALID_EXTENSION)
        assertTrue(created.isEmpty())
    }

    @Test fun tooLittleRamDoesNotTry() = runBlocking {
        expectError(manager(ram = 4 * CoachFakes.GB), CoachError.LOW_MEMORY)
        assertTrue(created.isEmpty())
    }

    @Test fun loadsOnceAndReuses() = runBlocking {
        val m = manager()
        m.withEngine { }
        m.withEngine { }
        assertEquals(1, created.size)
        val s = m.state.value as EngineState.Ready
        assertEquals(AiBackend.GPU, s.backend)
        assertFalse(s.fellBackToCpu)
    }

    @Test fun gpuInitFailureFallsBackToCpu() = runBlocking {
        val m = manager(factory = { _, b -> if (b == AiBackend.GPU) error("no OpenCL") else FakeEngine(b).also { created += it } })
        assertEquals(AiBackend.CPU, m.withEngine { it.backend })
        assertEquals(EngineState.Ready(AiBackend.CPU, (m.state.value as EngineState.Ready).loadMillis, true), m.state.value)
    }

    @Test fun gpuInferenceFailureRetriesOnCpu() = runBlocking {
        val m = manager(factory = { _, b ->
            (if (b == AiBackend.GPU) FakeEngine(b, failWith = IllegalStateException("gpu delegate")) else FakeEngine(b)).also { created += it }
        })
        val reply = m.withEngine { it.generate("s", null, "p") }
        assertEquals(CoachFakes.GOOD_JSON, reply)
        assertEquals(listOf(AiBackend.GPU, AiBackend.CPU), created.map { it.backend })
        assertTrue("the failed GPU engine is closed", created[0].closed)
        // CPU sticks for the rest of the session.
        m.withEngine { }
        assertEquals(2, created.size)
    }

    @Test fun initFailureOnCpuShowsUnsupported() = runBlocking {
        expectError(manager(factory = { _, _ -> error("bad model") }), CoachError.UNSUPPORTED_DEVICE)
    }

    @Test fun coachErrorsAreNotBlamedOnTheGpu() = runBlocking {
        val m = manager()
        try { m.withEngine { throw CoachException(CoachError.BAD_RESPONSE) } } catch (e: CoachException) { assertEquals(CoachError.BAD_RESPONSE, e.error) }
        assertEquals(1, created.size)
    }

    @Test fun neverTwoEnginesAtOnce() = runBlocking {
        val m = manager()
        val a = async(Dispatchers.Default) { m.withEngine { it } }
        val b = async(Dispatchers.Default) { m.withEngine { it } }
        assertTrue(a.await() === b.await())
        assertEquals(1, created.size)
    }

    @Test fun releaseClosesAndReloadsLater() = runBlocking {
        val m = manager()
        m.withEngine { }
        m.release()
        assertTrue(created.single().closed)
        assertEquals(EngineState.Idle, m.state.value)
        m.withEngine { }
        assertEquals(2, created.size)
    }

    @Test fun switchingToCpuInSettingsReloads() = runBlocking {
        var pref = AiBackend.GPU
        val m = GemmaEngineManager(
            CoachFakes.provider { CoachFakes.fakeModel(tmp.root).absolutePath },
            { _, b -> FakeEngine(b).also { created += it } }, CoachFakes.device(), { pref }, io = Dispatchers.Unconfined,
        )
        m.withEngine { }
        pref = AiBackend.CPU
        assertEquals(AiBackend.CPU, m.withEngine { it.backend })
        assertTrue(created.first().closed)
    }
}
