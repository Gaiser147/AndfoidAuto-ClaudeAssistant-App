package io.github.gaiser147.claudeauto.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.lifecycleScope

/** Eine Session entspricht einer Verbindung zum Auto, also in der Regel einer Fahrt. */
class AssistantSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        // Der Controller lebt so lange wie die Session; später hängt hier auch der Gesprächsverlauf dran.
        val controller = DemoAssistantController(lifecycleScope)
        return AssistantScreen(carContext, controller)
    }
}
