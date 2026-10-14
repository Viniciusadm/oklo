package com.archieapps.oklo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DeckEntity::class, CardEntity::class, ReviewEntity::class, LessonEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OkloDatabase : RoomDatabase() {
    abstract fun decks(): DeckDao

    abstract fun cards(): CardDao

    abstract fun reviews(): ReviewDao

    abstract fun lessons(): LessonDao

    abstract fun settings(): SettingsDao

    companion object {
        fun build(context: Context): OkloDatabase =
            Room.databaseBuilder(context, OkloDatabase::class.java, "oklo.db").build()
    }
}
