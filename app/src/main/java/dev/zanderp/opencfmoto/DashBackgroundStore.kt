package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File

/**
 * Saves the rider's dashboard picture in the app's private storage (`filesDir/dash_bg/<bike>.jpg`, excluded
 * from backups). The picture never leaves the phone and the app ships none of its own.
 */
object DashBackgroundStore {
    private const val JPEG_QUALITY = 88

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, DashBackground.DIR)

    fun file(ctx: Context): File = File(dir(ctx), DashBackground.fileName(BikeScope.suffix(ctx)))

    /** Decodes with sampling (never at full size), applies the EXIF rotation, scales and saves. False on any failure. Call off the UI thread. */
    fun import(ctx: Context, uri: Uri): Boolean {
        val app = ctx.applicationContext
        var decoded: Bitmap? = null
        var rotated: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            val cr = app.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            val degrees = try {
                cr.openInputStream(uri)?.use {
                    DashBackground.exifDegrees(android.media.ExifInterface(it).getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL))
                } ?: 0
            } catch (_: Exception) { 0 }
            val sideways = degrees == 90 || degrees == 270
            val rw = if (sideways) bounds.outHeight else bounds.outWidth
            val rh = if (sideways) bounds.outWidth else bounds.outHeight
            val (tw, th) = DashBackground.storedSize(rw, rh)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = DashBackground.sampleSize(rw, rh, tw, th)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val dec: Bitmap = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
            decoded = dec
            val src: Bitmap = if (degrees == 0) dec else Bitmap.createBitmap(
                dec, 0, 0, dec.width, dec.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
            rotated = src
            val (ow, oh) = DashBackground.storedSize(src.width, src.height)
            val sc: Bitmap = if (ow == src.width && oh == src.height) src else Bitmap.createScaledBitmap(src, ow, oh, true)
            scaled = sc
            val d = dir(app)
            d.mkdirs()
            val out = file(app)
            val tmp = File(d, out.name + ".tmp")
            tmp.outputStream().use { sc.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!tmp.renameTo(out)) { tmp.delete(); return false }
            true
        } catch (_: Throwable) {   // includes OutOfMemoryError: a failed pick must not crash the app
            false
        } finally {
            val s = scaled; val r = rotated; val dcd = decoded
            if (s != null) s.recycle()
            if (r != null && r !== s) r.recycle()
            if (dcd != null && dcd !== r && dcd !== s) dcd.recycle()
        }
    }

    fun remove(ctx: Context) {
        try { file(ctx).delete() } catch (_: Exception) { }
    }
}
