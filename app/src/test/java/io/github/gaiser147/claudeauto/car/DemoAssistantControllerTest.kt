package io.github.gaiser147.claudeauto.car

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoAssistantControllerTest {

    @Test
    fun runsThroughAllStatesAndEndsIdle() = runTest {
        val controller = DemoAssistantController(backgroundScope)
        assertEquals(AssistantStatus.IDLE, controller.status.value)

        controller.start()
        runCurrent()
        assertEquals(AssistantStatus.LISTENING, controller.status.value)

        advanceTimeBy(3_001)
        assertEquals(AssistantStatus.THINKING, controller.status.value)

        advanceTimeBy(2_000)
        assertEquals(AssistantStatus.SPEAKING, controller.status.value)

        advanceTimeBy(4_000)
        assertEquals(AssistantStatus.IDLE, controller.status.value)
    }

    @Test
    fun stopCancelsImmediately() = runTest {
        val controller = DemoAssistantController(backgroundScope)
        controller.start()
        advanceTimeBy(3_500)
        assertEquals(AssistantStatus.THINKING, controller.status.value)

        controller.stop()
        runCurrent()
        assertEquals(AssistantStatus.IDLE, controller.status.value)

        // Der abgebrochene Durchlauf darf den Status später nicht mehr ändern.
        advanceTimeBy(10_000)
        assertEquals(AssistantStatus.IDLE, controller.status.value)
    }

    @Test
    fun startWhileBusyIsIgnored() = runTest {
        val controller = DemoAssistantController(backgroundScope)
        controller.start()
        advanceTimeBy(3_500)
        controller.start()
        runCurrent()
        assertEquals(AssistantStatus.THINKING, controller.status.value)
    }
}
