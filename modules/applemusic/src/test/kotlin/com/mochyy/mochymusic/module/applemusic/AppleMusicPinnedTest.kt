// AppleMusicPinnedTest.kt
//
// The manual-pin path in the Apple module: a recording a person chose is fetched by its catalogue
// id, and another provider's id is never mistaken for one. Kept out of upstream's test file, which
// has a seam budget of its own; this copy of the fake host is the smallest one that answers the
// requests this path makes.

package com.mochyy.mochymusic.module.applemusic

import com.mochyy.mochymusic.module.HttpRequest
import com.mochyy.mochymusic.module.HttpResponse
import com.mochyy.mochymusic.module.ModuleHost
import com.mochyy.mochymusic.module.ModuleStorage
import com.mochyy.mochymusic.module.ResolveResult
import com.mochyy.mochymusic.module.TrackQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64
import kotlin.io.path.createTempDirectory

class AppleMusicPinnedTest {

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing fixture $name" }.readText()

    private class FakeHost(val routes: (HttpRequest) -> HttpResponse) : ModuleHost {
        val requests = ArrayList<HttpRequest>()
        override val storage = object : ModuleStorage {
            val map = HashMap<String, String>()
            override fun get(key: String) = map[key]
            override fun set(key: String, value: String?) {
                if (value == null) map.remove(key) else map[key] = value
            }
        }
        override fun http(request: HttpRequest): HttpResponse {
            requests += request
            return routes(request)
        }
        override fun log(message: String) = Unit
        override val folder: File = createTempDirectory("applemusic").toFile()
    }

    /** a developer token whose payload says it's good for a day */
    private val devToken = "eyJhbGciOiJFUzI1NiJ9." +
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"iss":"AMPWebPlay","exp":${System.currentTimeMillis() / 1000 + 86_400}}""".toByteArray()
        ) + ".c2ln"

    private val keyUri = "data:;base64,ESIzRFVmd4iZqrvM3e7/AA=="

    private val legacyPlaylist = """
        #EXTM3U
        #EXT-X-VERSION:5
        #EXT-X-TARGETDURATION:11
        #EXT-X-KEY:METHOD=SAMPLE-AES,URI="$keyUri",KEYFORMAT="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed",KEYFORMATVERSIONS="1"
        #EXT-X-MAP:URI="P123_A456_ctrp256.mp4",BYTERANGE="700@0"
        #EXTINF:10.0,
        #EXT-X-BYTERANGE:1000@700
        P123_A456_ctrp256.mp4
        #EXT-X-ENDLIST
    """.trimIndent()

    private val webPlayback = """{"songList":[{"assets":[
        {"flavor":"30:cbcp256","URL":"https://aod.itunes.apple.com/a/b/fairplay.m3u8"},
        {"flavor":"32:ctrp64","URL":"https://aod.itunes.apple.com/a/b/P123_64.m3u8"},
        {"flavor":"28:ctrp256","URL":"https://aod.itunes.apple.com/a/b/P123.m3u8"}]}]}"""

    private fun signedInModule(routes: (HttpRequest) -> HttpResponse?): Pair<AppleMusicModule, FakeHost> {
        val host = FakeHost { request ->
            routes(request) ?: when {
                request.url.endsWith("/v1/me/storefront") ->
                    HttpResponse(200, """{"data":[{"id":"us","attributes":{"name":"United States"}}]}""".toByteArray())
                "/search?" in request.url -> HttpResponse(200, resource("search.json").toByteArray())
                request.url.endsWith("wa/webPlayback") -> HttpResponse(200, webPlayback.toByteArray())
                request.url.startsWith("https://aod.itunes.apple.com/a/b/P123") -> HttpResponse(200, legacyPlaylist.toByteArray())
                else -> HttpResponse(404, ByteArray(0))
            }
        }
        val module = AppleMusicModule().also { it.attach(host) }
        module.onSignInHeader("authorization", "Bearer $devToken", "https://amp-api.music.apple.com/v1/me")
        assertTrue(module.onSignInHeader("media-user-token", "A".repeat(40), "https://amp-api.music.apple.com/v1/me"))
        return module to host
    }

    private val espresso = TrackQuery(title = "Espresso", artists = listOf("Sabrina Carpenter"), durationMs = 175_000)

    @Test
    fun `a pinned catalogue id is fetched, not searched`() = runBlocking {
        // a manual rematch names the recording, so the exact copy in the library is what plays
        val (module, host) = signedInModule { request ->
            if (request.url.endsWith("/v1/catalog/us/songs/1988776174")) {
                HttpResponse(
                    200,
                    """{"data":[{"id":"1988776174","attributes":{"name":"Alive","artistName":"Sia","albumName":"This Is Acting","durationInMillis":263000}}]}"""
                        .toByteArray(),
                )
            } else {
                null
            }
        }
        val pinned = TrackQuery(
            title = "Alive",
            artists = listOf("Sia"),
            durationMs = 263_000,
            externalId = "applemusic:1988776174",
        )
        val stream = (module.resolve(pinned, emptyMap()) as ResolveResult.Found).stream
        assertEquals("1988776174", stream.matched.id)
        assertTrue(host.requests.any { it.url.endsWith("/v1/catalog/us/songs/1988776174") })
        assertTrue(host.requests.none { "/search?" in it.url })
    }

    @Test
    fun `another provider's id is not mistaken for a catalogue id`() = runBlocking {
        val (module, host) = signedInModule { null }
        module.resolve(espresso.copy(externalId = "monochrome:abc123"), emptyMap())
        assertTrue(host.requests.none { "/v1/catalog/us/songs/abc123" in it.url })
        assertTrue(host.requests.any { "/search?" in it.url })
    }
}
