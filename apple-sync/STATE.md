# Where this stands

Read this first. It is the state of the fork, what is proven, and what is next.
`PLAN.md` is the original design; `architecture/` is the map of upstream; this file is the present.

## Proven on a live account

| Thing | Evidence |
| --- | --- |
| Apple reads (library, playlists, search, favourites) | 1490 songs / 26 playlists read repeatedly, `in` storefront |
| Playlist create / add / replace / delete | sandbox run, verified by re-read, audit rows |
| Library add / remove | 2 songs added and removed from the real library |
| Favourite / unfavourite | `200` + heart present, then cleared |
| Import into Mochy | `AM_<catalogueId>` songs in `mochy.db`, idempotent on re-run |
| Epoch + audit | 1516 rows, "everything that was there is still there" after every write test |

## Endpoints that matter (all confirmed on the account)

- **Library writes**: `amp-api.music.apple.com`. Add a song with `POST /v1/me/library?ids[songs]=<catalogId>` and **no body** (a JSON body is a 400).
- **Ratings/favourites**: `api.music.apple.com` — **different host**. `PUT /v1/me/ratings/songs/{id}` with `{"type":"rating","attributes":{"value":1}}`; `DELETE` clears; `GET /v1/me/ratings/songs?ids=a,b,c` reads a hundred at a time. Type is `rating` singular.
- **Playlist tracks**: `PUT …/{id}/tracks` with the full ordered list, `{"data":[{"id":"i.…","type":"library-songs"}]}`. No delete endpoint; the list is the truth.
- **Library list is not ordered by date added** — do not poll the first page for a new row. Use `/v1/me/library/search`.
- **Never fold a read failure into `emptyList()`** — a dead token then looks like an empty library.

## How the fork is built

- Sidecar database `apple_sync.db` (never `mochy.db`): epoch, owned, link, track, snapshot, journal, audit, `library_song`, `library_playlist`. Migrations are explicit; a version bump must never drop the epoch.
- Songs imported from Apple have id `AM_<catalogueId>`, which is what makes the import idempotent and every later write addressable.
- `AppleSyncStatus` holds the one progress every screen reads; `AppleSyncRunner` runs the sync on a process-wide scope so leaving the screen does not cancel it.
- `AppleSyncCard` is the progress UI, in the import screens' own card component.
- Seams are enforced by `scripts/seams.sh` (budget 20 added lines per upstream file, each tagged `// MOCHY-APPLE:`). Current seams: `NavigationBuilder.kt`, `IntegrationsScreen.kt`, `LibraryHomeScreen.kt`, `PlaylistMenu.kt`, `Items.kt`, `DatabaseDao.kt`.
- Strings live in `res/values/apple_sync_strings.xml`, never in upstream `strings.xml`.

## Decisions already made

- Apple is the library of record; YouTube stays a separate source.
- A playlist is only writable once **linked** — the link row is the user's permission, and the guard refuses anything else.
- Nothing in the epoch is ever modified; nothing not owned is ever deleted.
- Songs added from other providers default to **not** syncing; adds to the library or to an Apple playlist sync both ways.
- The UI follows Mochy's own patterns: settings groups for settings, playlist menus for playlist actions, import cards for progress.

## Where this stopped (2026‑11‑19, all of it committed)

Built today, in the commits named in `git log upstream/main..HEAD`:

- **Buttons reach Apple.** `AppleSongWrites` pushes the heart (PUT/DELETE `api.music.apple.com/v1/me/ratings/songs/{id}`) and the library add/remove (`amp-api` `POST /v1/me/library?ids[songs]=…`, `DELETE /v1/me/library/songs/{id}`) with the captured token pair, behind `AppleGuard`, each verified by a read and an audit row. Ratings are no longer "mutating" in the guard: a heart removes nothing, so the epoch does not refuse it; removing a song from the library still is, and must stay that way.
  Proven on the account: `RATING_SET 1858248614 ALLOW 200 verified=1` then Apple answering `value:1`; `RATING_CLEARED … 204 verified=1` then `404`; a library remove refused as `PRE_EXISTING_OBJECT` with Apple untouched.
- **Manual picker** in `ForceProviderSheet` (`ui/pick/ManualYouTubePick.kt`): YouTube Music search, prefilled with the song, pick = the row becomes that video (`ImportedVersions.replace`, now `internal`), likes/playlists/library flag move with it. Shown on every song, Apple ones included.
- **Apple identity survives a rematch**: a pick on an `AM_…` song writes `rematch(applemusic:<catalogId>, MANUAL)` + a `track` row, and `AppleImport` now honours those mappings — a re-import refreshes the chosen row instead of inserting `AM_<id>` again.
- Tapping the playing row in the library opens the player (`LibraryEntityListScreen`) instead of restarting.
- "See video" honoured in module/skin styles, and it says so instead of doing nothing when YouTube has no video.

