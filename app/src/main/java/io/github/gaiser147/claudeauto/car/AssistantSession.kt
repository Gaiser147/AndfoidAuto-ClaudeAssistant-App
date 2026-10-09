package io.github.gaiser147.claudeauto.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.lifecycleScope
import io.github.gaiser147.claudeauto.voice.CarMicrophonePermission
import io.github.gaiser147.claudeauto.voice.CarSpeechInput

/** Eine Session entspricht einer Verbindung zum Auto, also in der Regel einer Fahrt. */
class AssistantSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        // Der Controller lebt so lange wie die Session; später hängt hier auch der Gesprächsverlauf dran.
        val controller = VoiceAssistantController(
            scope = lifecycleScope,
            permission = CarMicrophonePermission(carContext),
            speechInput = CarSpeechInput(carContext),
        )
        return AssistantScreen(carContext, controller)
    }
}
