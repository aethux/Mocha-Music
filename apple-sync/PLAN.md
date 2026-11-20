# Mocha + Apple Music: two-way library and playlist sync

Build plan for a personal fork of `realidkroo/Mochy-Music` (GPL-3.0, Kotlin/Compose, Android).
Written 2026-11-18. Target: Apple Music becomes the library of record, YouTube stays as a
secondary source, everything syncs both ways, and upstream updates keep pulling cleanly.

---

## 1. Goal

1. Listen to music in Mocha from whatever source works. Apple's AAC 256 stream is fine.
2. Two-way library sync: adding a song to the library in Mocha adds it to the Apple Music
   library; removing it removes it from Apple. And the reverse.
3. Two-way playlist sync: adds and removes in either place reach the other.
4. Playlist cover art does not need to sync (Apple exposes no artwork write).
5. Apple Music is the default library. YouTube stays working, as a separate source.
6. A song that won't match Apple Music offers two choices: pick the right recording from an
   Apple catalog list, or keep it as a YouTube row.

Non-goals: playlist covers, lossless/Atmos (FairPlay only, not reachable), downloading Apple
streams (no offline licenses exist), touching the module system's UI or player modules, a public
release.

## 2. Locked decisions

- Apple is the source of truth. Mocha's Room database is a local replica plus a queue of edits
  made offline.
- Offline listening is Monochrome plus local files. Apple streams cannot be stored.
- The Apple source module already in the repo (`modules/applemusic`) stays the player. The sync
  layer does not reimplement playback.
- Writes go through a live music.apple.com page inside the app, driving the web player's own
  MusicKit rather than a bare REST client with copied headers. Reads stay on REST. See section 9.
- No pre-existing Apple object is ever modified. The engine refuses writes to anything it did not
  create, and a pre-flight snapshot makes that checkable. See section 4.
- Undocumented Apple endpoints are acceptable. They are isolated behind one interface with a
  capability flag, because they are the only way to delete.
- No changes to upstream's Room schema, no new columns on upstream tables, no upstream file
  reformatting. See section 6.

## 3. What Apple actually allows

| Operation | Call | Status |
|---|---|---|
| Read library songs | `GET /v1/me/library/songs?limit=100` | documented |
| Read library playlists | `GET /v1/me/library/playlists?limit=100` | documented |
| Read playlist tracks | `GET /v1/me/library/playlists/{id}/tracks` | documented |
| Create playlist | `POST /v1/me/library/playlists` | documented |
| Add tracks | `POST /v1/me/library/playlists/{id}/tracks` | documented, ~100 ids per call |
| Add song to library | `POST /v1/me/library?ids[songs]={catalogId}` | documented, answers 202 async |
| Remove song from library | `DELETE /v1/me/library/songs/{libraryId}` | field-reported only |
| Remove tracks / reorder | `PUT /v1/me/library/playlists/{id}/tracks` with `{"data":[{"id":"i.…","type":"library-songs"}]}`, the full ordered list | undocumented, verified on this account 2026-11-18 |
| Rename playlist | `PATCH /v1/me/library/playlists/{id}` | field-reported only |
| Delete playlist | `DELETE /v1/me/library/playlists/{id}` | field-reported only |
| Change feed or timestamps | none | absent |

Field notes that shape the code (all from tools running against real accounts):

- Library adds are 202 async. The song appears seconds later. The response code is not evidence.
- A wrong-key `DELETE` returns 204 and does nothing. Every write must be verified by a read.
- `PUT …/tracks` only accepts `library-songs` ids (`i.…`), never catalog ids.
- Adding a catalog song to a library playlist silently adds it to the library too.
- An empty playlist's tracks relationship answers 404 ("No related resources found").
- Library playlists have `dateAdded` but no `dateModified`.
- Adding an album (`ids[albums]`) is flaky: songs often land, the album row often doesn't.
- The removal `PUT` needs an object with `data`. A bare array of ids answers 400 `Unable to parse
  request body` (code 40007), and `type: "songs"` works as well as `library-songs`.
- That same `PUT` replaces the entire list, so a body that parses but carries no `data` empties the
  playlist. `{"attributes":{}}` returned 204 and left zero tracks. Never send a body without
  `data`, and always verify by reading the playlist back.
