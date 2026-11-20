# Next session — start here

You are continuing work on the **Mochy-Music apple-sync fork** at `~/WebDev/Mochy-Music`
(branch `apple-sync`, GPL-3.0, Kotlin/Compose/Media3). Load the skill
`mochy-music-apple-sync` first — it has the build/install/DB commands and the traps that
have already cost time. Read `apple-sync/STATE.md`, `apple-sync/PLAN.md` and
`apple-sync/architecture/50-ui.md` before touching code.

## 1. Understand before changing

Read, in this order, and summarise the structure back before proposing anything:

1. `apple-sync/STATE.md` — what is proven, what is next, the decisions already taken.
2. `app/src/main/kotlin/com/mocha/mochymusic/applesync/` — the whole engine:
   `AppleClient` (reads), `AppleLibraryWrites`/`AppleSongWrites` (writes), `AppleGuard`
   (the permission model), `AppleImport`, `AppleLibraryPull`, `ApplePlaylistImport`,
   `ApplePlaylistWrites`, `ApplePlaylistPush`, `AppleCovers`, `CoverOverrides`,
   `AppleSyncStatus`, `AppleSyncRunner`, `db/` (the sidecar database).
3. `scripts/seams.sh` and how `// MOCHY-APPLE:` tags work — the fork keeps upstream edits
   under ~20 added lines per file and moves code into fork-owned files.
