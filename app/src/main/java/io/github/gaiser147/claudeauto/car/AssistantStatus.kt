package io.github.gaiser147.claudeauto.car

import androidx.annotation.StringRes
import io.github.gaiser147.claudeauto.R

/** Die Zustände, die der Assistent im Auto anzeigt. */
enum class AssistantStatus(@StringRes val label: Int) {
    IDLE(R.string.status_idle),
    LISTENING(R.string.status_listening),
    THINKING(R.string.status_thinking),
    SPEAKING(R.string.status_speaking);

    val isBusy: Boolean get() = this != IDLE
}