- There is no track-level delete: `DELETE …/tracks/{id}` is 404 and `DELETE …/tracks?ids=…` answers
  400 `No mode supplied`. Removal exists only through the `PUT`.
- Deleting a song from the library also removes it from every library playlist (verified).

The transport matters separately from the docs: every write goes through the page (section 9), so
the documented column above says whether an endpoint is documented, not how the app calls it.

## 4. Gate: the two-evening spike (do this before forking)

No Kotlin. Script or `tonearm` against your own session, throwaway data.

1. Create a throwaway playlist in the Apple web player.
2. `POST` two catalog songs into it. Confirm both appear.
3. `PUT` a one-song library-id array. Confirm the other track is gone.
4. Add a catalog song to the library. Confirm it appears. Delete it. Confirm it disappears.
5. Check whether step 4 also removed that song from the throwaway playlist. This is the cascade
   question: if library removal cascades into playlists, the UI must warn before deleting.
6. Remove a song from a playlist while it stays in the library. Confirm the library row survives.

While you're in the web player, settle the write transport in one line: open the console on
music.apple.com and run `MusicKit.getInstance().addToLibrary('<catalogId>', 'SONGS')`. If the song
lands in the library, the page path works and P2's transport is proven before any Kotlin exists.

### Nothing pre-existing is modified

These rules hold for the spike, the engine, and any manual poking at the account.

- **Snapshot first.** Before the first write, record every library song id and playlist id the
  account exposes into a sidecar `epoch` table. Anything in that set is immutable: no delete, no
  move, no rename, ever.
- **Create, then own.** Only objects this app created, recorded at creation, may be edited or
  deleted. An id outside that set is refused, not attempted and not retried.
- **Scope.** Only explicitly linked playlists are writable. An unlinked playlist is read-only even if
  the app created it; unlinking is how a test playlist becomes inert.
- **Never touch.** "Favorite Songs" / "Favourite Songs" and any playlist with `canEdit = false` are
  excluded by name and attribute, whatever the links say.
- **Writes off by default.** `writesEnabled` is false until a settings toggle is confirmed. The spike
  is the only code that writes without it, and it only writes to things it just created.
- **Every write is a decision.** `AppleGuard.decide(op)` returns ALLOW or REFUSE(reason) before
  anything leaves the device. Refusals go to the audit log and the UI; nothing silently retries.
- **Plan, then apply.** A dry run prints the exact diff (ids, names, direction). Applying requires
  that plan to be the thing that runs.
- **Audit log, append-only.** Timestamp, operation, target ids, decision, HTTP result, and the
  read-back that confirms it. A write with no audit entry did not happen.
- **Drift check after every run.** Re-read the library and compare with the epoch: added, removed or
  moved rows are reported, and a changed pre-existing id is a hard failure, not a warning.

The test diet follows from this: new playlists, and library rows the app itself added, which it then
removes. Nothing else.

Go: steps 2-6 behave as described.

Run 2026-11-18, storefront `in`: 9/9 passed. create 201, add 204, `PUT` removal 204 (once the body
was right), library add 202 and landing, library delete 204, cascade yes, playlist delete 204. The
first attempt failed only on the `PUT` body shape, which is now settled.
No-go: library delete is refused or silently ignored. The fallback is "removal is Mocha-only,
manual cleanup on Apple", which is weak enough that the fork should be reconsidered first.

Record the results in `apple-sync/spike-results.md` in the fork, because the merge rules
depend on the cascade answer.

## 5. Architecture

### Truth, replica, provenance

- Apple holds the library and the linked playlists.
- Mocha's `song` table holds rows for everything visible, with provenance in the sidecar DB:
  `apple` (catalog id + library id), `youtube` (video id), `local` (file).
- A Mocha playlist is an ordered list of those rows. The Apple copy is the subsequence of rows
  that have library ids, in the same order. YouTube-only rows simply don't exist on the Apple
  side. That is what makes mixed playlists work without a second model.
- Song ids for Apple rows are `am:<catalogId>`. No collision with YouTube ids, and it makes
  provenance visible in the database, in logs and in bug reports.
- Apple albums and artists get rows too (`am:album:<id>`, `am:artist:<id>`), because the library
  screens join on them. This is a large part of phase 1's cost; do it properly rather than
  leaving nulls that break sorting and filters.
