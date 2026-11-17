// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Large Thai / English / follow-system choices. Glove-sized buttons live in [R.layout.dialog_language]. */
object LanguagePicker {
    fun bind(activity: AppCompatActivity, button: MaterialButton) {
        button.text = AppLocale.chipLabel(activity)
        button.setOnClickListener { show(activity) }
    }

    fun show(activity: AppCompatActivity) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_language, null)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.lang_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        view.findViewById<MaterialButton>(R.id.lang_system).setOnClickListener {
            AppLocale.apply(AppLocale.SYSTEM)
            dialog.dismiss()
        }
        view.findViewById<MaterialButton>(R.id.lang_th).setOnClickListener {
            AppLocale.apply(AppLocale.THAI)
            dialog.dismiss()
        }
        view.findViewById<MaterialButton>(R.id.lang_en).setOnClickListener {
            AppLocale.apply(AppLocale.ENGLISH)
            dialog.dismiss()
        }
        dialog.show()
    }
}
