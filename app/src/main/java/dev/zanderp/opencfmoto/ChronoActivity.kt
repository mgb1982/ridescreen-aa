// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import java.text.DateFormat
import java.util.Date

/** 0-60 / 0-100 km/h timer: arm, manual Go, last result, records and the last 20 runs. */
class ChronoActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var last: TextView
    private lateinit var action: MaterialButton
    private lateinit var cancel: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chrono)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.chrono_root)) { v, insets ->
            val b = insets.getInsets(SafeInsets.BARS)
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        status = findViewById(R.id.chrono_status)
        last = findViewById(R.id.chrono_last)
        action = findViewById(R.id.chrono_action)
        cancel = findViewById(R.id.chrono_cancel)

        action.setOnClickListener {
            val err = when (ChronoController.state.phase) {
                ChronoPhase.ARMED -> ChronoController.go(this)
                ChronoPhase.IDLE, ChronoPhase.DONE, ChronoPhase.CANCELLED -> ChronoController.arm(this)
                else -> null
            }
            if (err != null) Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
        }
        cancel.setOnClickListener { ChronoController.cancel() }
        findViewById<MaterialButton>(R.id.chrono_clear).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.chr_clear_title)
                .setMessage(R.string.chr_clear_msg)
                .setPositiveButton(R.string.chr_clear_yes) { _, _ -> ChronoStore.clear(this); refresh() }
                .setNegativeButton(R.string.chr_clear_no, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        ChronoController.onChange = { refresh() }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        if (ChronoController.onChange != null) ChronoController.onChange = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun fmtRun(r: ChronoRun): String {
        val src = getString(if (r.gps) R.string.chr_source_gps else R.string.chr_source_obd)
        val parts = listOfNotNull(
            r.t1?.let { "0–${ZeroToSpeed.target1Label(r.imperial)}: ${getString(R.string.chr_seconds_fmt, ZeroToSpeed.fmtTime(it))}" },
            r.t2?.let { "0–${ZeroToSpeed.target2Label(r.imperial)}: ${getString(R.string.chr_seconds_fmt, ZeroToSpeed.fmtTime(it))}" },
        )
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.atMs))
        return "$date · ${parts.joinToString("  ")} · $src"
    }

    private fun refresh() {
        val s = ChronoController.state
        val imperial = if (s.phase == ChronoPhase.IDLE) Fmt.imperial(this) else s.imperial
        val running = s.phase == ChronoPhase.COUNTDOWN || s.phase == ChronoPhase.WAITING_LAUNCH || s.phase == ChronoPhase.RUNNING
        if (running) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        status.text = when (s.phase) {
            ChronoPhase.IDLE -> getString(R.string.chr_state_idle)
            ChronoPhase.ARMED -> getString(R.string.chr_state_armed)
            ChronoPhase.COUNTDOWN -> getString(R.string.chr_state_countdown) + " " + s.count
            ChronoPhase.WAITING_LAUNCH -> getString(R.string.chr_state_waiting)
            ChronoPhase.RUNNING -> getString(R.string.chr_state_running)
            ChronoPhase.DONE -> getString(R.string.chr_state_done)
            ChronoPhase.CANCELLED -> getString(when (s.reason) {
                ChronoReason.MOVED -> R.string.chr_cancel_moved
                ChronoReason.ROLLING -> R.string.chr_cancel_rolling
                ChronoReason.TIMEOUT -> R.string.chr_cancel_timeout
                else -> R.string.chr_cancel_user
            })
        }
        action.text = getString(if (s.phase == ChronoPhase.ARMED) R.string.chr_btn_go else R.string.chr_btn_arm)
        action.isEnabled = !running
        cancel.visibility = if (s.phase == ChronoPhase.IDLE || s.phase == ChronoPhase.DONE || s.phase == ChronoPhase.CANCELLED) View.GONE else View.VISIBLE

        last.text = if (s.phase == ChronoPhase.DONE || s.t1 != null) listOfNotNull(
            s.t1?.let { "0–${ZeroToSpeed.target1Label(imperial)}  ${ZeroToSpeed.fmtTime(it)} s" },
            s.t2?.let { "0–${ZeroToSpeed.target2Label(imperial)}  ${ZeroToSpeed.fmtTime(it)} s" },
        ).joinToString("\n") else ""
        val newRec = ChronoController.lastRecords
        findViewById<View>(R.id.chrono_record_flag).visibility =
            if (s.phase == ChronoPhase.DONE && (newRec.first || newRec.second)) View.VISIBLE else View.GONE

        findViewById<View>(R.id.chrono_gps).visibility = if (ChronoController.gpsSource) View.VISIBLE else View.GONE
        findViewById<View>(R.id.chrono_dash_note).visibility = if (DashboardMode.wanted) View.GONE else View.VISIBLE

        val runs = ChronoStore.load(this)
        fun rec(second: Boolean): String {
            val t = ChronoHistory.best(runs, imperial, second)
            val label = if (second) ZeroToSpeed.target2Label(imperial) else ZeroToSpeed.target1Label(imperial)
            return "0–$label: " + (t?.let { getString(R.string.chr_seconds_fmt, ZeroToSpeed.fmtTime(it)) } ?: "–")
        }
        findViewById<TextView>(R.id.chrono_records).text = getString(R.string.chr_record) + "\n" + rec(false) + "\n" + rec(true)
        findViewById<TextView>(R.id.chrono_history).text =
            if (runs.isEmpty()) getString(R.string.chr_history_empty) else runs.joinToString("\n") { fmtRun(it) }
    }
}
