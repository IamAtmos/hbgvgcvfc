package com.studyflow.app.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val CHANNEL_ID      = "studyflow_timer"
        private const val NOTIFICATION_ID = 1001
    }

    private val ctx   = application.applicationContext
    private val prefs = application.getSharedPreferences("studyflow_timer", Context.MODE_PRIVATE)

    private val _presets     = MutableStateFlow(mutableListOf(5, 10, 25, 50))
    val presets: StateFlow<MutableList<Int>> = _presets

    private val _totalMillis     = MutableStateFlow(25 * 60_000L)
    val totalMillis: StateFlow<Long> = _totalMillis

    private val _remainingMillis = MutableStateFlow(25 * 60_000L)
    val remainingMillis: StateFlow<Long> = _remainingMillis

    private val _isRunning   = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _isFinished  = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished

    private var timerJob: Job? = null

    fun addPreset(minutes: Int) {
        if (minutes <= 0 || minutes > 999 || _presets.value.contains(minutes)) return
        _presets.value = _presets.value.toMutableList().also { it.add(minutes); it.sort() }
    }

    fun removePreset(minutes: Int) {
        _presets.value = _presets.value.toMutableList().also { it.remove(minutes) }
    }

    fun setPreset(minutes: Int) {
        if (_isRunning.value) return
        val millis = minutes * 60_000L
        _totalMillis.value     = millis
        _remainingMillis.value = millis
        _isFinished.value      = false
        saveWidgetState()
    }

    fun setCustomTime(minutes: Int) {
        if (_isRunning.value || minutes <= 0) return
        val millis = minutes * 60_000L
        _totalMillis.value     = millis
        _remainingMillis.value = millis
        _isFinished.value      = false
        saveWidgetState()
    }

    fun start(context: Context) {
        if (_isRunning.value) return
        _isRunning.value  = true
        _isFinished.value = false
        saveWidgetState()

        timerJob = viewModelScope.launch {
            while (_remainingMillis.value > 0 && _isRunning.value) {
                delay(1_000L)
                if (!_isRunning.value) break
                _remainingMillis.value = (_remainingMillis.value - 1_000L).coerceAtLeast(0)
                saveWidgetState()
                if (_remainingMillis.value == 0L) {
                    _isRunning.value  = false
                    _isFinished.value = true
                    saveWidgetState()
                    sendAlarm(context)
                }
            }
        }
    }

    fun pause() {
        _isRunning.value = false
        timerJob?.cancel()
        saveWidgetState()
    }

    fun reset() {
        pause()
        _remainingMillis.value = _totalMillis.value
        _isFinished.value      = false
        saveWidgetState()
    }

    private fun saveWidgetState() {
        prefs.edit()
            .putLong("remaining_ms", _remainingMillis.value)
            .putBoolean("is_running", _isRunning.value)
            .putBoolean("is_finished", _isFinished.value)
            .apply()
    }

    // ── Alarm ─────────────────────────────────────────────────────────────────

    private fun sendAlarm(context: Context) {
        ensureChannel(context)
        vibrate(context)

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏳ StudyFlow")
            .setContentText("تایمر تموم شد! وقت استراحته")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(alarmUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 1000))
            .setAutoCancel(true)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }

    private fun vibrate(context: Context) {
        val pattern = longArrayOf(0, 500, 200, 500, 200, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)
                ?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else @Suppress("DEPRECATION") v?.vibrate(pattern, -1)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmUri  = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val ch = NotificationChannel(CHANNEL_ID, "Timer Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(alarmUri, audioAttr)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 1000)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onCleared() { super.onCleared(); timerJob?.cancel() }
}
