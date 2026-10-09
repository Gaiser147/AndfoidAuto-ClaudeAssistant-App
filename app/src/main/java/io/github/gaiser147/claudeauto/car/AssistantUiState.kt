package io.github.gaiser147.claudeauto.car

import androidx.annotation.StringRes

/**
 * Was der Bildschirm im Auto anzeigt.
 *
 * @property transcript erkannter Text (während des Zuhörens als Zwischenergebnis).
 * @property info Hinweis oder Fehlermeldung, falls es gerade keinen Text gibt.
 */
data class AssistantUiState(
    val status: AssistantStatus = AssistantStatus.IDLE,
    val transcript: String? = null,
    @StringRes val info: Int? = null,
)
