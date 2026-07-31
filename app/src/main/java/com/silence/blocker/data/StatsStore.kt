package com.silence.blocker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.statsDataStore by preferencesDataStore(name = "silence_stats")

/**
 * Persisted daily counters so process death does not zero the Home screen.
 */
class StatsStore(private val context: Context) {
    private object Keys {
        val day = stringPreferencesKey("day")
        val blocked = longPreferencesKey("blocked")
        val allowed = longPreferencesKey("allowed")
    }

    data class DayStats(
        val day: String,
        val blocked: Long,
        val allowed: Long,
    )

    val dayStats: Flow<DayStats> = context.statsDataStore.data.map { prefs ->
        val today = LocalDate.now().toString()
        if (prefs[Keys.day] != today) {
            DayStats(today, 0L, 0L)
        } else {
            DayStats(
                day = today,
                blocked = prefs[Keys.blocked] ?: 0L,
                allowed = prefs[Keys.allowed] ?: 0L,
            )
        }
    }

    val blockedToday: Flow<Long> = dayStats.map { it.blocked }

    suspend fun loadToday(): DayStats = dayStats.first()

    suspend fun saveToday(blocked: Long, allowed: Long) {
        context.statsDataStore.edit {
            it[Keys.day] = LocalDate.now().toString()
            it[Keys.blocked] = blocked
            it[Keys.allowed] = allowed
        }
    }
}