- Thumbnails come from Apple's templated artwork URL expanded to 600x600, the same substitution
  `AppleMusicAccount.kt` already performs.

### Storage: a sidecar database

All new state lives in `AppleSyncDatabase` (Room, its own file), version 1, created by new code in
`com.mocha.mochymusic.applesync`. Upstream's `InternalDatabase` is untouched, so every upstream
schema bump (v43 as of 2026-11-18, 24 entities) rebases with zero conflicts. The DB is opened with
`fallbackToDestructiveMigration`, so `mochy.db` must never hold state the sync cannot rebuild.
Joins happen in Kotlin, keyed by string
ids. The cost is no foreign keys across the two; the benefit is that this fork stays pullable
indefinitely.

| Table | Key | Purpose |
|---|---|---|
| `account` | id=1 | user token, developer token, storefront, expiry, label |
| `link` | playlistId | kind (LIBRARY or PLAYLIST), remoteId, remoteName, direction, enabled, lastSyncAt, lastError |
| `track` | songId | catalogId, libraryId, isrc, durationMs, state (AUTO / PICKED / NONE / YOUTUBE_ONLY), updatedAt |
| `snapshot` | (playlistId, position) | the last synced ordered list: songId, catalogId, libraryId |
| `journal` | id | playlistId, songId, op (ADD / REMOVE / MOVE), origin (LOCAL / REMOTE), at, applied |
| `op` | id | durable write queue: playlistId, kind, payload, attempts, lastError |
| `settings` | key | pacing, poll interval, defaults |

### Two transports

Reads use REST with captured headers. Writes run inside a live music.apple.com page. Section 9
covers the page in detail and why the split exists.

The sync layer keeps its own token pair in `EncryptedSharedPreferences` for the read path. It
reuses upstream's `CaptureWebView` with the Apple page probe, which already returns `{dev, user}`
as JSON, so the sign-in flow is one already-written composable. Tokens last up to six months; on
a 401 the app shows a reconnect banner and keeps the queue.

Known wart: the Apple source module stores its own copy of the same tokens in module storage, so
there are two sign-ins. Fixing that properly needs a module-API read, which is upstream work.
Accept it for v1 and note it in the fork README.

### Playback exactly, not approximately

`TrackQuery` carries title, artists, album, duration and ISRC, but no id, so the Apple module
searches the catalog on every play. Add `externalId: String? = null` and have the module fetch
`/v1/catalog/{sf}/songs/{id}` when it's set. Two seams, nine lines, and it turns fuzzy playback
into the exact recording that's in the library.

### Triggers and pacing

- Push on edit: debounce 20 s, then drain the queue.
- Pull on app foreground, and periodically through WorkManager (15 min floor).
- Full library walk once a day (a 9k library is roughly 90 pages).
- Watch only playlists opted into two-way; never poll the rest.
- One write at a time with a short gap; respect `Retry-After` on 429.

## 6. Rebase discipline (the hard requirement)

The rule is add, don't edit. Everything new goes in its own packages:

```
app/src/main/kotlin/com/mochyy/mochymusic/applesync/       engine, API client, storage, work
app/src/main/kotlin/com/mochyy/mochymusic/ui/applesync/    screens, sheets, view models
modules/applemusic/                                        untouched except one seamless hook
```

Upstream edits are seams. A seam is:

- tagged with a comment on the line above: `// MOCHY-APPLE: <why>`
- as small as possible, ideally one to three lines
- never a reformat, never an import reorder, never a change to neighbouring lines
- never a rename or move of an upstream file

Seam budget for the whole feature:

| Upstream file | Change | Lines |
|---|---|---|
| `modules/api/.../MochyModule.kt` | `TrackQuery.externalId: String? = null` | 1 |
| `modules/applemusic/.../AppleMusicModule.kt` | fetch by externalId when present | ~9 |
| `App.kt` | register the periodic worker (or reuse an existing scheduler) | ~4 |
| `ui/screens/library/LibraryTabs.kt` | Apple tab first by default | ~3 |
| `ui/screens/Screens.kt` + `NavigationBuilder.kt` | two routes: sync settings, match sheet | ~6 |
| `ui/menu/PlaylistMenu.kt` | "Sync with Apple Music" row | ~3 |
| `ui/menu/SongMenu.kt` | "Match on Apple Music…" | ~3 |
| `ui/screens/settings/SettingsPane.kt` (or `MochySettingsScreen.kt`) | settings row | ~2 |

