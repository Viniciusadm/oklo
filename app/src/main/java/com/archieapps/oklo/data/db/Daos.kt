package com.archieapps.oklo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks WHERE deletedAt IS NULL")
    fun observeAlive(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE deletedAt IS NULL")
    suspend fun alive(): List<DeckEntity>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun get(id: String): DeckEntity?

    @Query("SELECT * FROM decks WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<DeckEntity>

    @Upsert
    suspend fun upsert(deck: DeckEntity)

    @Upsert
    suspend fun upsertAll(decks: List<DeckEntity>)

    @Query("SELECT * FROM decks WHERE dirty = 1")
    suspend fun dirty(): List<DeckEntity>

    @Query("UPDATE decks SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("SELECT id, updatedAt, dirty FROM decks WHERE id IN (:ids)")
    suspend fun versions(ids: List<String>): List<RowVersion>

    @Query("SELECT COUNT(*) FROM decks WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("DELETE FROM decks")
    suspend fun wipe()
}

@Dao
interface CardDao {
    @Query("SELECT id, deckId, state, dueAt, suspended, createdAt FROM cards WHERE deletedAt IS NULL")
    fun observeStats(): Flow<List<CardStatRow>>

    @Query("SELECT * FROM cards WHERE deletedAt IS NULL AND deckId = :deckId ORDER BY position, createdAt")
    fun observeByDeck(deckId: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deletedAt IS NULL AND deckId IN (:deckIds)")
    suspend fun aliveInDecks(deckIds: List<String>): List<CardEntity>

    @Query("SELECT * FROM cards WHERE deletedAt IS NULL AND suspended = 0 AND deckId IN (:deckIds)")
    suspend fun studyableInDecks(deckIds: List<String>): List<CardEntity>

    @Query("SELECT * FROM cards WHERE deletedAt IS NULL AND suspended = 0")
    suspend fun studyableAll(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<CardEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM cards WHERE deckId = :deckId AND deletedAt IS NULL")
    suspend fun maxPosition(deckId: String): Int

    @Upsert
    suspend fun upsert(card: CardEntity)

    @Upsert
    suspend fun upsertAll(cards: List<CardEntity>)

    @Query("SELECT * FROM cards WHERE dirty = 1")
    suspend fun dirty(): List<CardEntity>

    @Query("UPDATE cards SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("SELECT id, updatedAt, dirty FROM cards WHERE id IN (:ids)")
    suspend fun versions(ids: List<String>): List<RowVersion>

    @Query("SELECT COUNT(*) FROM cards WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("DELETE FROM cards")
    suspend fun wipe()
}

@Dao
interface ReviewDao {
    @Query("SELECT cardId, rating, stateBefore, reviewedAt, durationMs FROM reviews WHERE deletedAt IS NULL AND reviewedAt >= :since")
    fun observeSince(since: Long): Flow<List<ReviewStatRow>>

    @Query("SELECT cardId, rating, stateBefore, reviewedAt, durationMs FROM reviews WHERE deletedAt IS NULL AND reviewedAt >= :since")
    suspend fun since(since: Long): List<ReviewStatRow>

    @Query("SELECT COUNT(*) FROM reviews WHERE deletedAt IS NULL")
    fun observeTotal(): Flow<Int>

    @Query("SELECT c.deckId AS deckId, MAX(r.reviewedAt) AS lastReviewedAt FROM reviews r INNER JOIN cards c ON c.id = r.cardId WHERE r.deletedAt IS NULL GROUP BY c.deckId")
    fun observeDeckActivity(): Flow<List<DeckActivityRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(review: ReviewEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(reviews: List<ReviewEntity>)

    @Query("SELECT * FROM reviews WHERE dirty = 1")
    suspend fun dirty(): List<ReviewEntity>

    @Query("UPDATE reviews SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("SELECT id FROM reviews WHERE id IN (:ids)")
    suspend fun existing(ids: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM reviews WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("DELETE FROM reviews")
    suspend fun wipe()
}

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons WHERE deckId = :deckId AND deletedAt IS NULL")
    fun observeByDeck(deckId: String): Flow<LessonEntity?>

    @Query("SELECT * FROM lessons WHERE deckId = :deckId")
    suspend fun getByDeck(deckId: String): LessonEntity?

    @Query("SELECT * FROM lessons WHERE id = :id")
    suspend fun get(id: String): LessonEntity?

    @Upsert
    suspend fun upsert(lesson: LessonEntity)

    @Upsert
    suspend fun upsertAll(lessons: List<LessonEntity>)

    @Query("DELETE FROM lessons WHERE deckId = :deckId AND id != :keepId")
    suspend fun deleteOtherForDeck(deckId: String, keepId: String)

    @Query("SELECT * FROM lessons WHERE dirty = 1")
    suspend fun dirty(): List<LessonEntity>

    @Query("UPDATE lessons SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Query("SELECT id, updatedAt, dirty FROM lessons WHERE id IN (:ids)")
    suspend fun versions(ids: List<String>): List<RowVersion>

    @Query("SELECT COUNT(*) FROM lessons WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("DELETE FROM lessons")
    suspend fun wipe()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 'global'")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 'global'")
    suspend fun get(): SettingsEntity?

    @Upsert
    suspend fun upsert(settings: SettingsEntity)

    @Query("UPDATE settings SET dirty = 0 WHERE id = 'global' AND updatedAt = :updatedAt")
    suspend fun markClean(updatedAt: Long)

    @Query("SELECT COUNT(*) FROM settings WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("DELETE FROM settings")
    suspend fun wipe()
}
