package io.github.gaiser147.claudeauto.car

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Platzhalter für Phase 1: spielt die Zustände mit festen Wartezeiten durch,
 * damit sich Oberfläche und Stopp-Button in der Desktop Head Unit testen lassen.
 */
class DemoAssistantController(
    private val scope: CoroutineScope,
    private val steps: List<Pair<AssistantStatus, Long>> = DEFAULT_STEPS,
) : AssistantController {

    private val _status = MutableStateFlow(AssistantStatus.IDLE)
    override val status: StateFlow<AssistantStatus> = _status.asStateFlow()

    private var job: Job? = null

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                for ((step, durationMs) in steps) {
                    _status.value = step
                    delay(durationMs)
                }
            } finally {
                _status.value = AssistantStatus.IDLE
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _status.value = AssistantStatus.IDLE
    }

    companion object {
        val DEFAULT_STEPS = listOf(
            AssistantStatus.LISTENING to 3_000L,
            AssistantStatus.THINKING to 2_000L,
            AssistantStatus.SPEAKING to 4_000L,
        )
    }
}