Budget: 8 files, about 30 lines. Verify the exact file names on clone; the package layout above
is the part that matters.

New Hilt bindings and new `@Inject` classes need no upstream DI edits. Follow the existing
`di/*EntryPoint.kt` pattern for anything a worker needs (there are already
`PlaylistResyncEntryPoint`, `DiscordEntryPoint`, `ScrobblerEntryPoint`). New string resources use
an `am_` prefix so upstream's `strings.xml` edits never collide.

Keep a `scripts/seams.sh` that diffs the branch against `origin/upstream/main` and prints only
hunks outside `applesync/` packages, so a rebase review is a one-screen read.

Branch layout:

```
upstream  -> realidkroo/Mochy-Music (fetch only)
main      -> mirrors upstream/main, never committed to
apple-sync-> the work, rebased onto upstream/main before each release build
```

Commit messages prefixed `apple-sync:`; feature work squashed into logical commits
(engine, storage, screens, seams) rather than one blob or fifty WIPs.

Upgrade ritual, monthly or before any build you care about:

```
git fetch upstream && git rebase upstream/main
scripts/seams.sh            # only tagged lines should show
./build.sh --debug && ./gradlew test
# then the manual smoke test from section 12
```

If a rebase conflicts inside a seam, the upstream side is probably right; re-apply your one line
on top of it rather than resolving by hand.

## 7. Sync algorithm

One cycle, in order.

1. Verify the token (`GET /v1/me/storefront`). On 401 or 403, stop and surface "reconnect".
2. Drain the write queue through the page transport (section 9). Local intent goes first so a
   pull can't erase an edit made offline. For each op: apply, then read back to confirm, then mark
   done. A write that didn't land after one retry becomes a visible error, not a silent no. If the
   page isn't ready, leave the queue alone and go straight to the pull.
3. Pull the library delta: page `library/songs`, compare ids against `track`.
4. Pull each linked playlist's tracks.
5. Diff against `snapshot` in both directions.
6. Merge:
   - additions on either side: union
   - removal on one side, untouched on the other: apply it
   - removed on both: apply once
   - removed locally and edited remotely (or the reverse): keep, log a conflict line
7. Write remote-only changes into Room with `origin = REMOTE` so the journal doesn't echo them
   back as new local edits.
8. Rebuild `snapshot` from what actually exists after the writes, then report counts.

Write ordering for a playlist that needs both adds and removals: create if the link has no
remoteId, `POST` additions, read the playlist back to record each entry's library id, then `PUT`
the full ordered list only if order or removals actually changed. Never `PUT` speculatively: it
rewrites the whole playlist and is the cheapest way to lose an entry.

Error taxonomy:

| Signal | Meaning | Action |
|---|---|---|
| 401 / 403 | token expired or no subscription | stop, banner, keep queue |
| 429 | rate limited | back off with `Retry-After`, retry twice |
| 404 on `…/tracks` | empty playlist | treat as empty |
| 5xx | Apple hiccup | retry with backoff |
| 2xx but the read doesn't show it | silent no-op | retry once, then surface an error |
| page shows a sign-in wall | WebView session gone | banner, keep queue, no retry loop |
| JS exception in the page | the transport broke, Apple changed the page | fall back to REST for add and create, mark degraded |

## 8. UI surface

- Settings → Integrations → "Apple Music". Sign in, default sync direction, which playlists are
  linked, last error per playlist, reconnect button.
- Library: Apple tab first. Rows show a small badge for YouTube-only entries.
- Playlist header: a sync chip (linked / not linked / syncing / error) that opens a sheet with
  direction, last sync, and a dry-run preview ("3 to add, 1 to remove, 2 unmatched").
- Match sheet: Apple candidates ranked by ISRC then duration, with manual search, and a "keep as
  YouTube" action. Reachable from an unmatched row and from the playlist preview.
- Library removal warning. The cascade is confirmed, so this ships with removal, not later.

No new top-level tabs, no changes to the player, no changes to modules UI.

