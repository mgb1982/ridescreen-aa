package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Saves the rider's dashboard picture in the app's private storage (`filesDir/dash_bg/<bike>.jpg`, or `.png`
 * when the picture has transparent pixels; excluded from backups). The picture never leaves the phone and the
 * app ships none of its own. Only the bike's own file counts: there is no fallback to another bike's picture.
 */
object DashBackgroundStore {
    private const val JPEG_QUALITY = 88
    private val importing = AtomicBoolean(false)

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, DashBackground.DIR)

    /** The bike's picture if it exists (PNG first), else where a JPEG would go. */
    fun file(ctx: Context): File = file(ctx, BikeScope.suffix(ctx))

    fun file(ctx: Context, bikeKey: String?): File {
        val png = File(dir(ctx), DashBackground.fileName(bikeKey, true))
        return if (png.isFile) png else File(dir(ctx), DashBackground.fileName(bikeKey, false))
    }

    /** True while an import runs: another choice is ignored meanwhile. */
    fun isImporting(): Boolean = importing.get()

    /**
     * Decodes with sampling (never at full size), applies EXIF rotation and mirroring, scales and saves for the
     * bike [bikeKey] (captured by the caller before its thread started). False on any failure or when another
     * import is running. Call off the UI thread.
     */
    fun import(ctx: Context, uri: Uri, bikeKey: String?): Boolean {
        if (!importing.compareAndSet(false, true)) return false
        val app = ctx.applicationContext
        var decoded: Bitmap? = null
        var rotated: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            val cr = app.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            val orient = try {
                cr.openInputStream(uri)?.use {
                    DashBackground.exifTransform(android.media.ExifInterface(it).getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL))
                } ?: DashBackground.Orientation(0, false)
            } catch (_: Exception) { DashBackground.Orientation(0, false) }
            val sideways = orient.degrees == 90 || orient.degrees == 270
            val rw = if (sideways) bounds.outHeight else bounds.outWidth
            val rh = if (sideways) bounds.outWidth else bounds.outHeight
            val (tw, th) = DashBackground.storedSize(rw, rh)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = DashBackground.sampleSize(rw, rh, tw, th)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val dec: Bitmap = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
            decoded = dec
            val src: Bitmap = if (orient.degrees == 0 && !orient.flipH) dec else Bitmap.createBitmap(
                dec, 0, 0, dec.width, dec.height,
                Matrix().apply {
                    if (orient.flipH) postScale(-1f, 1f)
                    postRotate(orient.degrees.toFloat())
                }, true)
            rotated = src
            val (ow, oh) = DashBackground.storedSize(src.width, src.height)
            val sc: Bitmap = if (ow == src.width && oh == src.height) src else Bitmap.createScaledBitmap(src, ow, oh, true)
            scaled = sc
            var transparent = false
            if (sc.hasAlpha()) {
                val row = IntArray(sc.width)
                for (y in 0 until sc.height) {
                    sc.getPixels(row, 0, sc.width, 0, y, sc.width, 1)
                    if (DashBackground.anyTransparent(row)) { transparent = true; break }
                }
            }
            val png = DashBackground.savesAsPng(transparent)
            val d = dir(app)
            d.mkdirs()
            val out = File(d, DashBackground.fileName(bikeKey, png))
            val tmp = File(d, out.name + "." + System.nanoTime() + ".tmp")
            tmp.outputStream().use {
                if (png) sc.compress(Bitmap.CompressFormat.PNG, 100, it) else sc.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            }
            if (!tmp.renameTo(out)) { tmp.delete(); return false }
            // The other format of this bike must not linger (PNG would win over a newer JPEG).
            File(d, DashBackground.fileName(bikeKey, !png)).delete()
            true
        } catch (_: Throwable) {   // includes OutOfMemoryError: a failed pick must not crash the app
            false
        } finally {
            val s = scaled; val r = rotated; val dcd = decoded
            if (s != null) s.recycle()
            if (r != null && r !== s) r.recycle()
            if (dcd != null && dcd !== r && dcd !== s) dcd.recycle()
            importing.set(false)
        }
    }

    fun remove(ctx: Context) {
        try {
            for (png in listOf(true, false)) File(dir(ctx), DashBackground.fileName(BikeScope.suffix(ctx), png)).delete()
        } catch (_: Exception) { }
    }
}
