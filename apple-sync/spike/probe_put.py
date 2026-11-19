#!/usr/bin/env python3
"""Probe what PUT /v1/me/library/playlists/{id}/tracks actually wants.

The spike's bare-array PUT answered 400 on this account. This script creates a
throwaway playlist with three songs and tries every plausible removal shape,
printing the status and error body for each, and re-reading the playlist after
each attempt so we know what changed rather than what was claimed.

Cleans up after itself: deletes the playlist and any library rows it created.

    set -a; . /dev/shm/apple.env; set +a
    python3 apple-sync/spike/probe_put.py
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://amp-api.music.apple.com"
DEV = os.environ.get("AM_DEV_TOKEN", "").strip()
USER = os.environ.get("AM_USER_TOKEN", "").strip()


def call(method, path, body=None, raw_body=None):
    url = path if path.startswith("http") else API + path
    data = None
    hdrs = {
        "Authorization": f"Bearer {DEV}",
        "Media-User-Token": USER,
        "Origin": "https://music.apple.com",
        "Referer": "https://music.apple.com/",
        "Accept": "application/json",
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) Chrome/131.0.0.0 Safari/537.36",
    }
    if raw_body is not None:
        data = raw_body.encode()
        hdrs["Content-Type"] = "application/json"
    elif body is not None:
        data = json.dumps(body).encode()
        hdrs["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=45) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def entries(pid):
    code, body = call("GET", f"/v1/me/library/playlists/{pid}/tracks?limit=100")
    if code == 404:
        return []
    if code != 200:
        return []
    return json.loads(body).get("data", [])


def lib_ids(pid):
    return [e["id"] for e in entries(pid)]


def main() -> int:
    if not DEV or not USER:
        print("set AM_DEV_TOKEN and AM_USER_TOKEN", file=sys.stderr)
        return 2
    code, body = call("GET", "/v1/me/storefront")
    sf = json.loads(body)["data"][0]["id"]
    print(f"storefront {sf}")

    terms = ["yesterday beatles", "blackbird beatles", "something beatles"]
    ids = []
    for t in terms:
        code, body = call("GET", f"/v1/catalog/{sf}/search?term={urllib.parse.quote(t)}&types=songs&limit=3")
        songs = json.loads(body)["results"]["songs"]["data"]
        ids.append(songs[0]["id"])
    print("catalog ids:", ids)

    code, body = call("POST", "/v1/me/library/playlists", {
        "attributes": {"name": "mochy put probe", "description": "throwaway"},
        "relationships": {"tracks": {"data": [{"id": i, "type": "songs"} for i in ids]}},
    })
    pid = json.loads(body)["data"][0]["id"]
    print(f"playlist {pid}")
    time.sleep(3)

    got = entries(pid)
    print(f"entries before: {len(got)}")
    lids = [e["id"] for e in got]
    print("library ids:", lids)
    if got:
        print("one entry, raw:", json.dumps(got[0])[:400])

    if len(lids) < 3:
        print("not enough entries to probe removals", file=sys.stderr)
        call("DELETE", f"/v1/me/library/playlists/{pid}")
        return 1

    keep = lid = lids[0]
    attempts = [
        ("bare array of library ids", json.dumps([keep]).encode()),
        ("bare array, catalog id", json.dumps([ids[0]]).encode()),
        ("bare array, all three", json.dumps(lids).encode()),
        ("{data:[{id,type:library-songs}]}", json.dumps(
            {"data": [{"id": x, "type": "library-songs"} for x in lids[:1]]}).encode()),
        ("{data:[{id,type:songs}]}", json.dumps(
            {"data": [{"id": ids[0], "type": "songs"}]}).encode()),
        ("{ids:[...]}", json.dumps({"ids": lids[:1]}).encode()),
        ("{attributes:{}}", json.dumps({"attributes": {}}).encode()),
    ]
    for name, payload in attempts:
        code, body = call("PUT", f"/v1/me/library/playlists/{pid}/tracks", raw_body=payload.decode())
        time.sleep(2)
        now = lib_ids(pid)
        changed = len(now) != len(lids)
        print(f"\nPUT {name}: HTTP {code}, entries {len(lids)} -> {len(now)}"
              f"{' CHANGED' if changed else ''}")
        snippet = body.strip().replace("\n", " ")[:300]
        if snippet:
            print(f"   body: {snippet}")

    # track-level delete variants, in case removal lives there
    for suffix in (f"/v1/me/library/playlists/{pid}/tracks/{lids[0]}",
                   f"/v1/me/library/playlists/{pid}/tracks?ids={lids[0]}"):
        code, body = call("DELETE", suffix)
        time.sleep(2)
        now = lib_ids(pid)
        print(f"\nDELETE {suffix.split(str(pid))[1]}: HTTP {code}, entries -> {len(now)}")
        s = body.strip().replace("\n", " ")[:200]
        if s:
            print(f"   body: {s}")

    # cleanup: drop the playlist, then any library rows the probe created
    code, _ = call("DELETE", f"/v1/me/library/playlists/{pid}")
    print(f"\ncleanup playlist: HTTP {code}")
    remaining = entries(pid)
    if remaining:
        print("warning: playlist still holds", len(remaining))
    for lid_ in lids:
        code, _ = call("DELETE", f"/v1/me/library/songs/{lid_}")
        print(f"cleanup library {lid_}: HTTP {code}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