## 9. Write transport: drive the web player's page

Every write runs inside a live `music.apple.com` page in the app, through the web player's own
MusicKit client. Reads stay on REST. The split exists because the page is a better writer and a
worse reader.

Why the page writes:

- The page already holds a valid developer token and music-user-token, refreshed by Apple's own
  code. Nothing to store, nothing to expire, no 401 flow to design.
- Requests leave from the origin Apple whitelists, with the header set the real client sends, so
  there is no guessing at what a non-web client is permitted to send.
- `MusicKit.getInstance()` is reachable from the page and exposes resource calls as
  `music.api.*`, plus `addToLibrary(id, 'SONGS')` for the library. A library add is one call.
- Operations the JS doesn't surface (create playlist, the full-list `PUT`, patch, delete) are
  still reachable: capture the request the page sends once, then replay it with `fetch()` inside
  the page, same path, body and headers.

Why the page doesn't read: Android kills background WebViews. Timers throttle, Doze suspends the
process, and a periodic sync that depends on the page quietly stops. Reads are cheap REST calls
against the captured header pair `AppleMusicAccount.kt` already obtains.

One interface, two implementations:

```kotlin
interface AppleTransport {
    suspend fun read(path: String, query: Map<String, String> = emptyMap()): JsonObject
    suspend fun write(op: AppleOp): WriteResult   // evaluated in the page
    val writesAvailable: Boolean                  // false while the page is dead or signed out
}
```

Operation by operation: add, create, library add, the full-list `PUT`, patch and delete all take
the page path. REST remains the fallback for add and create only. If the page is unavailable the
app says so and keeps the queue instead of silently doing half the job.

Lifecycle:

- One WebView per process, created lazily on the first write, attached to the activity at 1dp so
  its timers keep running, then reused.
- App closed means writes wait. That costs nothing real, because edits only happen with the app
  open.
- "Sync now" in the foreground drains immediately.
- A sign-in wall, an Apple error page, or a JS exception sets `writesAvailable = false`, raises the
  reconnect banner and leaves the queue intact. No retry loop against a broken page.

Verification doesn't change. The page returns the same 204s and 202s, and the same silent lies, so
every write is still confirmed by a read no matter which transport performed it.

Four things to settle on the device rather than assume:

- Cookie sharing. Android's `CookieManager` is app-wide, so a page loaded after the existing
  sign-in should already be signed in. Verify, and if not, load the page from the same WebView
  that captured the tokens.
- Site flavour. The mobile site may not expose the same MusicKit surface as the desktop one. Try
  the desktop user agent first, since that is what the working third-party tools drive.
- Timer throttling at 1dp. A hidden but attached WebView should keep running; if it doesn't, keep
  it 1x1 px at the back of the hierarchy instead of detached.
- What the page actually calls. Capture the requests for one add, one remove and one create before
  writing the replay layer, because the paths and bodies are what the implementation depends on.

Costs, plainly: 100-200 MB while alive, worse debugging than REST (JS exceptions and DOM states
instead of status codes), and a dependency on a page Apple can redesign whenever it likes. The
rebase budget absorbs none of this because it is all new code in `applesync`, which is the one
clear upside.

## 10. Phases

P0, spike (2 evenings). Section 4. Output: spike results, go/no-go.

P1, Apple library as a read-through (2 weeks). Library tab defaults to Apple, library songs,
albums, artists mirrored into Room with `am:` ids, playback through the Apple module with
`externalId`. Done when: a fresh install with an Apple account shows the full library, plays any
song exactly, and the YouTube library still works untouched.

P2, library writes (1.5 weeks). The WebView transport plus `addToLibrary` and its delete pair, queue
and verification, cascade warning. Done when: add on Mocha appears on an iPhone within a minute;
remove behaves the same and never removes something it shouldn't.

P3, playlist push (2 weeks). Link or create, per-playlist direction, additions by `POST`,
removals and order by `PUT`, snapshot maintenance, and the request-replay layer in the page for
the operations MusicKit won't call directly. Done when: edits in Mocha land in Apple on a linked
playlist, including removals, and a dry run matches what actually happens.

P4, pull and merge (1.5 weeks). Remote additions and removals into Room, echo suppression,
conflict rules, undo. Done when: an edit made in the Apple app shows up in Mocha and nothing
bounces back.

