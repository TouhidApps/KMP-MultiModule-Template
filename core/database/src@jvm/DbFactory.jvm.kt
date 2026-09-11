package com.touhidapps.kmp.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

fun jvmDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dir = File(System.getProperty("user.home"), ".kmp-multimodule-template")
    dir.mkdirs()
    return Room.databaseBuilder<AppDatabase>(name = File(dir, "app.db").absolutePath)
}
