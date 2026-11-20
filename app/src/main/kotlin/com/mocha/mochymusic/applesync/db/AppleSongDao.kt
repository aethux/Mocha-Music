// AppleSongDao.kt
//
// The Apple-only reads, kept out of upstream's DatabaseDao so the fork's seam budget there stays
// for real seams. Same database, its own dao: Room reads it like any other.

package com.mocha.mochymusic.applesync.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.mocha.mochymusic.db.entities.AlbumEntity

@Dao
interface AppleSongDao {
    // backfill a cover without touching anything else on the row
    @Transaction
    @Query("UPDATE song SET thumbnailUrl = :url WHERE id = :songId AND (thumbnailUrl IS NULL OR thumbnailUrl = '')")
    suspend fun setSongThumbnailIfMissing(songId: String, url: String)

    // the ids this fork imports, so a repeat import skips what it already added without one query per song
    @Query("SELECT id FROM song WHERE id GLOB 'AM_*'")
    suspend fun appleSongIds(): List<String>

    // the album import reads the whole set in one query instead of one per album
    @Query("SELECT * FROM album WHERE id LIKE 'AM_ALB_%'")
    suspend fun appleAlbums(): List<AlbumEntity>

    @Query("SELECT id, albumId FROM song WHERE id LIKE 'AM_%'")
    suspend fun appleAlbumLinks(): List<AppleAlbumLink>

    @Query("SELECT id FROM song WHERE id LIKE 'AM_%' AND thumbnailUrl IS NULL")
    suspend fun appleSongsWithoutArtwork(): List<String>

    @Query("SELECT id FROM song WHERE id LIKE 'AM_%' AND inLibrary IS NULL")
    suspend fun appleSongsOutsideTheLibrary(): List<String>
}
