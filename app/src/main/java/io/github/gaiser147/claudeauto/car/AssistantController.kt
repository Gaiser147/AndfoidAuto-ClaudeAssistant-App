package io.github.gaiser147.claudeauto.car

import kotlinx.coroutines.flow.StateFlow

/**
 * Steuert einen Gesprächsdurchlauf: Zuhören → Nachdenken → Sprechen.
 *
 * Phase 1 nutzt [DemoAssistantController]. Ab Phase 2–4 kommt hier die echte Implementierung
 * (CarAudioRecord + SpeechRecognizer, Claude Messages API, TextToSpeech) hinein; der Screen
 * bleibt dabei unverändert.
 */
interface AssistantController {
    val status: StateFlow<AssistantStatus>

    /** Startet einen neuen Durchlauf. Wird ignoriert, solange einer läuft. */
    fun start()

    /** Bricht den laufenden Durchlauf ab und kehrt zu [AssistantStatus.IDLE] zurück. */
    fun stop()
}
