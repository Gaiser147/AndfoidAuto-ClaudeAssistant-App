package io.github.gaiser147.claudeauto.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.car.app.CarContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface MicrophonePermission {
    fun isGranted(): Boolean

    /** Fragt die Berechtigung an; der Dialog erscheint auf dem Handy. */
    suspend fun request(): Boolean
}

class CarMicrophonePermission(private val carContext: CarContext) : MicrophonePermission {

    override fun isGranted(): Boolean =
        carContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override suspend fun request(): Boolean = suspendCancellableCoroutine { cont ->
        carContext.requestPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { granted, _ ->
            if (cont.isActive) cont.resume(Manifest.permission.RECORD_AUDIO in granted)
        }
    }
}
