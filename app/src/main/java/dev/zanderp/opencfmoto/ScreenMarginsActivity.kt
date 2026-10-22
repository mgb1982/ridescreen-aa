// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

/** Dedicated page to set top / bottom / left / right dash insets independently. */
class ScreenMarginsActivity : AppCompatActivity() {

    private var syncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_screen_margins)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.margins_root)) { v, insets ->
            val b = insets.getInsets(SafeInsets.BARS)
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        AppSettings.applyToHolder(this)
        ScreenMargins.load(this)

        bind(R.id.seek_margin_top, R.id.tv_margin_top) { ScreenMargins.top }
        bind(R.id.seek_margin_bottom, R.id.tv_margin_bottom) { ScreenMargins.bottom }
        bind(R.id.seek_margin_left, R.id.tv_margin_left) { ScreenMargins.left }
        bind(R.id.seek_margin_right, R.id.tv_margin_right) { ScreenMargins.right }
        refreshSummary()

        findViewById<MaterialButton>(R.id.btn_margins_reset).setOnClickListener {
            ScreenMargins.reset(this)
            syncFromPrefs()
            refreshSummary()
        }
        findViewById<MaterialButton>(R.id.btn_margins_done).setOnClickListener { finish() }
    }

    private fun bind(seekId: Int, valueId: Int, current: () -> Int) {
        val seek = findViewById<SeekBar>(seekId)
        val value = findViewById<TextView>(valueId)
        seek.max = ScreenMargins.MAX
        seek.progress = current()
        value.text = current().toString()
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                value.text = progress.toString()
                if (!fromUser || syncing) return
                persist()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) { persist() }
        })
    }

    private fun persist() {
        ScreenMargins.set(
            this,
            findViewById<SeekBar>(R.id.seek_margin_top).progress,
            findViewById<SeekBar>(R.id.seek_margin_bottom).progress,
            findViewById<SeekBar>(R.id.seek_margin_left).progress,
            findViewById<SeekBar>(R.id.seek_margin_right).progress,
        )
        refreshSummary()
    }

    private fun syncFromPrefs() {
        syncing = true
        try {
            fun apply(seekId: Int, valueId: Int, v: Int) {
                findViewById<SeekBar>(seekId).progress = v
                findViewById<TextView>(valueId).text = v.toString()
            }
            apply(R.id.seek_margin_top, R.id.tv_margin_top, ScreenMargins.top)
            apply(R.id.seek_margin_bottom, R.id.tv_margin_bottom, ScreenMargins.bottom)
            apply(R.id.seek_margin_left, R.id.tv_margin_left, ScreenMargins.left)
            apply(R.id.seek_margin_right, R.id.tv_margin_right, ScreenMargins.right)
        } finally {
            syncing = false
        }
    }

    private fun refreshSummary() {
        findViewById<TextView>(R.id.tv_margins_summary).text = when {
            !ScreenMargins.any -> getString(R.string.margins_summary_none)
            !ScreenMargins.customised -> getString(
                R.string.margins_summary_profile_default,
                ScreenMargins.top,
                ScreenMargins.bottom,
                ScreenMargins.left,
                ScreenMargins.right,
            )
            else -> getString(
                R.string.margins_summary_custom,
                ScreenMargins.top,
                ScreenMargins.bottom,
                ScreenMargins.left,
                ScreenMargins.right,
            )
        }
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, ScreenMarginsActivity::class.java))
        }
    }
}
