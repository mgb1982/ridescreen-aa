package dev.zanderp.opencfmoto

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

internal object HeartRateStore {
    private const val PREF = "wear_heart_rate"
    private const val MAX_AGE = 20_000L
    fun receive(ctx: Context, payload: String) {
        val p = payload.split(';')
        val bpm = p.getOrNull(0)?.toIntOrNull()?.takeIf { it in 30..240 } ?: return
        val at = p.getOrNull(1)?.toLongOrNull() ?: System.currentTimeMillis()
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt("bpm", bpm).putLong("at", at).apply()
    }
    fun show(ctx: Context) {
        val view = TextView(ctx).apply { textSize = 24f; setPadding(48, 24, 48, 8) }
        val dialog = AlertDialog.Builder(ctx).setTitle(R.string.help_heart_rate).setView(view).setPositiveButton(android.R.string.ok, null).create()
        val handler = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                val bpm = p.getInt("bpm", 0); val at = p.getLong("at", 0L)
                view.text = if (bpm > 0 && System.currentTimeMillis() - at < MAX_AGE) "$bpm BPM\n\n${ctx.getString(R.string.heart_rate_live)}" else ctx.getString(R.string.heart_rate_waiting)
                if (dialog.isShowing) handler.postDelayed(this, 1000)
            }
        }
        dialog.setOnDismissListener { handler.removeCallbacks(tick) }
        dialog.show(); handler.post(tick)
    }
}
