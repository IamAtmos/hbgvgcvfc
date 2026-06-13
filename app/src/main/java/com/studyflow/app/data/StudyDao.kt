package com.studyflow.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {

    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun getAllSubjects(): Flow<List<Subject>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject)

    @Update
    suspend fun updateSubject(subject: Subject)

    @Delete
    suspend fun deleteSubject(subject: Subject)

    @Query("SELECT * FROM study_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<StudySession>>

    /** Used by widgets and streak calculation (runs on background thread) */
    @Query("SELECT * FROM study_sessions ORDER BY timestamp DESC")
    suspend fun getAllSessionsOnce(): List<StudySession>

    @Query("SELECT * FROM study_sessions WHERE date = :date")
    suspend fun getSessionsForDate(date: String): List<StudySession>

    @Query("SELECT DISTINCT date FROM study_sessions ORDER BY date DESC")
    suspend fun getAllDistinctDates(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySession)

    @Delete
    suspend fun deleteSession(session: StudySession)
}
