// ApplePageScripts.kt

package com.mocha.mochymusic.applesync.page

/**
 * The scripts the page runs. Every one of them answers through the bridge as JSON, so the Kotlin side
 * never has to parse JavaScript's return-value encoding, and every one of them answers exactly once:
 * a script that can dead-end is a script that looks like a broken transport.
 */
object ApplePageScripts {

    /**
     * Read-only proof that the page is usable: MusicKit is loaded, it holds the session, and a fetch
     * with those tokens reaches Apple from the page's own origin.
     *
     * The failure answers carry `href`, `readyState` and `hasMusicKit` on purpose. When this fails on
     * a real device, those three fields say whether the page was still loading, had navigated
     * somewhere else, or never had the web player's client on it at all.
     */
    fun probe(): String = """
(function() {
  var id = "__MOCHY_ID__";
  var started = Date.now();
  function answer(payload) {
    try { MochyAppleBridge.done(id, JSON.stringify(payload)); } catch (e) { }
  }
  function where() { return { href: String(location.href), ready: String(document.readyState), hasMusicKit: !!window.MusicKit }; }
  function tick() {
    try {
      var m = (window.MusicKit && window.MusicKit.getInstance) ? window.MusicKit.getInstance() : null;
      if (!m || !m.developerToken || !m.musicUserToken) {
        if (Date.now() - started < 8000) { setTimeout(tick, 300); return; }
        answer(Object.assign({ error: "no session on the page" }, where()));
        return;
      }
      fetch("https://amp-api.music.apple.com/v1/me/storefront", {
        headers: {
          "Authorization": "Bearer " + m.developerToken,
          "Media-User-Token": m.musicUserToken
        }
      }).then(function(r) {
        return r.text().then(function(t) {
          answer(Object.assign({ status: r.status, body: String(t).slice(0, 200), authorized: !!m.isAuthorized }, where()));
        });
      }).catch(function(e) {
        answer(Object.assign({ error: String(e) }, where()));
      });
    } catch (e) {
      answer(Object.assign({ error: String(e) }, where()));
    }
  }
  setTimeout(tick, 400);
})();
""".trimIndent()

    /**
     * Records the requests the page itself makes, so a write shape can be read off the real client
     * instead of guessed at.
     *
     * Only amp-api calls that look like ratings or library writes are kept, no headers are touched,
     * and the ring buffer is small: this is a tape recorder for one tap on a heart.
     */
    fun installCapture(): String = captureHook()

    /**
     * The same hook as a document-start script.
     *
     * Patching `window.fetch` after the page has loaded is too late: Apple's client keeps its own
     * reference to the original function, so its calls never pass through a later patch and the tape
     * stays empty while the UI happily toggles. This runs before any of the page's scripts.
     */
    fun captureHook(): String = """
(function() {
  try {
    if (window.__mochyHooked) return "already watching";
    window.__mochyHooked = 1;
    window.__mochySeen = [];
    function record(method, url, body) {
      try {
        var u = String(url || "");
        // everything that talks to Apple is kept, not just the endpoints guessed at: the first
        // attempt filtered on amp-api plus a few words in the path and the favourite toggle went
        // somewhere else entirely, so the tape recorded nothing
        if (u.indexOf("apple.com") < 0) return;
        if (u.indexOf("/assets/") >= 0) return;
        if (/\.(js|css|png|jpe?g|webp|svg|woff2?|ttf)(\?|$)/i.test(u)) return;
        window.__mochySeen.push({
          method: String(method || "GET").toUpperCase(),
          url: u.replace("https://", ""),
          body: body ? String(body).slice(0, 300) : null
        });
        if (window.__mochySeen.length > 40) window.__mochySeen.shift();
      } catch (e) { }
    }
    var originalFetch = window.fetch;
    window.fetch = function (input, init) {
      try {
        var url = (typeof input === "string") ? input : (input && input.url) || "";
        var method = (init && init.method) || (input && input.method) || "GET";
        record(method, url, init && init.body);
      } catch (e) { }
      return originalFetch.apply(this, arguments);
    };
    var open = XMLHttpRequest.prototype.open;
    var send = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (method, url) {
      this.__mochyCall = { method: method, url: url };
      return open.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function (body) {
      try { if (this.__mochyCall) record(this.__mochyCall.method, this.__mochyCall.url, body); } catch (e) { }
      return send.apply(this, arguments);
    };
    return "watching";
  } catch (e) { return "failed: " + e; }
})();
""".trimIndent()

