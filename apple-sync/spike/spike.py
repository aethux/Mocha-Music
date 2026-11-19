#!/usr/bin/env python3
"""Mochy Apple sync: the P0 spike.

Runs the six checks from apple-sync/PLAN.md section 4 against your own Apple Music
account, using the same endpoints the web player uses. Nothing here touches the
Mochy codebase; it exists to answer one question before the fork grows any Kotlin:
which write operations actually work on your account, and does a library delete
cascade into playlists.

Tokens are read from the environment and never printed:

    export AM_DEV_TOKEN='eyJ...'      # Authorization: Bearer <this>   (a JWT, starts with eyJ)
    export AM_USER_TOKEN='...'        # Media-User-Token
    python3 apple-sync/spike/spike.py

Where to get them: open https://music.apple.com in a desktop browser, sign in, open
DevTools -> Network, click any request to amp-api.music.apple.com, and copy the
`authorization` header (drop the leading "Bearer ") and the `media-user-token` header.
They are session credentials. Do not paste them into a chat, an issue, or a commit.

Writes are real. Use a throwaway playlist; the script names it "mochy spike <time>".
It adds one song to your library and then removes it again. If a check fails midway,
inspect your library for the spike song before re-running.

Exit codes: 0 all checks passed, 1 a check failed, 2 bad usage/tokens.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

API = "https://amp-api.music.apple.com"
UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
      "Chrome/131.0.0.0 Safari/537.36")

DEV = os.environ.get("AM_DEV_TOKEN", "").strip()
USER = os.environ.get("AM_USER_TOKEN", "").strip()

results: list[dict] = []


def headers() -> dict[str, str]:
    return {
        "Authorization": f"Bearer {DEV}",
        "Media-User-Token": USER,
        "Origin": "https://music.apple.com",
        "Referer": "https://music.apple.com/",
        "Accept": "application/json",
        "User-Agent": UA,
    }


def call(method: str, path: str, body=None, expect=(200, 201, 202, 204), retries=2) -> tuple[int, dict, str]:
    """One request. 429 is retried using Retry-After; anything else is returned as-is."""
    url = path if path.startswith("http") else API + path
    for attempt in range(retries + 1):
        data = None
        hdrs = headers()
        if body is not None:
            data = json.dumps(body).encode()
            hdrs["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
        try:
            with urllib.request.urlopen(req, timeout=45) as r:
                raw = r.read().decode("utf-8", "replace")
                code = r.status
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", "replace")
            code = e.code
        except Exception as e:  # network
            if attempt < retries:
                time.sleep(2)
                continue
            return 0, {}, str(e)
        if code == 429 and attempt < retries:
            wait = 5
            time.sleep(wait)
            continue
        try:
            parsed = json.loads(raw) if raw.strip() else {}
        except json.JSONDecodeError:
            parsed = {"_raw": raw[:400]}
        return code, parsed, raw
    return 0, {}, "retries exhausted"


def record(name: str, ok: bool, detail: str, extra: dict | None = None) -> None:
    results.append({"check": name, "ok": ok, "detail": detail, **(extra or {})})
    print(("PASS  " if ok else "FAIL  ") + name + "  " + detail)


def tracks_of(playlist_id: str) -> list[dict]:
    """Every entry of a library playlist, following next links. 404 means empty."""
    out: list[dict] = []
    path = f"/v1/me/library/playlists/{playlist_id}/tracks?limit=100"
    for _ in range(30):
        code, body, _ = call("GET", path, expect=(200, 404))
        if code == 404:
            return out
        if code != 200:
            return out
        out.extend(body.get("data", []))
        nxt = body.get("next")
        if not nxt:
            break
        path = nxt if nxt.startswith("http") else API + nxt
    return out


def library_songs(pages: int = 6) -> list[dict]:
    out: list[dict] = []
    path = "/v1/me/library/songs?limit=100"
    for _ in range(pages):
        code, body, _ = call("GET", path)
        if code != 200:
            break
        out.extend(body.get("data", []))
        nxt = body.get("next")
        if not nxt:
            break
        path = API + nxt
    return out


def catalog_id_of(item: dict) -> str | None:
    attrs = item.get("attributes", {})
    pp = attrs.get("playParams", {}) or {}
    return pp.get("catalogId") or attrs.get("catalogId")


def cleanup_from_report(path: str, extra: list[str] | None = None) -> int:
    """Remove only the library rows a recorded run created. Never touches anything else."""
    with open(path) as f:
        report = json.load(f)
    ids = list(extra or [])
    for c in report.get("checks", []):
        ids.extend(c.get("created_library_ids") or [])
    ids = sorted(set(ids))
    if not ids:
        print("nothing recorded as created by that run")
        return 0
    print(f"removing {len(ids)} library row(s) created by {report.get('run_at')}")
    ok = 0
    for lid in ids:
        code, _, _ = call("DELETE", f"/v1/me/library/songs/{lid}")
        print(f"  {lid}: HTTP {code}")
        ok += 1 if code in (200, 204, 404) else 0
    print(f"{ok}/{len(ids)} cleared")
    return 0 if ok == len(ids) else 1


def main() -> int:
    ap = argparse.ArgumentParser(description="Apple Music write spike for the Mochy fork")
    ap.add_argument("--track", default="bohemian rhapsody queen",
                    help="search term for the two test songs")
    ap.add_argument("--report", default="apple-sync/spike-results.json")
    ap.add_argument("--keep", action="store_true", help="leave the spike playlist in place")
    ap.add_argument("--cleanup", metavar="REPORT",
                    help="delete the library rows a previous run created, per its report file")
    ap.add_argument("--ids", metavar="i.aaa,i.bbb",
                    help="with --cleanup: explicit library ids to remove (for reports from older runs)")
    args = ap.parse_args()

    if not DEV or not USER:
        print("Set AM_DEV_TOKEN and AM_USER_TOKEN first. See the docstring.", file=sys.stderr)
        return 2

    if args.cleanup or args.ids:
        ids = [x.strip() for x in (args.ids or "").split(",") if x.strip()]
        if args.cleanup:
            return cleanup_from_report(args.cleanup, ids)
        print(f"removing {len(ids)} explicit id(s)")
        ok = 0
        for lid in ids:
            code, _, _ = call("DELETE", f"/v1/me/library/songs/{lid}")
            print(f"  {lid}: HTTP {code}")
            ok += 1 if code in (200, 204, 404) else 0
        print(f"{ok}/{len(ids)} cleared")
        return 0 if ok == len(ids) else 1

    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    name = f"mochy spike {stamp}"

    # 0. does the session work at all, and which storefront
    code, body, raw = call("GET", "/v1/me/storefront")
    if code != 200:
        print(f"sign-in check failed: HTTP {code} {raw[:200]}", file=sys.stderr)
        print("The developer token or media-user-token is wrong, expired, or the account "
              "has no Apple Music subscription.", file=sys.stderr)
        return 2
    storefront = (body.get("data") or [{}])[0].get("id", "us")
    record("session", True, f"storefront={storefront}")

    # Everything already in the library is off limits. Snapshot it first so cleanup can
    # delete only rows this run created, never a row that was there before.
    before = library_songs(pages=12)
    before_catalog = {catalog_id_of(i) for i in before if catalog_id_of(i)}
    before_ids = {i["id"] for i in before}

    # find two catalog songs
    term = urllib.parse.quote(args.track)
    code, body, _ = call("GET", f"/v1/catalog/{storefront}/search?term={term}&types=songs&limit=25")
    songs = (((body.get("results") or {}).get("songs") or {}).get("data") or [])
    if len(songs) < 2:
        record("catalog search", False, f"only {len(songs)} result(s) for {args.track!r}")
        return 1
    a, b = songs[0], songs[1]
    a_id, b_id = a["id"], b["id"]
    a_name = a["attributes"]["name"]
    b_name = b["attributes"]["name"]
    record("catalog search", True, f"{a_name!r} / {b_name!r}")

    # 1. create a playlist with the first song
    code, body, raw = call("POST", "/v1/me/library/playlists", {
        "attributes": {"name": name, "description": "mochy sync spike, safe to delete"},
        "relationships": {"tracks": {"data": [{"id": a_id, "type": "songs"}]}},
    })
    if code != 201:
        record("create playlist", False, f"HTTP {code} {raw[:200]}")
        return 1
    playlist_id = body["data"][0]["id"]
    record("create playlist", True, f"id={playlist_id}")

    # 2. add the second song, then read back for library ids
    code, _, raw = call("POST", f"/v1/me/library/playlists/{playlist_id}/tracks",
                        {"data": [{"id": b_id, "type": "songs"}]})
    ok_add = code in (200, 204)
    time.sleep(2)
    entries = tracks_of(playlist_id)
    library_ids = [e["id"] for e in entries]
    # Creating a playlist with catalog songs also puts them in the library, so these
    # entries are rows this run is responsible for and must remove again.
    created_ids = [e["id"] for e in entries if catalog_id_of(e) not in before_catalog]
    record("add tracks", ok_add and len(entries) >= 2,
           f"HTTP {code}, playlist now holds {len(entries)} entr(y/ies), "
           f"{len(created_ids)} of them newly in the library",
           {"library_ids": library_ids, "created_library_ids": created_ids})

    # 3. PUT the full ordered list with one entry removed (the only way to delete)
    if len(library_ids) >= 2:
        keep = [library_ids[0]]
        # the body is an object with "data", not a bare array: a bare array answers 400
        # "Unable to parse request body". PUT replaces the whole list, so an empty or
        # malformed body silently empties the playlist and still answers 204.
        body = {"data": [{"id": x, "type": "library-songs"} for x in keep]}
        code, _, raw = call("PUT", f"/v1/me/library/playlists/{playlist_id}/tracks", body)
        time.sleep(2)
        after = tracks_of(playlist_id)
        ok = len(after) == 1
        record("remove track via PUT", ok,
               f"HTTP {code}, {len(library_ids)} -> {len(after)} entr(y/ies)")
    else:
        record("remove track via PUT", False, "not enough entries to test a removal")

    # 4. library add, then verify it landed (202 is async)
    code, _, raw = call("POST", f"/v1/me/library?ids[songs]={a_id}")
    landed = False
    lib_before = library_songs()
    for _ in range(6):
        time.sleep(3)
        lib = library_songs()
        if any(catalog_id_of(i) == a_id or i.get("id") == a_id for i in lib):
            landed = True
            break
    record("add to library", code in (200, 202) and landed,
           f"HTTP {code}, appears in library: {landed}")

    # 5. the cascade question: is the spike song still in the playlist after library delete
    entry = next((e for e in (tracks_of(playlist_id) or []) if catalog_id_of(e) == a_id), None)
    cascade = "unknown"
    if a_id in before_catalog:
        # it was in the library before this run: deleting it would be a change to the
        # user's library, which is exactly what this spike must not do
        record("remove from library", True,
               "skipped: the track was already in the library before this run")
        record("library delete cascades into playlists", True,
               "skipped for the same reason")
    elif entry and landed:
        code, _, raw = call("DELETE", f"/v1/me/library/songs/{entry['id']}")
        time.sleep(3)
        lib = library_songs()
        gone = not any(catalog_id_of(i) == a_id or i.get("id") == a_id for i in lib)
        still_in_playlist = any(e["id"] == entry["id"] for e in tracks_of(playlist_id))
        cascade = "yes" if (gone and not still_in_playlist) else "no"
        record("remove from library", gone, f"HTTP {code}, gone from library: {gone}")
        record("library delete cascades into playlists", cascade == "yes",
               f"cascade={cascade}"
               + ("" if still_in_playlist else " (playlist entry also vanished)"))
    else:
        record("remove from library", False, "could not locate the library entry to delete")

    # 6. cleanup: drop the playlist, then the library rows this run created and nothing else
    if not args.keep:
        code, _, raw = call("DELETE", f"/v1/me/library/playlists/{playlist_id}")
        record("delete playlist (undocumented)", code in (200, 204), f"HTTP {code}")
        survivors = [i for i in (created_ids or []) if i not in before_ids]
        removed = 0
        for lid_ in survivors:
            c, _, _ = call("DELETE", f"/v1/me/library/songs/{lid_}")
            removed += 1 if c in (200, 204) else 0
        record("clean up library rows this run created", removed == len(survivors),
               f"{removed}/{len(survivors)} removed; {len(before_ids)} pre-existing rows untouched")

    report = {
        "run_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "storefront": storefront,
        "playlist": name,
        "checks": results,
        "playlist_id": playlist_id,
        "cascade": cascade,
    }
    try:
        os.makedirs(os.path.dirname(args.report) or ".", exist_ok=True)
        with open(args.report, "w") as f:
            json.dump(report, f, indent=2)
        print(f"\nreport: {args.report}")
    except OSError as e:
        print(f"could not write report: {e}", file=sys.stderr)

    failed = [r for r in results if not r["ok"]]
    print(f"{len(results) - len(failed)}/{len(results)} checks passed")
    if failed:
        print("failed: " + ", ".join(r["check"] for r in failed))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