4. The UI it builds on: `ui/menu/` (SongMenu, PlayerActionMenu's four tiles, CoverSheet),
   `ui/screens/search/` (SearchCategoryRow, AppleSearchView), `ui/screens/library/`
   (LibraryGridScreen, LibraryEntityListScreen), `ui/component/PopupSheet.kt`.

Facts that matter in every decision:

- Apple is the library of record; YouTube supplies what Apple lacks. Songs imported from
  Apple have id `AM_<catalogueId>`; local files are `LOCAL_…`.
- `apple_sync.db` is a sidecar (epoch, owned, link, track, snapshot, journal, audit,
  library_song, library_playlist, rematch, cover_cache, cover_override). Never write into
  `mochy.db` from the engine; migrations must match entities exactly.
- **A playlist is only writable once linked**; the link row is the user's permission.
  Songs/playlists that existed before the sync are never removed without a confirmed tap.
- Every write is guarded, audited and verified by a read afterwards.

## 2. What was built and what state it is in

Commits on `apple-sync` (oldest first): engine → UI → rest → playlist cover sheet → Songs
sort extraction → Discord Apple links → Discord per-button switch. Working tree clean.

Working and verified on the device/account:

- **Library import**: 1403–1480 Apple songs as `AM_` rows, idempotent; ratings mirrored back
  onto local rows; library add/remove pushed to Apple with toasts and audit rows.
- **Song writes**: heart (`api.music.apple.com` ratings) and library (`amp-api`) both ways,
  behind the guard; confirmed removals allowed for pre-existing songs; red dots now correct
  (an `AM_` row is synced by construction).
- **Pickers**: "Pick a recording…" (YouTube Music search as a page) and "Match on Apple
  Music…" (Apple catalogue), both writing rematch rows so likes/library/import follow.
- **Covers**: `cover_cache` with the chain animated → Apple still → the song's own artwork
  (no name-search fallback, which was giving wrong covers); animated artwork fetched from
  the album endpoint with `?extend=editorialVideo` (108+ `apple_motion` rows with real
  `mvod.itunes.apple.com` HLS urls); "Clear cover cache" in Settings; player draws the
  animated cover in the same box via ExoPlayer (never confirmed visually).
- **Playlists**: import as real Mocha playlists (26 playlists, 742 songs, Apple's order,
  auto-linked, `bookmarkedAt` set so they appear in lists); refresh button now runs the
  playlist import too; push writer (`PUT …/playlists/{id}/tracks`, full list) plus a 5s
  watcher that pushes changed playlists; imports seed the watcher so they don't echo back.
- **UI**: four tiles in the player menu (library, playlist, download, liked) with shuffle
  and repeat as rows, chevron opens that menu directly, sheets swap content in place with a
  back arrow, search source chips (All/YouTube Music/Apple Music) with Apple's catalogue and
  an in-library mark, results play without being added, Apple/cover footer on both menus,
  Songs sort control + play bar, Apple Music logo in Integrations, Discord links opening
  `music.apple.com/song/<id>`, per-button switch.

Broken or unproven — fix before adding features:

1. **Playlist pushes: Apple answers 500 for most playlists** (104 × 204 accepted vs ~1000 ×
   500). Suspects, in order: duplicate song ids in a playlist, stale library ids, and
   `ApplePlaylistWrites` hardcoding `playlistEditable = true` instead of reading the row's
   `isEditable`. The retry storm is capped (30s cooldown) but the cause is not fixed.
2. **`scripts/seams.sh` exits 1**: `MusicService` 25, `PopupSheet` 43, `PlaylistMenu` 34,
   `SongMenu` 48, `BottomSheetPlayer` 24 added lines over the 20 budget. Mechanical: move
   each block into a fork file and leave a one-line call.
3. **Animated cover never watched on screen** — data is cached, the render path is installed.
4. **Refresh felt stuck**: now has phases (library → songs → playlists) and the animated
   sweep runs in the background; re-check the pacing end to end.

## 3. What to build next, in order

1. **Albums on import.** `AppleImport` writes the song and its artist but no `AlbumEntity`
   and no `song.albumId`, so the Albums shelf is empty for Apple music. Read
   `relationships.albums` / `attributes.playParams` in `AppleLibrary.songs()` into
   `RemoteSong.albumId`, write an `AlbumEntity` per album (`AM_ALB_<id>`, Apple artwork,
   artist link), set `song.albumId`, and honour `CoverOverrides` for album artwork.
2. **Playlists as a grid** like Albums: the playlists shelf gains list/grid, using
   `LibraryGridScreen` and the same controls (play all, shuffle, sort) — not a new design.
3. **Cover customization, the rest**: upload from device + square crop (there is no cropper
   in the repo — one has to be written), the online lookup already exists as a URL field,
   and the same `CoverSheet` wired to artists and songs. Manual covers must keep surviving
   imports (already enforced) and "Use Apple's cover" must keep working.
4. **Discord presence, made visual**: `DiscordPreview` already renders the presence; make
   its regions tap targets (heading, description, large image, small image, each button)
   opening the matching picker, with text templates as presets rather than raw
   `{artist} on {app}` fields.

## 4. Rules for the work

- **The UI must look like Mocha's, never like a new app.** Reuse the app's own components
  before writing any: `SearchCategoryRow`, `LibraryGridScreen`/`LibraryGridArtwork`,
  `SheetActionCard`, `MenuRow`, `Material3MenuItemGroup`, `SettingsItem`/`SettingsGroup`,
  `ImportInfoCard`, `PlaylistSortControl`. Matching means the same heights, paddings,
  radii, chip style, font sizes and empty states — compare against the nearest existing
  screen, and screenshot both before claiming a match.
- Put new UI and logic in fork-owned files (`applesync/…`, `ui/menu/CoverSheet.kt`,
  `applesync/ui/…`) and call them from upstream files with a one-line, tagged seam.
- Every Apple write: guard first, audit row, then verify by reading back. Never fold a read
  failure into an empty list.
- Verify on the device (`adb -s 001873517000081`), pull both `apple_sync.db` **and** its
  `-wal`, and report real output — no claims without evidence.
- Commit in `apple-sync:` slices, ≤72-char subjects, and keep `seams.sh` green.

## 5. Done since this brief was written (2026-11-19)

**The seam budget is met again — `./scripts/seams.sh` exits 0.** The brief counted five
over-budget files; there were ten. All are fixed by moving code, never by shrinking features:

| file | was | now | moved to |
| --- | --- | --- | --- |
| SongMenu | 48 | 15 | `ui/menu/SongAppleMenuItems.kt` (spread into `listOfNotNull`) |
| PopupSheet | 43 | 10 | `applesync/ui/SheetPages.kt` (page stack + back arrow) |
| PlaylistMenu | 34 | 5 | `playlistCoverMenuItem` in `PlaylistAppleSyncMenu.kt` |
| LibraryEntityListScreen | 33 | 17 | `applesync/ui/LibraryRows.kt`; one `playBar` slot replaces three callbacks |
| LibrarySongsScreen | 32 | 9 | `SongsOrdering.playBar()` |
| AppleMusicModuleTest | 32 | 0 | `AppleMusicPinnedTest.kt` (own fake host) |
| MusicService | 28 | 19 | `pushLibraryThen`, `ApplePresenceFacts` |
| MorphingCover | 74 | 9 | `applesync/ui/AppleCover.kt` (motion + its ExoPlayer) |
| NavigationBuilder | 28 | 3 | `ui/applesync/AppleNavGraph.kt` |
| BottomSheetPlayer | 24 | 11 | `applesync/ui/SeeVideo.kt` |
| (also tagged) | — | — | NowPlaying, PresenceActivity, PresenceSettings, LibraryDesign, DiscordScreen, DiscordSettingCards |

Verified on the device this pass: the song menu still shows *Pick a recording…* and
*Match on Apple Music…* in place and the match sheet opens; the playlist menu shows *Cover*,
and it opens as a page with a back arrow that returns to the menu; the Songs list draws its
play bar with the sort control at its end; **the animated cover was seen moving** — two
screenshots of the player 2 s apart differ across the cover box (560k px), and the menu
footer reads "Cover: Apple's animated artwork, cached"; *Full video* toggles without a crash.
Tests: 49 applesync unit tests, 12 applemusic module tests, all green.

One behaviour worth a decision, untouched: Android **Back** while a page is open closes the
whole sheet (`BackHandler` → `close()`), while the sheet's own back arrow steps back a page.
Making Back step back first is a one-line change in `SheetPages` + the handler, if wanted.

## 6. Playlist pushes: the 500s are explained and fixed (2026-11-19)

The cause was not the payload. Apple answers **500** to `PUT …/{id}/tracks` for a playlist the
account only *follows*, and says so nowhere: the playlist's own resource carries
`attributes.canEdit=false`, and the writer was passing `playlistEditable = true` to the guard by
hand. Measured on the account: **13 playlists with `canEdit=false`, every one of them 500 on
every attempt (1085 rows); 13 with `canEdit=true`, every one 204.** No duplicate or stale
library ids anywhere — the two suspicions in the old brief were wrong.

What changed:

- the writer reads `library_playlist.canEdit` (falling back to the imported `playlist.isEditable`)
  and hands it to `AppleGuard`; a refusal is audited `REFUSE / PLAYLIST_NOT_EDITABLE`, written to
  `link.lastError`, and shown as *"Apple doesn't allow this playlist to be changed"*;
- a failed call now keeps **Apple's own sentence** in the audit `reason` (and the link), instead of
  a bare status;
- a 2xx is proved by reading the playlist back and comparing the ordered library ids
  (`PlaylistBody.sameOrder`), so `verified` means verified — not "sent";
- the writer runs on `Dispatchers.IO`: `push()` called from a menu tap used to crash, because the
  playlist's songs come from Room's plain (non-suspend) queries, which refuse the main thread.
  **This was only found on the device.**
- the watcher keeps its baseline **on the link row** (`lastSignature`, schema 8, a single
  `ALTER TABLE`). Before, the map was in memory, so every launch rewrote all 26 playlists with a
  replica that could be older than Apple's — a silent way to delete songs added on Apple. A link
  with no baseline now adopts what is there instead of sending it.

Verified on the device: migration to 8 with the audit intact (1488 rows); **0 pushes on launch**
(was 26); a non-editable playlist refused with no request at all; a linked editable one pushed by
hand — `ALLOW 204 verified=1`, `link.lastSyncAt` set, `lastError` cleared; **0 × 500 in the window**
where the old build produced them.

Not exercised end to end: a *local edit* reaching Apple through the watcher. Doing so means adding
and then removing a song on the real account, and the song menu's **"Add" row closes the menu
without showing the playlist chooser** in this build — a separate bug, seen while testing, not
investigated. Worth a look next: `AddToPlaylistDialog(isVisible = …)` inside a `PopupSheet` menu.

## 7. Albums, and the song menu's grid (2026-11-19)

**Albums on import — done.** `AppleImport` wrote a song and an artist and no album, so the Albums
shelf held 5 rows for 1,400 songs. The library read now asks for `include=albums`, `AppleLibrary`
parses the album off each row (id, artwork, `trackCount`, `releaseDate`, and the song's
`trackNumber`), and `AppleAlbums` writes one `AM_ALB_<id>` row per album after the songs are in:
`song_album_map` entries for the album's page, and `song.albumId` for the shelf's query — both are
needed, and one without the other is an album that appears and opens empty. A chosen cover
(`CoverOverrides.Kind.ALBUM`, new) wins; a re-import refreshes count/year/artwork and leaves
bookmarks alone.

The trap worth remembering: **Apple's `next` page is a bare offset URL** (`…/songs?offset=100&limit=100`)
— `include=albums` is dropped on page two, so the first cut linked 97 of 1,405 songs and looked like
"Apple only gives albums for saved albums". Keeping the parameter across pages: **1,406 songs linked,
918 albums**, one song with no album at all. Verified on the device: Albums 917 in the Library home,
the grid draws Apple albums with artwork and artist, and an album page lists its songs in Apple's
track order (Familia → "Bam Bam").

**The song menu's four tiles — done.** `SongActionTileGrid` (in `SongActionTiles.kt`) is the two card
rows and nothing else; the player's menu keeps its shuffle/repeat/radio/share rows, and a row's menu
(`SongRowActionGrid`) supplies actions that act on *that song* — library first through
`AppleSongWrites`, heart through `syncUtils.likeSong`. Removed from `SongMenu`: the small
add-to-library row, the header heart and the duplicate add-to-playlist row; "Play next" moved to the
queue rows (`playNextMenuItem`).

**Covers — done for all four kinds (2026-11-19, later the same day).**

`Cover` is a row in a song's, artist's, album's and playlist's menu, and every one of them opens
the same sheet: an image off the phone, an image link, or "Use Apple's cover". A picked image is
centre-cropped square at 720 px, flattened onto white and kept in `files/covers/`, so the row points
at a file the picker's permission cannot take away; a stored file is deleted when the row goes back
to Apple's cover. Applying writes the database row at once and remembers what it replaced
(`backup:<kind>:<id>` in `cover_override`), so revert restores *that* rather than blanking a row
nothing else rewrites. Imports already respected a chosen cover for songs, albums and playlists; the
artist's picture is now written from the override when an import creates the artist.

Verified on the device, each kind end to end: URL → override + row + header repaint; revert →
override and backup gone, artwork back; photo picker → `file://…` value, a 720×720 JPEG on disk, the
header showing it; revert → file deleted. One thing *not* built: a draggable cropper — the crop is
the centre square, which is the honest default for covers.

## Import speed and playlist covers (2026-11-19, later still)

**Playlist covers.** `AppleLibrary.playlists()` read `name` and `canEdit` and dropped
`attributes.artwork`, so every imported playlist wore its first track's album cover — 26 of 26 in
`mochy.db` on this account. It now reads Apple's artwork; a playlist that sends none (a radio-style
list) still falls back to the first track. After a refresh, 21 of 26 carry Apple's own art (a
`SG-MQ-…` or `gen/1200x1200` URL); the other 5 match their first track's, which is the fallback
doing its job — those playlists send no artwork.

