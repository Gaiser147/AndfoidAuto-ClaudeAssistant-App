package io.github.gaiser147.claudeauto.voice

import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.media.CarAudioRecord
import io.github.gaiser147.claudeauto.voice.SpeechInputException.Reason
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Nimmt über das Automikrofon ([CarAudioRecord]) auf und reicht das Audio per Pipe an den
 * [SpeechRecognizer] des Handys weiter ([RecognizerIntent.EXTRA_AUDIO_SOURCE], Android 13+).
 *
 * Bevorzugt den On-Device-Erkenner. Scheitert der (z. B. weil Deutsch offline nicht installiert ist
 * oder er keine externe Audioquelle annimmt), wird sofort und für den Rest der Fahrt der normale
 * (ggf. Online-)Erkenner verwendet.
 */
class CarSpeechInput(
    private val carContext: CarContext,
    private val language: String = "de-DE",
) : SpeechInput {

    private val audioFocus = AudioFocus(carContext)
    private val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var useOnDevice = true

    override suspend fun listen(onPartialResult: (String) -> Unit): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) throw SpeechInputException(Reason.NOT_SUPPORTED)
        return withContext(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(carContext)) throw SpeechInputException(Reason.NOT_AVAILABLE)
            audioFocus.hold {
                val onDevice = useOnDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(carContext)
                try {
                    recognize(onDevice, onPartialResult)
                } catch (e: SpeechInputException) {
                    if (!onDevice || e.reason !in ON_DEVICE_FALLBACK_REASONS) throw e
                    Log.i(TAG, "On-Device-Erkennung fehlgeschlagen (${e.message}), nutze Standard-Erkenner")
                    useOnDevice = false
                    recognize(onDevice = false, onPartialResult)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun recognize(onDevice: Boolean, onPartialResult: (String) -> Unit): String {
        val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe()
        val stopRecording = AtomicBoolean(false)
        val recognizer = if (onDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(carContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(carContext)
        }
        try {
            return withTimeoutOrNull(MAX_RECORDING_MS + RESULT_GRACE_MS) {
                suspendCancellableCoroutine { cont ->
                    recognizer.setRecognitionListener(Listener(cont, stopRecording, onPartialResult))
                    recognizer.startListening(recognizerIntent(readEnd, onDevice))
                    pumpScope.launch {
                        try {
                            pumpMicrophone(writeEnd, stopRecording)
                        } catch (e: Exception) {
                            Log.e(TAG, "Automikrofon fehlgeschlagen", e)
                            withContext(Dispatchers.Main) {
                                if (cont.isActive) cont.resumeWithException(SpeechInputException(Reason.MICROPHONE, e.message))
                            }
                        }
                    }
                }
            } ?: throw SpeechInputException(Reason.OTHER, "Keine Antwort vom Spracherkenner")
        } finally {
            stopRecording.set(true)
            recognizer.destroy()
            readEnd.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun recognizerIntent(audioSource: ParcelFileDescriptor, onDevice: Boolean) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, onDevice)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audioSource)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, CarAudioRecord.AUDIO_CONTENT_SAMPLING_RATE)

    /**
     * Liest das Automikrofon (16 kHz, mono, PCM 16 Bit) und schreibt es in die Pipe, bis der
     * Erkenner das Sprechende meldet, die Höchstdauer erreicht ist oder das Auto das Mikrofon schließt.
     * Das Schließen der Pipe signalisiert dem Erkenner das Ende der Aufnahme.
     */
    @SuppressLint("MissingPermission") // Prüft der VoiceAssistantController vor dem Start.
    private fun pumpMicrophone(writeEnd: ParcelFileDescriptor, stop: AtomicBoolean) {
        ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { out ->
            val record = CarAudioRecord.create(carContext)
            record.startRecording()
            try {
                val buffer = ByteArray(CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE)
                val deadline = SystemClock.elapsedRealtime() + MAX_RECORDING_MS
                while (!stop.get() && SystemClock.elapsedRealtime() < deadline) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                }
            } catch (_: IOException) {
                // Der Erkenner hat seine Seite der Pipe geschlossen; die Aufnahme ist damit vorbei.
            } finally {
                record.stopRecording()
            }
        }
    }

    private class Listener(
        private val cont: CancellableContinuation<String>,
        private val stopRecording: AtomicBoolean,
        private val onPartialResult: (String) -> Unit,
    ) : RecognitionListener {

        override fun onPartialResults(partialResults: Bundle) {
            partialResults.firstResult()?.let(onPartialResult)
        }

        override fun onEndOfSpeech() {
            stopRecording.set(true)
        }

        override fun onResults(results: Bundle) {
            if (!cont.isActive) return
            val text = results.firstResult()
            if (text == null) {
                cont.resumeWithException(SpeechInputException(Reason.NO_MATCH))
            } else {
                cont.resume(text)
            }
        }

        override fun onError(error: Int) {
            if (cont.isActive) cont.resumeWithException(SpeechInputException(reasonFor(error), "SpeechRecognizer-Fehler $error"))
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun Bundle.firstResult(): String? =
            getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "CarSpeechInput"

        /** Höchstdauer einer Frage; danach wird die Aufnahme beendet und ausgewertet. */
        const val MAX_RECORDING_MS = 15_000L
        private const val RESULT_GRACE_MS = 10_000L

        private val ON_DEVICE_FALLBACK_REASONS = setOf(Reason.LANGUAGE, Reason.NOT_AVAILABLE, Reason.OTHER)

        fun reasonFor(error: Int): Reason = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Reason.NO_MATCH
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> Reason.NETWORK
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Reason.PERMISSION
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> Reason.BUSY
            SpeechRecognizer.ERROR_AUDIO -> Reason.MICROPHONE
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> Reason.LANGUAGE
            else -> Reason.OTHER
        }
    }
}
