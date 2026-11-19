// AppleSyncDatabase.kt

package com.mochyy.mochymusic.applesync.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The Apple sync's own database, in its own file.
 *
 * Deliberately not part of mochy.db. The app's schema version is the most conflict-prone line in a
 * fork, and the main database is opened with fallbackToDestructiveMigration, so anything that has to
 * survive belongs somewhere else. Joins with the main database happen in Kotlin, by string ids.
 *
 * Version 1, and every later version only costs a migration here.
 */
@Database(
    entities = [
        AppleAccountEntity::class,
        AppleEpochEntity::class,
        AppleOwnedEntity::class,
        AppleLinkEntity::class,
        AppleTrackEntity::class,
        AppleSnapshotEntity::class,
        AppleJournalEntity::class,
        ApplePendingOpEntity::class,
        AppleAuditEntity::class,
        AppleLibrarySongEntity::class,
        ApplePlaylistEntity::class,
        AppleRematchEntity::class,
        CoverCacheEntity::class,
        CoverOverrideEntity::class,
        PlaylistMetaEntity::class,
        AppleStateEntity::class,
        PlaylistCollaboratorEntity::class,
        UnmatchedDecisionEntity::class,
    ],
    version = 14,
    exportSchema = false,
)
abstract class AppleSyncDatabase : RoomDatabase() {
    abstract val dao: AppleSyncDao

    companion object {
        const val DB_NAME = "apple_sync.db"

        /**
         * The epoch in this database is the promise that nothing pre-existing is touched, so a schema
         * change may never be allowed to drop it. Every version gets a real migration; the destructive
         * fallback stays only for versions this code has never seen, which cannot happen in a build
         * that ships its own migrations.
         */
        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `library_song` (" +
                        "`catalogId` TEXT NOT NULL, " +
                        "`libraryId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, " +
                        "`album` TEXT, " +
                        "`durationMs` INTEGER, " +
                        "`isrc` TEXT, " +
                        "`addedAt` INTEGER, " +
                        "`pulledAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`catalogId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_song_libraryId` ON `library_song` (`libraryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_song_title` ON `library_song` (`title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_song_artist` ON `library_song` (`artist`)")
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `library_song` ADD COLUMN `rated` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `library_playlist` (" +
                        "`remoteId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`trackCount` INTEGER NOT NULL, " +
                        "`canEdit` INTEGER NOT NULL, " +
                        "`pulledAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`remoteId`))",
                )
            }
        }

        /**
         * Manual rematches. A new table only: the epoch, the replica and the audit trail are all left
         * exactly as they were, which is the point of writing this out instead of bumping the
         * version and letting the fallback drop everything.
         */
        private val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rematch` (" +
                        "`songId` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`remoteId` TEXT NOT NULL, " +
                        "`title` TEXT, " +
                        "`artist` TEXT, " +
                        "`album` TEXT, " +
                        "`durationMs` INTEGER, " +
                        "`isrc` TEXT, " +
                        "`state` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`songId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rematch_source` ON `rematch` (`source`)")
            }
        }

        private val Migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cover_cache` (" +
                        "`songId` TEXT NOT NULL, " +
                        "`animatedUrl` TEXT, " +
                        "`staticUrl` TEXT, " +
                        "`source` TEXT NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`songId`))",
                )
            }
        }

        private val Migration6To7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cover_override` (" +
                        "`key` TEXT NOT NULL, " +
                        "`value` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`key`))",
                )
            }
        }

        /**
         * What a linked playlist held the last time it was imported or sent. A column, not a table:
         * the value is one playlist's own content, and the link row is where its state already lives.
         */
        private val Migration7To8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `link` ADD COLUMN `lastSignature` TEXT")
            }
        }

        /**
         * Who owns an imported playlist. A new table only: the epoch, the replica and the audit
         * trail stay exactly as they are.
         */
        private val Migration8To9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_meta` (" +
                        "`playlistId` TEXT NOT NULL, " +
                        "`ownerName` TEXT, " +
                        "`collaborative` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlistId`))",
                )
            }
        }

        /**
         * What a playlist looked like when it was last imported, and one key/value table for the
         * last sync's own facts. Both additive.
         */
        private val Migration9To10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_meta` ADD COLUMN `lastModified` TEXT")
                db.execSQL("ALTER TABLE `playlist_meta` ADD COLUMN `canDelete` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_state` (" +
                        "`key` TEXT NOT NULL, " +
                        "`value` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`key`))",
                )
            }
        }

        /**
         * Whether a playlist's owner has been looked up. The library list does not carry the name,
         * so it is one request per playlist, once.
         */
        private val Migration10To11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_meta` ADD COLUMN `ownerChecked` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val Migration11To12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `playlist_meta` ADD COLUMN `catalogId` TEXT")
            }
        }

        private val Migration12To13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_collaborator` (" +
                        "`playlistId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlistId`, `name`))",
                )
            }
        }

        private val Migration13To14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `unmatched_decision` (" +
                        "`songId` TEXT NOT NULL, " +
                        "`decision` TEXT NOT NULL, " +
                        "`decidedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`songId`))",
                )
            }
        }

        fun newInstance(context: Context): AppleSyncDatabase =
            Room
                .databaseBuilder(context, AppleSyncDatabase::class.java, DB_NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(
                    Migration1To2,
                    Migration2To3,
                    Migration3To4,
                    Migration4To5,
                    Migration5To6,
                    Migration6To7,
                    Migration7To8,
                    Migration8To9,
                    Migration9To10,
                    Migration10To11,
                    Migration11To12,
                    Migration12To13,
                    Migration13To14,
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