**Import speed.** The refresh was ~400 s for 1494 songs and had almost no network in the slow part:
every cost was a per-row round trip — a `cover_cache` read per song, an unconditional mapping write
per song, a thumbnail update per song, `album(id).first()` (a relation query that loads the album's
songs) plus a row read per song per album, and a `getSongById` per playlist entry. Now it is one
read per table up front, a mapping write only when a field moved, and the album pass in batches of
200 inside one transaction. Measured on the device with nothing changed between runs: **399 s → 102 s**.
Apple's own read is ~63 s of that (library + favourites over the wire) and the playlist imports ~13 s.

If it needs to get faster again, the next wins are, in order: import playlists concurrently (26
requests serially is minutes of latency, not CPU), and skip the album pass entirely when the album
set and its songs are unchanged.

## Refresh cost, owners, the footer and the background (2026-11-19, evening)

**What a refresh now does when nothing changed.** Apple's library read is the only part that has to
happen: ~1,400 songs over the wire is ~60-70 s on this connection. Everything after it is cheap or
skipped —

- songs: mappings are compared in memory and written only when a field moved (that landed earlier);
- albums: a stamp of "which album holds which song" is compared and the whole pass is skipped when it
  matches (`album_signature` in `sync_state`);
- playlists: `lastModifiedDate` is kept per playlist, so an unchanged playlist is not read at all —
  one request for the list, none for the songs. A measured second run: *"Nothing new · 1420 songs
  already there · 26 playlists unchanged"*.

