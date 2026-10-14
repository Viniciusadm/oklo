package com.archieapps.oklo.data.sync

import androidx.room.withTransaction
import com.archieapps.oklo.core.net.ChangeSet
import com.archieapps.oklo.core.net.RejectedRow
import com.archieapps.oklo.data.db.OkloDatabase
import com.archieapps.oklo.data.db.RowVersion
import com.archieapps.oklo.data.db.toDto
import com.archieapps.oklo.data.db.toEntity
import com.archieapps.oklo.data.prefs.AppPreferences

class RoomSyncStore(
    private val db: OkloDatabase,
    private val preferences: AppPreferences,
) : SyncStore {
    override suspend fun deviceId(): String = preferences.deviceId()

    override suspend fun lastUsn(): Long = preferences.lastUsn()

    override suspend fun setLastUsn(usn: Long) = preferences.setLastUsn(usn)

    override suspend fun dirtyChanges(): ChangeSet = db.withTransaction {
        ChangeSet(
            decks = db.decks().dirty().map { it.toDto() },
            cards = db.cards().dirty().map { it.toDto() },
            reviews = db.reviews().dirty().map { it.toDto() },
            lessons = db.lessons().dirty().map { it.toDto() },
            settings = db.settings().get()?.takeIf { it.dirty }?.toDto(),
        )
    }

    override suspend fun markClean(refs: List<CleanRef>) {
        db.withTransaction {
            refs.forEach { ref ->
                when (ref.entity) {
                    SyncEntity.Deck -> db.decks().markClean(ref.id, ref.updatedAt)
                    SyncEntity.Card -> db.cards().markClean(ref.id, ref.updatedAt)
                    SyncEntity.Review -> db.reviews().markClean(ref.id, ref.updatedAt)
                    SyncEntity.Lesson -> db.lessons().markClean(ref.id, ref.updatedAt)
                    SyncEntity.Settings -> db.settings().markClean(ref.updatedAt)
                }
            }
        }
    }

    override suspend fun recordRejected(rows: List<RejectedRow>) {
        preferences.addRejected(rows.size, rows.lastOrNull()?.reason)
    }

    override suspend fun <R> transaction(block: suspend () -> R): R = db.withTransaction { block() }

    override suspend fun versions(entity: SyncEntity, ids: List<String>): Map<String, RowVersion> {
        if (ids.isEmpty()) return emptyMap()
        val rows = when (entity) {
            SyncEntity.Deck -> ids.chunked(CHUNK).flatMap { db.decks().versions(it) }
            SyncEntity.Card -> ids.chunked(CHUNK).flatMap { db.cards().versions(it) }
            SyncEntity.Review -> ids.chunked(CHUNK).flatMap { chunk ->
                db.reviews().existing(chunk).map { RowVersion(it, 0, false) }
            }
            SyncEntity.Lesson -> ids.chunked(CHUNK).flatMap { db.lessons().versions(it) }
            SyncEntity.Settings -> listOfNotNull(db.settings().get()?.let { RowVersion(it.id, it.updatedAt, it.dirty) })
        }
        return rows.associateBy { it.id }
    }

    override suspend fun writeClean(changes: ChangeSet) {
        db.withTransaction {
            if (changes.decks.isNotEmpty()) db.decks().upsertAll(changes.decks.map { it.toEntity(dirty = false) })
            if (changes.cards.isNotEmpty()) db.cards().upsertAll(changes.cards.map { it.toEntity(dirty = false) })
            if (changes.reviews.isNotEmpty()) db.reviews().insertAll(changes.reviews.map { it.toEntity(dirty = false) })
            changes.lessons.forEach { db.lessons().deleteOtherForDeck(it.deckId, it.id) }
            if (changes.lessons.isNotEmpty()) db.lessons().upsertAll(changes.lessons.map { it.toEntity(dirty = false) })
            changes.settings?.let { db.settings().upsert(it.toEntity(dirty = false)) }
        }
    }

    override suspend fun replaceAll(changes: ChangeSet, usn: Long) {
        db.withTransaction {
            db.reviews().wipe()
            db.lessons().wipe()
            db.cards().wipe()
            db.decks().wipe()
            db.settings().wipe()
            writeClean(changes)
        }
        preferences.resetSyncState()
        preferences.setLastUsn(usn)
        preferences.recordSyncSuccess(System.currentTimeMillis())
    }

    companion object {
        private const val CHUNK = 500
    }
}
