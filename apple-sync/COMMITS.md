# Commit conventions

This fork exists to stay rebaseable. Commit history is part of that: someone reading
`git log upstream/main..apple-sync` should see exactly what the fork adds, in an order that can be
re-applied by hand if a rebase goes sideways.

## Subject

```
apple-sync: <imperative, lowercase, no trailing period>
```

Optional scope for larger slices: `apple-sync(sync-engine): drain the queue in one pass`.
Hard cap 72 characters, target 60. The hook in `scripts/commit-msg` rejects anything else.

## Rules

- One logical change per commit. A seam and the code it enables belong together; two unrelated
  screens do not.
- Body after a blank line, wrapped at 72, explaining why rather than what. The diff already says
  what.
- A commit that edits an upstream file names it in the body with the line count, for example
  `seam: ui/menu/PlaylistMenu.kt (+3)`. That makes `git log -S'MOCHY-APPLE'` unnecessary.
- Never commit `local.properties`, tokens, `spike-results.json` with credentials in it, build
  output, or `.idea/`.
- Rebase, never merge upstream in. `git fetch upstream && git rebase upstream/main`.
- Do not rewrite history that has left the machine. Local amends and rebases are expected: this is
  a private fork tracking a moving upstream.

## Before a release build

```bash
./scripts/seams.sh          # exits non-zero on an untagged or oversized seam
git log --oneline upstream/main..apple-sync
```

## Install the message hook

```bash
./scripts/install-hooks.sh
```
