package com.lastasylum.alliance.data.chat.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatRoomEntity::class,
        ChatMessageEntity::class,
        ChatReadCursorEntity::class,
        ChatTombstoneEntity::class,
        ChatOutboxEntity::class,
        ForumTopicEntity::class,
        ForumMessageEntity::class,
        ForumReadCursorEntity::class,
        ForumOutboxEntity::class,
        LatencySampleEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SquadRelayDatabase : RoomDatabase() {
    abstract fun chatRoomDao(): ChatRoomDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatReadCursorDao(): ChatReadCursorDao
    abstract fun chatTombstoneDao(): ChatTombstoneDao
    abstract fun chatOutboxDao(): ChatOutboxDao
    abstract fun forumTopicDao(): ForumTopicDao
    abstract fun forumMessageDao(): ForumMessageDao
    abstract fun forumReadCursorDao(): ForumReadCursorDao
    abstract fun forumOutboxDao(): ForumOutboxDao
    abstract fun latencySampleDao(): LatencySampleDao

    companion object {
        private const val DB_NAME = "squadrelay_store.db"

        @Volatile
        private var instance: SquadRelayDatabase? = null

        fun get(context: Context): SquadRelayDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SquadRelayDatabase::class.java,
                    DB_NAME,
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }

        /** In-memory DB for unit tests. */
        fun createInMemory(context: Context): SquadRelayDatabase =
            Room.inMemoryDatabaseBuilder(context, SquadRelayDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
