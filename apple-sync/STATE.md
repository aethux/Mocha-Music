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
| Import into Mocha | `AM_<catalogueId>` songs in `mochy.db`, idempotent on re-run |
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
- The UI follows Mocha's own patterns: settings groups for settings, playlist menus for playlist actions, import cards for progress.

## Where this stopped (2026‑11‑19, all of it committed)

Built today, in the commits named in `git log upstream/main..HEAD`:

- **Buttons reach Apple.** `AppleSongWrites` pushes the heart (PUT/DELETE `api.music.apple.com/v1/me/ratings/songs/{id}`) and the library add/remove (`amp-api` `POST /v1/me/library?ids[songs]=…`, `DELETE /v1/me/library/songs/{id}`) with the captured token pair, behind `AppleGuard`, each verified by a read and an audit row. Ratings are no longer "mutating" in the guard: a heart removes nothing, so the epoch does not refuse it; removing a song from the library still is, and must stay that way.
  Proven on the account: `RATING_SET 1858248614 ALLOW 200 verified=1` then Apple answering `value:1`; `RATING_CLEARED … 204 verified=1` then `404`; a library remove refused as `PRE_EXISTING_OBJECT` with Apple untouched.
- **Manual picker** in `ForceProviderSheet` (`ui/pick/ManualYouTubePick.kt`): YouTube Music search, prefilled with the song, pick = the row becomes that video (`ImportedVersions.replace`, now `internal`), likes/playlists/library flag move with it. Shown on every song, Apple ones included.
- **Apple identity survives a rematch**: a pick on an `AM_…` song writes `rematch(applemusic:<catalogId>, MANUAL)` + a `track` row, and `AppleImport` now honours those mappings — a re-import refreshes the chosen row instead of inserting `AM_<id>` again.
- Tapping the playing row in the library opens the player (`LibraryEntityListScreen`) instead of restarting.
- "See video" honoured in module/skin styles, and it says so instead of doing nothing when YouTube has no video.

Fixed after their first test round, same day: the YouTube **liked-songs sync was un-liking every Apple song** on each run (`localToggleLike()` over everything not in YouTube's LM), which is why a heart never stuck; it now skips `AM_` ids. The **Apple → Mocha** direction now exists: `AppleImport` mirrors the rating onto the local row on every import (proven on device: a rating set through Apple's API came back as `liked = 1` after one refresh). The **library button** was refused 19 times in a row by the epoch (`PRE_EXISTING_OBJECT`) — a confirmed tap is now allowed: the refusal toast offers "Remove anyway" and says the removal cascades into Apple playlists. `aYesDoesNotRemoveAnIdTheAppHasNeverSeen` and friends hold that line.

Unverified on device: the picker's UI and the rematch-aware re-import (compiles, installs, 48 applesync tests green). Still unproven: the library **add** push (needs a song whose Apple copy is not in the library yet).

Traps hit again today: Room rejects a hand-written migration whose entity doesn't declare the index it creates; `adb input tap` on a skin player's heart lands on the seekbar.

## Proven on the new package, 2026-11-20 (8.4.5)

- **Sign-in**: the page captured both tokens (sealed in DataStore), the account row then said the
  account's name, and the integrations page showed Sign out. The page itself is fixed: own bar, no
  app chrome over it, and a **Region** setting (28 storefronts, "let Apple decide") because a
  guessed storefront gets Apple's "choose another country" prompt instead of the library.
- **Audit**: `AUDIT_FIRST_RUN ALLOW 200 verified=1`, "1493 songs and 26 playlists in IN".
- **Import**: `1419 new songs · 0 already there · 26 of 26 playlists updated · 1032 playlist songs ·
  took 2m 11s`; the library holds 1405 songs, 26 playlists, 911 albums, 795 artists.
- **Playlist writes, both directions** on "Angst and Yearn" (`p.vMO5BDKs1bb82mE`): add → audit
  `PLAYLIST_REPLACE 204 verified=1` with the new song in the link signature; remove → `204
  verified=1` with the signature back to the original 16 ids. Each push landed about a second after
  the tap, and `verified` is Apple's own list re-read and compared in order, not the 2xx.

## Proven: an add from a search result reaches Apple (2026-11-20)

