package io.github.gaiser147.claudeauto.voice

import androidx.annotation.StringRes
import io.github.gaiser147.claudeauto.R

/** Spracheingabe: hört einmal zu und liefert den erkannten Text. */
interface SpeechInput {
    /**
     * Nimmt auf, bis der Erkenner das Ende der Äußerung erkennt.
     *
     * @param onPartialResult Zwischenergebnisse während des Sprechens (Main-Thread).
     * @throws SpeechInputException wenn nichts erkannt wurde oder die Erkennung fehlschlägt.
     */
    suspend fun listen(onPartialResult: (String) -> Unit): String
}

class SpeechInputException(
    val reason: Reason,
    detail: String? = null,
) : Exception(detail ?: reason.name) {

    enum class Reason(@StringRes val message: Int) {
        NO_MATCH(R.string.error_no_match),
        NOT_SUPPORTED(R.string.error_not_supported),
        NOT_AVAILABLE(R.string.error_recognizer_unavailable),
        PERMISSION(R.string.error_permission),
        MICROPHONE(R.string.error_microphone),
        NETWORK(R.string.error_network),
        LANGUAGE(R.string.error_language),
        BUSY(R.string.error_busy),
        OTHER(R.string.error_speech),
    }
}
