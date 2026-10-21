// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
// Ported from the ionutradu252/open-cfmoto fork.
package dev.zanderp.opencfmoto

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Remap what each handlebar gesture does, and keep the destinations a button can navigate to. Rows
 * are built from [ButtonGesture]'s entries, and everything is read live by [MediaButtonBridge] on
 * each press — no reconnect needed to try a change.
 */
class ButtonMappingActivity : AppCompatActivity() {

    private val placeNames = mutableListOf<EditText>()
    private val placeQueries = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_button_mapping)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mapping_root)) { v, insets ->
            val b = insets.getInsets(SafeInsets.BARS)
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        buildGestureRows()
        buildPlaceRows()

        findViewById<MaterialButton>(R.id.btn_cluster_preset).setOnClickListener { pickClusterPreset() }
        findViewById<MaterialButton>(R.id.btn_cluster_clear).setOnClickListener { confirmClearPreset() }
        findViewById<TextView>(R.id.tv_bt_status).setOnClickListener {
            BluetoothHelper.openBluetoothSettings(this)
        }
        findViewById<MaterialButton>(R.id.btn_teach_handlebar).setOnClickListener { teachHandlebar() }
        findViewById<MaterialButton>(R.id.remote_pad_on).setOnClickListener { setRemotePad(true) }
        findViewById<MaterialButton>(R.id.remote_pad_off).setOnClickListener { setRemotePad(false) }
        findViewById<MaterialButton>(R.id.btn_teach_remote).setOnClickListener { teachRemote() }
        findViewById<MaterialButton>(R.id.btn_dedicated_pick).setOnClickListener { pickDedicatedRemote() }
        setupExternalControl()

        findViewById<MaterialButton>(R.id.btn_overlay).setOnClickListener {
            try {
                startActivity(NavLauncher.overlayPermissionIntent(this))
            } catch (e: Exception) {
                LogBus.log("couldn't open the overlay permission screen ($e)")
                Toast.makeText(this, "Open Settings → Apps → OpenCfMoto → Display over other apps",
                    Toast.LENGTH_LONG).show()
            }
        }

        findViewById<MaterialButton>(R.id.btn_reset).setOnClickListener { confirmReset() }
    }

    override fun onResume() {
        super.onResume()
        refreshDedicated()
        refresh()
    }

    /** Saved places are free text, so commit them whenever the screen goes away. */
    override fun onPause() {
        savePlaces()
        super.onPause()
    }

    // ─────────────────────────── gestures ───────────────────────────

    private fun buildGestureRows() {
        val container = findViewById<LinearLayout>(R.id.mapping_container)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (gesture in ButtonGesture.entries) {
            val row = inflater.inflate(R.layout.row_button_mapping, container, false)
            row.tag = gesture
            row.findViewById<TextView>(R.id.tv_gesture).text = gesture.label
            row.findViewById<TextView>(R.id.tv_hint).text = gesture.hint
            row.setOnClickListener { pickAction(gesture) }
            container.addView(row)
        }
    }

    private fun pickAction(gesture: ButtonGesture) {
        val actions = ButtonAction.entries
        val labels = actions.map { it.displayLabel(this) }.toTypedArray()
        val current = actions.indexOf(ButtonMap.get(this, gesture))
        MaterialAlertDialogBuilder(this)
            .setTitle(gesture.label)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val action = actions[which]
                ButtonMap.set(this, gesture, action)
                LogBus.log("→ button mapping: ${gesture.label} = ${action.label}")
                if (action.isNav && !SavedPlaces.isSet(this, action.navSlot)) {
                    Toast.makeText(this, "Set that place's address below", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─────────────────────────── saved places ───────────────────────────

    private fun buildPlaceRows() {
        val container = findViewById<LinearLayout>(R.id.places_container)
        container.removeAllViews()
        placeNames.clear()
        placeQueries.clear()
        val inflater = LayoutInflater.from(this)
        for (slot in 0 until SavedPlaces.COUNT) {
            val row = inflater.inflate(R.layout.row_saved_place, container, false)
            val name = row.findViewById<EditText>(R.id.et_place_name)
            val query = row.findViewById<EditText>(R.id.et_place_query)
            name.setText(SavedPlaces.name(this, slot))
            query.setText(SavedPlaces.query(this, slot))
            name.hint = "Name (e.g. Home)"
            query.hint = "Address or place"
            placeNames += name
            placeQueries += query
            container.addView(row)
        }
    }

    private fun savePlaces() {
        for (slot in 0 until SavedPlaces.COUNT) {
            SavedPlaces.set(
                this, slot,
                placeNames.getOrNull(slot)?.text?.toString() ?: "",
                placeQueries.getOrNull(slot)?.text?.toString() ?: "",
            )
        }
    }

    // ─────────────────────────── state ───────────────────────────

    private fun refresh() {
        savePlaces()   // so a just-typed name shows up in the action labels below
        val container = findViewById<LinearLayout>(R.id.mapping_container)
        var navMapped = false
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i)
            val gesture = row.tag as? ButtonGesture ?: continue
            val action = ButtonMap.get(this, gesture)
            row.findViewById<TextView>(R.id.tv_action).text = action.displayLabel(this)
            if (action.isNav) navMapped = true
        }

        // The overlay grant only matters once a button can actually launch Maps.
        findViewById<MaterialCardView>(R.id.card_overlay).visibility =
            if (navMapped && !NavLauncher.canLaunchFromBackground(this)) View.VISIBLE else View.GONE

        findViewById<MaterialButton>(R.id.btn_reset).isEnabled = !ButtonMap.isAllDefault(this)
        val active = ButtonClusterPreset.active(this)
        findViewById<TextView>(R.id.tv_cluster_active).text =
            if (active != null) "Active: ${active.title}"
            else "No preset — shipped defaults / your custom map"
        findViewById<MaterialButton>(R.id.btn_cluster_clear).isEnabled = active != null
        findViewById<TextView>(R.id.tv_bt_status).text = BluetoothHelper.status(this).shortLine()
        findViewById<TextView>(R.id.tv_presence).text =
            "Handlebar sources: ${ButtonPresencePrefs.summarize(this)}"
        highlightOnOff(R.id.remote_pad_on, R.id.remote_pad_off, RemotePad.enabled(this))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (RemotePad.consume(this, event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        if (RemotePad.teachListener != null) RemotePad.teachListener = null
        super.onDestroy()
    }

    private fun setRemotePad(on: Boolean) {
        RemotePad.setEnabled(this, on)
        LogBus.log("→ remote pad ${if (on) "on" else "off"}")
        refresh()
        Toast.makeText(
            this,
            if (on) "Remote pad on — HID keys map to handlebar gestures"
            else "Remote pad off",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun teachRemote() {
        if (!RemotePad.enabled(this)) {
            Toast.makeText(this, "Turn remote pad on first", Toast.LENGTH_SHORT).show()
            return
        }
        val gestures = ButtonGesture.entries
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.buttons_teach_remote_pick)
            .setItems(gestures.map { it.label }.toTypedArray()) { _, which ->
                waitForRemoteKey(gestures[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun waitForRemoteKey(gesture: ButtonGesture) {
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.buttons_teach_remote_wait)
            .setMessage(gesture.label)
            .setNegativeButton(android.R.string.cancel) { _, _ -> RemotePad.teachListener = null }
            .setOnDismissListener { RemotePad.teachListener = null }
            .create()
        RemotePad.teachListener = listen@{ ev ->
            if (ev.device?.isVirtual == true) return@listen false
            if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
                RemotePad.teach(this, ev.keyCode, gesture)
                RemotePad.teachListener = null
                dlg.dismiss()
                Toast.makeText(
                    this,
                    getString(
                        R.string.buttons_teach_remote_ok,
                        KeyEvent.keyCodeToString(ev.keyCode),
                        gesture.label,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                LogBus.log("→ remote pad teach: ${KeyEvent.keyCodeToString(ev.keyCode)} → ${gesture.label}")
            }
            true
        }
        dlg.show()
    }

    private fun highlightOnOff(onId: Int, offId: Int, on: Boolean) {
        val onColor = androidx.core.content.ContextCompat.getColor(this, R.color.brand_orange)
        val onText = androidx.core.content.ContextCompat.getColor(this, R.color.on_brand)
        val offColor = androidx.core.content.ContextCompat.getColor(this, R.color.surface_high)
        val offText = androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)
        listOf(onId to true, offId to false).forEach { (id, value) ->
            val btn = findViewById<MaterialButton>(id)
            val selected = value == on
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (selected) onColor else offColor,
            )
            btn.setTextColor(if (selected) onText else offText)
        }
    }

    private fun teachHandlebar() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.buttons_teach_title)
            .setMessage(R.string.buttons_teach_message)
            .setPositiveButton(R.string.buttons_teach_volume_present) { _, _ ->
                ButtonPresencePrefs.setVolumeRocker(this, ButtonPresence.PRESENT)
                LogBus.log("→ teach handlebar: volume rocker PRESENT")
                MediaButtonBridge.instance?.refreshVolumePresencePolicy()
                refresh()
                Toast.makeText(this, "▲/▼ marked present — volume pin stays on", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.buttons_teach_volume_absent) { _, _ ->
                ButtonPresencePrefs.setVolumeRocker(this, ButtonPresence.ABSENT)
                LogBus.log("→ teach handlebar: volume rocker ABSENT")
                MediaButtonBridge.instance?.refreshVolumePresencePolicy()
                refresh()
                Toast.makeText(this, "▲/▼ absent — phone volume no longer pinned", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.buttons_teach_reset) { _, _ ->
                ButtonPresencePrefs.setVolumeRocker(this, ButtonPresence.UNKNOWN)
                ButtonPresencePrefs.setTrackKeys(this, ButtonPresence.UNKNOWN)
                LogBus.log("→ teach handlebar: presence reset to UNKNOWN")
                MediaButtonBridge.instance?.refreshVolumePresencePolicy()
                refresh()
                Toast.makeText(this, "Presence reset — will auto-probe again", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun pickClusterPreset() {
        val presets = ButtonClusterPreset.entries
        val labels = presets.map { it.title }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Left switch cluster")
            .setItems(labels) { _, which ->
                val preset = presets[which]
                MaterialAlertDialogBuilder(this)
                    .setTitle(preset.title)
                    .setMessage(preset.summary + "\n\nReplace the current mapping for this bike? Saved places are kept.")
                    .setPositiveButton("Apply") { _, _ ->
                        preset.apply(this)
                        LogBus.log("→ button mapping preset: ${preset.title}")
                        refresh()
                        // Cluster only picks the bar map + turns handlebar→AA on. Touch stays as-is
                        // (800MT etc. keep touch + bars together — never force Disable touchscreen).
                        Toast.makeText(
                            this,
                            "Applied: ${preset.title} (handlebar → AA on)",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClearPreset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear cluster preset?")
            .setMessage(
                "Removes the active cluster tag and restores shipped gesture defaults. " +
                    "Saved places stay. Some bikes work fine with no preset — just tweak rows below if needed."
            )
            .setPositiveButton("Clear") { _, _ ->
                ButtonClusterPreset.clear(this)
                LogBus.log("→ button mapping preset cleared")
                refresh()
                Toast.makeText(this, "Preset cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset to defaults?")
            .setMessage(
                "Every gesture goes back to the shipped defaults: ◀/▶ = knob, " +
                    "★ = Select, ★ hold = Home, ◀◀/▶▶ = D-pad ←→, ★★ = Back.\n\n" +
                    "Does not change which cluster preset is active. Use Clear preset to drop the tag."
            )
            .setPositiveButton("Reset") { _, _ ->
                ButtonMap.resetAll(this)
                LogBus.log("→ button mapping reset to defaults")
                refresh()
                Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- v2.3: dedicated remote (Android Auto only) ----
    private fun refreshDedicated() {
        val tv = findViewById<TextView?>(R.id.tv_dedicated_status) ?: return
        // The card now hosts "remote with Key Mapper / Tasker"; the built-in picker returns when a
        // key source that needs no accessibility exists (see DedicatedRemote.available).
        val avail = DedicatedRemote.available(this)
        tv.visibility = if (avail) View.VISIBLE else View.GONE
        findViewById<View?>(R.id.btn_dedicated_pick)?.visibility = if (avail) View.VISIBLE else View.GONE
        if (!avail) return
        val r = DedicatedRemote.chosen(this)
        tv.text = if (r == null) getString(R.string.remote_dedicated_none) else getString(R.string.remote_dedicated_ready, r.name)
    }

    private fun setupExternalControl() {
        val sw = findViewById<com.google.android.material.materialswitch.MaterialSwitch?>(R.id.sw_external_control) ?: return
        val list = ExternalControl.ACTIONS.keys.joinToString("\n") { ExternalControl.PREFIX + it }
        findViewById<TextView?>(R.id.tv_external_actions)?.text = list
        sw.isChecked = AppSettings.externalControl(this)
        sw.setOnCheckedChangeListener { _, on ->
            AppSettings.setExternalControl(this, on)
            if (on) ExternalControl.start(this) else ExternalControl.stop(this)
        }
        findViewById<MaterialButton?>(R.id.btn_external_copy)?.setOnClickListener {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("RideScreen actions", list))
            android.widget.Toast.makeText(this, R.string.ext_control_copied, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickDedicatedRemote() {
        val devs = DedicatedRemote.candidates(this)
        val current = DedicatedRemote.chosen(this)
        if (devs.isEmpty()) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.remote_dedicated_title)
                .setMessage(R.string.remote_dedicated_no_devices)
                .setPositiveButton(android.R.string.ok, null)
                .apply { if (current != null) setNegativeButton(R.string.remote_dedicated_remove) { _, _ -> DedicatedRemote.clear(this@ButtonMappingActivity); refreshDedicated() } }
                .show()
            return
        }
        val names = devs.map { it.name }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remote_dedicated_pick)
            .setItems(names) { _, i ->
                DedicatedRemote.choose(this, devs[i])
                refreshDedicated()
            }
            .apply { if (current != null) setNegativeButton(R.string.remote_dedicated_remove) { _, _ -> DedicatedRemote.clear(this@ButtonMappingActivity); refreshDedicated() } }
            .show()
    }
}

/** True for the navigate-to-a-saved-place actions. */
private val ButtonAction.isNav: Boolean
    get() = this == ButtonAction.NAV_1 || this == ButtonAction.NAV_2 || this == ButtonAction.NAV_3

/** Which [SavedPlaces] slot a nav action points at. */
private val ButtonAction.navSlot: Int
    get() = when (this) {
        ButtonAction.NAV_1 -> 0
        ButtonAction.NAV_2 -> 1
        else -> 2
    }
