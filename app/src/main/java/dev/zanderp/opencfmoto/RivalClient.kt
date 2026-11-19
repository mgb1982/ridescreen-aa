// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/**
 * The official bike apps (CFMoto, Zontes Smart, Carbit Ride…). They bind the same mirroring-link ports the
 * bike calls back on (10920-10922), so when one is running the bike connects to *it* instead of us and the
 * dash stays blank ([EasyConnProber] detects the failed bind and surfaces this). Until 14 Nov 2026 only the
 * CFMoto app was known, so a Zontes rider (Thailand, 150X) got a dialog about "CFMoto" whose buttons did
 * nothing for his app.
 *
 * Android has no public API to force-stop another app, so the best we can do is:
 *   1. [closeBestEffort] — ask the OS to kill its *background* process (needs KILL_BACKGROUND_PROCESSES;
 *      unreliable across OEMs and useless if the app is foregrounded), then
 *   2. [openAppInfo] — jump to its App-info screen where the user taps the guaranteed **Force stop**.
 */
object RivalClient {
    const val PKG = "com.cfmoto.cfmotointernational"

    /** Set by EasyConnProber when the link ports are taken; MainActivity shows the close-app dialog on it. */
    @Volatile var portsHeld = false

    /**
     * Known official apps, in the order they are offered. Each one needs a `<queries>` entry in the manifest
     * (Android 11+ package visibility). Zontes packages come from the Zontes Smart EU / CN APKs; the Carbit Ride
     * id is the one a third-party listing gives and is unverified (harmless if wrong: it just is never "installed").
     */
    val PACKAGES = listOf(
        "tayo.com.ZontesIntelligence",   // Zontes Smart (EU / Thailand build)
        "com.tayo.msbox",                // Zontes Smart (CN)
        PKG,                             // CFMoto
        "com.cfmoto.motoplay",           // CFMoto MotoPlay
        "net.easyconn.carman",           // Carbit Ride (id seen in riders' phones by MOTO-HUB)
        "com.carbit.easyconnect",        // Carbit EasyConnect
        "net.easyconn.easyride.wws",     // Carbit Ride (older unverified id, kept: harmless if absent)
    )

    /** Names of the installed official apps, as the phone shows them ("Zontes Smart", "CFMOTO"…). */
    fun installedLabels(ctx: Context): List<String> = installed(ctx).map { pkg ->
        try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg }
    }

    /** The known apps that are installed on this phone. */
    fun installed(ctx: Context): List<String> = PACKAGES.filter { isInstalled(ctx, it) }

    fun isInstalled(ctx: Context): Boolean = installed(ctx).isNotEmpty()

    private fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** The name the phone shows for the first installed official app, or null if none is installed. */
    fun firstLabel(ctx: Context): String? {
        val pkg = installed(ctx).firstOrNull() ?: return null
        return try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg }
    }

    /** Best-effort kill of every installed official app's background process. Returns false if we couldn't even try. */
    fun closeBestEffort(ctx: Context): Boolean = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val found = installed(ctx)
        found.forEach { am.killBackgroundProcesses(it) }
        found.isNotEmpty()
    } catch (_: Exception) {
        false
    }

    /** Open the first installed official app's system App-info page so the user can hit the guaranteed Force stop button. */
    fun openAppInfo(ctx: Context): Boolean = try {
        val pkg = installed(ctx).firstOrNull() ?: return false
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (_: Exception) {
        false
    }
}
