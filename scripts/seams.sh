#!/usr/bin/env bash
# Seam check for the apple-sync branch.
#
# Everything this fork adds should live in packages containing "applesync" plus
# apple-sync/. Anything else it touches is a seam and must stay tiny and
# tagged with "// MOCHY-APPLE:".
#
# The fork also renamed the app: the package is com.mocha.mochymusic and the brand
# reads Mocha. Every upstream file carries one of those words, so both sides are
# normalised back to upstream's spelling before they are compared - a file that
# only wears the rename is not a seam, while a real change still shows up.
#
# Usage: scripts/seams.sh [upstream-ref]     default: upstream/main
set -euo pipefail

base="${1:-upstream/main}"
if ! git rev-parse --verify --quiet "$base" >/dev/null; then
  echo "unknown ref: $base (try: git fetch upstream)" >&2
  exit 2
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# our spelling -> upstream's, so a rename never reads as an edit
norm() {
  sed -e 's/com\.mocha\.mochymusic/com.mochyy.mochymusic/g' \
      -e 's/Java_com_mocha_mochymusic/Java_com_mochyy_mochymusic/g' \
      -e 's/Mocha/Mochy/g' \
      -e 's/mocha/mochy/g'
}

# A seam is a change to a file upstream owns. New files this fork adds are not seams even
# when they sit in an upstream directory (scripts/, docs/), so only M, D and R count; a rename
# is the package move and is read at its new path.
git diff --name-status -M "$base"...HEAD -- . ':(exclude)*applesync*' ':(exclude)apple-sync/*' > "$tmp/status"

fork_files=$(awk -F'\t' '$1=="A"{print $2}' "$tmp/status")
if [ -n "$fork_files" ]; then
  echo "added by this fork (not seams):"
  printf '  %s\n' $fork_files
  echo
fi

status=0
table="$tmp/table"
: > "$table"

while IFS=$'\t' read -r kind old new; do
  [ -n "${kind:-}" ] || continue
  case "$kind" in
    A) continue ;;
    D)
      printf '%-70s %6s %6s %5s\n' "$old" "(deleted)" "-" "-" >> "$table"
      status=1
      continue
      ;;
    R*)
      from="$old"; to="$new"
      ;;
    *)
      from="$old"; to="$old"
      ;;
  esac

  git show "$base:$from" | norm > "$tmp/a"
  norm < "$to" > "$tmp/b"
  added=$(diff "$tmp/a" "$tmp/b" | grep -c '^>' || true)
  removed=$(diff "$tmp/a" "$tmp/b" | grep -c '^<' || true)
  [ "$added" -eq 0 ] && [ "$removed" -eq 0 ] && continue

  tags=$(grep -c 'MOCHY-APPLE:' "$to" || true)
  printf '%-70s %6s %6s %5s\n' "$to" "$added" "$removed" "$tags" >> "$table"

  if [ "$added" -gt 20 ]; then
    printf '  ^ over budget (20 added lines): %s\n' "$to" >&2
    status=1
  fi
  if [ "$tags" -eq 0 ]; then
    printf '  ^ no // MOCHY-APPLE: tag: %s\n' "$to" >&2
    status=1
  fi
done < "$tmp/status"

if [ -s "$table" ]; then
  printf '%-70s %6s %6s %5s\n' FILE ADDED REMOVED TAGS
  cat "$table"
else
  echo "clean: no upstream files modified"
fi

exit $status
