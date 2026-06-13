package com.studyflow.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studyflow.app.data.Subject
import com.studyflow.app.ui.theme.*
import com.studyflow.app.viewmodel.StudyState
import com.studyflow.app.viewmodel.StudyViewModel

@Composable
fun StudyScreen(
    viewModel: StudyViewModel,
    onHistoryClick: () -> Unit,
) {
    val studyState   by viewModel.studyState.collectAsState()
    val subjects     by viewModel.subjects.collectAsState()
    val totalToday   by viewModel.totalTodayMillis.collectAsState()
    val currentBlock by viewModel.currentBlockMillis.collectAsState()
    val dailyGoal    by viewModel.dailyGoalMillis.collectAsState()
    val allSessions  by viewModel.allSessions.collectAsState()

    // Today's sessions for the goal bar segments
    val todaySessions = remember(allSessions) {
        allSessions.filter { it.date == viewModel.getToday() }
    }

    var showFinishSheet  by remember { mutableStateOf(false) }
    var showAddSubject   by remember { mutableStateOf(false) }
    var showManage       by remember { mutableStateOf(false) }
    var showGoalDialog   by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Header ────────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Study", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderChip("⚙") { showManage = true }
                    HeaderChip("📊  History") { onHistoryClick() }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Total Today card ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface)
                    .padding(vertical = 24.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Today", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        formatDuration(totalToday),
                        color = Primary, fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp,
                    )
                    AnimatedVisibility(studyState == StudyState.RUNNING) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(4.dp))
                            Text("Block · ${formatDuration(currentBlock)}", color = Primary.copy(0.55f), fontSize = 13.sp)
                        }
                    }
                    AnimatedVisibility(studyState == StudyState.PAUSED) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(4.dp))
                            Text("⏸ Paused · ${formatDuration(currentBlock)}", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Daily Goal bar (compact) ───────────────────────────────────────
            DailyGoalBar(
                totalMs      = totalToday,
                goalMs       = dailyGoal,
                sessions     = todaySessions,
                onEditGoal   = { showGoalDialog = true },
            )

            Spacer(Modifier.height(20.dp))

            // ── Buttons ───────────────────────────────────────────────────────
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (studyState) {
                    StudyState.IDLE -> {
                        StudyActionButton("▶   Let's Start", Primary, OnPrimary) { viewModel.startStudy() }
                    }
                    StudyState.RUNNING -> {
                        StudyActionButton("✓   Finish Block", Primary, OnPrimary) { showFinishSheet = true }
                        StudyActionButton("⏸   Pause", SurfaceVariant, TextPrimary) { viewModel.pauseStudy() }
                        StudyActionButton("⬛   End Day", Surface, TextSecondary) { viewModel.endDay() }
                    }
                    StudyState.PAUSED -> {
                        StudyActionButton("▶   Resume", Primary, OnPrimary) { viewModel.resumeStudy() }
                        StudyActionButton("✓   Finish Block", SurfaceVariant, TextPrimary) { showFinishSheet = true }
                        StudyActionButton("⬛   End Day", Surface, TextSecondary) { viewModel.endDay() }
                    }
                }
            }
        }

        if (showFinishSheet) {
            FinishBlockSheet(
                subjects     = subjects,
                onSave       = { subject, note, tests ->
                    viewModel.finishBlock(subject, note, tests)
                    showFinishSheet = false
                },
                onDismiss    = { showFinishSheet = false },
                onAddSubject = { showAddSubject = true },
            )
        }
        if (showAddSubject) {
            AddSubjectDialog(
                onAdd     = { name, colorIdx -> viewModel.addSubject(name, colorIdx); showAddSubject = false },
                onDismiss = { showAddSubject = false },
            )
        }
        if (showManage) {
            ManageSubjectsSheet(
                subjects  = subjects,
                onRename  = { subject, name -> viewModel.renameSubject(subject, name) },
                onDelete  = { viewModel.deleteSubject(it) },
                onAdd     = { showAddSubject = true },
                onDismiss = { showManage = false },
            )
        }
        if (showGoalDialog) {
            GoalDialog(
                currentGoalMs = dailyGoal,
                onSet         = { viewModel.setDailyGoal(it); showGoalDialog = false },
                onDismiss     = { showGoalDialog = false },
            )
        }
    }
}

