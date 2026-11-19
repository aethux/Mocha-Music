#!/usr/bin/env bash
# Install the local git hooks this fork relies on. Safe to re-run.
set -euo pipefail

root=$(git rev-parse --show-toplevel)
hooks="$root/.git/hooks"

ln -sfn ../../scripts/commit-msg "$hooks/commit-msg"
chmod +x "$root/scripts/commit-msg" "$root/scripts/seams.sh"

echo "installed: commit-msg -> scripts/commit-msg"
echo "before a release build: ./scripts/seams.sh"
