// PlaylistCollaborators.kt

package com.mochyy.mochymusic.applesync

import com.mochyy.mochymusic.applesync.db.PlaylistMetaEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * What [ApplePageScripts.readCollaborators] answered, turned into names.
 *
 * The page is HTML that Apple changes when it likes, so this never invents a person: a name has to
 * have been on the page, and the words around "collaborat" are kept as evidence so an empty result
 * can be told apart from a markup change.
 */
object PlaylistCollaborators {

    data class Read(val names: List<String>, val evidence: List<String>)

    private val labelsToIgnore = setOf(
        "collaborators", "collaborator", "add", "invite", "share", "more", "play", "shuffle",
        "edit", "delete", "collaborate", "collaborative", "preview", "playlist", "artwork",
    )

    /** Avatars and buttons carry alt/label text that is not a person. */
    private val notAPerson = listOf(
        "cover", "artwork", "album", "playlist", "button", "logo", "icon", "preview",
        "open navigation", "my account", "library", "music controls", "featured",
        "delete from library", "explicit", "play", "shuffle", "search", "menu",
    )

    fun parse(raw: String?): Read {
        if (raw.isNullOrBlank()) return Read(emptyList(), emptyList())
        val text = raw.trim().removeSurrounding("\"").replace("\\\"", "\"")
        val obj = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull()
            ?: return Read(emptyList(), emptyList())

        val names = obj.strings("names")
            .filterNot { it.lowercase() in labelsToIgnore }
            .filterNot { n -> notAPerson.any { it in n.lowercase() } }
            .filterNot { it.length > 60 }
        // a handle is not a name: "@person" reads worse than the name the page showed
        val handles = obj.strings("handles")
        return Read(
            names = (names + handles.map { "@$it" }).distinct().take(12),
            evidence = obj.strings("evidence"),
        )
    }

    /**
     * The account's own name, taken from the playlists it owns (`canDelete`): it is the name that
     * shows up most often as their owner, and it must never be listed as somebody else.
     */
    fun selfName(metas: List<PlaylistMetaEntity>): String? = metas
        .filter { it.canDelete }
        .mapNotNull { it.ownerName?.trim()?.takeIf(String::isNotEmpty) }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()
}
