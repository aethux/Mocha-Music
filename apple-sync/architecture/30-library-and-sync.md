# 30. Library, sync and import

This is the existing machinery the Apple work copies. Mochy already has a two-way sync, but with
YouTube Music, and it already has an Apple Music *reader*. What it lacks is an Apple writer.

## Library surface

`ui/screens/library/` holds the tabs: songs, albums, artists, playlists, imported, cached, local.
`LibraryFilter`, `SongFilter`, `SongSortType`, `LibraryViewType`, `GridItemSize` and friends live in
`constants/`. Queries come from `db/DatabaseDao.kt`; `LibrarySongsScreen`, `LibraryAlbumsScreen`,
`LibraryArtistsScreen`, `LibraryPlaylistsScreen` are the lists, `LibraryScaffold`/`LibraryTabs` the
frame.

## YouTube Music sync (the two-way precedent)

`utils/SyncUtils.kt` owns a queue of `SyncOperation`s and a `SyncState`/`SyncStatus` for the UI.
Public entry points:

- `performFullSync()` / `performFullSyncSuspend()`, `tryAutoSync()`, `runAllSyncs()`
- per-domain: `syncLikedSongs`, `syncLibrarySongs`, `syncUploadedSongs`, `syncLikedAlbums`,
  `syncUploadedAlbums`, `syncArtistsSubscriptions`, `syncSavedPlaylists`, `syncAutoSyncPlaylists`,
  `syncPlaylist(browseId, playlistId)`, `syncAllAlbums`, `syncAllArtists`
- maintenance: `cleanupDuplicatePlaylists()`, `clearAllSyncedContent()`

Each operation reads the remote (InnerTube) and reconciles it into Room, setting `liked`,
`inLibrary`, `lastUpdateTime` and similar flags.

### Per-playlist two-way, in three small files

- `importer/YouTubePlaylistMirror.kt` — creates a YouTube copy of a local playlist, stores the remote
  id in `playlist.browseId`, then back-fills each entry's `setVideoId` (YouTube needs that to remove
  a specific entry). Local files are filtered out because they have no video.
- `importer/PlaylistAddedHere.kt` — SharedPreferences store of "songs added here, newest first" plus
  "what the remote had at the last sync". The KDoc states the rule: *"a sync keeps one and follows
  the other"*. This is the base snapshot, and the reason `playlist.isAutoSync` exists.
- `importer/SyncUndo.kt` — journals what the last full sync is about to change so it can be undone.

That trio is exactly the shape an Apple equivalent needs, except Apple also needs a writer, and the
snapshot belongs in a table rather than SharedPreferences.

## Import (one-way, in)

`importer/MusicImportService.kt` is the coordinator: `startImport(parsed, options)`,
`startResync(requests)`, progress events, `cancel()`, `repairEarlierImports()`.

Sources:

- `LinkImporter` — a pasted link. Detects and parses Spotify embeds (first 100 tracks only), Apple
  Music pages (`parseApplePage`), Deezer JSON, YouTube/YouTube Music playlists. The Apple path is
  HTML scraping, not the API; the account path is what uses `amp-api`.
- `CsvImport` / `MochyCsvExporter` — TuneMyMusic-style CSV with flexible column names, plus Mochy's
  own export format.
- `DeviceMusicScanner`, `LocalImportService`, `LocalAudioImport`, `LocalAudioTags`,
  `LocalSidecars`, `LocalFolders`, `LocalArtwork`, `LocalSongRepair` — local files and their
  metadata/covers.
- `importer/account/*` — signed-in services, each an `AccountConnector` returning an
  `AccountLibrary` of `RemotePlaylist` + `ImportedTrack`:
  - `AppleMusicConnector` — `amp-api.music.apple.com` with `Authorization: Bearer <dev token>` and
    `Media-User-Token`, both captured from the web player. Reads `/v1/me/storefront`,
    `/v1/me/library/playlists`, `/v1/me/library/playlists/{id}/tracks`, `/v1/me/library/songs`.
    Treats a playlist called "Favorite Songs" as liked songs.
  - `SpotifyConnector` + `SpotifyWebPlayer` / `WebPlayerClient` — web-player session reuse.
  - `TidalConnector`, `YouTubeMusicLibrary`.
  - `AccountHttp` — shared GET/POST with 429/`Retry-After` handling and 401 marking.
  - `AccountImportSession` — drives the WebView sign-in (`CaptureWebView`), captures headers,
    keeps credentials **in memory for the session only**.

Matching happens in `TrackMatcher` / `ImportMatches` / `ImportSteps`, with `UnmatchedTrack` and
`ImportNotices` for what it could not find.

### Re-sync

`importer/PlaylistResync.kt` decides how to re-read an imported playlist later: by its saved link
(`Route.Direct`), through the signed-in account (`Route.ThroughAccount`, which is how Apple imports
re-sync), or by asking for a link (`Route.NeedsLink`). `PlaylistSourceRecord`, `ImportHistory`,
`ImportedVersions` and `SyncUndo` record provenance and allow undo.

## Transfer and backup

`importer/transfer/` is a full-install archive: `mochy-transfer` format v1 with `manifest.json`,
copies of `database/library.db` and `database/media.db`, `settings/settings.json`, the carried
preferences, local songs and files. `TransferExporter` / `TransferImporter` / `TransferRestore` write
and read it; `BackupRestoreViewModel` drives the UI. Login keys are deliberately excluded (see
`TransferArchive.LoginKeys`).

## Where Apple outbound attaches

| Piece | Existing analogue | New work |
|---|---|---|
| Which playlist is linked to which remote id | `playlist.browseId` for YouTube | `link` table keyed by `playlist.id`, `kind = PLAYLIST`, `remoteId = p.…` |
| The library itself | `song.inLibrary` + SyncUtils | `link` row `kind = LIBRARY`, plus per-song `libraryId` in the sidecar `track` table |
| Base snapshot | `PlaylistAddedHere.remoteSeen` | `snapshot` table (ordered, survives process death) |
| Echo suppression | `PlaylistAddedHere.added` | `journal` with `origin` (LOCAL/REMOTE) |
| Remote reads | `AppleMusicConnector` (already written) | reuse, move behind a transport interface |
| Remote writes | none | new: page-driven MusicKit calls plus REST fallback |
| Re-sync routing | `PlaylistResync.accountFor(APPLE_MUSIC)` already returns the Apple service | extend to the outbound direction |
| Undo | `SyncUndo` | extend with the Apple op queue |
| Import bookkeeping | `imported_song`, `import_session`, `imported_playlist` | new sidecar tables; do not reuse, since `fallbackToDestructiveMigration` can empty `mochy.db` |

The one structural gap: nothing in the app currently writes to a music service. `SyncUtils` writes
only to Room, and `YouTubePlaylistMirror` writes through InnerTube. An Apple writer is the first
outbound mutation path, which is why the journal and the op queue matter more here than in the
YouTube case.
