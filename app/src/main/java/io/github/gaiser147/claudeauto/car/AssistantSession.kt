package io.github.gaiser147.claudeauto.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.lifecycleScope
import io.github.gaiser147.claudeauto.voice.CarMicrophonePermission
import io.github.gaiser147.claudeauto.voice.CarSpeechInput

/** Eine Session entspricht einer Verbindung zum Auto, also in der Regel einer Fahrt. */
class AssistantSession : Session() {

    private lateinit var controller: AssistantController

    override fun onCreateScreen(intent: Intent): Screen {
        // Der Controller lebt so lange wie die Session; später hängt hier auch der Gesprächsverlauf dran.
        controller = VoiceAssistantController(
            scope = lifecycleScope,
            permission = CarMicrophonePermission(carContext),
            speechInput = CarSpeechInput(carContext),
        )
        return AssistantScreen(carContext, controller)
    }

    /** App wird erneut gestartet, während sie schon offen ist: direkt wieder zuhören. */
    override fun onNewIntent(intent: Intent) {
        controller.start()
    }
}
