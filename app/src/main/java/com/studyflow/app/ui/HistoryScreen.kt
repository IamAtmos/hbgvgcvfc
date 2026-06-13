package com.studyflow.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyflow.app.data.StudySession
import com.studyflow.app.ui.theme.*
import com.studyflow.app.viewmodel.StudyViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: StudyViewModel) {
    val allSessions by viewModel.allSessions.collectAsState()
    val grouped = remember(allSessions) {
        allSessions.groupBy { it.date }.entries.sortedByDescending { it.key }
    }

    if (grouped.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📚", fontSize = 52.sp)
                Text("No history yet", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("Finish a block to see it here", color = TextSecondary, fontSize = 14.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        grouped.forEach { (date, dateSessions) ->
            item(key = date) {
                DayCard(date = date, sessions = dateSessions, viewModel = viewModel)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ─── Day Card ─────────────────────────────────────────────────────────────────

@Composable
private fun DayCard(date: String, sessions: List<StudySession>, viewModel: StudyViewModel) {
    val totalMs    = sessions.sumOf { it.durationMillis }
    val totalTests = sessions.sumOf { it.testCount }

    val bySubject = sessions
        .groupBy { it.subjectName }
        .entries
        .sortedByDescending { (_, s) -> s.sumOf { it.durationMillis } }

    val segmentData = bySubject.map { (_, s) ->
        val dur   = s.sumOf { it.durationMillis }
        val color = SubjectColors[s.first().subjectColorIndex % SubjectColors.size]
        val frac  = if (totalMs > 0) dur.toFloat() / totalMs else 0f
        Triple(color, frac, dur)
    }

    val displayDate = remember(date) {
        runCatching {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)!!
            SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(d)
        }.getOrDefault(date)
    }

    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Surface).padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // Date header
            Text(displayDate, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

            // Circle + subjects row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                    SegmentedArc(segments = segmentData, modifier = Modifier.size(110.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatDurationShort(totalMs), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (totalTests > 0) {
                            Spacer(Modifier.height(2.dp))
                            Text("$totalTests tests", color = TextSecondary, fontSize = 10.sp)
                        }
                    }
                }

                // Subject rows — clickable to expand notes
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bySubject.forEach { (subjectName, subSessions) ->
                        SubjectRow(subjectName = subjectName, sessions = subSessions)
                    }
                }
            }

            // Footer pills
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("⏱ ${formatDurationShort(totalMs)}", "Total")
                if (totalTests > 0) StatPill("📝 $totalTests", "Tests")
            }
        }
    }
}

// ─── Subject Row with expandable notes ───────────────────────────────────────

@Composable
private fun SubjectRow(subjectName: String, sessions: List<StudySession>) {
    val totalDur   = sessions.sumOf { it.durationMillis }
    val totalTests = sessions.sumOf { it.testCount }
    val colorIdx   = sessions.first().subjectColorIndex
    val color      = SubjectColors[colorIdx % SubjectColors.size]

    // Notes = all non-blank notes from every session of this subject this day
    val notes = sessions.filter { it.note.isNotBlank() }.map { it.note }

    var expanded by remember { mutableStateOf(false) }
    val hasNotes = notes.isNotEmpty()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .then(if (hasNotes) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Text(
                    subjectName,
                    color = TextPrimary, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (hasNotes) {
                    Text(
                        if (expanded) "▲" else "▼",
                        color = TextSecondary.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDurationShort(totalDur), color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (totalTests > 0) Text("$totalTests tests", color = TextSecondary, fontSize = 11.sp)
            }
        }

        // Expandable notes section
        AnimatedVisibility(
            visible = expanded && hasNotes,
            enter   = expandVertically(),
            exit    = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                    .background(SurfaceVariant.copy(alpha = 0.6f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                notes.forEach { note ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text("·", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            note,
                            color    = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

// ─── Stat Pill ────────────────────────────────────────────────────────────────

@Composable
private fun StatPill(value: String, label: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

// ─── Arc Canvas ───────────────────────────────────────────────────────────────

@Composable
private fun SegmentedArc(
    segments: List<Triple<Color, Float, Long>>,
    modifier: Modifier = Modifier,
) {
    val strokeWidth = 14.dp
    Canvas(modifier = modifier) {
        val stroke  = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        val inset   = strokeWidth.toPx() / 2f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        var startAngle = -90f

        if (segments.isEmpty()) {
            drawArc(SurfaceVariant, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidth.toPx()))
            return@Canvas
        }
        segments.forEach { (color, frac, _) ->
            val sweep = frac * 360f * 0.98f
            drawArc(color, startAngle, sweep, false, topLeft, arcSize, style = stroke)
            startAngle += frac * 360f
        }
    }
}
