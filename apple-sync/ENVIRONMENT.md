# Build environment

Set up on 2026-11-18 on the EndeavourOS laptop. Nothing here is committed to upstream files;
`local.properties` is gitignored and the toolchain lives outside the repo.

## Installed

| Piece | Location | Notes |
|---|---|---|
| JDK 21 (Temurin 21.0.13+9) | `~/.local/jdk/jdk-21.0.13+9` | The project needs the Java 21 toolchain. Arch's `jdk21-openjdk` package 404'd on every mirror, so this is a direct Adoptium tarball. System default (`archlinux-java`) is untouched and still points at 27. |
| Android SDK | `~/Android/Sdk` | `cmdline-tools/latest` 22.0, `platform-tools` 37.0.1, `platforms;android-37.0`, `build-tools;37.0.0`, `ndk;27.0.12077973`, `cmake;3.22.1` |
| `local.properties` | repo root | `sdk.dir=/home/aeth/Android/Sdk`, gitignored |

The project as pulled today: AGP 9.0.0, Gradle 9.3.1, Kotlin 2.3.10, `compileSdk`/`targetSdk` 37,
`minSdk` 24, JDK toolchain 21, NDK 27.0.12077973, CMake 3.22.1. The README still says SDK 36 and
NDK 27; the build files are the truth.

## Build

Always pass the JDK explicitly, since the system default is 27:

```bash
cd ~/WebDev/Mochy-Music
export JAVA_HOME=$HOME/.local/jdk/jdk-21.0.13+9
export PATH=$JAVA_HOME/bin:$PATH
./gradlew :app:assembleUniversalFossDebug --no-configuration-cache --console=plain
```

CI builds `assembleUniversalGmsDebug` with `--no-configuration-cache`, so that flag stays in local
commands too. First build downloads the Gradle distribution plus the whole dependency graph and
takes a while; later builds are minutes.

APK lands in `app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk` (the flavor
name is part of the path, so `foss/debug` is wrong).

`./build.sh` also works for plain debug builds, but its custom/nightly path uses BSD
`sed -i ''` syntax and will fail on Linux. Call Gradle directly for anything the plan needs.

## Two things a fresh machine gets wrong

**SDK 37 platform naming.** Google publishes API 37 as `platforms/android-37.0`, but AGP resolves
`compileSdk = 37` to the target hash `android-37` and fails with
`Failed to find target with hash string 'android-37'`. Fix, outside the repo:

```bash
ln -sfn android-37.0 ~/Android/Sdk/platforms/android-37
```

Side effect: `avdmanager` then sees the SDK as having a duplicate package "in inconsistent location"
and refuses to scan system images, so create AVDs with the symlink temporarily removed, or use an
already-created AVD.

**Debug keystore.** AGP 9 does not generate one. A clean machine fails at
`:app:validateSigningUniversalFossDebug` with `Keystore file '~/.android/debug.keystore' not found`.
Create the standard one:

```bash
keytool -genkeypair -keystore ~/.android/debug.keystore -storepass android \
  -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

## Emulator

`/dev/kvm` is present and usable, so the x86_64 image gets hardware acceleration for the CPU. The AVD
is `mochy` (Pixel 7, API 36.1 Google APIs, 4 GB RAM, 8 cores), created from
`system-images;android-36.1;google_apis;x86_64`.

Headless plus host GPU is the fast combination, and the earlier hang was not the GPU's fault alone:

| configuration | qemu CPU |
|---|---|
| windowed, host GPU, 4 GB AVD, four gradle daemons resident | KWin main thread hung, desktop restarted |
| headless, `-gpu swiftshader_indirect`, 3 GB, 4 cores | ~485% (unusable, and it looks like a RAM problem but isn't) |
| headless, `-gpu host`, 4 GB, 8 cores, no daemons running | ~128-150% (usable) |

Memory was never the constraint: qemu sat at 4 GB RSS with 7 GB free while burning five cores in
software rendering. What actually took the machine down was that pair of things at once, on top of
gradle and the Kotlin daemon. So: `GPU=host ./apple-sync/dev/run-emulator.sh`, and stop the daemons
(`./gradlew --stop`) before booting. Fall back to `-gpu swiftshader_indirect` only if the host GPU
path glitches, and expect it to feel like a slow phone.

Measured 2026-11-18 with the emulator already booted and one build running: `assembleUniversalFossDebug`
took 1m13s with 4 GB still available and no swap use, and the new APK installed while the app was
running. Both at once is fine at this size. What is not fine is several daemons, a windowed emulator
and a software renderer together, which is what took the machine down the first time. One build at a
time, AVD at 3 GB, and stop the daemons when the session ends.

```bash
./apple-sync/dev/run-emulator.sh             # boot, install, launch
./apple-sync/dev/run-emulator.sh --screen    # same, plus a scrcpy window
./apple-sync/dev/run-emulator.sh --install   # into an already-booted device
```

The emulator itself runs headless (`-no-window`): its bundled Qt 6 cannot initialise the xcb plugin
on this Wayland desktop ("no Qt platform plugin could be initialized"), and `xcb-util-cursor` is
already installed, so the fix is to not use its UI at all. scrcpy supplies the window, which is where
the Apple sign-in happens, because it runs in the app's WebView. Type the Apple ID there, not in bash.

Verified 2026-11-18: the debug APK installs on the API 36.1 image, completes first-run onboarding and
loads the home feed with the `in` storefront, so networking and rendering work. Playback through the
Apple module still needs Widevine in the image, which the Google APIs images ship.

## Device

`adb` is available (system android-tools 37.0.0 and the SDK's own platform-tools). No phone is
connected right now; plug the Nothing Phone in with USB debugging on, then:

```bash
adb devices -l
adb install -r app/build/outputs/apk/foss/debug/*.apk
```

## Memory

15 GB RAM, `org.gradle.jvmargs=-Xmx6144M` plus a 6 GB Kotlin daemon. If a build dies with
`OutOfMemoryError` or the OOM killer takes the daemon, override for that invocation:

```bash
./gradlew ... -Dorg.gradle.jvmargs=-Xmx4g
```

No edits to `gradle.properties`, which is an upstream file.
