// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Scans the bike's information QR to fill in the McuID, instead of typing 32 hex digits by hand (the
 * official Zontes app reads the same QR). Reuses [QrScanActivity], extracts the McuID with
 * [PkeUnlock.mcuIdFromQr], and — because the QR's exact layout is inferred — shows the value masked for
 * the rider to confirm before saving. Only the McuID is taken; the rest of the QR is discarded, and
 * nothing (neither the raw QR nor the McuID) is written to the log.
 */
class PkeMcuScanActivity : AppCompatActivity() {
    private val scan = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val raw = res.data?.getStringExtra(QrScanActivity.RESULT_QR)
        if (res.resultCode != Activity.RESULT_OK || raw.isNullOrBlank()) { finish(); return@registerForActivityResult }
        val id = PkeUnlock.mcuIdFromQr(raw)
        if (id == null || !PkeUnlock.isValidMcuId(id)) {
            LogBus.log("[PKE-unlock] info QR scanned: no McuID found")
            Toast.makeText(this, R.string.pke_unlock_scan_not_found, Toast.LENGTH_LONG).show()
            finish(); return@registerForActivityResult
        }
        LogBus.log("[PKE-unlock] info QR scanned: McuID found")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pke_unlock_mcu_title)
            .setMessage(getString(R.string.pke_unlock_scan_confirm, masked(id)))
            .setPositiveButton(R.string.dest_save) { _, _ ->
                PkeUnlock.setMcuId(this, id)
                Toast.makeText(this, R.string.pke_unlock_scan_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton(R.string.dash_cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) scan.launch(Intent(this, QrScanActivity::class.java))
    }

    /** First 4 and last 4 of the 32 hex digits, enough to tell bikes apart without showing the whole key. */
    private fun masked(id: String): String = id.take(4) + "…" + id.takeLast(4)
}