Fixed after their first test round, same day: the YouTube **liked-songs sync was un-liking every Apple song** on each run (`localToggleLike()` over everything not in YouTube's LM), which is why a heart never stuck; it now skips `AM_` ids. The **Apple → Mochy** direction now exists: `AppleImport` mirrors the rating onto the local row on every import (proven on device: a rating set through Apple's API came back as `liked = 1` after one refresh). The **library button** was refused 19 times in a row by the epoch (`PRE_EXISTING_OBJECT`) — a confirmed tap is now allowed: the refusal toast offers "Remove anyway" and says the removal cascades into Apple playlists. `aYesDoesNotRemoveAnIdTheAppHasNeverSeen` and friends hold that line.

Unverified on device: the picker's UI and the rematch-aware re-import (compiles, installs, 48 applesync tests green). Still unproven: the library **add** push (needs a song whose Apple copy is not in the library yet).

Traps hit again today: Room rejects a hand-written migration whose entity doesn't declare the index it creates; `adb input tap` on a skin player's heart lands on the seekbar.

## Deferred on purpose

- **Creating a playlist on Apple.** Today a local playlist can only be *linked* to an existing
  Apple playlist, and "Send to Apple Music" replaces that playlist's tracks - so the first push of
  a new playlist would overwrite one of the account's own. The call is already known (`POST
  /v1/me/library/playlists`, in `AppleWrites`' sandbox stage): give the unlinked state of the menu
  row a create path, link what Apple answers with, then verify the ordered ids by re-read like
  every other write.

## Not built yet, in the order I would do them

0. **What was asked for next, in their words:**
   1. **The Apple match sheet comes back.** It was removed from the song menu today (only the YouTube picker in force-change survived). The song menu needs "Match on Apple Music…" again, searching the Apple catalogue and pinning the recording — this is the same sheet as the picker, with Apple as the provider.
   2. **Adding to the library in Mochy must reach Apple**: on "add to library", match the song on Apple (`AppleMatcher`), and push the add when it matches.
   3. **Like and add/remove must work both ways**: Mochy → Apple is built and proven; Apple → Mochy at pull time is **not** — `AppleImport` writes `liked` only when it inserts a new song, never on a re-import, and nothing reconciles a rating changed on Apple afterwards.
   4. **When no Apple match is found on an add**: ask — a sheet with Apple search prefilled with the song's name, plus a **Skip** that adds the YouTube version instead, marked as not synced with Apple.
1. **Manual rematch, Apple side.** The mapping, the resolver hook and the `externalId` path all exist; only the Apple-facing sheet and the "keep as YouTube" answer are missing. Monochrome has no search API, only `addLink`.
2. **Playlists as real Mochy playlists** (import + membership), then add/remove/reorder push through the guard.
3. **Refresh diff**: snapshot vs Apple tells us what was deleted on Apple; propose removals rather than deleting silently.
4. Foreground service, so a sync survives the process being killed.

## UI conventions this fork follows (assessed in `architecture/50-ui.md`)

- Settings live in **Settings → Integrations → Apple Music**, built from `SubSettingsScaffold` +
  `SettingsGroup(style = Grouped)` + `SettingsItem`, exactly like the YouTube Music page. Never a
  bespoke screen, never a pane inside scrolling content.
- Anything that runs for more than a moment draws `ImportInfoCard` with a label, a bar when the total
  is known, and counts that only go up.
- Playlist actions belong in the playlist's own menu, next to Re-sync (`PlaylistMenu.kt`), built with
  the same `Material3MenuItemData` helper.
- Per-song actions belong in the existing player sheets (`ForceProviderSheet` for providers).
- Progress has one owner (`AppleSyncStatus`); a screen that invents a second counter will disagree.
- Strings go in `apple_sync_strings.xml`, with an `apple_sync_` prefix, in one tagged block.

## Working on it

```bash
export JAVA_HOME=$HOME/.local/jdk/jdk-21.0.13+9
./gradlew :app:assembleUniversalFossDebug --no-configuration-cache
adb -s 001873517000081 install -r app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk
./scripts/seams.sh      # must exit 0
```

Emulator fallback: `apple-sync/dev/run-emulator.sh` (headless, `GPU=host`).
Device database for inspection: `adb exec-out run-as com.mochyy.mochymusic.debug cat databases/apple_sync.db > /tmp/apple_sync.db` (pull the `-wal` too, or recent writes will not show).

## Traps this fork has already hit

- A `JavascriptInterface` added after page load is invisible to that document; attach before `loadUrl`, and run scripts from `onPageFinished`.
- Apple's web player UI is unusable inside a WebView (event queue overflows, menus paint empty, its clicks send nothing a hook can see). Use the documented endpoints; the page is for tokens only.
- The stored media-user-token dies when the user signs in elsewhere; refresh it from the page session on every load.
- A destructive schema fallback would drop the epoch. Every migration must be written out.
