package io.github.gaiser147.claudeauto.hook

import android.content.Intent
import de.robv.android.xposed.AndroidAppHelper
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/**
 * LSPosed-Modul: fängt in Android Auto den Auslöser der Sprach-/Lenkradtaste ab und startet
 * statt Google/Gemini den Standard-Assistenten des Handys (bei dir Claude).
 *
 * Fundstelle in Android Auto 17.7.663654 (com.google.android.projection.gearhead), ermittelt per
 * statischer Analyse des APK:
 *   - Die Lenkradtaste kommt als Key-Event mit Keycode VOICE_ASSIST an.
 *   - Die verschleierte Klasse [ASSISTANT_CONTROLLER_CLASS] (`tfl`) startet die Sprach-Session;
 *     ihre Methode [START_SESSION_METHOD] (`k`) ist der Einstieg, der Callback `tfk` meldet
 *     „Error starting assistant session“.
 *
 * WICHTIG: Diese Namen sind obfuskiert und gelten nur für genau diese AA-Version. Bei einem
 * Update brechen sie und müssen neu ermittelt werden. Deshalb protokolliert der Hook jeden Treffer,
 * damit sich auf dem Gerät (LSPosed-Log) prüfen lässt, ob die richtige Stelle erwischt wurde.
 */
class ClaudeAssistHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != GEARHEAD_PACKAGE) return
        XposedBridge.log("$TAG geladen in ${lpparam.packageName}")

        try {
            val controller = XposedHelpers.findClass(ASSISTANT_CONTROLLER_CLASS, lpparam.classLoader)
            // Über den Methodennamen hooken statt über die (verschleierte, 7-teilige) Signatur:
            // robuster gegen kleine Änderungen und fängt alle Überladungen von `k`.
            val callback = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    XposedBridge.log("$TAG Assistent-Auslöser erkannt -> leite auf Standard-Assistent um")
                    if (launchDefaultAssistant()) {
                        // Original-Start (Google/Gemini) überspringen.
                        param.result = null
                    }
                }
            }
            val hooked = XposedBridge.hookAllMethods(controller, START_SESSION_METHOD, callback)
            XposedBridge.log("$TAG Hook auf $ASSISTANT_CONTROLLER_CLASS.$START_SESSION_METHOD gesetzt (${hooked?.size ?: 0} Methode[n])")
        } catch (t: Throwable) {
            // Typische Ursache: AA-Update hat die obfuskierten Namen geändert.
            XposedBridge.log("$TAG Hook fehlgeschlagen – vermutlich andere AA-Version: ${t.message}")
            XposedBridge.log(t)
        }
    }

    /**
     * Startet den vom System gesetzten Sprach-Assistenten – dasselbe Ziel wie ein Druck auf die
     * Assistententaste des Handys. Liegt Claude dort als Standard, kommt Claude.
     */
    private fun launchDefaultAssistant(): Boolean {
        val app = AndroidAppHelper.currentApplication() ?: run {
            XposedBridge.log("$TAG keine Application – kann Assistent nicht starten")
            return false
        }
        for (action in ASSIST_ACTIONS) {
            try {
                val intent = Intent(action).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
                if (intent.resolveActivity(app.packageManager) == null) continue
                app.startActivity(intent)
                XposedBridge.log("$TAG Assistent gestartet via $action")
                return true
            } catch (t: Throwable) {
                XposedBridge.log("$TAG $action fehlgeschlagen: ${t.message}")
            }
        }
        XposedBridge.log("$TAG kein Assistent-Intent ließ sich auflösen")
        return false
    }

    private companion object {
        const val TAG = "[ClaudeAA]"
        const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"

        // Obfuskierte Namen aus AA 17.7.663654 – bei Updates neu ermitteln.
        const val ASSISTANT_CONTROLLER_CLASS = "tfl"
        const val START_SESSION_METHOD = "k"

        // ACTION_VOICE_COMMAND entspricht dem Assistenten-Knopf; ASSIST als Rückfall.
        val ASSIST_ACTIONS = listOf(
            Intent.ACTION_VOICE_COMMAND,
            Intent.ACTION_ASSIST,
        )
    }
}
