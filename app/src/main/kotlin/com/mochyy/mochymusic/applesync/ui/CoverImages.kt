// CoverImages.kt
//
// A cover someone picked off their phone. Read once, cropped to a square and kept in the app's own
// files, so the row points at something that cannot move or be revoked: a content:// uri from the
// photo picker outlives the app only until the permission does.
//
// The crop is a centre crop, not a draggable one: the image is squared from the middle and scaled to
// the size the app's lists draw. A cropper is a screen of its own and this is not it.

package com.mochyy.mochymusic.applesync.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.mochyy.mochymusic.applesync.CoverOverrides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object CoverImages {

    /** Enough for the player's cover box on a 1080p screen without holding a 12 MP photo in memory. */
    private const val Size = 720

    private fun dir(context: Context) = File(context.filesDir, "covers")

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun fileFor(context: Context, kind: CoverOverrides.Kind, id: String) =
        File(dir(context), "${kind.tag}-${safe(id)}.jpg")

    /** True when a stored cover value is one of ours, so a revert can delete the file too. */
    fun isStored(context: Context, value: String?): Boolean =
        value != null && value.startsWith("file://") && value.contains("/covers/")

    /**
     * Saves [uri] as this row's cover and returns the value to store (`file://…`), or null when the
     * picture cannot be read. Replaces any earlier one for the same row.
     */
    suspend fun store(
        context: Context,
        kind: CoverOverrides.Kind,
        id: String,
        uri: Uri,
    ): String? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        // decode at roughly twice the target so the scaled result stays sharp, and no more: a 12 MP
        // photo decoded whole is 48 MB of bitmap for a 720 px cover
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return@withContext null

        val square = square(decoded)
        val file = fileFor(context, kind, id)
        val saved = runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }.isSuccess
        if (!saved) return@withContext null
        "file://${file.absolutePath}"
    }

    /** Drops the file behind a cover, for a row that went back to Apple's. */
    suspend fun forget(context: Context, kind: CoverOverrides.Kind, id: String) =
        withContext(Dispatchers.IO) {
            runCatching { fileFor(context, kind, id).delete() }
            Unit
        }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= Size * 2 || height / (sample * 2) >= Size * 2) sample *= 2
        return sample
    }

    private fun square(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        val cropped = Bitmap.createBitmap(source, x, y, side, side)
        val scaled = if (side == Size) cropped else Bitmap.createScaledBitmap(cropped, Size, Size, true)
        // JPEG has no alpha, and a transparent png would otherwise come out black
        val out = Bitmap.createBitmap(Size, Size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        return out
    }
}