`YouTubeSongMenu`'s "Add to library" used to call only `YouTube.toggleSongLibrary` and insert a local
row — Apple was never asked, so a song added from search never reached the library of record. It now
offers the recording to `AppleSongWrites.pushLibrary` first (which matches the catalogue when the row
has no Apple id, pushes the add and reads the library back) and keeps YouTube as the fallback for
recordings the catalogue cannot name. The work runs on its own `CoroutineScope(Dispatchers.IO)`
because the sheet's `rememberCoroutineScope()` is cancelled by the recomposition a tap causes.

Proven on the device, twice, on the shipped code (8.4.5 debug, writes on, session live):

```
LIBRARY_ADD 1440801919 (Sultans Of Swing)  202  verified=1
LIBRARY_ADD 1440912599 (Money For Nothing) 202  verified=1
```

202 is Apple accepting the add asynchronously; `verified=1` is `awaitLibraryId` finding the song in
Apple's library afterwards, so the add is real and not just an accepted request. Local rows went in
with the Apple id mapped (`cF3OWCYLLVQ`, `wTP2RUD_cL0`).

**Probe notes, because two of them lied.** `android.util.Log.d` from this app does not reach
`adb logcat` on this phone (19 lines from the pid, none of them the app's own) — and a file trace in
`cacheDir` from the first statement of the click handler also never appeared, while the handler was
in fact running. What settled it was a `TopToaster` probe, which is visible in a screenshot. Also:
never read these databases by copying `mochy.db` and `mochy.db-wal` as two files — SQLite silently
ignores the WAL and rows written seconds ago look absent. Stream them together
(`tar -cf - databases/mochy.db databases/mochy.db-wal`).

## Proven: a playlist made here can be made on Apple (2026-11-20)

The picker in "Sync with Apple Music" only ever offered Apple's own playlists, and linking means every
push replaces *that* list — so a playlist made in Mocha had nowhere to go, and the first push of a new
one would have overwritten somebody else's. `ApplePlaylistCreate` (new) makes one instead: guard, then
`POST /v1/me/library/playlists` with the local name, the id out of Apple's answer, the link row saved
(so the watcher may push), then the songs through the same `ApplePlaylistWrites.push` every linked
playlist uses. The row at the top of the picker is "Create it on Apple Music".

Proven on the device:

```
PLAYLIST_CREATE  p.dl0kkO6IMvvD8VJ  201  verified=1
PLAYLIST_REPLACE p.dl0kkO6IMvvD8VJ  204  verified=1
```

The first run of this (`p.NJZrr3rIOrrg6qR`) came back `verified=0` while its songs verified fine: the
read-back used `playlistTrackTotal`, which reads `meta.total` — a brand-new **empty** playlist has
none, so a good create was called unverified. It now asks Apple's playlist list for the id instead.

Two test playlists were left on the account ("Aeth Apple Test", "Aeth Apple Test 2", one and two songs
of real library songs) because the fork still has no delete-on-Apple action; remove them in Apple
Music, or build that next and let the app do it.

## Proven: a playlist this app made on Apple can be deleted (2026-11-20)

`ApplePlaylistDelete` (new) plus a "Delete it on Apple Music" row in the playlist menu, behind a
confirmation dialog that names the playlist. The guard is what makes it safe: a delete is refused for
anything in the epoch, and refused again unless the playlist is in `owned` — the table written only
when this fork created it — so the account's own playlists are not deletable from here whatever the
menu offers. The local playlist is untouched; the link is dropped, so it simply stops following.

Proven on the device, on both test playlists this session had left on the account:

```
PLAYLIST_DELETE p.NJZrr3rIOrrg6qR  204  verified=1
PLAYLIST_DELETE p.dl0kkO6IMvvD8VJ  204  verified=1
```

`verified=1` is Apple's own playlist list no longer carrying the id — a 204 is also what Apple answers
to a call it ignored. Both link rows and both `owned` rows went with them, and the account is back to
its original 26 playlists.

One UI lesson worth keeping: the menu a long-press opens is a **snapshot** of the state at that moment,
so a row that only exists once an async read (`linked`) has finished never appears at all. The row is
listed unconditionally now and the tap reads the link, which is also why the linked/unlinked title on
the row next to it can read stale.

## Cues: what the playlist says about Apple (2026-11-20)

Every write ends in a toast, and the playlist itself says where it stands:

- the line under its name reads "16 songs · 53m · **Connected to Apple Music**", or "0 songs · **Not on
  Apple Music**" for a playlist Apple has never seen — read through `appleConnectionSuffix` in
  `applesync/ui/PlaylistConnection.kt`, one keyed lookup, so it cannot lag behind like an async
  prefetch;
- the menu row reads **Connected to Apple Music** with "Sent N min ago", or **Not on Apple Music**;
- toasts: "Sent 16 songs to Apple Music" (with a check), "Created “X” on Apple Music", "Deleted “X”
  from Apple Music", "No longer connected to Apple Music", and an error sentence for every refusal.

Wired without spending seam budget: the playlist screen was at 20 added lines, so the collaborators
action moved into `appleCollaboratorsNav` (5 lines to 1) and the cue is one appended call. It now sits
at 17.

## The session page wears the module sign-in's bar (2026-11-20)

The hand-rolled row ("‹ Back" / "Mocha") is gone. The page now carries `BrowserSheetBar` with
`ProviderSquircle(AccountService.APPLE_MUSIC)`, the title **Apple Music**, the **live URL** the page
is on (`music.apple.com/in/home` after Apple's own storefront redirect), and the close button — the
same bar the module sign-in uses, so the two look like one app. Verified on the phone: the page
renders Apple's own header and Home below it, nothing under the clock, no app chrome over it.

**What was not done, and why:** the WebView itself was left alone rather than moved onto
`CaptureWebContent`. The fork's capture runs `ApplePageScripts.captureHook()` as a document-start
script and drives `WebViewApplePage` through `AppleSessionViewModel`; `CaptureWebContent` is the
importer's pipeline with its own probes. Swapping the pipeline to gain a bar would put a working,
proven sign-in at risk for a cosmetic win — so the bar was replicated and the capture path kept.

Because the page was only reachable while signed out, the account row now opens it too: it is where a
session would be renewed, and the only way to see what Mocha asks Apple for without signing out first.

## Fixed: "Create playlist" did nothing (2026-11-20)

The create sheet came up with its fields, and the pill under it did nothing. The cause was the fork's
own "a menu opened from inside a menu replaces it" change: the second `showWithHandle` draws its
content as a **page** over the open sheet, and a page carried only `content` — the nested sheet's
**button bar was dropped**. So the pill on screen was the picker's own "Create playlist", whose onClick
sets `showCreate = true` again: a no-op on a sheet that is already open. Nothing was broken in the
create dialog at all, which is why every probe inside it looked dead.

Fixed in `applesync/ui/SheetPages.kt`: a page now carries its `buttonBar` as well as its content, and
the host draws the front page's bar when there is one (`PopupSheet.kt`, two lines changed, no lines
added — `seams.sh` 0). The create dialog's own `CoroutineScope(Dispatchers.IO)` stayed too: its scope
did die with the recomposition a tap causes.

Proven on the device with the real label: the pill appears, typing enables it, tapping creates the
playlist — `Aeth Fixed` (`LPDTWNucyl`) and then `Aeth Shipped` (`LPoIIgodzx`), both deleted again after.

**And the playlist is not empty any more:** the picker opened the create sheet without
`onPlaylistCreated`, so the song that started the flow never joined the playlist it made. It now
passes `topSongId` through `copySongsToPlaylist` (a fork file, so the upstream picker gains one line
and stays inside its 20-line seam budget). Proven: `Aeth Song2` (`LPjOBKDEYC`) was created with its
song at position 0. A playlist screen's "add every song of this one to another" flow now hands its songs over as well
(`initialSongIds`), so a create from there is not empty either. Compile-checked; not driven on the
phone yet — the picker's create button was not reachable from that path in this session.
`SelectionSongsMenu` is dead code (nothing calls it), so it was left alone.

## The crossmix cap stays here (2026-11-20, decided)

`CrossmixPlanner`'s unmatched-overlap cap is a genuine upstream bug fix — a blend that cannot beat-match
at a slow tempo ran to 9.6 s. It was considered for a PR to `realidkroo/Mochy-Music` and the decision
is: **keep it in this fork**. No upstream PR is planned from here, so nothing about it needs the
upstream repo's permission and the fork stops tracking it as a pending errand.

## Whose app this is, and which links it keeps (2026-11-20)

**Aeth is first everywhere a dev is named**: the About sheet's lead devs (Aeth, Roo, key — and the dev
space pages were re-indexed to match, 0/1/2), and the dev space itself, whose first page is Aeth.

