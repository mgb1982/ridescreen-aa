// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors

/** Help → "OBD (test)": pick the paired adapter, run [ObdProbe] and show what it reads, live. */
object ObdProbeUi {
    private const val REQ_BT_PERM = 7352
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, R.string.pke_need_nearby, Toast.LENGTH_LONG).show()
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQ_BT_PERM)
            return
        }
        val found = ObdProbe.candidates(activity)
        when {
            found.isEmpty() -> {
                val names = ObdProbe.pairedNames(activity)
                LogBus.log("[OBD] no paired adapter with an OBD-like name · paired: ${names.joinToString(", ").ifEmpty { "none" }}")
                MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.obd_title)
                    .setMessage(activity.getString(R.string.obd_no_adapter, names.joinToString(", ").ifEmpty { "—" }))
                    .apply {
                        if (names.isNotEmpty()) setNeutralButton(R.string.obd_pick_other) { _, _ -> pickAny(activity) }
                    }
                    .setPositiveButton(R.string.whats_new_ok, null)
                    .show()
            }
            found.size == 1 -> confirm(activity, found[0])
            else -> pick(activity, found)
        }
    }

    private fun pickAny(activity: Activity) {
        val adapter = activity.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter ?: return
        val all = runCatching { adapter.bondedDevices.toList() }.getOrDefault(emptyList())
        if (all.isNotEmpty()) pick(activity, all)
    }

    @Suppress("MissingPermission")
    private fun pick(activity: Activity, devices: List<BluetoothDevice>) {
        val names = devices.map { runCatching { it.name }.getOrNull() ?: "?" }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.obd_pick_title)
            .setItems(names) { _, i -> confirm(activity, devices[i]) }
            .setNegativeButton(R.string.dash_cancel, null)
            .show()
    }

    @Suppress("MissingPermission")
    private fun confirm(activity: Activity, device: BluetoothDevice) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.obd_title)
            .setMessage(activity.getString(R.string.obd_intro, runCatching { device.name }.getOrNull() ?: "?"))
            .setPositiveButton(R.string.obd_start) { _, _ -> run(activity, device) }
            .setNegativeButton(R.string.dash_cancel, null)
            .show()
    }

    private fun run(activity: Activity, device: BluetoothDevice) {
        val dp = activity.resources.displayMetrics.density
        val text = TextView(activity).apply {
            typeface = Typeface.MONOSPACE
            textSize = 15f
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), 0)
            setTextIsSelectable(true)
            setText(R.string.obd_connecting)
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.obd_title)
            .setView(ScrollView(activity).apply { addView(text) })
            .setCancelable(false)
            .setPositiveButton(R.string.whats_new_ok, null)
            .setNeutralButton(" ", null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.isEnabled = false
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.visibility = android.view.View.GONE
        val app = activity.applicationContext
        val name = runCatching { device.name }.getOrNull()
        io.execute {
            ObdLive.stop(wait = true)   // one client at a time on the adapter
            val res = try { ObdProbe.run(app, device) { line -> main.post { text.text = line } } }
                catch (e: Exception) { LogBus.log("[OBD] probe crashed: $e"); ObdProbe.Result(ObdProbe.Failure.NO_LINK, emptyList()) }
            main.post {
                val why = when (res.failure) {
                    ObdProbe.Failure.BLUETOOTH_OFF -> activity.getString(R.string.pke_bt_off)
                    ObdProbe.Failure.NO_ADAPTER, ObdProbe.Failure.NO_LINK -> activity.getString(R.string.obd_fail_link)
                    ObdProbe.Failure.NO_ECU -> activity.getString(R.string.obd_fail_ecu)
                    null -> null
                }
                text.text = listOfNotNull(why, res.summary.joinToString("\n").ifEmpty { null }, activity.getString(R.string.obd_share_hint))
                    .joinToString("\n\n")
                dialog.setCancelable(true)
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.isEnabled = true
                if (res.failure == null && name != null) offerLive(activity, dialog, name)
                else io.execute { ObdLive.start(app) }
            }
        }
    }

    /** After a good test: keep reading this adapter while projecting (or stop doing so). */
    private fun offerLive(activity: Activity, dialog: androidx.appcompat.app.AlertDialog, name: String) {
        val app = activity.applicationContext
        val btn = dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL) ?: return
        fun label() { btn.setText(if (AppSettings.obdLiveAdapter(app) == name) R.string.obd_live_off else R.string.obd_live_on) }
        label()
        btn.visibility = android.view.View.VISIBLE
        btn.setOnClickListener {
            val on = AppSettings.obdLiveAdapter(app) != name
            AppSettings.setObdLiveAdapter(app, if (on) name else null)
            LogBus.log("[OBD] continuous reading ${if (on) "on for '$name'" else "off"}")
            Toast.makeText(activity, activity.getString(if (on) R.string.obd_live_enabled else R.string.obd_live_disabled, name), Toast.LENGTH_LONG).show()
            label()
        }
        dialog.setOnDismissListener { io.execute { ObdLive.start(app) } }
    }
}