**Owners.** Apple's library playlist resource sends exactly: `canDelete, canEdit, dateAdded,
description, hasCatalog, hasCollaboration, isPublic, lastModifiedDate, name, playParams` — verified
against the live account. There is **no owner name**, so "by Guest" could not be replaced with a
person's name honestly. What it can say, it now says: `canDelete` → *You* (or *Shared with you* when
false), and `hasCollaboration` adds a second creator, so a shared playlist reads *You & others*.

**Background.** `AppleSyncService` (foreground, `dataSync`) owns the refresh, so leaving the app no
longer interrupts it, and the notification reports the phase, the counts and a bar, then finishes
with the same sentence the in-app card shows.

**Footer.** End of the library's All tab: songs / albums / playlists as chips that open each screen,
then one line — *1,407 on Apple Music · 6 on YouTube · synced 2 min ago*. The timestamp is a Flow
from `sync_state`, so it moves when a sync finishes.

## The owner's name was one level down (2026-11-19, night)

The library playlist resource carries no owner, which is why the page said "Guest"/"You": verified
attributes are `canDelete, canEdit, dateAdded, description, hasCatalog, hasCollaboration, isPublic,
lastModifiedDate, name, playParams`. The name exists one level below it: **a single playlist requested
with `include=catalog` returns `relationships.catalog.data[0].attributes.curatorName`** — the same
field the web player prints, plus `curatorSocialHandle`.

- The *list* endpoint does **not** return those attributes even with `include=catalog`, so owners are
  looked up one playlist at a time, once ever: `playlist_meta.ownerChecked` stops the refresh asking
  again. Result on this account: 19 the account's own name, 1 another person's, 2 Apple's curators
  ("Apple Music", "Apple Music Pop"), 4 with none (`You` / `Shared with you`).
- Sidecar is at **schema 11** (`ownerChecked` on `playlist_meta`).

Also in this pass: the library footer is two plain sentences with tappable words (no chips), and
*on Apple Music* / *on YouTube* open `apple/songs/{apple|youtube}` — the Songs list filtered, same
rows, same sort control. Playlists are a grid now, via `LibraryGridScreen`, which grew an
`onLongClick` so playlist tiles keep their menu.

## Playlists that were short, hidden artists, and the toggle (2026-11-19, late)

**Why playlists arrived short.** Two separate drops, both silent:

1. `if (songId !in known) { skipped++ }` — a playlist entry that is not in the library was thrown
   away, so a followed playlist arrived with the few songs that happened to be saved. Those entries
   are now imported as songs with **`inLibrary = null`**: the playlist is whole, and the library, its
   counts, its Artists shelf and its footer do not grow with music nobody saved. If Apple's library
   later has one, `AppleImport` gives it its `inLibrary` back.
2. The tracks were read from `/v1/me/library/playlists/{id}/tracks`, which lists only the tracks that
   are also in the library. When the playlist has a catalogue counterpart (`include=catalog` on the
   single resource gives `relationships.catalog.data[0].id`), its own
   `/v1/catalog/{storefront}/playlists/{id}/tracks` is read instead — the complete list, in Apple's
   order. The id is stored in `playlist_meta.catalogId` (sidecar schema 12).

Measured on this account afterwards: both endpoints agree on **every** playlist (27/27, 18/18, 72/72,
146/146, …), so what is imported is exactly what Apple sends. Four of the 26 playlists are genuinely
1-3 tracks in Apple's own data ("Study/Chill" 1, "SWAG CLEANED" 1, "IDK" 3). If the Music app shows
more for one of those, the next thing to read is `attributes.trackCount` on the catalogue playlist
resource — that is the only count Apple has not been asked for yet.

**Why the Artists shelf was nearly empty.** `artists()` kept only `isYouTubeArtist || isLocal`, and an
imported Apple artist is neither — 794 of the 804 artists with songs were filtered out, leaving
eleven. An artist that has songs in the library is listed now.

Known, unfixed: duplicate artist rows ("Alan Walker" twice, 1 and 35 songs) because
`artistByName` matches exactly and Apple's spelling can differ from a row created earlier.

**Credit line.** The shared-playlist marker was a second creator literally named "others", so the
header read "Faizaan & others". The line is the owner alone now and "Collaborative" joins the stats
line, in the same muted type as the song count.

**Playlists.** The grid replaced the list for one build; it is a toggle now, list by default, with
the choice remembered (`playlistsGridView`) and the switch in the header. `LibraryGridScreen` grew a
`trailing` slot and an `onLongClick` for it.

## What Apple will not tell us about a collaborative playlist (2026-11-19, last)

Trying to show the other people's names, probed against the live account:

| asked | answered |
|---|---|
| `GET /v1/me/library/playlists/{id}` | attributes end at `hasCollaboration`; `relationships` is null |
| `?include=collaborators` / `?extend=collaborators` on the library resource | same resource, no relationship |
| `?include=catalog,collaborators,curators,owner` | `relationships = [catalog]` only |
| `/v1/me/library/playlists/{id}/collaborators` | **400** |
| `/v1/me/library/playlists/{id}/curators` | **400** |
| `/v1/catalog/{storefront}/playlists/{id}?include=curator` | `relationships = [curator, tracks]`; `curator.data[0]` is a **social-profile** (`artwork, handle, isPrivate, isVerified, name, url`) and there is exactly one — the owner |

So MusicKit exposes the owner as a social profile and nothing about who else is on the playlist. The
header therefore reads: owner's name, then a `Collaborative` pill with the people icon
(`applesync/ui/CollaborativeBadge.kt`, hung on `PlaylistHeader`'s `below` slot).

The owner's social profile does carry an **avatar** and a handle — a future touch is to fetch
`/v1/catalog/{storefront}/playlists/{id}?include=curator` once per playlist and show the real face
instead of the initial, at one extra request per playlist, once.

## Correction: the web-player probe has to use the app's own session (2026-11-19)

An earlier entry here said Apple "does not expose" collaborators and cited a web-page probe. That
probe was wrong: it built a throwaway `WebView` with the default user agent and no warm session, so
Apple served the page to an **anonymous** visitor — `canEdit: false`, `"collaborators": []`. That is
a fact about the probe, not about Apple.

Re-run the way Integrations signs in (`CaptureWebContent`): the app's `CookieManager` + local storage,
`DESKTOP_USER_AGENT` (WebView's `; wv` marker makes Apple serve a reduced page), DOM storage and
third-party cookies on. Then the page reports:

- `musickit: true`, `authorized: true`, `musicUserToken` 246 chars → **genuinely signed in**;
- `script#serialized-server-data` is **absent** on signed-in pages (`pageLength: 0`) — the player is
  client-rendered for signed-in users, so there is nothing server-side to parse;
