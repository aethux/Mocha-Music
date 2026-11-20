# What this fork changed, and how

Fork of `realidkroo/Mochy-Music` on branch `apple-sync`: one commit per feature, 165 files
touched. Apple Music is the library of record; YouTube still supplies what Apple does not have.

This file is the list of what changed and the method behind each change. It is organised by area,
because the same three techniques keep coming back:

1. **A tagged line in an upstream file**, never more than the seam budget allows
   (`scripts/seams.sh`, ~20 added lines per file).
2. **A fork-owned file** that holds the actual work, called from that line.
3. **The sidecar database** (`apple_sync.db`) for anything the sync has to remember, so the app's own
   schema is never rewritten for fork state.

## The shape of the fork

- 80 Kotlin files under `app/.../applesync/**` are fork-owned: engine, sidecar entities and DAO,
  page automation, and the small UI pieces the engine needs.
- 16 more new files live outside that tree: `ui/menu/CoverSheet.kt`, `CoverMenuItems.kt`,
  `SongAppleMenuItems.kt`, `PlaylistAppleSyncMenu.kt`, `SongActionTiles.kt`, `SongMenuRows.kt`,
  `SongInfoFooter.kt`, `ui/pick/*` (the two pickers), `ui/screens/search/AppleSearchView.kt`,
  `res/values/apple_sync_strings.xml`, the pinned-recording test, `scripts/seams.sh`,
  `scripts/commit-msg`, `scripts/install-hooks.sh`, `README-APPLE-SYNC.md`.
- Upstream files carry 38 seams between them (the ones with actual edits), the largest at 19 added lines
  (`db/DatabaseDao.kt`), then `playback/MusicService.kt` 19, `ui/menu/SongMenu.kt` 18,
  `ui/screens/playlist/LocalPlaylistScreen.kt` 17, `ui/component/SwipeActions.kt` 15,
  `discord/PresenceActivity.kt` 13, `ui/player/BottomSheetPlayer.kt` 11,
  `ui/component/PopupSheet.kt` and `ui/component/detail/PlaylistDetail.kt` 10,
  everything else under 10. `seams.sh` exits 0.
- The sidecar is at schema 13 with twelve real migrations (`1→2` … `12→13`). Its own file, so the
  app's schema version and its `fallbackToDestructiveMigration` never enter the argument.
- 7 test files, 62 unit tests, all in `app/src/test/.../applesync/`.

## Groundwork

- **Plan and method first**: `apple-sync/PLAN.md`, `README-APPLE-SYNC.md`, an architecture map of the
  app, and a spike that proved what the Apple API allowed before any of it was built.
- **Seam harness**: `scripts/seams.sh` counts added lines per upstream file against `upstream/main`
  and fails the build check when one goes over. New fork files are not seams.
- **Commit hook**: `scripts/commit-msg` enforces subjects of 72 characters or fewer, installed with
  `scripts/install-hooks.sh`.
- **Environment notes**: `apple-sync/ENVIRONMENT.md` and `dev/` record the build JDK, the emulator
  settings (software GPU, headless behind scrcpy) and the two failures a fresh machine hits.

## The engine, in the order it was built

**Sidecar database and the guard.** `applesync/db/` holds the tables; `AppleGuard.kt` refuses any
write that touches something which existed before the sync did. The **epoch** is taken once, in
`AppleAudit.kt`, and `apple_sync.db`'s `epoch` table is what "pre-existing" means from then on. Everything is
audited (`audit` table: op, target, decision, http status, verified, reason) and every write is read
back to verify it landed.

