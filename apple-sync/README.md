# apple-sync

Everything this fork adds lives here until it moves into the app packages. Nothing in this folder
is part of upstream, so it never conflicts on a rebase.

| File | What it is |
|---|---|
| `PLAN.md` | The build plan: goal, Apple capability table, spike, architecture, rebase rules, algorithm, phases, tests, risks. |
| `architecture/` | How the app works today: data model, playback, library/sync, modules, UI, integrations, and a feature inventory with file pointers. Start at `architecture/README.md`. |
| `ENVIRONMENT.md` | Toolchain paths, the exact Gradle invocation, device install. |
| `spike/spike.py` | The P0 gate. Runs the real write operations against your own account and reports which work. |
| `spike-results.json` | Written by the spike. Commit it (it holds no credentials); the merge rules branch on the cascade answer. |
| `../scripts/seams.sh` | Seam check: every change this branch makes outside its own packages, with line and tag counts. |

## Run the spike first

It decides whether the fork is worth starting. It creates a playlist named `mochy spike <time>`,
adds a song to your library, removes it again, tests the undocumented remove/reorder call, and
checks whether a library delete cascades into playlists.

```bash
export AM_DEV_TOKEN='eyJ...'     # Authorization header, without "Bearer "
export AM_USER_TOKEN='...'       # Media-User-Token header
python3 apple-sync/spike/spike.py
```

Get both from DevTools on music.apple.com (Network tab, any request to `amp-api.music.apple.com`).
They are session credentials: never paste them into a chat, issue or commit.

## Seam discipline

Every upstream edit is tagged `// MOCHY-APPLE: <why>` and stays small. Before any release build:

```bash
./scripts/seams.sh            # default base: upstream/main
```

It exits non-zero if a seam is untagged or over 20 added lines.

## Remotes

| remote | repository | role |
| --- | --- | --- |
| `origin` | `aethux/Mocha-Music` | the fork. This is where work is pushed. |
| `upstream` | `realidkroo/Mochy-Music` | where updates come from. Never pushed to. |

`main` tracks `upstream/main`, `apple-sync` tracks `origin/apple-sync`. Pushing is never ambiguous:
plain `git push` from `apple-sync` goes to the fork, and `git pull` on `main` takes the newest
upstream without touching the fork's copy.

## Rebase ritual

```bash
git fetch upstream && git rebase upstream/main
./scripts/seams.sh
export JAVA_HOME=$HOME/.local/jdk/jdk-21.0.13+9
./gradlew :app:assembleUniversalFossDebug --no-configuration-cache
git push --force-with-lease origin apple-sync
```
