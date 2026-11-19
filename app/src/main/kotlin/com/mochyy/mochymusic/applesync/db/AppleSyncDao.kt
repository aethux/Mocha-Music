// AppleSyncDao.kt

package com.mochyy.mochymusic.applesync.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The sidecar's queries. Small on purpose: the guard only needs "is this in the epoch", "did we
 * create it", and "is the playlist linked", and everything else is bookkeeping for the engine.
 */
@Dao
interface AppleSyncDao {

    // ---- the library replica -------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibrarySongs(songs: List<AppleLibrarySongEntity>)

    @Query("DELETE FROM library_song")
    suspend fun clearLibrarySongs()

    @Transaction
    suspend fun replaceLibrarySongs(songs: List<AppleLibrarySongEntity>) {
        clearLibrarySongs()
        insertLibrarySongs(songs)
    }

    @Query("SELECT COUNT(*) FROM library_song")
    suspend fun librarySongCount(): Int

    @Query("SELECT * FROM library_song ORDER BY artist, album, title")
    fun librarySongsReplica(): Flow<List<AppleLibrarySongEntity>>

    @Query("SELECT * FROM library_song WHERE catalogId = :catalogId")
    suspend fun librarySong(catalogId: String): AppleLibrarySongEntity?

    /** Local songs that already have an Apple copy: the ones that must not show the red dot. */
    @Query("SELECT t.songId FROM track t INNER JOIN library_song l ON l.catalogId = t.catalogId")
    suspend fun syncedSongIds(): List<String>

    @Query("SELECT catalogId FROM library_song WHERE catalogId IN (:catalogIds)")
    suspend fun catalogIdsInLibrary(catalogIds: List<String>): List<String>

    // ---- manual rematches: the recording a person said this song is ---------------------------------

    @Query("SELECT * FROM rematch")
    suspend fun rematches(): List<AppleRematchEntity>

    @Query("SELECT * FROM rematch WHERE songId = :songId")
    suspend fun rematch(songId: String): AppleRematchEntity?

    @Query("SELECT songId FROM rematch WHERE source = :source")
    suspend fun rematchedSongIds(source: String): List<String>

    @Query("SELECT * FROM rematch WHERE source = :source")
    suspend fun rematchRows(source: String): List<AppleRematchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRematch(row: AppleRematchEntity)

    @Query("DELETE FROM rematch WHERE songId = :songId")
    suspend fun clearRematch(songId: String)

    // ---- playlists ------------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<ApplePlaylistEntity>)

    @Query("DELETE FROM library_playlist")
    suspend fun clearPlaylists()

    @Transaction
    suspend fun replacePlaylists(playlists: List<ApplePlaylistEntity>) {
        clearPlaylists()
        insertPlaylists(playlists)
    }

    @Query("SELECT COUNT(*) FROM library_playlist")
    suspend fun playlistCount(): Int

    @Query("SELECT * FROM library_playlist ORDER BY name")
    fun playlistsReplica(): Flow<List<ApplePlaylistEntity>>

    /** One playlist as Apple last described it, for the writes that have to ask about `canEdit`. */
    @Query("SELECT * FROM library_playlist WHERE remoteId = :remoteId")
    suspend fun playlist(remoteId: String): ApplePlaylistEntity?

    // ---- account ------------------------------------------------------------------------------

    @Query("SELECT * FROM account WHERE id = 1")
    fun account(): Flow<AppleAccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAccount(account: AppleAccountEntity)

    // ---- epoch: what existed before this app wrote anything ------------------------------------

    @Query("SELECT COUNT(*) FROM epoch WHERE kind = :kind AND remoteId = :remoteId")
    suspend fun epochContains(kind: String, remoteId: String): Int

    @Query("SELECT * FROM epoch")
    suspend fun epoch(): List<AppleEpochEntity>

