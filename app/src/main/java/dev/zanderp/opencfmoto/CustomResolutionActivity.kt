// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

/**
 * Match-panel-aspect page: Android Auto reflows to the dash panel's aspect via AAP margins, then the
 * compositor crops to the usable area. Default is Auto (learned panel size / profile); Manual lets
 * the rider type a size; Off restores letterbox/crop. Applies on the next connect.
 */
class CustomResolutionActivity : AppCompatActivity() {

    private var mode = MatchAspectMode.AUTO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_resolution)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.custom_res_root)) { v, insets ->
            val b = insets.getInsets(SafeInsets.BARS)
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        mode = VideoPrefs.matchAspectMode(this)
        val (mw, mh) = VideoPrefs.aspectTarget(this)
        findViewById<EditText>(R.id.match_w).setText(mw.toString())
        findViewById<EditText>(R.id.match_h).setText(mh.toString())

        findViewById<MaterialButton>(R.id.match_auto).setOnClickListener {
            mode = MatchAspectMode.AUTO
            refresh()
        }
        findViewById<MaterialButton>(R.id.match_off).setOnClickListener {
            mode = MatchAspectMode.OFF
            refresh()
        }
        findViewById<MaterialButton>(R.id.match_manual).setOnClickListener {
            mode = MatchAspectMode.MANUAL
            refresh()
        }
        findViewById<MaterialButton>(R.id.btn_custom_res_save).setOnClickListener { save() }
        findViewById<MaterialButton>(R.id.btn_custom_res_done).setOnClickListener { save(); finish() }

        listOf(R.id.match_w, R.id.match_h).forEach { id ->
            findViewById<EditText>(id).addTextChangedListener(SimpleWatcher { refreshMatchNote() })
        }
        refresh()
    }

    private fun readInt(id: Int, def: Int): Int =
        findViewById<EditText>(id).text.toString().trim().toIntOrNull() ?: def

    private fun save() {
        val (mw0, mh0) = VideoPrefs.aspectTarget(this)
        VideoPrefs.setMatchAspect(
            this,
            mode,
            readInt(R.id.match_w, mw0),
            readInt(R.id.match_h, mh0),
        )
        Toast.makeText(this, "Saved (applies next connect)", Toast.LENGTH_SHORT).show()
    }

    private fun refresh() {
        highlight(
            mode,
            R.id.match_auto to MatchAspectMode.AUTO,
            R.id.match_off to MatchAspectMode.OFF,
            R.id.match_manual to MatchAspectMode.MANUAL,
        )
        val manualRow = findViewById<View>(R.id.match_size_row)
        manualRow.visibility = if (mode == MatchAspectMode.MANUAL) View.VISIBLE else View.GONE
        refreshMatchNote()
    }

    /** Show detected panel + the margins/usable size for the current AA coded resolution. */
    private fun refreshMatchNote() {
        val detected = VideoPrefs.detectedPanelSize(this)
        val detectedNote = if (detected != null) {
            getString(R.string.res_detected_panel, detected.first, detected.second)
        } else {
            getString(R.string.res_no_panel_size_yet)
        }

        val coded = VideoPrefs.resolution(this).spec ?: BikeProfileHolder.aaVideo
        val panel = when (mode) {
            MatchAspectMode.OFF -> null
            MatchAspectMode.AUTO -> detected
            MatchAspectMode.MANUAL -> {
                val (mw0, mh0) = VideoPrefs.aspectTarget(this)
                readInt(R.id.match_w, mw0) to readInt(R.id.match_h, mh0)
            }
        }
        val marginNote = if (panel == null) {
            if (mode == MatchAspectMode.OFF) {
                getString(R.string.res_match_aspect_off_note)
            } else {
                getString(R.string.res_no_margins_until_panel)
            }
        } else {
            val m = AaMargins.forAspect(coded, panel.first, panel.second)
            val uw = coded.width - m.marginW
            val uh = coded.height - m.marginH
            if (!m.any) {
                getString(R.string.res_aa_matches_panel, coded.width, coded.height)
            } else {
                getString(
                    R.string.res_aa_coded_margins,
                    coded.width,
                    coded.height,
                    m.marginW,
                    m.marginH,
                    uw,
                    uh,
                    "%.3f".format(uw.toDouble() / uh),
                )
            }
        }
        findViewById<TextView>(R.id.match_note).text = "$detectedNote\n$marginNote"
    }

    private fun <T> highlight(selected: T, vararg pairs: Pair<Int, T>) {
        val onColor = ContextCompat.getColor(this, R.color.brand_orange)
        val onText = ContextCompat.getColor(this, R.color.on_brand)
        val offColor = ContextCompat.getColor(this, R.color.surface_high)
        val offText = ContextCompat.getColor(this, R.color.text_primary)
        for ((id, value) in pairs) {
            val btn = findViewById<MaterialButton>(id)
            val on = value == selected
            btn.backgroundTintList = ColorStateList.valueOf(if (on) onColor else offColor)
            btn.setTextColor(if (on) onText else offText)
        }
    }

    private class SimpleWatcher(val onChange: () -> Unit) : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) = onChange()
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, CustomResolutionActivity::class.java))
        }
    }
}
