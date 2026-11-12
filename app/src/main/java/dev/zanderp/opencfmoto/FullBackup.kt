// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (OpenCfMoto fork). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * v2.4 · Full backup of this phone's RideScreen AA: every setting (all SharedPreferences), trips,
 * places, garage, and the rest of the app's files and databases — one .zip the rider keeps
 * (before a system update, a reset or a new phone). Unlike [SettingsBackup] it is personal and
 * complete: it **does include** the bike Wi‑Fi password, keyless code/McuID and contacts, so it
 * must stay with the rider. Files over [MAX_FILE] (offline map packs) are skipped and counted.
 *
 * Restore overwrites the app's data with the zip and restarts the app.
 */
object FullBackup {
    private const val FORMAT = "ridescreen.fullbackup"
    private const val VERSION = 1
    private const val MAX_FILE = 100L * 1024 * 1024
    private val ROOTS = listOf("shared_prefs", "files", "databases")

    data class Result(val ok: Boolean, val message: String)

    fun suggestedFileName(): String =
        "RideScreenAA-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip"

    fun export(ctx: Context, out: OutputStream): Result {
        val data = File(ctx.applicationInfo.dataDir)
        var files = 0; var bytes = 0L; var skipped = 0
        ZipOutputStream(out.buffered()).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT).put("version", VERSION)
                .put("appVersion", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE)
                .put("exportedAt", System.currentTimeMillis())
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (root in ROOTS) {
                val dir = File(data, root)
                if (!dir.isDirectory) continue
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    if (f.length() > MAX_FILE) { skipped++; return@forEach }
                    val rel = root + "/" + f.relativeTo(dir).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(rel).apply { time = f.lastModified() })
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    files++; bytes += f.length()
                }
            }
        }
        LogBus.log("[BACKUP] full backup written: $files files, ${bytes / 1024} KB" + if (skipped > 0) ", $skipped large file(s) skipped" else "")
        val mb = String.format(Locale.getDefault(), "%.1f", bytes / 1048576.0)
        val res = ctx.resources
        return Result(true, res.getQuantityString(R.plurals.fmt_backup_saved, files, files, mb) +
            if (skipped > 0) " " + res.getQuantityString(R.plurals.fmt_backup_skipped, skipped, skipped) else "")
    }

    /** Checks the zip is one of ours and returns its manifest (or an error). */
    fun inspect(ctx: Context, uri: Uri): Pair<JSONObject?, String?> = try {
        ctx.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null to "Can't open the file"
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (e.name == "manifest.json") {
                        val m = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        return if (m.optString("format") == FORMAT) m to null else null to "Not a RideScreen AA full backup"
                    }
                    e = zip.nextEntry
                }
            }
        }
        null to "Not a RideScreen AA full backup (no manifest)"
    } catch (e: Exception) { null to "Can't read the backup: ${e.message}" }

    /**
     * Replaces the app's settings, files and databases with the backup's, then the caller restarts
     * the process right away (in-memory preferences would otherwise write old values back).
     */
    fun restore(ctx: Context, uri: Uri): Result {
        val data = File(ctx.applicationInfo.dataDir).canonicalFile
        val (manifest, error) = inspect(ctx, uri)
        if (manifest == null) return Result(false, error ?: "Not a backup")
        var files = 0
        ctx.contentResolver.openInputStream(uri).use { input ->
            ZipInputStream((input ?: return Result(false, "Can't open the file")).buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    val allowed = !e.isDirectory && ROOTS.any { name.startsWith("$it/") }
                    if (allowed) {
                        val target = File(data, name).canonicalFile
                        // Never write outside the app's own data folder.
                        if (target.path.startsWith(data.path + File.separator)) {
                            target.parentFile?.mkdirs()
                            val tmp = File(target.path + ".restoring")
                            tmp.outputStream().use { zip.copyTo(it) }
                            if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
                            files++
                        }
                    }
                    e = zip.nextEntry
                }
            }
        }
        LogBus.log("[BACKUP] full backup restored: $files files (from ${manifest.optString("appVersion")})")
        return Result(true, "Restored $files files from the backup of ${manifest.optString("appVersion")}. The app restarts now.")
    }

    /** Restart cleanly so every screen and service reads the restored data. */
    fun restart(ctx: Context) {
        ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let { i ->
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ctx.startActivity(i)
        }
        Runtime.getRuntime().exit(0)
    }
}
