package com.touhidapps.kmp.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Placeholder entity/DAO demonstrating the pattern — delete this file once
 * your app has real tables, or keep it as a working example to copy from.
 */
@Entity(tableName = "Note")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long,
)

@Dao
interface NoteDao {
    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("SELECT * FROM Note ORDER BY createdAt DESC")
    suspend fun all(): List<NoteEntity>

    @Query("DELETE FROM Note WHERE id = :id")
    suspend fun delete(id: Long)
}
