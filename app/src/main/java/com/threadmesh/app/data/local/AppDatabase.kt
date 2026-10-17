package com.threadmesh.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.OutboxEntity

@Database(entities = [EventEntity::class, OutboxEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun threadMeshDao(): ThreadMeshDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        /**
         * Version 1 contained mesh_events. Version 2 adds the durable mesh_outbox table.
         * Preserve all existing conversation data during the upgrade.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `mesh_outbox` (
                        `packetId` TEXT NOT NULL,
                        `eventId` TEXT NOT NULL,
                        `rawJson` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `hopCount` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastRelayAttemptAt` INTEGER,
                        `relayedToPeersCount` INTEGER NOT NULL,
                        `deliveredPeerIds` TEXT NOT NULL,
                        PRIMARY KEY(`packetId`)
                    )""".trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_mesh_outbox_eventId` ON `mesh_outbox` (`eventId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_mesh_outbox_status` ON `mesh_outbox` (`status`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_mesh_outbox_expiresAt` ON `mesh_outbox` (`expiresAt`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "threadmesh_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
