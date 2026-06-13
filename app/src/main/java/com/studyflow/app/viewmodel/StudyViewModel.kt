package com.studyflow.app.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.studyflow.app.StudyForegroundService
import com.studyflow.app.data.StudyDatabase
import com.studyflow.app.data.StudySession
import com.studyflow.app.data.Subject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class StudyState { IDLE, RUNNING, PAUSED }

private const val PREFS_NAME        = "studyflow_session"
private const val KEY_STATE         = "state"
private const val KEY_SESSION_START = "session_start"
private const val KEY_PAUSED_TOTAL  = "paused_total"
private const val KEY_PAUSE_START   = "pause_start"
private const val KEY_DAY_RESET     = "day_reset_time"
private const val KEY_LOCAL_ACCUM   = "local_accum_today"
private const val KEY_SAVED_DATE    = "saved_date"
private const val KEY_DAILY_GOAL    = "daily_goal_ms"

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx   = application.applicationContext
    private val dao   = StudyDatabase.getDatabase(application).studyDao()
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val subjects: StateFlow<List<Subject>> = dao.getAllSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allSessions: StateFlow<List<StudySession>> = dao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _studyState         = MutableStateFlow(StudyState.IDLE)
    val studyState: StateFlow<StudyState> = _studyState

    private val _totalTodayMillis   = MutableStateFlow(0L)
    val totalTodayMillis: StateFlow<Long> = _totalTodayMillis

    private val _currentBlockMillis = MutableStateFlow(0L)
    val currentBlockMillis: StateFlow<Long> = _currentBlockMillis

    private val _dailyGoalMillis    = MutableStateFlow(prefs.getLong(KEY_DAILY_GOAL, 6 * 3600_000L))
    val dailyGoalMillis: StateFlow<Long> = _dailyGoalMillis

    private val _streak             = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak

    // In-memory state restored from prefs
    private var sessionStart    = 0L
    private var pauseStart      = 0L
    private var pausedTotal     = 0L
    private var dayResetTime    = 0L
    private var localAccumToday = 0L

    init {
        restoreState()
        viewModelScope.launch {
            while (true) {
                refreshTotals()
                delay(1_000)
            }
        }
        viewModelScope.launch {
            allSessions.collect { recalcStreak(it) }
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun restoreState() {
        val savedDate     = prefs.getString(KEY_SAVED_DATE, "") ?: ""
        val today         = getToday()
        val savedStateStr = prefs.getString(KEY_STATE, "IDLE") ?: "IDLE"
        val restoredState = runCatching { StudyState.valueOf(savedStateStr) }.getOrDefault(StudyState.IDLE)

        dayResetTime    = prefs.getLong(KEY_DAY_RESET, 0L)
        localAccumToday = if (savedDate == today) prefs.getLong(KEY_LOCAL_ACCUM, 0L) else 0L
        sessionStart    = prefs.getLong(KEY_SESSION_START, 0L)
        pausedTotal     = prefs.getLong(KEY_PAUSED_TOTAL, 0L)
        pauseStart      = prefs.getLong(KEY_PAUSE_START, 0L)

        _studyState.value = if (savedDate == today && sessionStart > 0L) restoredState else StudyState.IDLE
    }

    private fun persistState() {
        prefs.edit()
            .putString(KEY_STATE, _studyState.value.name)
            .putLong(KEY_SESSION_START, sessionStart)
            .putLong(KEY_PAUSED_TOTAL, pausedTotal)
            .putLong(KEY_PAUSE_START, pauseStart)
            .putLong(KEY_DAY_RESET, dayResetTime)
            .putLong(KEY_LOCAL_ACCUM, localAccumToday)
            .putString(KEY_SAVED_DATE, getToday())
            .apply()
    }

    // ── Live refresh ──────────────────────────────────────────────────────────

    private fun refreshTotals() {
        val today        = getToday()
        val savedDb      = allSessions.value
            .filter { it.date == today && it.timestamp > dayResetTime }
            .sumOf { it.durationMillis }
        val displayed    = maxOf(savedDb, localAccumToday)
        val currentBlock = liveBlockMillis()
        _currentBlockMillis.value = currentBlock
        _totalTodayMillis.value   = displayed + currentBlock
    }

    private fun liveBlockMillis(): Long = when (_studyState.value) {
        StudyState.RUNNING -> maxOf(0L, System.currentTimeMillis() - sessionStart - pausedTotal)
        StudyState.PAUSED  -> maxOf(0L, pauseStart - sessionStart - pausedTotal)
        StudyState.IDLE    -> 0L
    }

    // ── Streak ────────────────────────────────────────────────────────────────

    private fun recalcStreak(sessions: List<StudySession>) {
        val fmt   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dates = sessions.map { it.date }.toSortedSet().toList().sortedDescending()
        if (dates.isEmpty()) { _streak.value = 0; return }

        val today = getToday()
        var streak = 0
        val cal   = Calendar.getInstance()

        // Allow today or yesterday as starting point
        val startDate = if (dates.first() == today) today else {
            val yesterday = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -1) }
            val yStr = fmt.format(yesterday.time)
            if (dates.first() == yStr) yStr else return.also { _streak.value = 0 }
        }

        cal.time = fmt.parse(startDate) ?: return
        for (date in dates) {
            val expected = fmt.format(cal.time)
            if (date == expected) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else break
        }
        _streak.value = streak
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun startStudy() {
        sessionStart      = System.currentTimeMillis()
        pausedTotal       = 0L
        pauseStart        = 0L
        _studyState.value = StudyState.RUNNING
        persistState()
        startService()
    }

    fun pauseStudy() {
        pauseStart        = System.currentTimeMillis()
        _studyState.value = StudyState.PAUSED
        persistState()
        // Service stays running, shows "Paused" state
    }

    fun resumeStudy() {
        pausedTotal      += System.currentTimeMillis() - pauseStart
        pauseStart        = 0L
        _studyState.value = StudyState.RUNNING
        persistState()
        startService()
    }

    fun finishBlock(subject: Subject, note: String, testCount: Int) {
        val now        = System.currentTimeMillis()
        val extraPause = if (_studyState.value == StudyState.PAUSED) now - pauseStart else 0L
        val duration   = maxOf(0L, now - sessionStart - pausedTotal - extraPause)

        localAccumToday  += duration
        sessionStart      = 0L
        pausedTotal       = 0L
        pauseStart        = 0L
        _studyState.value = StudyState.IDLE
        persistState()
        stopService()

        viewModelScope.launch {
            dao.insertSession(
                StudySession(
                    subjectId         = subject.id,
                    subjectName       = subject.name,
                    subjectColorIndex = subject.colorIndex,
                    durationMillis    = duration,
                    testCount         = testCount,
                    note              = note,
                    date              = getToday(),
                )
            )
        }
    }

    fun endDay() {
        dayResetTime       = System.currentTimeMillis()
        localAccumToday    = 0L
        sessionStart       = 0L
        pausedTotal        = 0L
        pauseStart         = 0L
        _studyState.value  = StudyState.IDLE
        persistState()
        stopService()
    }

    // ── Daily Goal ────────────────────────────────────────────────────────────

    fun setDailyGoal(millis: Long) {
        _dailyGoalMillis.value = millis
        prefs.edit().putLong(KEY_DAILY_GOAL, millis).apply()
    }

    // ── Subject management ────────────────────────────────────────────────────

    fun addSubject(name: String, colorIndex: Int) = viewModelScope.launch {
        dao.insertSubject(Subject(name = name, colorIndex = colorIndex))
    }
    fun renameSubject(subject: Subject, newName: String) = viewModelScope.launch {
        dao.updateSubject(subject.copy(name = newName))
    }
    fun deleteSubject(subject: Subject) = viewModelScope.launch {
        dao.deleteSubject(subject)
    }

    // ── Foreground Service ────────────────────────────────────────────────────

    private fun startService() {
        val intent = StudyForegroundService.startIntent(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
        else ctx.startService(intent)
    }

    private fun stopService() {
        ctx.stopService(StudyForegroundService.startIntent(ctx))
    }

    // ── Date helpers ──────────────────────────────────────────────────────────

    fun getToday(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun formatDate(date: String): String {
        val today     = getToday()
        val cal       = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -1) }
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        return when (date) {
            today     -> "Today"
            yesterday -> "Yesterday"
            else      -> runCatching {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)!!
                SimpleDateFormat("MMM d", Locale.ENGLISH).format(d)
            }.getOrDefault(date)
        }
    }
}
