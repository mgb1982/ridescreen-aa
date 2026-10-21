// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Diagnostic only: catches ANY throwable (including Errors such as VerifyError / NoClassDefFoundError)
 * that kills the process before [CrashGuard] is installed, and offers it through the system share
 * sheet so a launch crash can be read on a phone without adb.
 */
object EarlyCrash {
    fun install(app: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val text = "RideScreen AA early crash (thread=${thread.name})\n" + sw.toString().take(30_000)
                try { app.filesDir.resolve("early_crash.txt").writeText(text) } catch (_: Throwable) {}
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                val chooser = Intent.createChooser(send, "RideScreen AA crash").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(chooser)
                Thread.sleep(1200)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