- `GET /v1/me/library/playlists/{id}/relationships/collaborators` → **404**;
- `?include=collaborators` → 200, and the attributes are the same set as before (no collaborators).

So the endpoint the *player UI* uses to draw collaborator avatars is still unknown. The way to find it
is to hook `fetch`/XHR in the page (the repo already does this for Spotify in `CaptureWebView.HOOK_JS`)
and read `window.__mochyCalls` after hydration — the harness for that was written and removed again
without a successful capture, so treat it as unstarted rather than failed.

**Before building anything on top of it, get a playlist that actually has a second person.** Every
empty result so far — `collaborators: []` in the page, nothing in REST — is equally explained by "this
playlist has no collaborators". A solo playlist cannot validate the feature in either direction.

## Collaborators: how it actually works (2026-11-19)

Built the way every other page interaction in this fork is built - `AppleSessionScreen`'s pattern,
not a headless WebView:

1. **`AppleCollaboratorsScreen`** (`ui/applesync/`), reached by tapping the **Collaborative** badge on
   a playlist page. A real, attached WebView on `music.apple.com/library/playlist/<remote id>` with
   the app's `CookieManager`, the phone Chrome user agent, and `ApplePageScripts.captureHook()`
   installed as a document-start script. `WebViewApplePage` is the transport.
