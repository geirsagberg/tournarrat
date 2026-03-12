package net.sagberg.tournarrat.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.sagberg.tournarrat.core.model.InsightHistory
import net.sagberg.tournarrat.core.model.InsightRecord

interface InsightHistoryRepository {
    val history: Flow<List<InsightRecord>>

    suspend fun save(record: InsightRecord)

    suspend fun delete(id: String)

    suspend fun clear()

    suspend fun getById(id: String): InsightRecord?
}

private val Context.historyDataStore by preferencesDataStore(name = "history_preferences")

class InsightHistoryRepositoryImpl(
    private val context: Context,
    private val json: Json,
) : InsightHistoryRepository {
    private val key = stringPreferencesKey("insight_history_json")

    override val history: Flow<List<InsightRecord>> =
        context.historyDataStore.data.map { store ->
            decode(store[key]).items.sortedByDescending { it.createdAtEpochMillis }
        }

    override suspend fun save(record: InsightRecord) {
        context.historyDataStore.edit { store ->
            val current = decode(store[key]).items
            val next = listOf(record) + current.filterNot { it.id == record.id }
            store[key] = json.encodeToString(InsightHistory(next))
        }
    }

    override suspend fun delete(id: String) {
        context.historyDataStore.edit { store ->
            val next = decode(store[key]).items.filterNot { it.id == id }
            store[key] = json.encodeToString(InsightHistory(next))
        }
    }

    override suspend fun clear() {
        context.historyDataStore.edit { store ->
            store.remove(key)
        }
    }

    override suspend fun getById(id: String): InsightRecord? =
        history.map { items -> items.firstOrNull { it.id == id } }.first()

    private fun decode(raw: String?): InsightHistory =
        raw?.let { stored ->
            runCatching { json.decodeFromString<InsightHistory>(stored) }
                .getOrElse { InsightHistory() }
        } ?: InsightHistory()
}
