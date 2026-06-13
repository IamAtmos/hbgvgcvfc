package com.studyflow.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.studyflow.app.MainActivity
import com.studyflow.app.ui.formatTime

class TimerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs     = context.getSharedPreferences("studyflow_timer", Context.MODE_PRIVATE)
        val remaining = prefs.getLong("remaining_ms", 25 * 60_000L)
        val isRunning = prefs.getBoolean("is_running", false)
        val isFinished = prefs.getBoolean("is_finished", false)

        provideContent {
            TimerWidgetContent(remaining, isRunning, isFinished)
        }
    }
}

@Composable
private fun TimerWidgetContent(remaining: Long, isRunning: Boolean, isFinished: Boolean) {
    val bg       = ColorProvider(Color(0xFF1E1E1E))
    val green    = ColorProvider(Color(0xFF69FF47))
    val textMain = ColorProvider(Color(0xFFFFFFFF))
    val textMute = ColorProvider(Color(0xFF9E9E9E))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .padding(12.dp)
            .cornerRadius(18.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "⏳",
                style = TextStyle(fontSize = 22.sp),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    if (isFinished) "Done! ✓" else formatTime(remaining),
                    style = TextStyle(
                        color      = if (isFinished) green else textMain,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    when {
                        isFinished -> "Tap to restart"
                        isRunning  -> "Running"
                        else       -> "Paused"
                    },
                    style = TextStyle(
                        color    = if (isRunning) green else textMute,
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }
}

class TimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TimerWidget()
}
