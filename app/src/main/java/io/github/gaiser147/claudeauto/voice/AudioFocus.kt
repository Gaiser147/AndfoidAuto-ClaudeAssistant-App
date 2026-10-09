package io.github.gaiser147.claudeauto.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Hält während der Aufnahme exklusiven Audio-Focus, damit Musik und Navigation pausieren.
 * So empfiehlt es auch die Car App Library für Apps, die das Automikrofon nutzen.
 */
class AudioFocus(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { }
        .build()

    suspend fun <T> hold(block: suspend () -> T): T {
        // Auch ohne Focus aufnehmen: lieber mit laufender Musik als gar nicht.
        audioManager.requestAudioFocus(request)
        try {
            return block()
        } finally {
            audioManager.abandonAudioFocusRequest(request)
        }
    }
}
