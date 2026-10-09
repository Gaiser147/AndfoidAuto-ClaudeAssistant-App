package io.github.gaiser147.claudeauto

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView

/** Handy-Bildschirm mit Hinweisen; fragt das Mikrofonrecht an. Die eigentliche App läuft in Android Auto. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val text = TextView(this).apply {
            setText(R.string.phone_info)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(padding, padding, padding, padding)
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(text)
        })

        // Mikrofonrecht am besten schon hier erteilen; im Auto erscheint die Abfrage sonst während der Fahrt auf dem Handy.
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }

    private companion object {
        const val REQUEST_RECORD_AUDIO = 1
    }
}