**Read-only library access.** `AppleClient` (amp-api with the account's two tokens), `AppleLibrary`
(tolerant parsers for the recorded response shapes), `AppleLibraryPull` (paged reads with a live
counter), `AppleLibraryDiff` (what disappeared). Reading is always allowed; writing is separate code.

**Favourites, both ways.** `AppleLibraryWrites` and `AppleSongWrites` set and clear hearts, guarded,
audited, verified. The import mirrors Apple's rating into the local `liked` flag on every run, so a
heart set in Apple comes back here.

**Import into Mocha's own tables.** `AppleImport` writes `AM_<catalogueId>` rows: song, artist,
album links, cover. It is idempotent, and a refresh compares in memory instead of asking per row.

**Matching.** `AppleMatcher` (catalogue search and ISRC), `AppleRematch` / `AppleRematchStore` (a
person's manual pick is the address from then on), with the pinned-id tests moved into their own file
so upstream's test file stayed clean.

**Albums.** `AppleAlbums` writes `AM_ALB_<id>` album rows, `song.albumId` and `song_album_map`, all
in batched transactions, and skips the pass entirely when a stamp of the album set has not changed.

**Playlists.** `ApplePlaylistImport` creates real Mocha playlists (Apple's order, bookmarked, linked),
`ApplePlaylistWrites` and `ApplePlaylistPush` send changes back, `ApplePlaylistLinker` links a local
playlist to an Apple one, `PlaylistBody` builds the ordered track list.

**Covers.** `AppleCovers` caches still and animated artwork (album endpoint with
`?extend=editorialVideo`), `CoverOverrides` stores a person's own choice per song, artist, album and
playlist, `CoverImages` crops a chosen phone image to a square file the app owns. `ui/menu/CoverSheet.kt`
is the sheet all four kinds open.

## Bugs this fork found and fixed, with the cause

| symptom | cause | fix |
|---|---|---|
| Playlist pushes answered 500 for most playlists | the writer passed `playlistEditable = true` regardless; Apple refuses a playlist the account only follows | read `canEdit` from the row, refuse early with a sentence, keep Apple's own error text |
| The same playlist was pushed again on every launch | the "what it looked like" baseline lived in memory | baseline moved to the link row (`lastSignature`, schema 8) with a 30 s cooldown |
| Playlists wore their first track's cover | the parser read `name` and `canEdit` and dropped `attributes.artwork` | parse the artwork; only fall back to the first track when Apple sends none |
| Playlist pages read "by Guest" | the credit line came from the signed-in YouTube account, and there is none | credit the playlist's own owner |
| Playlist owners read "You" | library playlists carry no owner field | one request per playlist with `include=catalog`, whose catalogue counterpart carries `curatorName` |
| A shared playlist showed a collaborator called "others" | the shared marker was faked as a second creator | owner only, plus a real mark |
| Playlists arrived short of songs | entries not in the library were skipped, and the library track endpoint lists only library tracks | import those entries with `inLibrary = null`, and read the catalogue copy's tracks when there is one |
| The Artists shelf showed 11 artists | the list kept only artists flagged YouTube or local; 794 Apple artists are neither | list an artist that has songs |
| A refresh took 6.5 minutes | a cover read, a mapping write and a thumbnail update per song, a relation query per album, a row lookup per playlist entry | bulk reads up front, writes only for rows that moved, batches of 100 to 200: 399 s → 102 s |
| A cover applied from the sheet only appeared after the next import | the write ran on the menu's coroutine scope, which dies with the sheet | fork writes use their own process scope |
| "Use Apple's cover" blanked artwork | it wrote `null` over a row nothing else rewrites | remember the replaced value and restore it |

## UI

- Song menu: four action tiles, shuffle and repeat rows, a chevron that opens the real menu, in-place
  sheet pages with back (`applesync/ui/SheetPages.kt`).
- Two pickers: "Pick a recording…" (YouTube) and "Match on Apple Music…", both writing rematch rows.
- Source chips and the Apple catalogue in search; an in-library mark; a footer on both menus.
- Songs list: sort control and play bar (`applesync/ui/SongsOrdering.kt`).
- Cover rows in the song, artist, album and playlist menus, all opening one sheet.
- Playlists: list or grid, remembered (`playlistsGridView`), the grid being the same component albums
  use. The grid grew a long-press so playlist tiles keep their menu.
- Library footer: two plain sentences with tappable words, `1,413 songs · 917 albums · 26 playlists`
  and `1,407 on Apple Music · 6 on YouTube · synced 2 min ago`. The Apple and YouTube counts open
  `apple/songs/{apple|youtube}`, the same list filtered.
- Playlist header: owner plus collaborators on the credit line, and a round people icon beside shuffle
  that opens the reader.
- Discord: an Apple song's links open `music.apple.com/song/<id>`, and each button on the presence
  card can be switched off on its own.

## Background and reporting

`AppleSyncFlow` holds the whole refresh; `AppleSyncService` runs it as a foreground service, so
leaving the app no longer interrupts it. The notification reports phase, counts and a bar, and
finishes with the same sentence the in-app card shows ("Nothing new · 1420 songs already there ·
26 playlists unchanged · took 1m 13s"). The last run's time and sentence are kept in the sidecar
(`sync_state`) and read by the footer.

## Reading things Apple does not put in its API

Two pieces of data only exist on the web page, and both are read the same way: a real, attached
WebView on music.apple.com with the app's own session, the capture hook installed as a document-start
script, and `WebViewApplePage` answering through a JavascriptInterface. That is the pattern
`AppleSessionScreen` already used for the like and library sync, and it is what
`AppleCollaboratorsScreen` reuses now.

- **Owners**: `GET /v1/me/library/playlists/{id}?include=catalog` returns `curatorName` on the
  catalogue counterpart. The list endpoint does not, so this is one request per playlist, once ever
  (`playlist_meta.ownerChecked`).
- **Collaborators**: the page's credit line reads `Owner & N other` and the avatars beside it carry
  the names in their `alt` text. `ApplePageScripts.readCollaborators()` walks up from that line until
  the block holds two avatars. `PlaylistCollaborators.parse` drops page chrome, the playlist's own
  title, and the account's own name. The screen walks a queue, so one run reads every shared playlist
  at about 10 seconds each.

## What is still open

- Three of the nine shared playlists still read no names. They stay in the queue; their pages may
  simply show no second avatar.
- A draggable cropper does not exist. A chosen cover is the centre square, 720 px.
- Duplicate artist rows appear when Apple's spelling differs from a row created earlier ("Alan
  Walker" twice), because `artistByName` matches exactly.
- Android Back on a menu's sheet still closes the sheet instead of stepping back a page (deliberately
  deferred).
- The song menu's "Add" row was once seen closing the menu without showing the playlist chooser. Not
  investigated since.