    /**
     * What is actually clickable on the page.
     *
     * Apple's own menu does not render inside this WebView (its event queue overflows, the backdrop
     * appears and the menu does not), so the page is described instead of trusted: labels, roles and
     * test ids are listed, which is enough to aim a click at the right control.
     */
    fun describePage(): String = """
(function() {
  function labelOf(el) {
    return [el.getAttribute && el.getAttribute("aria-label"),
            el.getAttribute && el.getAttribute("data-testid"),
            el.getAttribute && el.getAttribute("title"),
            (el.textContent || "").trim().slice(0, 40)].filter(Boolean).join(" | ");
  }
  var out = { href: String(location.href), title: String(document.title) };
  var clickable = document.querySelectorAll("button, [role=button], a[href]");
  out.count = clickable.length;
  out.items = [];
  for (var i = 0; i < clickable.length && out.items.length < 40; i++) {
    var label = labelOf(clickable[i]);
    if (label) out.items.push(label);
  }
  out.likeCandidates = [];
  var all = document.querySelectorAll("[aria-label], [data-testid]");
  for (var j = 0; j < all.length && out.likeCandidates.length < 10; j++) {
    var l = (all[j].getAttribute("aria-label") || all[j].getAttribute("data-testid") || "");
    if (/lov|favourit|favorit|like|heart/i.test(l)) out.likeCandidates.push(l);
  }
  return JSON.stringify(out);
})();
""".trimIndent()


    /**
     * What the web player's own playlist page lists: the header's song count, and every track link
     * it has drawn. The REST copy of a library playlist answers with only the library's tracks, so
     * this is what tells "the playlist is that short" from "the API hides the rest".
     */
    fun readPlaylistTracks(): String = """
(function() {
  var out = { href: String(location.href), title: String(document.title), header: 0, headerText: "", links: 0, ids: [], names: [], rows: 0 };
  try {
    var bodyText = document.body ? String(document.body.innerText || "") : "";
    var m = bodyText.match(/([0-9]+)\s+songs?/);
    if (m) { out.header = parseInt(m[1], 10); out.headerText = m[0]; }
    var anchors = document.querySelectorAll('a[href*="/song/"]');
    var seen = {};
    for (var i = 0; i < anchors.length; i++) {
      var href = String(anchors[i].getAttribute("href") || "");
      var mm = href.match(/\/song\/([^\/?#]+)/);
      if (!mm) continue;
      var id = mm[1];
      if (seen[id]) continue;
      seen[id] = 1;
      out.ids.push(id);
      out.names.push(String(anchors[i].textContent || "").trim());
    }
    out.links = out.ids.length;
    out.rows = document.querySelectorAll('[data-testid="track-list-item"], div[role="row"], li[role="row"]').length;
  } catch (e) {
    out.error = String(e);
  }
  return JSON.stringify(out);
})()
""".trimIndent()

    /**
     * Who else is on a shared playlist, read from the page's own header.
     *
     * MusicKit has no answer for this (the relationship 404s and `include=collaborators` adds
     * nothing), so the page is the source: the header of a shared playlist lists the people on it.
     * The script also returns the words around "collaborat" so a markup change shows up as evidence
     * instead of an empty list.
     */
    fun readCollaborators(): String = """
(function() {
  function text(el) { return el ? String(el.textContent || "").trim() : ""; }
  var out = { href: String(location.href), title: String(document.title), names: [], handles: [], evidence: [] };

  // The credit line reads "Owner & 1 other". The avatars beside it are the people, and walking up
  // from that line until the block holds two of them is what separates them from the page's chrome
  // (account menu, navigation, song rows all have alt text too).
  var credit = null;
  var nodes = document.querySelectorAll("div,span,p,h1,h2,a");
  for (var i = 0; i < nodes.length; i++) {
    var t = text(nodes[i]);
    if (!t || t.length > 140) continue;
    if (/&\s*\d+\s+other/i.test(t) || /^\s*\d+\s+others?\s*$/i.test(t)) { credit = nodes[i]; break; }
  }
  if (credit) {
    out.evidence.push("credit: " + text(credit).slice(0, 90));
    var box = credit;
    for (var up = 0; up < 6 && box; up++) {
      var imgs = box.querySelectorAll("img[alt]");
      if (imgs.length >= 2) {
        for (var j = 0; j < imgs.length; j++) {
          var alt = String(imgs[j].getAttribute("alt") || "").trim();
          if (alt && alt.length <= 60) out.names.push(alt);
        }
        break;
      }
      box = box.parentElement;
    }
  }

  // the same people by handle, if the header links them
  var links = document.querySelectorAll("header a[href*='/@'], a[href*='/@']");
  for (var k = 0; k < links.length && k < 12; k++) {
    var m = String(links[k].getAttribute("href") || "").match(/\/@([A-Za-z0-9._-]+)/);
    if (m) out.handles.push(m[1]);
  }

  function uniq(arr) { var seen = {}; return arr.filter(function (v) { if (!v || seen[v]) return false; seen[v] = 1; return true; }); }
  out.names = uniq(out.names);
  out.handles = uniq(out.handles);
  return JSON.stringify(out);
})();
""".trimIndent()

