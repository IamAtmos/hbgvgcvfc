package com.studyflow.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyflow.app.data.StudySession
import com.studyflow.app.ui.theme.*
import com.studyflow.app.viewmodel.StudyViewModel
import java.text.SimpleDateFormat
import java.util.*

private data class DayStats(val label: String, val date: String, val totalMs: Long, val tests: Int)
private data class WeekStats(val label: String, val days: List<DayStats>) {
    val totalMs: Long get() = days.sumOf { it.totalMs }
    val totalTests: Int get() = days.sumOf { it.tests }
}

private fun buildWeeks(sessions: List<StudySession>): List<WeekStats> {
    if (sessions.isEmpty()) return emptyList()
    val fmt     = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dayFmt  = SimpleDateFormat("EEE", Locale.ENGLISH)
    val weekFmt = SimpleDateFormat("w_yyyy", Locale.getDefault())

    val byDate  = sessions.groupBy { it.date }
    val byWeek  = mutableMapOf<String, MutableList<DayStats>>()

    byDate.keys.sorted().forEach { dateStr ->
        val date = fmt.parse(dateStr) ?: return@forEach
        val key  = weekFmt.format(date)
        val s    = byDate[dateStr] ?: emptyList()
        byWeek.getOrPut(key) { mutableListOf() }
            .add(DayStats(dayFmt.format(date).take(3), dateStr, s.sumOf { it.durationMillis }, s.sumOf { it.testCount }))
    }

    return byWeek.entries.sortedBy { it.key }
        .mapIndexed { idx, (_, days) -> WeekStats("Week ${idx + 1}", days.sortedBy { it.date }) }
}

private fun computeBestDay(sessions: List<StudySession>): Pair<String, Long>? {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sessions.groupBy { it.date }
        .mapValues { (_, s) -> s.sumOf { it.durationMillis } }
        .maxByOrNull { it.value }
        ?.let { (date, ms) ->
            val label = runCatching {
                SimpleDateFormat("MMM d", Locale.ENGLISH).format(fmt.parse(date)!!)
            }.getOrDefault(date)
            Pair(label, ms)
        }
}

@Composable
fun WeeklyScreen(viewModel: StudyViewModel) {
    val allSessions by viewModel.allSessions.collectAsState()
    val streak      by viewModel.streak.collectAsState()
    val weeks   = remember(allSessions) { buildWeeks(allSessions) }
    val bestDay = remember(allSessions) { computeBestDay(allSessions) }
    val bestWeek = remember(weeks) { weeks.maxByOrNull { it.totalMs } }

    if (weeks.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📈", fontSize = 52.sp)
                Text("No data yet", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("Study some sessions to see your stats", color = TextSecondary, fontSize = 14.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Streak + Records ───────────────────────────────────────────────────
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Streak card
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(Surface).padding(16.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("🔥", fontSize = 28.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("$streak", color = Primary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Day Streak", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                // Best Day
                if (bestDay != null) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(Surface).padding(16.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("⭐", fontSize = 28.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(formatDurationShort(bestDay.second), color = Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Best Day · ${bestDay.first}", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }

                // Best Week
                if (bestWeek != null && weeks.size > 1) {
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(Surface).padding(16.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("🏆", fontSize = 28.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(formatDurationShort(bestWeek.totalMs), color = Primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Best · ${bestWeek.label}", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // ── This Week ─────────────────────────────────────────────────────────
        item {
            val latestWeek = weeks.last()
            SectionLabel("This Week")
            Spacer(Modifier.height(8.dp))
            WeekDetailCard(week = latestWeek)
        }

        // ── Week Comparison ───────────────────────────────────────────────────
        if (weeks.size > 1) {
            item {
                Spacer(Modifier.height(2.dp))
                SectionLabel("All Weeks")
                Spacer(Modifier.height(8.dp))
                WeekComparisonCard(weeks = weeks)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

// ─── Week Detail Card ─────────────────────────────────────────────────────────

@Composable
private fun WeekDetailCard(week: WeekStats) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(week.label, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatDurationShort(week.totalMs), color = Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (week.totalTests > 0) Text("${week.totalTests} tests", color = TextSecondary, fontSize = 12.sp)
                }
            }
            if (week.days.isNotEmpty()) {
                DailyBarChart(days = week.days, maxMs = week.days.maxOf { it.totalMs }.coerceAtLeast(1L))
            }
        }
    }
}

@Composable
private fun DailyBarChart(days: List<DayStats>, maxMs: Long) {
    val maxTests = days.maxOf { it.tests }.coerceAtLeast(1)
    val barH     = 130.dp

    Box(modifier = Modifier.fillMaxWidth().height(barH + 36.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(barH)) {
            val slotW    = size.width / days.size
            val barW     = slotW * 0.42f
            val testBarW = slotW * 0.18f
            val maxH     = size.height * 0.9f

            days.forEachIndexed { i, day ->
                val cx = slotW * i + slotW / 2f
                val studyH = (day.totalMs.toFloat() / maxMs * maxH).coerceAtLeast(if (day.totalMs > 0) 6f else 0f)
                if (studyH > 0f) {
                    drawRoundRect(
                        color = Color(0xFF69FF47),
                        topLeft = Offset(cx - barW / 2f - testBarW / 2f, size.height - studyH),
                        size = Size(barW, studyH),
                        cornerRadius = CornerRadius(6f, 6f),
                    )
                }
                if (day.tests > 0) {
                    val testH = (day.tests.toFloat() / maxTests * maxH).coerceAtLeast(6f)
                    drawRoundRect(
                        color = Color(0xFF4FC3F7).copy(alpha = 0.8f),
                        topLeft = Offset(cx + barW / 2f - testBarW / 2f + 2f, size.height - testH),
                        size = Size(testBarW, testH),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceAround) {
            days.forEach { day ->
                Text(day.label, color = TextSecondary, fontSize = 11.sp,
                    modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendDot(Primary, "Study time")
        LegendDot(Color(0xFF4FC3F7), "Tests")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

// ─── Week Comparison ─────────────────────────────────────────────────────────

@Composable
private fun WeekComparisonCard(weeks: List<WeekStats>) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface).padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Week Comparison", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val maxMs    = weeks.maxOf { it.totalMs }.coerceAtLeast(1L)
            val maxTests = weeks.maxOf { it.totalTests }.coerceAtLeast(1)

            weeks.reversed().forEach { week ->
                val studyFrac = week.totalMs.toFloat() / maxMs
                val testFrac  = if (week.totalTests > 0) week.totalTests.toFloat() / maxTests else 0f
                val isBest    = week.totalMs == maxMs

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(week.label, color = TextPrimary, fontSize = 13.sp)
                            if (isBest && weeks.size > 1) Text("🏆", fontSize = 11.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(formatDurationShort(week.totalMs), color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            if (week.totalTests > 0) Text("${week.totalTests} tests", color = Color(0xFF4FC3F7), fontSize = 12.sp)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(SurfaceVariant)) {
                        Box(Modifier.fillMaxWidth(studyFrac).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Primary))
                    }
                    if (testFrac > 0f) {
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(SurfaceVariant)) {
                            Box(Modifier.fillMaxWidth(testFrac).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color(0xFF4FC3F7).copy(0.7f)))
                        }
                    }
                }
            }
        }
    }
}
