package io.github.gaiser147.claudeauto.hook

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView

/**
 * Reiner Infobildschirm. Die eigentliche Arbeit macht der Hook im Android-Auto-Prozess,
 * sobald das Modul in LSPosed aktiviert und für Android Auto freigegeben ist.
 */
class HookInfoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val text = TextView(this).apply {
            text = INFO
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(text) })
    }

    private companion object {
        const val INFO = """Claude-Assistent für Android Auto – Hook-Modul

Dieses Modul leitet in Android Auto die Sprach-/Lenkradtaste auf den Standard-Assistenten des Handys um (statt auf Google/Gemini).

Einrichtung:
1. In LSPosed dieses Modul aktivieren.
2. Als Anwendungsbereich NUR „Android Auto“ auswählen.
3. Android Auto zwangsstoppen (oder Handy neu starten), damit der Hook greift.
4. In Magisk Android Auto Root gewähren. Beim ersten Tastendruck erscheint die Abfrage; mit „Für immer zulassen“ bestätigen.

So funktioniert es:
Claude ist als Assistenten-Dienst (VoiceInteractionService) eingebunden und lässt sich nicht per Intent starten. Der Hook injiziert deshalb den Assistenten-Tastendruck (KEYCODE_ASSIST) per Root – genau wie der Power-Knopf – und Android startet den Standard-Assistenten.

Prüfen:
• Im LSPosed-Log nach Einträgen mit [ClaudeAA] suchen.
• „Hook ... gesetzt“ = richtige Stelle gefunden.
• Beim Tastendruck: „Auslöser erkannt“ und „KEYCODE 219 gesendet“.
• „Injektion fehlgeschlagen“ = Android Auto hat in Magisk kein Root.

Achtung:
• Getestet gegen Android Auto 17.7.663654. Nach einem AA-Update kann der Hook brechen; dann im Log „Hook fehlgeschlagen“ und die Stelle muss neu ermittelt werden.
• Automatische Updates für Android Auto im Play Store am besten abschalten.
• Erst in der Desktop Head Unit testen, nicht während der Fahrt."""
    }
}
