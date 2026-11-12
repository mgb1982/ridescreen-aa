// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * One full-screen black overlay at minimum brightness that hides the (still lit) phone screen while the
 * map runs on the dash from a pocket. Touchable: a double tap removes it and [PocketDim.TOUCH_PAUSE_MS] must pass
 * before the watchdog may cover again. Main thread only (calls from elsewhere are posted).
 */
object PocketCover {
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var dismissedAtMs = Long.MIN_VALUE / 4
    private var lastTapAtMs = Long.MIN_VALUE / 4
    private const val DOUBLE_TAP_MS = 600L

    val covering: Boolean get() = view != null

    fun sinceDismissMs(): Long =
        if (dismissedAtMs == Long.MIN_VALUE / 4) Long.MAX_VALUE / 4
        else SystemClock.elapsedRealtime() - dismissedAtMs

    fun show(ctx: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { show(ctx) }
            return
        }
        if (view != null) return
        val app = ctx.applicationContext
        try {
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val root = FrameLayout(app).apply {
                setBackgroundColor(Color.BLACK)
                // Double tap, not a single one: in a pocket the cloth or a leg touches the screen all the time,
                // and a single tap would bring the full brightness back on every brush.
                setOnClickListener {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastTapAtMs <= DOUBLE_TAP_MS) {
                        lastTapAtMs = Long.MIN_VALUE / 4
                        dismissedAtMs = now
                        hide("double tap")
                    } else {
                        lastTapAtMs = now
                    }
                }
                addView(
                    TextView(app).apply {
                        text = app.getString(R.string.pocket_hint)
                        setTextColor(Color.parseColor("#3A3A3A"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        gravity = Gravity.CENTER
                    },
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    ),
                )
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE,
            ).apply { screenBrightness = 0.01f }
            wm.addView(root, lp)
            view = root
            LogBus.log("[POCKET] covering the screen at minimum brightness (app in background ${AppVisibility.backgroundForMs() / 1000}s)")
        } catch (e: Exception) {
            LogBus.log("[POCKET] cover failed: $e")
        }
    }

    fun hide(reason: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { hide(reason) }
            return
        }
        val v = view ?: return
        view = null
        try {
            val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (e: Exception) {
            LogBus.log("[POCKET] remove failed: $e")
        }
        LogBus.log("[POCKET] uncovered: $reason")
    }
}
