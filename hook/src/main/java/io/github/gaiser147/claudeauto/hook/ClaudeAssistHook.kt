package io.github.gaiser147.claudeauto.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.DataOutputStream

/**
 * LSPosed-Modul: fängt in Android Auto den Auslöser der Sprach-/Lenkradtaste ab und löst statt
 * Google/Gemini den Standard-Assistenten des Handys aus (bei dir Claude).
 *
 * Fundstelle in Android Auto 17.7.663654 (com.google.android.projection.gearhead), per statischer
 * Analyse des APK:
 *   - Die Lenkradtaste kommt als Key-Event mit Keycode VOICE_ASSIST an.
 *   - Die verschleierte Klasse [ASSISTANT_CONTROLLER_CLASS] (`tfl`) startet die Sprach-Session;
 *     ihre Methode [START_SESSION_METHOD] (`k`) ist der Einstieg (bestätigt per Gerät-Log).
 *
 * Claude ist auf dem Gerät als VoiceInteractionService eingebunden
 * (com.anthropic.claude/.bell.assist.ClaudeVoiceInteractionService), also NICHT per Intent startbar.
 * So ein Dienst wird nur vom System über den Assistenten-Mechanismus aufgerufen. Der Hook bildet
 * deshalb den Assistenten-Tastendruck nach: er injiziert KEYCODE_ASSIST per Root (`su`), exakt wie
 * der Power-Knopf. Android startet daraufhin den eingestellten Standard-Assistenten.
 *
 * WICHTIG:
 *   - Die obfuskierten Namen gelten nur für diese AA-Version und brechen bei Updates (dann im Log
 *     `Hook fehlgeschlagen`); AA-Updates deshalb abschalten.
 *   - Android Auto muss in Magisk Root erhalten, sonst schlägt die Injektion fehl.
 */
class ClaudeAssistHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != GEARHEAD_PACKAGE) return
        XposedBridge.log("$TAG geladen in ${lpparam.packageName}")

        try {
            val controller = XposedHelpers.findClass(ASSISTANT_CONTROLLER_CLASS, lpparam.classLoader)
            // Über den Methodennamen hooken (alle Überladungen), robuster als die obfuskierte Signatur.
            val callback = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    XposedBridge.log("$TAG Auslöser erkannt -> Gemini unterdrücken, Claude per KEYCODE_ASSIST starten")
                    // Original (Google/Gemini) immer unterdrücken, damit es nicht zusätzlich startet.
                    param.result = null
                    triggerDefaultAssistant()
                }
            }
            val hooked = XposedBridge.hookAllMethods(controller, START_SESSION_METHOD, callback)
            XposedBridge.log("$TAG Hook auf $ASSISTANT_CONTROLLER_CLASS.$START_SESSION_METHOD gesetzt (${hooked?.size ?: 0} Methode[n])")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Hook fehlgeschlagen – vermutlich andere AA-Version: ${t.message}")
            XposedBridge.log(t)
        }
    }

    /**
     * Injiziert den Assistenten-Tastendruck in einem Hintergrund-Thread. Nicht auf dem UI-Thread,
     * weil der erste `su`-Aufruf auf die Magisk-Freigabe wartet und das Auto sonst hängen würde.
     */
    private fun triggerDefaultAssistant() {
        Thread {
            // Erst laufende Medien pausieren (wie Gemini), sonst blockiert Claudes Sprachmodus mit
            // „Pausiert, während eine andere App Audio verwendet". Danach KEYCODE_ASSIST wie der Power-Knopf.
            val script = buildList {
                add("input keyevent $KEYCODE_MEDIA_PAUSE")
                add("sleep 0.4")
                add("input keyevent $KEYCODE_ASSIST")
            }.joinToString("\n")
            if (runAsRoot(script)) {
                XposedBridge.log("$TAG Medien pausiert + KEYCODE_ASSIST gesendet")
            } else {
                XposedBridge.log("$TAG Injektion fehlgeschlagen – Android Auto in Magisk Root gewähren")
            }
        }.start()
    }

    /** Führt ein (mehrzeiliges) Skript als Root aus. Gibt true bei Exit-Code 0 zurück. */
    private fun runAsRoot(script: String): Boolean = try {
        val process = Runtime.getRuntime().exec("su")
        DataOutputStream(process.outputStream).use { out ->
            out.writeBytes("$script\n")
            out.writeBytes("exit\n")
            out.flush()
        }
        process.waitFor() == 0
    } catch (t: Throwable) {
        XposedBridge.log("$TAG su fehlgeschlagen: ${t.message}")
        false
    }

    private companion object {
        const val TAG = "[ClaudeAA]"
        const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"

        // Obfuskierte Namen aus AA 17.7.663654 – bei Updates neu ermitteln.
        const val ASSISTANT_CONTROLLER_CLASS = "tfl"
        const val START_SESSION_METHOD = "k"

        const val KEYCODE_ASSIST = 219
        const val KEYCODE_MEDIA_PAUSE = 127
    }
}