// ─── Daily Goal Bar ────────────────────────────────────────────────────────────

@Composable
private fun DailyGoalBar(
    totalMs: Long,
    goalMs: Long,
    sessions: List<com.studyflow.app.data.StudySession>,
    onEditGoal: () -> Unit,
) {
    val progress = (totalMs.toFloat() / goalMs.toFloat()).coerceIn(0f, 1f)
    val animProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(600), label = "goal")

    // Build segments from today's sessions grouped by subject
    val segments = remember(sessions) {
        sessions.groupBy { it.subjectName }
            .entries.sortedByDescending { (_, s) -> s.sumOf { it.durationMillis } }
            .map { (_, s) -> Pair(s.sumOf { it.durationMillis }, s.first().subjectColorIndex) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .clickable { onEditGoal() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Daily Goal",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatDurationShort(totalMs)}  /  ${formatDurationShort(goalMs)}",
                        color = if (progress >= 1f) Primary else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (progress >= 1f) Text("✓", color = Primary, fontSize = 12.sp)
                }
            }

            // Segmented bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SurfaceVariant),
            ) {
                if (segments.isEmpty()) {
                    // Simple green fill when no subject breakdown
                    Box(
                        Modifier
                            .fillMaxWidth(animProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Primary.copy(alpha = 0.6f))
                    )
                } else {
                    // Subject-colored segments
                    Row(Modifier.fillMaxWidth(animProgress).fillMaxHeight()) {
                        segments.forEach { (ms, colorIdx) ->
                            val segFrac = (ms.toFloat() / totalMs.toFloat()).coerceIn(0.01f, 1f)
                            Box(
                                Modifier
                                    .weight(segFrac)
                                    .fillMaxHeight()
                                    .background(SubjectColors[colorIdx % SubjectColors.size])
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Goal Dialog ──────────────────────────────────────────────────────────────

@Composable
private fun GoalDialog(currentGoalMs: Long, onSet: (Long) -> Unit, onDismiss: () -> Unit) {
    val currentHours = (currentGoalMs / 3600_000L).toInt().coerceAtLeast(1)
    var hours by remember { mutableIntStateOf(currentHours) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = Surface,
        title            = { Text("Daily Goal", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${hours}h per day", color = Primary, fontSize = 28.sp, fontWeight = FontWeight.Light)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(2, 4, 6, 8, 10, 12).forEach { h ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (hours == h) Primary.copy(0.18f) else SurfaceVariant)
                                .border(1.dp, if (hours == h) Primary else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { hours = h }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) { Text("${h}h", color = if (hours == h) Primary else TextSecondary, fontSize = 13.sp) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(SurfaceVariant).clickable { if (hours > 1) hours-- },
                        contentAlignment = Alignment.Center,
                    ) { Text("−", color = TextPrimary, fontSize = 20.sp) }
                    Text("${hours}h", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(SurfaceVariant).clickable { if (hours < 24) hours++ },
                        contentAlignment = Alignment.Center,
                    ) { Text("+", color = TextPrimary, fontSize = 20.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(hours * 3600_000L) }) {
                Text("Set Goal", color = Primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun HeaderChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Surface)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(text, color = TextSecondary, fontSize = 12.sp) }
}

@Composable
private fun StudyActionButton(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(bg).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

// ─── Finish Block Sheet ────────────────────────────────────────────────────────

@Composable
private fun FinishBlockSheet(
    subjects: List<Subject>,
    onSave: (Subject, String, Int) -> Unit,
    onDismiss: () -> Unit,
    onAddSubject: () -> Unit,
) {
    var selectedSubject by remember(subjects) { mutableStateOf(subjects.firstOrNull()) }
    var note      by remember { mutableStateOf("") }
    var testInput by remember { mutableStateOf("") }
    var expanded  by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable { onDismiss() })
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Surface).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Finish Block", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.clickable { onDismiss() })
            }
            Text("Select Subject", color = TextSecondary, fontSize = 13.sp)
            if (subjects.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariant).clickable { onAddSubject() }.padding(16.dp),
                    contentAlignment = Alignment.Center) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, tint = Primary)
                        Text("Add your first subject", color = Primary)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(SurfaceVariant)
                    .border(1.dp, if (expanded) Primary else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            selectedSubject?.let { Box(Modifier.size(10.dp).clip(CircleShape).background(SubjectColors[it.colorIndex % SubjectColors.size])) }
                            Text(selectedSubject?.name ?: "Select…", color = TextPrimary)
                        }
                        Text(if (expanded) "▲" else "▼", color = TextSecondary, fontSize = 12.sp)
                    }
                }
                AnimatedVisibility(expanded) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(12.dp)).background(SurfaceVariant),
                        contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(subjects) { s ->
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (selectedSubject?.id == s.id) Primary.copy(0.15f) else Color.Transparent)
                                .clickable { selectedSubject = s; expanded = false }.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(SubjectColors[s.colorIndex % SubjectColors.size]))
                                Text(s.name, color = TextPrimary, fontSize = 15.sp)
                            }
                        }
                        item {
                            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { onAddSubject(); expanded = false }.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, null, tint = Primary, modifier = Modifier.size(16.dp))
                                Text("Add Subject", color = Primary, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
            Text("Note (optional)", color = TextSecondary, fontSize = 13.sp)
            OutlinedTextField(value = note, onValueChange = { note = it },
                placeholder = { Text("e.g. Chapter 3", color = TextSecondary) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = sheetFieldColors(), shape = RoundedCornerShape(12.dp))
            Text("Tests (optional)", color = TextSecondary, fontSize = 13.sp)
            OutlinedTextField(value = testInput, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 4) testInput = v },
                placeholder = { Text("e.g. 40", color = TextSecondary) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = sheetFieldColors(), shape = RoundedCornerShape(12.dp))
            val canSave = selectedSubject != null
            Box(modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                .background(if (canSave) Primary else SurfaceVariant)
                .clickable(enabled = canSave) { selectedSubject?.let { onSave(it, note, testInput.toIntOrNull() ?: 0) } },
                contentAlignment = Alignment.Center) {
                Text("Save", color = if (canSave) OnPrimary else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun sheetFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
    focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVariant, cursorColor = Primary,
    focusedContainerColor = SurfaceVariant, unfocusedContainerColor = SurfaceVariant,
)

@Composable
private fun ManageSubjectsSheet(subjects: List<Subject>, onRename: (Subject, String) -> Unit,
    onDelete: (Subject) -> Unit, onAdd: () -> Unit, onDismiss: () -> Unit) {
    var editingSubject by remember { mutableStateOf<Subject?>(null) }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable { onDismiss() })
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Surface).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Manage Subjects", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.clickable { onDismiss() })
            }
            if (subjects.isEmpty()) Text("No subjects yet.", color = TextSecondary)
            else LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(subjects, key = { it.id }) { subject ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariant).padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(SubjectColors[subject.colorIndex % SubjectColors.size]))
                            Text(subject.name, color = TextPrimary, fontSize = 15.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.Edit, null, tint = TextSecondary, modifier = Modifier.size(18.dp).clickable { editingSubject = subject })
                            Icon(Icons.Default.Delete, null, tint = TextSecondary.copy(0.7f), modifier = Modifier.size(18.dp).clickable { onDelete(subject) })
                        }
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(SurfaceVariant).clickable { onAdd() }.padding(14.dp), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text("Add Subject", color = Primary, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    editingSubject?.let { subject ->
        RenameDialog(current = subject.name, onRename = { newName -> onRename(subject, newName); editingSubject = null }, onDismiss = { editingSubject = null })
    }
}

@Composable
private fun RenameDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Surface,
        title = { Text("Rename Subject", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVariant, cursorColor = Primary)) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onRename(text.trim()) }, enabled = text.isNotBlank()) { Text("Save", color = Primary, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } })
}

@Composable
private fun AddSubjectDialog(onAdd: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Surface,
        title = { Text("New Subject", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Name", color = TextSecondary) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVariant, cursorColor = Primary))
            Text("Color", color = TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubjectColors.forEachIndexed { idx, color ->
                    Box(Modifier.size(28.dp).clip(CircleShape).background(color)
                        .border(if (selectedColor == idx) 3.dp else 0.dp, TextPrimary, CircleShape)
                        .clickable { selectedColor = idx })
                }
            }
        }},
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onAdd(name.trim(), selectedColor) }, enabled = name.isNotBlank()) { Text("Add", color = Primary, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } })
}
