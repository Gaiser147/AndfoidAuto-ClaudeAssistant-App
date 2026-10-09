package io.github.gaiser147.claudeauto.car

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import io.github.gaiser147.claudeauto.R

/** Einstiegspunkt für Android Auto. */
class ClaudeCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // Debug-Builds akzeptieren jeden Host (nötig für die Desktop Head Unit),
        // Release-Builds nur die bekannten Android-Auto-Hosts.
        return if (debuggable) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(R.array.hosts_allowlist)
                .build()
        }
    }

    override fun onCreateSession(): Session = AssistantSession()
}
