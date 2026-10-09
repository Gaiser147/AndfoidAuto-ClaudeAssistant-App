package io.github.gaiser147.claudeauto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.gaiser147.claudeauto.R
import kotlinx.coroutines.launch

/**
 * Hauptbildschirm: Statuszeile plus großer Sprechen-/Stopp-Button.
 *
 * Bewusst ein [PaneTemplate] mit festen Zeilentiteln: Ändert sich nur der Zeilentext, wertet
 * Android Auto das als Refresh und nicht als neuen Bildschirm, sodass Statuswechsel nicht
 * gegen das Limit an Bildschirmwechseln zählen.
 */
class AssistantScreen(
    carContext: CarContext,
    private val controller: AssistantController,
) : Screen(carContext) {

    private val accentColor = ContextCompat.getColor(carContext, R.color.claude_orange)
        .let { CarColor.createCustom(it, it) }

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.status.collect { invalidate() }
            }
        }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = controller.stop()
        })
    }

    override fun onGetTemplate(): Template {
        val status = controller.status.value

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.status_title))
                    .addText(carContext.getString(status.label))
                    .addText(carContext.getString(if (status.isBusy) R.string.hint_busy else R.string.hint_idle))
                    .build()
            )
            .setImage(icon(if (status.isBusy) R.drawable.ic_stop else R.drawable.ic_mic))
            .addAction(if (status.isBusy) stopAction() else talkAction())
            .build()

        val builder = PaneTemplate.Builder(pane)
        if (carContext.carAppApiLevel >= 7) {
            builder.setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.app_name))
                    .setStartHeaderAction(Action.APP_ICON)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setTitle(carContext.getString(R.string.app_name)).setHeaderAction(Action.APP_ICON)
        }
        return builder.build()
    }

    private fun talkAction(): Action = Action.Builder()
        .setTitle(carContext.getString(R.string.action_talk))
        .setIcon(icon(R.drawable.ic_mic))
        .setBackgroundColor(accentColor)
        .setFlags(Action.FLAG_PRIMARY)
        .setOnClickListener { controller.start() }
        .build()

    private fun stopAction(): Action = Action.Builder()
        .setTitle(carContext.getString(R.string.action_stop))
        .setIcon(icon(R.drawable.ic_stop))
        .setFlags(Action.FLAG_PRIMARY)
        .setOnClickListener { controller.stop() }
        .build()

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
}
