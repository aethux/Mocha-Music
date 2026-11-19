# 10. Data model

Everything the app remembers lives in one Room database, `mochy.db`, plus DataStore preferences
and a secret store. The DB is version **43** with 24 entities and 3 views
(`db/MusicDatabase.kt:110`). The exported JSON in `app/schemas/…/34.json` is stale: the database
now sets `exportSchema = false`, so nothing newer is exported and that file must not be treated as
current.

## Entities

| Table | Purpose | Key fields |
|---|---|---|
| `song` | one row per track, YouTube id in `id` | `title`, `duration`, `albumId`, `albumName`, `liked`, `inLibrary`, `isLocal`, `isDownloaded`, `isVideo`, `dateDownload`, `libraryAddToken`, `libraryRemoveToken`, `lyricsOffset`, `romanizeLyrics`, `isUploaded`, `totalPlayTime` |
| `artist` | artists, channels | `name`, `channelId`, `bookmarkedAt`, `isLocal` |
| `album` | albums | `playlistId`, `title`, `year`, `songCount`, `inLibrary`, `likedDate`, `themeColor`, `isLocal`, `isUploaded` |
| `playlist` | every playlist, local or remote | `name`, `browseId` (YouTube), `createdAt`, `lastUpdateTime`, `isEditable`, `isLocal`, `isAutoSync`, `remoteSongCount`, endpoint params for shuffle/radio |
| `playlist_song_map` | playlist membership and order | `playlistId`, `songId`, `position`, `setVideoId` (YouTube's per-entry id, needed for removal) |
| `song_artist_map`, `song_album_map`, `album_artist_map` | joins with position | |
| `event` | play history rows | `songId`, `timestamp`, `playTime` |
| `playCount` | monthly aggregates | `song`, `year`, `month`, `count` |
| `lyrics` | cached lyrics | `lyrics`, `provider`, `translatedLyrics`, `translationLanguage`, `translationMode` |
| `format` | cached stream formats | `itag`, `mimeType`, `codecs`, `bitrate`, `playbackUrl` |
| `related_song_map` | "related" graph | |
| `set_video_id` | one-off map of videoId → setVideoId | |
| `search_history` | recent searches | `query` |
| `recognition_history` | Shazam results, keeps an ISRC | `isrc`, `appleMusicUrl`, `spotifyUrl`, `youtubeVideoId`, `recognizedAt`, `liked` |
| `speed_dial_item` | pinned home shortcuts, capped | `type`, `secondaryId` |
| `playlist_event` | playlist change log | playlist + event kind, added with v43 |
| `song_edit` | user edits over cached metadata | `songId`, `title`, `artists`, `album`, `year`, `cover`, `videoId` |
| `imported_song`, `import_session`, `imported_playlist`, `imported_playlist_track` | import bookkeeping: which remote playlist a local one came from, per-track match results | |

Views: `SortedSongArtistMap`, `SortedSongAlbumMap`, `PlaylistSongMapPreview` (`@DatabaseView`).

## How it is opened

`db/MusicDatabase.kt` wraps `InternalDatabase` and delegates `DatabaseDao` to it, adding a few
helpers (`pinToSpeedDial`, `transaction`, `withTransaction`, `query`). The builder sets WAL, 4
query and 4 transaction threads, and — important — `fallbackToDestructiveMigration(dropAllTables =
true)`. If a migration is missing, Room drops everything rather than crashing. A fork that needs
user data to survive must not rely on `mochy.db` for anything it cannot rebuild.

`MusicDatabase` is a typealias-ish wrapper everywhere; screens and services inject the wrapper, not
Room directly.

## DAOs

`db/DatabaseDao.kt` is the single big DAO: songs (with filters, sorting, search, paging), albums,
artists, playlists and their maps, events, play counts, lyrics, formats, related songs, import
tables. `db/daos/SpeedDialDao.kt` is separate because it enforces the pin cap. `MusicDatabase`
adds the transaction helpers used by multi-step writes such as import and sync.

Queries return `Flow<...>` for screens and `suspend` functions for one-shot reads, so a sync engine
can observe the local side without polling the DB.

## Preferences

- `preferences/PreferenceKeys.kt` holds ~200 DataStore keys; helpers like
  `enumPreference(context, Key, Default)` and `rememberPreference` are the normal way to read them.
- `utils/SecretVault.kt` + `utils/SecretPreferences.kt` store credentials for accounts and
  integrations.
- `AccountImportSession` (importer) keeps captured web credentials **in memory only** for the length
  of an import, which is why a persistent Apple session has to live somewhere new.

## What this means for the Apple sync

- New tables (`link`, `track`, `snapshot`, `journal`, `op`) go in a **sidecar database**, not in
  `mochy.db`. The schema version constant is the single most conflict-prone line in the fork, and
  `fallbackToDestructiveMigration` means a bad day can wipe the main DB.
- Join in Kotlin by string ids. `song.id` is a YouTube video id today and would be `am:<catalogId>`
  for Apple rows; `playlist.id` is a UUID string and is stable, so it makes a good foreign key.
- Record what the app itself changed (the journal) rather than inferring it from diffs. The DAO has
  no notion of "who wrote this row", and without an origin the sync will echo its own writes.
- ISRC exists only in `recognition_history` (and module `TrackMetadata`), never on `song`. Apple
  matching that wants ISRC needs either a backfill or a sidecar column keyed by `song.id`.
