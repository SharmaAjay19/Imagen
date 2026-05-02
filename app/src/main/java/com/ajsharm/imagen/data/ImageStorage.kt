package com.ajsharm.imagen.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages persistence of image files in the app-private filesDir.
 * All paths returned/accepted are RELATIVE to filesDir, e.g. "uploads/abc.png".
 */
class ImageStorage(private val context: Context) {

    private val uploadsDir: File get() = File(context.filesDir, "uploads").also { it.mkdirs() }
    private val generatedDir: File get() = File(context.filesDir, "generated").also { it.mkdirs() }

    fun absolute(relativePath: String): File = File(context.filesDir, relativePath)

    /** Copy a content URI into uploads/, downsampled to <=2048 px and EXIF-rotated. */
    suspend fun saveUploadFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val out = File(uploadsDir, "$id.png")
        val bmp = decodeAndOrientUri(uri) ?: error("Could not decode image")
        FileOutputStream(out).use { os ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os)
        }
        bmp.recycle()
        "uploads/${out.name}"
    }

    suspend fun saveGeneratedBytes(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val out = File(generatedDir, "$id.png")
        out.writeBytes(bytes)
        "generated/${out.name}"
    }

    suspend fun delete(relativePath: String) = withContext(Dispatchers.IO) {
        runCatching { absolute(relativePath).delete() }
        Unit
    }

    suspend fun deleteAll(paths: Iterable<String>) = withContext(Dispatchers.IO) {
        paths.forEach { runCatching { absolute(it).delete() } }
    }

    private fun decodeAndOrientUri(uri: Uri): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        val maxDim = 2048
        var sample = 1
        while (w / sample > maxDim || h / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = runCatching {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        }
        return if (m.isIdentity) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            .also { if (it !== bmp) bmp.recycle() }
    }
}
