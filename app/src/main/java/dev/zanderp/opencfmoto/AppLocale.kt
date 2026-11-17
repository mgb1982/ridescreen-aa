// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * In-app language. Empty means follow the phone language (Thai on a Thai Galaxy, English otherwise).
 * AppCompat persists the choice; this does not touch the bike link.
 */
object AppLocale {
    const val SYSTEM = ""
    const val THAI = "th"
    const val ENGLISH = "en"

    fun currentTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return SYSTEM
        return locales[0]?.language ?: SYSTEM
    }

    fun apply(tag: String) {
        val list = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(list)
    }

    fun chipLabel(context: Context): String = when (currentTag()) {
        THAI -> context.getString(R.string.lang_thai)
        ENGLISH -> context.getString(R.string.lang_english)
        else -> context.getString(R.string.lang_chip_system)
    }
}
