# Mocha Music

A fork of [Mochy Music](https://github.com/realidkroo/Mochy-Music) where **Apple Music is the library
of record**, and YouTube Music only supplies what Apple does not have. Android, Kotlin, Compose,
Media3, GPL-3.0.

The app is called **Mocha** (`com.mocha.mochymusic`). Upstream's spelling survives only where it is a
wire format: the `mochyymodule` zip marker, the `X-Mochy-Auth` header, the `mochy.db` file, and the
`// MOCHY-APPLE:` tags this fork uses to mark its seams in upstream files.

> [!NOTE]
> This is one person's fork, not a product. It is built and tested on a Nothing Phone (2a) Plus.
> Report bugs that are not about Apple Music or the fork's UI to
> [upstream](https://github.com/realidkroo/Mochy-Music/issues).

---

## What the fork adds

- **The Apple Music library is imported and kept in step.** Songs arrive as `AM_<catalogueId>`, albums
  and playlists come with them, and a refresh records what changed on Apple's side instead of
  deleting quietly.
- **Apple wins when a song exists on both sides.** The duplicate is merged, and YouTube keeps only
  what Apple has nothing for — after the person says so.
- **Writes go both ways** through the app's own music.apple.com session: library add/remove, likes
  and playlist edits. Every write leaves an audit row, and `AppleGuard` refuses anything it does not
  own.
- **A playlist made here pushes to Apple within the second** once it is linked; a playlist Apple
  keeps to itself (a mix, or one the account only follows) is marked and refused, never replaced.
- **Ordering and sorting on All songs**, animated Apple covers in the player, a cover picker, and the
  fork's own song menu with the four tiles.
- **Updates come from this repo** (`aethux/Mocha-Music`), not upstream.

The engine, its tables and the traps already hit are written down in
[`README-APPLE-SYNC.md`](README-APPLE-SYNC.md) and [`apple-sync/STATE.md`](apple-sync/STATE.md);
[`apple-sync/CHANGELOG.md`](apple-sync/CHANGELOG.md) explains how each change was made.

---

## Install

1. Open the [releases page](https://github.com/aethux/Mocha-Music/releases) and download the APK for
   your phone: **arm64-v8a** is almost every phone from the last eight years, **universal** runs
   anywhere and is bigger.
2. Install it. Android asks to allow installs from the browser or file manager you used.
3. Open **Mocha**. No account is needed to start listening.

Updating works the same way: install the new APK over the old one and the library stays. The app
notifies when a newer build is published here.

> [!IMPORTANT]
> **Mocha is a separate app from Mochy.** The package id is `com.mocha.mochymusic`, so installing it
> does not replace Mochy Music and does not carry its data across. The library, the playlists and the
> Apple Music session start empty, and the old app stays installed until you remove it.

> [!IMPORTANT]
> From **8.4.6** the APKs published here are signed with this fork's own release key
> (`CN=Mocha Music, O=Aeth, C=IN`, SHA-256 `69:47:CF:5B:44:88:1C:DA:5A:06:56:45:90:C5:1E:61:12:7C:8D:B5:92:36:3D:4A:A2:64:9A:1D:B7:17:D7:8E`).
> Releases up to and including **8.4.5** were signed with a **local debug key**, so Android will refuse
> to update one over the other: uninstall 8.4.5 once, then install 8.4.6. From then on updates work
> normally, because the key does not change again.

Android 8.0+ (API 24). Android 7 works unofficially.

---

## Build it

You need JDK 21 and an Android SDK with the platform and NDK the build asks for.

```bash
export JAVA_HOME=$HOME/.local/jdk/jdk-21.0.13+9
export PATH=$JAVA_HOME/bin:$PATH

./gradlew :app:assembleUniversalFossDebug      # debug build, installs next to the release one
./gradlew :app:assembleUniversalFossRelease    # release APK, universal
./gradlew :app:assembleArm64FossRelease        # release APK, arm64 only
```

Outputs land in `app/build/outputs/apk/<abi><flavour>/<variant>/`. `foss` has no Google Play
services; `gms` is the flavour with Cast.

`build.sh` wraps the same builds, bumps the build number, names the APKs and can publish them:

```bash
./build.sh --debug -i                              # debug, installs and opens on the phone
./build.sh --release -cc                           # release for arm64 and universal
./build.sh --release -a -commit "what changed"     # all ABIs, published as a release here
./build.sh --release -a -n 3 -commit "what changed" # nightly <version>.3, a GitHub prerelease
./build.sh -k                                      # stop stuck Gradle daemons
```

Install one by hand:

```bash
adb install -r app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk
adb shell am start -n com.mocha.mochymusic.debug/com.mocha.mochymusic.MainActivity
```

### Signing a release

`app/build.gradle.kts` uses `keystore/release.keystore` when it exists:

```bash
export STORE_PASSWORD=…
export KEY_ALIAS=…
export KEY_PASSWORD=…
```

Without it, `release` builds fall back to the debug keystore so that a release-quality build always
installs locally. CI signs with the real key when the secrets are present. Nothing in this repo
commits a keystore.

### Optional keys

The build works without these; only the matching feature switches off. Put them in
`local.properties` (never committed):

```properties
LASTFM_API_KEY=...
LASTFM_SECRET=...
DISCORD_CLIENT_ID=...
```

### If the build fails

- `Unresolved reference` or impossible Hilt errors: delete `build/` folders and `.gradle/`, build
  again. A package rename leaves generated Hilt classes behind and they must be cleared.
- Stuck Gradle: `./build.sh -k`.
- Wrong Java: Gradle must run on JDK 21.

---

## Checks before a commit

```bash
./scripts/seams.sh          # must exit 0
```

The fork keeps its work out of upstream's files: new code lives in `applesync/` packages, and a
change to an upstream file is a *seam* — under ~20 added lines and tagged `// MOCHY-APPLE:`. The
check normalises the fork's package and name on both sides, so the rename itself costs nothing and a
real change still counts.

Apple-side unit tests:

```bash
./gradlew :app:testUniversalFossDebugUnitTest --tests "com.mocha.mochymusic.applesync.*"
```

---

## Known issues

- **`CrossmixTest > everyday picks only, and blends stay short` fails**, and it fails on upstream
  `main` too — the planner and the test are identical to upstream. A 16-beat Filter sweep (9.6 s) is
  admitted when a tempo estimate exists even though the two tempos cannot lock, while the test
  allows 8.5 s for a blend that is not beat-matched.
- **Upstream's cover zoom is not adopted**: the fork's `AppleCover` has no `zoom` parameter, so the
  player cover does not scale out the way upstream's does when it opens.
- **The Discord row opens upstream's server**, because the fork has none.
- **`core/updater/mochyupdater.kt` is dead upstream code** and still contains `realidkroo` URLs. The
  live updater is `core/updater/GithubUpdates.kt`.

---

## FAQ

**Is it safe to sign in?**

You never have to sign in to listen: search, playback, lyrics and downloads work without an account.
Signing in is only for syncing a YouTube Music library, importing playlists from Spotify / Apple
Music / Tidal, Discord presence — and, in this fork, Apple Music sync.

The app opens the service's own login page (accounts.google.com, music.apple.com, open.spotify.com)
inside a WebView. Your password goes into *their* page, never into a form in the app. What is kept is
the session token or cookie the service hands out, stored on the phone in the app's private
storage. There is no account system of the app's own and no server it sends logins to. The code is
GPL-3.0, so you can read exactly what happens to them.

**Why is it called Mocha?**

Because the fork is a rebrand of Mochy Music and the two have to be separable: different package,
different app, different update channel.

**Where did the "support the developer" popups go?**

Removed in this fork. No donate prompt opens on its own, and neither the About sheet nor the update
sheet carries a support row.

---

## Disclaimer

Mocha is an independent, unofficial project made for personal and educational use, provided as is,
without warranty of any kind.

- It is not affiliated with, endorsed by or sponsored by YouTube, YouTube Music, Google, Spotify,
  Apple, Last.fm, Shazam, Discord or any other service it can connect to. All names, logos and brands
  belong to their respective owners.
- It does not host, store or distribute any music. Everything is fetched from third-party services
  when you ask for it, and those services can change or stop working at any time.
- You are responsible for how you use the app, including the terms of service of the accounts you
  connect and the copyright law where you live.
- Imports from other services use your own account. Never share your login data or tokens.
  The app never sees your password.

---

## Forking

Forks are welcome. Keep the credit, and keep it free — upstream asks the same, and this fork keeps
both asks.

---

## Credits

Almost all of this app is other people's work.

- [Mochy Music](https://github.com/realidkroo/Mochy-Music) — Roo and key; the app this is forked from.
- [ViviMusic](https://github.com/vivizzz007/vivi-music) by vivizzz007 — the base app Mochy grew from.
- [Metrolist](https://github.com/mostafaalagamy/Metrolist) by Mostafa Alagamy — the base framework.
- [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by maxrave-dev — lyrics API provider.
- [ArchiveTune](https://github.com/rukamori/ArchiveTune) by Rukamori — design and architecture
  inspiration.
- [RiMusic](https://github.com/fast4x/RiMusic) by fast4x — YouTube Music InnerTube wrapper.
- [LRCLib](https://github.com/tranxuanthang/lrclib) by tranxuanthang — synchronized lyrics.
- [Monochrome](https://github.com/monochrome-music/monochrome) — FLAC streams.
- [InnertubeX](https://github.com/metrolistGroup/innertubex) by nyxireal — YouTube API access.
- [AutoEq](https://autoeq.app) — the equalizer database. [lrc.red](https://lrc.red) — word-timed
  lyrics.
- LyricsPlus, Better Lyrics API and SimpMusic Lyrics — the free lyric backends.

The fork's own work — the Apple Music engine and the UI around it — is by Aeth
([@aethux](https://github.com/aethux)).

GPL-3.0. See [LICENSE](LICENSE).
