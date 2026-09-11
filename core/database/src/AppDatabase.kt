package com.touhidapps.kmp.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Room database. Add your own `@Entity`/`@Dao` pairs (see [NoteEntity]/
 * [NoteDao] for the shape) and list the entities here; bump `version` and
 * supply a migration whenever the schema changes.
 */
@Database(entities = [NoteEntity::class], version = 1)
@ConstructedBy(DbFactory::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}

// Room's KSP processor generates the actual implementation of this per
// platform (see the *DatabaseBuilder() functions in src@android/src@ios/
// src@jvm) — there is deliberately no actual written by hand here.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object DbFactory : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/**
 * Applies the driver common to every platform and builds the database.
 * Each platform supplies the [RoomDatabase.Builder] (which is how the file
 * path/Context differs); leaving `queryCoroutineContext` unset is
 * deliberate — Room already defaults it to `Dispatchers.IO`, and setting it
 * explicitly here hits a real kotlinx.coroutines visibility quirk on Kotlin/
 * Native (`Dispatchers.IO` is internal there in some versions).
 */
fun buildDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .build()