P5, unmatched flow (1 week). Candidate picker, manual search, YouTube fallback, mixed playlists.
Done when: a YouTube-only track can be added to a playlist, shows its badge, and the Apple copy
stays correct.

P6, hardening (1 week). 429 pacing, token expiry, dry-run preview everywhere, tests around the
silent-204 paths, README for the fork.

Roughly eight to nine weeks of evenings, the extra half-week being the page host. P1 is the
longest single chunk and the least glamorous: it's data plumbing for albums and artists, not sync.

## 11. Test plan

- Unit: merge table (every combination of local and remote add/remove/keep), op-queue retry and
  ordering, matcher thresholds, id mapping.
- Fakes: a `MockWebServer` rig replaying recorded Apple responses, including 202, 204-with-no-
  effect, 404-empty-playlist and 429. The engine has to pass against those before touching a
  real account.
- Device: the section 4 script, plus a 12-step manual pass over add/remove on both sides.
- Regression: upstream's own tests still green (`CsvImportTest`, `TrackMatcherTest`,
  `PlaylistSourceRecordTest`, `ChromeUserAgentTest`), and the YouTube sync path still works
  with an Apple account configured.

## 12. Risks and fallbacks

- Library delete disappears: sync degrades to archive-only, with a banner. Keep the capability
  flag so this is a config change, not a rewrite.
- `PUT …/tracks` disappears: membership stays append-only, order unsynced. Say so in the UI.
- Silent 204s: the reason every write is verified. Never trust a code.
- Cascade deletes, confirmed: removing a song from the library also drops it from playlists, so the
  UI must warn, and the merge must not read that playlist change as an independent remote edit.
- Token expiry every six months: banner, not a crash.
- ToS and account risk: this is an automated client for your own account. Keep it personal, don't
  distribute builds.
- GPL-3.0: publish the fork's source if you hand out APKs.
- Upstream churn: eight seam files, all tagged. If the module API grows a sync hook upstream,
  delete seams and adopt it.
- The page transport breaks, because Apple redesigned the player: writes fall back to REST for add
  and create, delete and reorder go unavailable with a banner. Capability flags, not a rewrite.
- WebView memory: one page per process, the first thing the system kills under pressure. The queue
  is durable, so a kill costs a retry, not data.

## 13. Appendix: quirks checklist

- Send at most 100 ids per write call.
- `PUT …/tracks` takes `{"data":[{"id":"i.…","type":"library-songs"}]}`. Bare arrays are a 400, and an
  empty or `data`-less body silently empties the playlist.
- Storefront on this account is `in`; catalog searches without a storefront segment will be wrong.
- `PUT` takes library ids only, and returns 204 even when it changes nothing.
- Catalog adds to a playlist also add to the library.
- Empty playlist tracks answer 404, not an empty array.
- Library adds are 202 and land asynchronously, sometimes with a library-album row that never
  appears.
- "Favorite Songs" and "Favourite Songs" playlists are auto-managed. Never write to them.
- Artwork URLs are templates: `{w}x{h}` plus occasionally `{c}` and `{f}`.
- Apple library songs that are user uploads or matched files may have no catalog id. They can be
  shown and played only if a stream resolves; treat them as unmatched, never push them.
- Developer tokens are ES256 JWTs capped at six months; the web capture already gives you one
  with the tenant claim, which is fine for the web playback endpoint.
- `MusicKit.getInstance()` in the page exposes `api.*` and `addToLibrary(id, 'SONGS')`, and the web
  player's instance is already authorised because the user signed in there.
- Where the JS has no method for an operation, capture the request the page sends and replay it
  with `fetch()` inside the page; that inherits the origin and the header set Apple expects.

## 14. First commits in the fork

1. `git remote add upstream https://github.com/realidkroo/Mochy-Music.git`; branch `apple-sync`.
2. Add the plan and `scripts/seams.sh`.
3. Add the `applesync` package with the sidecar DB, the API client (reads only), and the token
   store. No seams yet: the app compiles with zero upstream changes.
4. Add the page host: WebView wrapper, JS bridge, and one smoke test that calls `addToLibrary`.
   Still no seams.
5. Add the sync settings screen and route it (first seam).
6. Then P1's library mirror.
