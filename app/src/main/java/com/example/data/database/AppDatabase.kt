package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.ClipboardDao
import com.example.data.dao.CommandSnippetDao
import com.example.data.dao.HostDao
import com.example.data.dao.RfcDao
import com.example.data.entity.ClipboardItemEntity
import com.example.data.entity.CommandSnippetEntity
import com.example.data.entity.HostEntity
import com.example.data.entity.RfcItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        HostEntity::class,
        RfcItemEntity::class,
        CommandSnippetEntity::class,
        ClipboardItemEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hostDao(): HostDao
    abstract fun rfcDao(): RfcDao
    abstract fun commandSnippetDao(): CommandSnippetDao
    abstract fun clipboardDao(): ClipboardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope? = null): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "host_manager_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
