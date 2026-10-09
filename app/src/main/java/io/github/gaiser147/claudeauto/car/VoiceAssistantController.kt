package io.github.gaiser147.claudeauto.car

import io.github.gaiser147.claudeauto.R
import io.github.gaiser147.claudeauto.voice.MicrophonePermission
import io.github.gaiser147.claudeauto.voice.SpeechInput
import io.github.gaiser147.claudeauto.voice.SpeechInputException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Phase 2: nimmt eine Frage über das Automikrofon auf und zeigt den erkannten Text an.
 * Ab Phase 3 geht der Text an Claude, ab Phase 4 wird die Antwort vorgelesen.
 */
class VoiceAssistantController(
    private val scope: CoroutineScope,
    private val permission: MicrophonePermission,
    private val speechInput: SpeechInput,
) : AssistantController {

    private val _state = MutableStateFlow(AssistantUiState())
    override val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!permission.isGranted()) {
                _state.value = AssistantUiState(info = R.string.info_permission_on_phone)
                if (!permission.request()) {
                    _state.value = AssistantUiState(info = R.string.error_permission)
                    return@launch
                }
            }

            _state.value = AssistantUiState(status = AssistantStatus.LISTENING)
            _state.value = try {
                val text = speechInput.listen { partial ->
                    _state.update { it.copy(transcript = partial) }
                }
                // TODO Phase 3: Text an Claude schicken.
                AssistantUiState(transcript = text)
            } catch (e: SpeechInputException) {
                AssistantUiState(info = e.reason.message)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _state.value = AssistantUiState()
    }
}