    @Query("SELECT COUNT(*) FROM epoch")
    suspend fun epochSize(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToEpoch(entries: List<AppleEpochEntity>)

    // ---- owned: what this app created ---------------------------------------------------------

    @Query("SELECT COUNT(*) FROM owned WHERE kind = :kind AND remoteId = :remoteId")
    suspend fun ownedContains(kind: String, remoteId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun own(entry: AppleOwnedEntity)

    @Query("SELECT * FROM owned WHERE kind = :kind")
    suspend fun owned(kind: String): List<AppleOwnedEntity>

    @Query("DELETE FROM owned WHERE kind = :kind AND remoteId = :remoteId")
    suspend fun disown(kind: String, remoteId: String)

    // ---- links --------------------------------------------------------------------------------

    @Query("SELECT * FROM link WHERE enabled = 1")
    fun links(): Flow<List<AppleLinkEntity>>

    @Query("SELECT * FROM link WHERE playlistId = :playlistId")
    suspend fun link(playlistId: String): AppleLinkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLink(link: AppleLinkEntity)

    @Query("DELETE FROM link WHERE playlistId = :playlistId")
    suspend fun unlink(playlistId: String)

    // ---- track mapping ------------------------------------------------------------------------

    @Query("SELECT * FROM track WHERE songId = :songId")
    suspend fun track(songId: String): AppleTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTrack(track: AppleTrackEntity)

    /** Every mapping in one read: the import decides in memory what changed instead of querying per song. */
    @Query("SELECT * FROM track")
    suspend fun tracks(): List<AppleTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTracks(tracks: List<AppleTrackEntity>)

    // ---- snapshots ----------------------------------------------------------------------------

    @Query("SELECT * FROM snapshot WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun snapshot(playlistId: String): List<AppleSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSnapshot(entries: List<AppleSnapshotEntity>)

    @Query("DELETE FROM snapshot WHERE playlistId = :playlistId")
    suspend fun clearSnapshot(playlistId: String)

    @Transaction
    suspend fun replaceSnapshot(playlistId: String, entries: List<AppleSnapshotEntity>) {
        clearSnapshot(playlistId)
        saveSnapshot(entries)
    }

    // ---- journal ------------------------------------------------------------------------------

    @Insert
    suspend fun journal(entry: AppleJournalEntity): Long

    @Query("SELECT * FROM journal WHERE playlistId = :playlistId ORDER BY at ASC")
    suspend fun journalFor(playlistId: String): List<AppleJournalEntity>

    // ---- pending writes -----------------------------------------------------------------------

    @Insert
    suspend fun enqueue(op: ApplePendingOpEntity): Long

    @Query("SELECT * FROM pending_op ORDER BY id ASC")
    suspend fun pendingOps(): List<ApplePendingOpEntity>

    @Query("UPDATE pending_op SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markAttempt(id: Long, error: String?)

    @Query("DELETE FROM pending_op WHERE id = :id")
    suspend fun doneOp(id: Long)

    // ---- playlist owners ------------------------------------------------------------------------

    @Query("SELECT * FROM playlist_meta WHERE playlistId = :playlistId")
    suspend fun playlistMeta(playlistId: String): PlaylistMetaEntity?

    @Query("SELECT * FROM playlist_meta")
    suspend fun playlistMetas(): List<PlaylistMetaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaylistMeta(meta: PlaylistMetaEntity)

    // ---- who else is on a shared playlist -----------------------------------------------------

    @Query("SELECT * FROM playlist_collaborator WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun collaborators(playlistId: String): List<PlaylistCollaboratorEntity>

    @Query("SELECT * FROM playlist_collaborator")
    suspend fun allCollaborators(): List<PlaylistCollaboratorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCollaborators(rows: List<PlaylistCollaboratorEntity>)

    @Query("DELETE FROM playlist_collaborator WHERE playlistId = :playlistId")
    suspend fun clearCollaborators(playlistId: String)

    // ---- songs Apple does not have: the person's answer ------------------------------------------

    @Query("SELECT songId FROM unmatched_decision WHERE decision = :decision")
    suspend fun decidedSongIds(decision: String = UnmatchedDecisionEntity.KEEP_YOUTUBE): List<String>

    /** Observed, so the library's card drops a song the moment it is kept on YouTube. */
    @Query("SELECT songId FROM unmatched_decision")
    fun decidedSongIdsFlow(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDecision(row: UnmatchedDecisionEntity)

    @Query("DELETE FROM unmatched_decision WHERE songId = :songId")
    suspend fun clearDecision(songId: String)

    // ---- one-off facts about the last sync ------------------------------------------------------

    @Query("SELECT `value` FROM sync_state WHERE `key` = :key")
    suspend fun state(key: String): String?

    /** Observed, so the library's footer updates the moment a sync records its timestamp. */
    @Query("SELECT * FROM sync_state")
    fun stateFlow(): Flow<List<AppleStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveState(state: AppleStateEntity)

    // ---- covers: resolved once, cleared only when the person asks ---------------------------------

    @Query("SELECT * FROM cover_cache WHERE songId = :songId")
    suspend fun cover(songId: String): CoverCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCover(cover: CoverCacheEntity)

    /** The same for covers: one read tells the import which songs still need a cache row. */
    @Query("SELECT * FROM cover_cache")
    suspend fun covers(): List<CoverCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCovers(covers: List<CoverCacheEntity>)

    @Query("DELETE FROM cover_cache")
    suspend fun clearCovers()

    @Query("SELECT COUNT(*) FROM cover_cache")
    suspend fun coverCount(): Int

    // ---- covers a person chose, which an import must never overwrite ------------------------------

    @Query("SELECT * FROM cover_override WHERE `key` = :key")
    suspend fun coverOverride(key: String): CoverOverrideEntity?

    @Query("SELECT * FROM cover_override")
    suspend fun coverOverrides(): List<CoverOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCoverOverride(override: CoverOverrideEntity)

    @Query("DELETE FROM cover_override WHERE `key` = :key")
    suspend fun clearCoverOverride(key: String)

    // ---- audit --------------------------------------------------------------------------------

    @Insert
    suspend fun audit(entry: AppleAuditEntity): Long

    @Query("SELECT * FROM audit ORDER BY id DESC LIMIT :limit")
    fun recentAudit(limit: Int = 200): Flow<List<AppleAuditEntity>>
}