**Links that pointed at Mochy's community are gone**: the "Switch to nightly" row in the update sheet
(the whole nightly/release branch, so no upstream nightlies are reachable), the Discord card on the
profile, the Discord row on the dev pages, Mochy's server as a module-browser fallback, and the
`DiscordUrl` constant itself. Credits are untouched — that is a separate rule.

**Discord rich presence is wired but cannot work in this build**, and the screen says so ("Not set up
in this build of Mocha"). `DiscordAuth.ClientId` is `BuildConfig.DISCORD_CLIENT_ID`, which the build
fills from `local.properties`/env and which is **empty here**. With an id it needs, in the Developer
Portal: OAuth redirect `discord-<client id>:/authorize/callback`, and the Social SDK / Rich Presence
bundle enabled (the scopes are `openid identify sdk.social_layer_presence`). Nothing else in the
pipeline is missing — `MusicService` already pushes play state into `DiscordPresence`.

## Known broken (2026-11-20)

- ~~**Add to playlist does nothing from a song's menu.**~~ **Fixed and verified**: the picker is
  hosted above the sheet, it opens from a song's menu, and the push to Apple is the write proven
  above.

## Deferred on purpose

- **Creating a playlist on Apple.** Today a local playlist can only be *linked* to an existing
  Apple playlist, and "Send to Apple Music" replaces that playlist's tracks - so the first push of
  a new playlist would overwrite one of the account's own. The call is already known (`POST
  /v1/me/library/playlists`, in `AppleWrites`' sandbox stage): give the unlinked state of the menu
  row a create path, link what Apple answers with, then verify the ordered ids by re-read like
  every other write.

- **Unsynced playlists with rules, made in Mocha.** A playlist that lives only here, filled by a
  rule the person sets (artist / liked / added-since / source), and never pushed: Apple has nowhere
  for a playlist this app creates, and the ones it makes come from rules of its own. It is the
  answer to "the automated playlists on Apple are not mine to change" - the same idea held locally.
  Needs: a rule model stored on the playlist row, one evaluation pass (the queries already exist),
  a create/edit sheet in Mocha's own card style, and an explicit "not synced" mark so nothing here
  ever offers to link it.

## Not built yet, in the order I would do them

0. **What was asked for next, in their words:**
   1. **The Apple match sheet comes back.** It was removed from the song menu today (only the YouTube picker in force-change survived). The song menu needs "Match on Apple Music…" again, searching the Apple catalogue and pinning the recording — this is the same sheet as the picker, with Apple as the provider.
   2. **Adding to the library in Mocha must reach Apple**: on "add to library", match the song on Apple (`AppleMatcher`), and push the add when it matches.
   3. **Like and add/remove must work both ways**: Mocha → Apple is built and proven; Apple → Mocha at pull time is **not** — `AppleImport` writes `liked` only when it inserts a new song, never on a re-import, and nothing reconciles a rating changed on Apple afterwards.
   4. **When no Apple match is found on an add**: ask — a sheet with Apple search prefilled with the song's name, plus a **Skip** that adds the YouTube version instead, marked as not synced with Apple.
1. **Manual rematch, Apple side.** The mapping, the resolver hook and the `externalId` path all exist; only the Apple-facing sheet and the "keep as YouTube" answer are missing. Monochrome has no search API, only `addLink`.
2. **Playlists as real Mocha playlists** (import + membership), then add/remove/reorder push through the guard.
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
Device database for inspection: `adb exec-out run-as com.mocha.mochymusic.debug cat databases/apple_sync.db > /tmp/apple_sync.db` (pull the `-wal` too, or recent writes will not show).

## Traps this fork has already hit

- A `JavascriptInterface` added after page load is invisible to that document; attach before `loadUrl`, and run scripts from `onPageFinished`.
- Apple's web player UI is unusable inside a WebView (event queue overflows, menus paint empty, its clicks send nothing a hook can see). Use the documented endpoints; the page is for tokens only.
- The stored media-user-token dies when the user signs in elsewhere; refresh it from the page session on every load.
- A destructive schema fallback would drop the epoch. Every migration must be written out.