2. **`ApplePageScripts.readCollaborators()`** waits for the page to draw, finds the credit line that
   reads `Owner & N other`, walks up from it until the block holds two `img[alt]` avatars, and returns
   their alts plus the words around "collaborat" as evidence.
3. **`PlaylistCollaborators.parse`** keeps the names, drops page chrome (My Account, Music controls,
   navigation) and turns handles into `@handle`. `PlaylistCollaboratorsTest` covers it.
4. **`playlist_collaborator`** (sidecar schema 13) holds `playlistId, name, position`. The credit line
   reads it and shows owner + collaborators; the playlist's own title is filtered at both ends,
   because the page's cover alt carries it.

Measured on the device: "Am I God?" → `Faizaan & Disha Singh`, badge still beside the stats.

Lessons that cost time here, so they do not cost it again:
- A **headless, unattached WebView** never reaches `onPageFinished` reliably in this app, and with the
  default user agent Apple serves an anonymous page (`canEdit:false`, `"collaborators":[]`). Use the
  attached WebView and the same UA as the sign-in flow.
- Signed-in pages are **client-rendered**: `script#serialized-server-data` is absent, so there is no
  JSON to parse - the DOM is the source.
- The page shows **"& 1 other"**, not the name: the name is only in the avatars' `alt`.

## Collaborators: the icon, and reading them all (2026-11-19, last)

- The mark is a **round icon beside shuffle** now (`PlaylistHeader`'s `onCollaboratorsClick`, drawn with
  the same `CircleAction` as search and add-to-library), not a pill under the header.
- The reader walks a **queue**: `apple_collaborators/<local id>` for one playlist, or
  `apple_collaborators/all` for every shared playlist that has no names yet — reachable from the icon's
  sheet ("Read the other playlists") and from Apple Music settings ("Collaborators"). One page load per
  playlist, ~10 s each.
- **This account's own name is filtered at both ends** (`PlaylistCollaborators.selfName`: the most
  common `ownerName` among playlists with `canDelete`). A row that is really the account is also treated
  as "unread", so the playlist is re-read and the stale row removed — a read that saw the credit line is
  authoritative even when it finds nobody.
- Measured after a bulk run: `Am I God?`, `Can I cry?`, `Concoction`, `Dim Lights`, `Let's Dance`,
  `Feeling Local` → `Disha Singh`. Three other shared playlists still read nothing; they stay in the
  queue for the next run, and their pages may simply show no second avatar.
