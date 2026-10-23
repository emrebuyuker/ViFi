package com.salticusteam.vifi.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.RecentExam
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

@Singleton
class DataStoreRecentExamsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : RecentExamsRepository {

    override val exams: Flow<List<RecentExam>> = dataStore.data.map { decode(it[KEY]) }

    override suspend fun record(path: ArchivePath, kind: ExamKind, at: Long) {
        update { RecentExam.recorded(it, path, kind, at) }
    }

    override suspend fun remove(path: ArchivePath) {
        update { list -> list.filter { it.path != path } }
    }

    override suspend fun clear() {
        update { emptyList() }
    }

    // Read-modify-write inside one edit{} so concurrent calls never lose an update; unchanged lists are not written.
    private suspend fun update(transform: (List<RecentExam>) -> List<RecentExam>) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY])
            val updated = transform(current)
            if (updated != current) prefs[KEY] = encode(updated)
        }
    }

    @Serializable
    private data class Entry(
        val components: List<String>,
        val displayNames: List<String>,
        val kind: String,
        val openedAt: Long,
    )

    private fun encode(list: List<RecentExam>): String = json.encodeToString(
        listSerializer,
        list.map { Entry(it.path.components, it.path.displayNames, it.kind.raw, it.openedAt) },
    )

    // A corrupt or outdated blob starts empty rather than being retried forever (iOS behaviour).
    private fun decode(raw: String?): List<RecentExam> {
        if (raw == null) return emptyList()
        return try {
            json.decodeFromString(listSerializer, raw).mapNotNull { entry ->
                val kind = ExamKind.fromRaw(entry.kind) ?: return@mapNotNull null
                RecentExam(ArchivePath(entry.components, entry.displayNames), kind, entry.openedAt)
            }
        } catch (e: Exception) {
            Timber.w(e, "Discarding unreadable recent exams")
            emptyList()
        }
    }

    private companion object {
        // Bump the version suffix on an incompatible schema change: the old key is ignored and the list starts empty.
        val KEY = stringPreferencesKey("recentExams.v1")
        val json = Json { ignoreUnknownKeys = true }
        val listSerializer = ListSerializer(Entry.serializer())
    }
}
