// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
// Adapted from ionutradu252/open-cflink.
package dev.zanderp.opencfmoto

/** Builds a self-contained problem-report text file (answers + diagnostics + log). */
object ProblemReport {

    private const val RULE = "════════════════════════════════════════════════════════"

    fun file(problem: String, model: String, year: String, diagnostics: String, log: String): String =
        buildString {
            appendLine(RULE)
            appendLine("RideScreen AA problem report")
            appendLine(RULE)
            appendLine()
            appendLine("WHAT WENT WRONG")
            appendLine(problem.trim())
            appendLine()
            appendLine("BIKE")
            appendLine(bikeLine(model, year))
            appendLine()
            appendLine("SETUP")
            appendLine(diagnostics.trim())
            appendLine()
            appendLine(RULE)
            appendLine("LOG — passwords and serials removed unless \"include secrets\" is on")
            appendLine(RULE)
            append(log)
        }

    fun body(problem: String, model: String, year: String, diagnostics: String): String =
        buildString {
            appendLine("### What went wrong")
            appendLine(problem.trim())
            appendLine()
            appendLine("### Bike")
            appendLine(bikeLine(model, year))
            appendLine()
            appendLine("### Setup")
            appendLine("```")
            appendLine(diagnostics.trim())
            appendLine("```")
            appendLine()
            append("Full log attached. Secrets redacted unless enabled in Setup.")
        }

    fun subject(model: String, version: String): String =
        "RideScreen AA ${bikeLine(model, "")} report — $version"

    private fun bikeLine(model: String, year: String): String {
        val m = model.trim().ifEmpty { "unknown model" }
        val y = year.trim()
        return if (y.isEmpty()) m else "$m, $y"
    }
}