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
# when they sit in an upstream directory (scripts/, docs/).
git diff --name-status -M "$base"...HEAD -- . ':(exclude)*applesync*' ':(exclude)apple-sync/*' \
    ':(exclude)README.md' ':(exclude)LICENSE' ':(exclude).github/*' > "$tmp/status"

# A package move, read as a rename: git pairs most of them itself, but a file this fork
# rewrote heavily falls under its similarity cut-off and arrives as a delete and an add.
declare -A added=() orphans=() pairs=()
declare -a changed=()

while IFS=$'\t' read -r kind old new; do
  case "$kind" in
    A) added["$old"]=1 ;;
    D) orphans["$old"]=1 ;;
    R*) changed+=("$old"$'\t'"$new") ;;
    *)  changed+=("$old"$'\t'"$old") ;;
  esac
done < "$tmp/status"

for old in "${!orphans[@]}"; do
  moved="${old/\/com\/mochyy\/mochymusic\//\/com\/mocha\/mochymusic\/}"
  if [ -n "${added[$moved]:-}" ]; then
    unset 'added[$moved]'
    changed+=("$old"$'\t'"$moved")
  else
    pairs+=("$old")
  fi
done

if [ "${#added[@]}" -gt 0 ]; then
  echo "added by this fork (not seams):"
  printf '  %s\n' "${!added[@]}" | sort
  echo
fi

status=0
table="$tmp/table"
: > "$table"

for old in "${pairs[@]:-}"; do
  [ -n "$old" ] || continue
  printf '%-70s %6s %6s %5s\n' "$old" "(deleted)" "-" "-" >> "$table"
  status=1
done

for entry in "${changed[@]:-}"; do
  [ -n "$entry" ] || continue
  from="${entry%%$'\t'*}"
  to="${entry##*$'\t'}"

  git show "$base:$from" | norm > "$tmp/a"
  norm < "$to" > "$tmp/b"
  added_lines=$(diff "$tmp/a" "$tmp/b" | grep -c '^>' || true)
  removed_lines=$(diff "$tmp/a" "$tmp/b" | grep -c '^<' || true)
  [ "$added_lines" -eq 0 ] && [ "$removed_lines" -eq 0 ] && continue

  tags=$(grep -c 'MOCHY-APPLE:' "$to" || true)
  printf '%-70s %6s %6s %5s\n' "$to" "$added_lines" "$removed_lines" "$tags" >> "$table"

  if [ "$added_lines" -gt 20 ]; then
    printf '  ^ over budget (20 added lines): %s\n' "$to" >&2
    status=1
  fi
  if [ "$added_lines" -gt 0 ] && [ "$tags" -eq 0 ]; then
    printf '  ^ no // MOCHY-APPLE: tag: %s\n' "$to" >&2
    status=1
  fi
done

if [ -s "$table" ]; then
  printf '%-70s %6s %6s %5s\n' FILE ADDED REMOVED TAGS
  cat "$table"
else
  echo "clean: no upstream files modified"
fi

exit $status
