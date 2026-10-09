package io.github.gaiser147.claudeauto.car

import io.github.gaiser147.claudeauto.R
import io.github.gaiser147.claudeauto.voice.MicrophonePermission
import io.github.gaiser147.claudeauto.voice.SpeechInput
import io.github.gaiser147.claudeauto.voice.SpeechInputException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceAssistantControllerTest {

    private class FakePermission(var granted: Boolean, private val grantOnRequest: Boolean = true) : MicrophonePermission {
        var requests = 0
        override fun isGranted() = granted
        override suspend fun request(): Boolean {
            requests++
            granted = grantOnRequest
            return granted
        }
    }

    /** Liefert erst ein Zwischenergebnis und wartet dann auf [result]. */
    private class FakeSpeechInput : SpeechInput {
        val result = CompletableDeferred<String>()
        var calls = 0
        override suspend fun listen(onPartialResult: (String) -> Unit): String {
            calls++
            onPartialResult("Wie wird")
            return result.await()
        }
    }

    @Test
    fun showsPartialAndFinalTranscript() = runTest {
        val speech = FakeSpeechInput()
        val controller = VoiceAssistantController(backgroundScope, FakePermission(granted = true), speech)

        controller.start()
        runCurrent()
        assertEquals(AssistantUiState(AssistantStatus.LISTENING, transcript = "Wie wird"), controller.state.value)

        speech.result.complete("Wie wird das Wetter morgen")
        runCurrent()
        assertEquals(AssistantUiState(transcript = "Wie wird das Wetter morgen"), controller.state.value)
    }

    @Test
    fun requestsMissingPermissionBeforeListening() = runTest {
        val permission = FakePermission(granted = false)
        val speech = FakeSpeechInput()
        val controller = VoiceAssistantController(backgroundScope, permission, speech)

        controller.start()
        runCurrent()
        assertEquals(1, permission.requests)
        assertEquals(AssistantStatus.LISTENING, controller.state.value.status)
    }

    @Test
    fun deniedPermissionShowsErrorAndDoesNotListen() = runTest {
        val speech = FakeSpeechInput()
        val controller = VoiceAssistantController(
            backgroundScope, FakePermission(granted = false, grantOnRequest = false), speech,
        )

        controller.start()
        runCurrent()
        assertEquals(AssistantUiState(info = R.string.error_permission), controller.state.value)
        assertEquals(0, speech.calls)
    }

    @Test
    fun recognitionErrorShowsMessage() = runTest {
        val speech = FakeSpeechInput()
        val controller = VoiceAssistantController(backgroundScope, FakePermission(granted = true), speech)

        controller.start()
        runCurrent()
        speech.result.completeExceptionally(SpeechInputException(SpeechInputException.Reason.NO_MATCH))
        runCurrent()
        assertEquals(AssistantUiState(info = R.string.error_no_match), controller.state.value)
    }

    @Test
    fun stopCancelsListening() = runTest {
        val speech = FakeSpeechInput()
        val controller = VoiceAssistantController(backgroundScope, FakePermission(granted = true), speech)

        controller.start()
        runCurrent()
        controller.stop()
        runCurrent()
        assertEquals(AssistantUiState(), controller.state.value)

        // Ein spätes Ergebnis des abgebrochenen Durchlaufs darf nichts mehr ändern.
        speech.result.complete("zu spät")
        runCurrent()
        assertEquals(AssistantUiState(), controller.state.value)
    }

    @Test
    fun startWhileBusyIsIgnored() = runTest {
        val speech = FakeSpeechInput()
        val controller = VoiceAssistantController(backgroundScope, FakePermission(granted = true), speech)

        controller.start()
        runCurrent()
        controller.start()
        runCurrent()
        assertEquals(1, speech.calls)
        assertTrue(controller.state.value.status.isBusy)
    }
}
