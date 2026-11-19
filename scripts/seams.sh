#!/usr/bin/env bash
# Seam check for the apple-sync branch.
#
# Everything this fork adds should live in packages containing "applesync" plus
# apple-sync/. Anything else it touches is a seam and must stay tiny and
# tagged with "// MOCHY-APPLE:".
#
# Usage: scripts/seams.sh [upstream-ref]     default: upstream/main
set -euo pipefail

base="${1:-upstream/main}"
if ! git rev-parse --verify --quiet "$base" >/dev/null; then
  echo "unknown ref: $base (try: git fetch upstream)" >&2
  exit 2
fi

# A seam is a change to a file upstream owns. New files this fork adds are not seams even
# when they sit in an upstream directory (scripts/, docs/), so only M and D are considered.
seams=$(git diff --name-only --diff-filter=MD "$base"...HEAD -- . ':(exclude)*applesync*' ':(exclude)apple-sync/*')
fork_files=$(git diff --name-only --diff-filter=A "$base"...HEAD -- . ':(exclude)*applesync*' ':(exclude)apple-sync/*')

if [ -n "$fork_files" ]; then
  echo "added by this fork (not seams):"
  printf '  %s\n' $fork_files
  echo
fi

if [ -z "$seams" ]; then
  echo "clean: no upstream files modified"
  exit 0
fi

status=0
printf '%-70s %6s %6s %5s\n' FILE ADDED REMOVED TAGS
while IFS= read -r f; do
  [ -f "$f" ] || { printf '%-70s %6s %6s %5s\n' "$f" "(deleted)" "-" "-"; status=1; continue; }
  added=$(git diff "$base"...HEAD -- "$f" | grep -c '^+[^+]' || true)
  removed=$(git diff "$base"...HEAD -- "$f" | grep -c '^-[^-]' || true)
  tags=$(grep -c 'MOCHY-APPLE:' "$f" || true)
  printf '%-70s %6s %6s %5s\n' "$f" "$added" "$removed" "$tags"
  if [ "$added" -gt 20 ]; then
    echo "  ^ over budget (20 added lines). Move the code into an applesync package." >&2
    status=1
  fi
  if [ "$tags" -eq 0 ]; then
    echo "  ^ no // MOCHY-APPLE: tag. Tag the seam so a rebase review can find it." >&2
    status=1
  fi
done <<< "$seams"

exit $status
