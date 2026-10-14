package com.archieapps.oklo.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.archieapps.oklo.data.db.LessonDao
import com.archieapps.oklo.data.db.LessonEntity
import com.archieapps.oklo.data.db.RowVersion
import com.archieapps.oklo.data.prefs.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FakeLessonDao : LessonDao {
    val rows = MutableStateFlow<Map<String, LessonEntity>>(emptyMap())

    override fun observeByDeck(deckId: String): Flow<LessonEntity?> =
        rows.map { all -> all.values.firstOrNull { it.deckId == deckId && it.deletedAt == null } }

    override suspend fun getByDeck(deckId: String): LessonEntity? = rows.value.values.firstOrNull { it.deckId == deckId }

    override suspend fun get(id: String): LessonEntity? = rows.value[id]

    override suspend fun upsert(lesson: LessonEntity) {
        rows.value = rows.value + (lesson.id to lesson)
    }

    override suspend fun upsertAll(lessons: List<LessonEntity>) {
        lessons.forEach { upsert(it) }
    }

    override suspend fun deleteOtherForDeck(deckId: String, keepId: String) {
        rows.value = rows.value.filterValues { it.deckId != deckId || it.id == keepId }
    }

    override suspend fun dirty(): List<LessonEntity> = rows.value.values.filter { it.dirty }

    override suspend fun markClean(id: String, updatedAt: Long) {
        rows.value[id]?.takeIf { it.updatedAt == updatedAt }?.let { rows.value = rows.value + (id to it.copy(dirty = false)) }
    }

    override suspend fun versions(ids: List<String>): List<RowVersion> =
        rows.value.values.filter { it.id in ids }.map { RowVersion(it.id, it.updatedAt, it.dirty) }

    override fun observeDirtyCount(): Flow<Int> = rows.map { all -> all.values.count { it.dirty } }

    override suspend fun wipe() {
        rows.value = emptyMap()
    }
}

class LessonRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 10_000L
    private var nextId = 0
    private val dao = FakeLessonDao()

    private fun repository(scope: TestScope, valid: (String) -> Boolean = { true }): Pair<LessonRepository, AppPreferences> {
        val store = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { folder.newFile("prefs-${System.nanoTime()}.preferences_pb") }
        val preferences = AppPreferences(store)
        val repository = LessonRepository(
            dao = dao,
            preferences = preferences,
            validator = LessonValidator(valid),
            time = { clock },
            ids = { "lesson-${++nextId}" },
        )
        return repository to preferences
    }

    private fun entity(id: String, deckId: String, version: Int, deletedAt: Long? = null) = LessonEntity(
        id = id, deckId = deckId, source = "# Aula", version = version, ribaltaVersion = "0.3.0", origin = "manual",
        createdAt = 1_000, updatedAt = 1_000, deletedAt = deletedAt, dirty = false,
    )

    @Test
    fun `first manual save creates a dirty version one lesson`() = runTest(UnconfinedTestDispatcher()) {
        val (repository, _) = repository(this)

        val saved = repository.saveManual("d1", "# Olá")

        assertEquals("lesson-1", saved.id)
        assertEquals(1, saved.version)
        assertEquals("manual", saved.origin)
        assertEquals(LessonRepository.RIBALTA_VERSION, saved.ribaltaVersion)
        assertEquals("# Olá", repository.get("d1")!!.source)
        val stored = dao.rows.value.getValue("lesson-1")
        assertTrue(stored.dirty)
        assertEquals(10_000L, stored.createdAt)
        assertEquals(10_000L, stored.updatedAt)
    }

    @Test
    fun `saving again reuses the row, bumps the version and stamps a later updatedAt`() = runTest(UnconfinedTestDispatcher()) {
        val (repository, _) = repository(this)
        repository.saveManual("d1", "um")

        val second = repository.saveManual("d1", "dois")

        assertEquals(1, dao.rows.value.size)
        assertEquals("lesson-1", second.id)
        assertEquals(2, second.version)
        assertEquals(10_001L, dao.rows.value.getValue("lesson-1").updatedAt)
        assertEquals(10_000L, dao.rows.value.getValue("lesson-1").createdAt)
    }

    @Test
    fun `saving over a tombstoned lesson reuses its id and revives it`() = runTest(UnconfinedTestDispatcher()) {
        val (repository, _) = repository(this)
        dao.upsert(entity("old", "d1", version = 4, deletedAt = 2_000))

        val saved = repository.saveManual("d1", "nova")

        assertEquals("old", saved.id)
        assertEquals(5, saved.version)
        assertEquals(1, dao.rows.value.size)
        val stored = dao.rows.value.getValue("old")
        assertNull(stored.deletedAt)
        assertTrue(stored.dirty)
        assertNotNull(repository.get("d1"))
    }

    @Test
    fun `needs gate is false without a lesson or with a deleted one`() = runTest(UnconfinedTestDispatcher()) {
        val (repository, _) = repository(this)
        assertFalse(repository.needsGate("d1"))

        dao.upsert(entity("l1", "d1", version = 2, deletedAt = 5_000))

        assertFalse(repository.needsGate("d1"))
    }

    @Test
    fun `needs gate is true for a valid unseen lesson`() = runTest(UnconfinedTestDispatcher()) {
        val (repository, _) = repository(this)
        dao.upsert(entity("l1", "d1", version = 1))

        assertTrue(repository.needsGate("d1"))
    }

    @Test
    fun `needs gate is false when the lesson does not compile cleanly`() = runTest(UnconfinedTestDispatcher()) {
        val (repository, _) = repository(this, valid = { false })
        dao.upsert(entity("l1", "d1", version = 1))

        assertFalse(repository.needsGate("d1"))
    }

    @Test
    fun `needs gate is false once the current version was seen and true again for a newer one`() = runTest(UnconfinedTestDispatcher()) {
        val (repository, preferences) = repository(this)
        dao.upsert(entity("l1", "d1", version = 2))
        preferences.markLessonSeen("d1", 2)

        assertFalse(repository.needsGate("d1"))

        dao.upsert(entity("l1", "d1", version = 3))

        assertTrue(repository.needsGate("d1"))
    }

    @Test
    fun `observe by deck emits the live lesson as a model`() = runTest(UnconfinedTestDispatcher()) {
        val (repository, _) = repository(this)
        assertNull(repository.observeByDeck("d1").first())

        repository.saveManual("d1", "# x")

        assertEquals("# x", repository.observeByDeck("d1").first()!!.source)
    }
}
