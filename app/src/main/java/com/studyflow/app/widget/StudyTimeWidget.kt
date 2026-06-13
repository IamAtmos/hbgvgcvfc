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
import com.studyflow.app.data.StudyDatabase
import com.studyflow.app.data.StudySession
import com.studyflow.app.ui.formatDurationShort
import java.text.SimpleDateFormat
import java.util.*

class StudyTimeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs   = context.getSharedPreferences("studyflow_session", Context.MODE_PRIVATE)
        val dao     = StudyDatabase.getDatabase(context).studyDao()
        val today   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sessions = dao.getSessionsForDate(today)

        // Calculate live block time
        val state        = prefs.getString("state", "IDLE") ?: "IDLE"
        val sessionStart = prefs.getLong("session_start", 0L)
        val pausedTotal  = prefs.getLong("paused_total", 0L)
        val pauseStart   = prefs.getLong("pause_start", 0L)
        val localAccum   = prefs.getLong("local_accum_today", 0L)
        val dayReset     = prefs.getLong("day_reset_time", 0L)

        val liveMs = when (state) {
            "RUNNING" -> maxOf(0L, System.currentTimeMillis() - sessionStart - pausedTotal)
            "PAUSED"  -> maxOf(0L, pauseStart - sessionStart - pausedTotal)
            else      -> 0L
        }
        val savedMs = sessions.filter { it.timestamp > dayReset }.sumOf { it.durationMillis }
        val totalMs = maxOf(savedMs, localAccum) + liveMs

        // Group by subject for bar
        val bySubject = sessions
            .filter { it.timestamp > dayReset }
            .groupBy { it.subjectName }
            .entries.sortedByDescending { (_, s) -> s.sumOf { it.durationMillis } }
            .take(4)

        val displayDate = runCatching {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(today)!!
            SimpleDateFormat("MMM d", Locale.ENGLISH).format(d)
        }.getOrDefault(today)

        provideContent {
            StudyTimeWidgetContent(
                date      = displayDate,
                totalMs   = totalMs,
                bySubject = bySubject.map { (name, sessions) ->
                    Triple(name, sessions.sumOf { it.durationMillis }, sessions.first().subjectColorIndex)
                },
                isRunning = state == "RUNNING",
            )
        }
    }
}

private val widgetSubjectColors = listOf(
    Color(0xFF69FF47), Color(0xFF4FC3F7), Color(0xFFCE93D8),
    Color(0xFFFFB74D), Color(0xFFF48FB1), Color(0xFF80CBC4),
    Color(0xFFFFD54F), Color(0xFFEF9A9A),
)

@Composable
private fun StudyTimeWidgetContent(
    date: String,
    totalMs: Long,
    bySubject: List<Triple<String, Long, Int>>,
    isRunning: Boolean,
) {
    val bg         = ColorProvider(Color(0xFF1E1E1E))
    val textMain   = ColorProvider(Color(0xFFFFFFFF))
    val textMuted  = ColorProvider(Color(0xFF9E9E9E))
    val green      = ColorProvider(Color(0xFF69FF47))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .padding(14.dp)
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {

            // Header: date + live dot
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    date,
                    style = TextStyle(color = textMain, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (isRunning) {
                    Text(
                        "● live",
                        style = TextStyle(color = green, fontSize = 11.sp),
                    )
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            // Subject rows
            if (bySubject.isEmpty()) {
                Text(
                    "No study sessions today",
                    style = TextStyle(color = textMuted, fontSize = 12.sp),
                )
            } else {
                bySubject.forEach { (name, ms, colorIdx) ->
                    val subColor = ColorProvider(widgetSubjectColors[colorIdx % widgetSubjectColors.size])
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .size(7.dp)
                                .background(subColor)
                                .cornerRadius(4.dp),
                        ) {}
                        Spacer(GlanceModifier.width(7.dp))
                        Text(
                            name,
                            style = TextStyle(color = textMain, fontSize = 12.sp),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Text(
                            formatDurationShort(ms),
                            style = TextStyle(color = subColor, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }

            Spacer(GlanceModifier.defaultWeight())

            // Total time large
            Text(
                formatDurationShort(totalMs),
                style = TextStyle(color = green, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )

            Spacer(GlanceModifier.height(6.dp))

            // Segmented bar
            if (totalMs > 0 && bySubject.isNotEmpty()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp)
                        .background(ColorProvider(Color(0xFF2A2A2A))),
                ) {
                    bySubject.forEach { (_, ms, colorIdx) ->
                        val frac = (ms.toFloat() / totalMs).coerceIn(0.01f, 1f)
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxHeight()
                                .background(ColorProvider(widgetSubjectColors[colorIdx % widgetSubjectColors.size])),
                        ) {}
                    }
                }
            }
        }
    }
}

class StudyTimeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = StudyTimeWidget()
}
