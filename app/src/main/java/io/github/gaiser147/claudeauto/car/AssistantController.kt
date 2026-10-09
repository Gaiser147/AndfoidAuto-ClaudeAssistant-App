package io.github.gaiser147.claudeauto.car

import kotlinx.coroutines.flow.StateFlow

/** Steuert einen Gesprächsdurchlauf: Zuhören → Nachdenken → Sprechen. */
interface AssistantController {
    val state: StateFlow<AssistantUiState>

    /** Startet einen neuen Durchlauf. Wird ignoriert, solange einer läuft. */
    fun start()

    /** Bricht den laufenden Durchlauf ab und kehrt zu [AssistantStatus.IDLE] zurück. */
    fun stop()
}