    /** Clicks the first control that looks like a heart, and says what it clicked. */
    fun tapLike(): String = """
(function() {
  var nodes = document.querySelectorAll("[aria-label], [data-testid]");
  for (var i = 0; i < nodes.length; i++) {
    var el = nodes[i];
    var l = (el.getAttribute("aria-label") || el.getAttribute("data-testid") || "");
    if (/lov|favourit|favorit/i.test(l)) {
      try {
        el.scrollIntoView({ block: "center" });
        el.click();
        return "clicked: " + l;
      } catch (e) { return "found but click failed: " + e; }
    }
  }
  return "no heart found on this page";
})();
""".trimIndent()

    /** Everything the tape has, as JSON, and clears it. */
    fun drainCapture(): String = """
(function() {
  try { return JSON.stringify(window.__mochySeen || []); } catch (e) { return "[]"; }
})();
""".trimIndent()

    /**
     * The session's two tokens, so the REST reads use the same one the page is using.
     *
     * They are answered to Kotlin only and never put in a UI string: the library read needs the
     * *user* token, catalogue reads do not, and a stale user token fails silently as "empty library".
     */
    fun sessionTokens(): String = """
(function() {
  var id = "__MOCHY_ID__";
  function answer(payload) {
    try { MochyAppleBridge.done(id, JSON.stringify(payload)); } catch (e) { }
  }
  try {
    var m = (window.MusicKit && window.MusicKit.getInstance) ? window.MusicKit.getInstance() : null;
    if (!m) { answer({ error: "no MusicKit" }); return; }
    answer({ developer: String(m.developerToken || ""), user: String(m.musicUserToken || "") });
  } catch (e) { answer({ error: String(e) }); }
})();
""".trimIndent()

    /**
     * One request made from inside the page, with MusicKit's own tokens.
     *
     * [body] is sent verbatim when present; DELETE and the reads pass null. The answer is always a
     * JSON object with either `status`/`body` or `error`, so the caller never has to distinguish
     * "no answer" from "answered with nothing".
     */
    fun request(method: String, url: String, body: String? = null): String {
        val bodyJs = if (body == null) "null" else org.json.JSONObject.quote(body)
        return """
(function() {
  var id = "__MOCHY_ID__";
  function answer(payload) {
    try { MochyAppleBridge.done(id, JSON.stringify(payload)); } catch (e) { }
  }
  try {
    var m = (window.MusicKit && window.MusicKit.getInstance) ? window.MusicKit.getInstance() : null;
    if (!m || !m.developerToken || !m.musicUserToken) { answer({ error: "no session on the page" }); return; }
    fetch(${org.json.JSONObject.quote(url)}, {
      method: ${org.json.JSONObject.quote(method)},
      headers: {
        "Authorization": "Bearer " + m.developerToken,
        "Media-User-Token": m.musicUserToken,
        "Content-Type": "application/json"
      },
      body: $bodyJs
    }).then(function(r) {
      return r.text().then(function(t) { answer({ status: r.status, body: String(t) }); });
    }).catch(function(e) { answer({ error: String(e) }); });
  } catch (e) { answer({ error: String(e) }); }
})();
""".trimIndent()
    }
}
