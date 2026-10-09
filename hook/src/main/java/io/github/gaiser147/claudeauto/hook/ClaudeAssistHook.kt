package io.github.gaiser147.claudeauto.hook

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
        val app = currentApplication() ?: run {
            XposedBridge.log("$TAG keine Application – kann Assistent nicht starten")
            return false
        }
        val assistantPkg = defaultAssistantPackage(app)
        XposedBridge.log("$TAG Standard-Assistent-Paket: ${assistantPkg ?: "unbekannt"}")

        // ACTION_VOICE_COMMAND zeigt einen Auswahldialog aller Sprach-Apps (und enthält Claude nicht).
        // Deshalb den Assistenten der System-Rolle gezielt über ACTION_ASSIST ansteuern.
        val attempts = buildList {
            // 1) ACTION_ASSIST gezielt an das Standard-Assistenten-Paket (kein Dialog).
            if (assistantPkg != null) add(Intent(Intent.ACTION_ASSIST).setPackage(assistantPkg))
            // 2) ACTION_ASSIST ohne Paket – das System leitet zum Rolleninhaber.
            add(Intent(Intent.ACTION_ASSIST))
            // 3) Rückfall: normaler Start des Assistenten-Pakets.
            if (assistantPkg != null) {
                app.packageManager.getLaunchIntentForPackage(assistantPkg)?.let { add(it) }
            }
        }
        for (intent in attempts) {
            if (tryStart(app, intent)) return true
        }
        XposedBridge.log("$TAG kein Assistent ließ sich starten")
        return false
    }

    private fun tryStart(app: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        val label = "${intent.action ?: "LAUNCH"} pkg=${intent.`package` ?: "-"}"
        if (intent.resolveActivity(app.packageManager) == null) {
            XposedBridge.log("$TAG nicht auflösbar: $label")
            false
        } else {
            app.startActivity(intent)
            XposedBridge.log("$TAG Assistent gestartet: $label")
            true
        }
    } catch (t: Throwable) {
        XposedBridge.log("$TAG Start fehlgeschlagen: ${t.message}")
        false
    }

    /** Liest das als Standard-Assistent gesetzte Paket aus den Systemeinstellungen. */
    private fun defaultAssistantPackage(app: Context): String? {
        for (key in listOf("assistant", "voice_interaction_service")) {
            val value = Settings.Secure.getString(app.contentResolver, key)
            if (!value.isNullOrBlank()) return value.substringBefore('/')
        }
        return null
    }

    /**
     * Liefert den Application-Context des Android-Auto-Prozesses. LSPosed stellt [AndroidAppHelper]
     * in diesem Build nicht bereit, deshalb über die Framework-Klasse ActivityThread per Reflection.
     */
    private fun currentApplication(): Context? = try {
        val activityThread = Class.forName("android.app.ActivityThread")
        activityThread.getMethod("currentApplication").invoke(null) as? Context
    } catch (t: Throwable) {
        XposedBridge.log("$TAG ActivityThread.currentApplication fehlgeschlagen: ${t.message}")
        null
    }

    private companion object {
        const val TAG = "[ClaudeAA]"
        const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"

        // Obfuskierte Namen aus AA 17.7.663654 – bei Updates neu ermitteln.
        const val ASSISTANT_CONTROLLER_CLASS = "tfl"
        const val START_SESSION_METHOD = "k"
    }
}
