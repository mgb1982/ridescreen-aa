// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import java.util.Locale

/**
 * Live ride HUD driven by the shared [TripRecorder] (via [TripLogger]). Shows GPS speed, distance,
 * moving time, and max/avg speed, and records automatically — the same recorder the foreground
 * service uses to auto-log projected rides, so opening this while riding just mirrors the live ride.
 *
 * Speed/distance come from the phone's GPS (the mirroring link carries no vehicle telemetry).
 */
class TripActivity : AppCompatActivity() {

    private lateinit var speedView: TextView
    private lateinit var gpsView: TextView
    private lateinit var distanceView: TextView
    private lateinit var durationView: TextView
    private lateinit var maxView: TextView
    private lateinit var avgView: TextView
    private lateinit var startBtn: MaterialButton
    private lateinit var resetBtn: MaterialButton

    private lateinit var recorder: TripRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_trip)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.trip_root)) { v, insets ->
            val b = insets.getInsets(SafeInsets.BARS)
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        speedView = findViewById(R.id.trip_speed)
        gpsView = findViewById(R.id.trip_gps)
        distanceView = findViewById(R.id.trip_distance)
        durationView = findViewById(R.id.trip_duration)
        maxView = findViewById(R.id.trip_max)
        avgView = findViewById(R.id.trip_avg)
        startBtn = findViewById(R.id.trip_start)
        resetBtn = findViewById(R.id.trip_reset)

        recorder = TripLogger.get(this)

        startBtn.setOnClickListener { if (recorder.recording) recorder.stopAndSave() else tryStart() }
        resetBtn.setOnClickListener { recorder.discard() }
        findViewById<MaterialButton>(R.id.trip_saved_btn).setOnClickListener { TripsListActivity.start(this) }

        render(recorder.snapshot())
    }

    override fun onResume() {
        super.onResume()
        recorder.onStats = { stats -> runOnUiThread { render(stats) } }
        render(recorder.snapshot())
        // Auto-start recording when the screen opens (unless it's already running, e.g. a projected
        // ride the service is logging), so trips log without a manual tap.
        if (!recorder.recording) tryStart(silent = true)
    }

    override fun onPause() {
        super.onPause()
        recorder.onStats = null
    }

    private fun tryStart(silent: Boolean = false) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOCATION,
            )
            return
        }
        if (!recorder.gpsAvailable()) {
            gpsView.text = "GPS is off"
            if (!silent) Toast.makeText(this, "Turn on GPS/Location to record", Toast.LENGTH_LONG).show()
            return
        }
        if (!recorder.start() && !silent) {
            Toast.makeText(this, "Couldn't start GPS", Toast.LENGTH_SHORT).show()
        }
    }

    private fun render(s: TripRecorder.Stats) {
        speedView.text = s.speedKmh.toString()
        gpsView.text = when {
            !s.recording -> "Stopped"
            !s.hasFix -> "Acquiring GPS…"
            else -> "GPS fix" + if (s.accuracyM > 0) " ±${s.accuracyM}m" else ""
        }
        distanceView.text = String.format(Locale.US, "%.2f km", s.distanceMeters / 1000.0)
        durationView.text = formatDuration(s.movingTimeMs)
        maxView.text = "${(s.maxSpeedMs * 3.6f).toInt()} km/h"
        val avgKmh = if (s.movingTimeMs > 0) (s.distanceMeters / (s.movingTimeMs / 1000.0)) * 3.6 else 0.0
        avgView.text = "${avgKmh.toInt()} km/h"
        startBtn.text = if (s.recording) getString(R.string.trip_pause) else getString(R.string.trip_start)
    }

    private fun formatDuration(ms: Long): String {
        val sec = (ms.coerceAtLeast(0)) / 1000
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) tryStart()
            else Toast.makeText(this, "Location permission is required for the trip HUD", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        recorder.onStats = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // A manual (non-projected) recording should be banked when the rider leaves; a projected ride
        // keeps logging in the foreground service.
        if (!AndroidAutoService.isRunning && recorder.recording) recorder.stopAndSave()
        super.onDestroy()
    }

    companion object {
        private const val REQ_LOCATION = 21

        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, TripActivity::class.java))
        }
    }
}
