// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Alexandru <https://alexandru.rocks> and the OpenCfMoto contributors.
// Part of OpenCfMoto. Free software under the GNU AGPL v3 or later; see LICENSE and NOTICE.
package dev.zanderp.opencfmoto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import dev.zanderp.opencfmoto.aa.AaInput

/**
 * "Cuadro": what the dash shows, live on the phone (Android Auto, dashboard or cards), with the selector
 * on top and the Mando (pad) below or beside it.
 *
 * Full-screen in-app dash: live AA video when connected, or Map / GPX phone preview when
 * [GpxSession] is active without a bike (debug / offline).
 */
class HudViewActivity : AppCompatActivity() {

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (RemotePad.consume(this, event)) return true
        return super.dispatchKeyEvent(event)
    }

    private lateinit var surface: SurfaceView
    private lateinit var gpxHost: FrameLayout
    private lateinit var hint: TextView
    private lateinit var titleView: TextView
    private lateinit var modeGroup: MaterialButtonToggleGroup
    private lateinit var body: LinearLayout
    private lateinit var controlsPanel: View
    private lateinit var pad: View
    private lateinit var padHandle: Button
    private var padOpen = false
    private var syncingMode = false
    // Touch gesture state (target is fixed at finger down).
    private var gestureTarget = CuadroLogic.Target.LEGACY
    private var downX = 0f
    private var downY = 0f
    private var dashMoved = false
    private val lastSrc = HashMap<Int, Pair<Int, Int>>()
    private var attached = false
    private var noSessionToastAt = 0L
    private var fullscreen = false
    private var gpxPreview = false
    private var gpxUi: GpxDashUi? = null
    private var boundSessionKey: String? = null
    private lateinit var insetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hud_view)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surface = findViewById(R.id.hud_surface)
        gpxHost = findViewById(R.id.hud_gpx_host)
        hint = findViewById(R.id.hud_hint)
        titleView = findViewById(R.id.hud_title)
        modeGroup = findViewById(R.id.hud_mode_group)
        body = findViewById(R.id.hud_body)
        controlsPanel = findViewById(R.id.hud_controls)
        pad = findViewById(R.id.hud_pad)
        padHandle = findViewById(R.id.hud_pad_handle)
        padOpen = intent.getBooleanExtra(EXTRA_OPEN_CONTROLS, false)
        applyPad()
        applyOrientation()

        insetsController = WindowInsetsControllerCompat(window, findViewById(R.id.hud_root)).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val topbar = findViewById<View>(R.id.hud_topbar)
        val baseTopPad = topbar.paddingTop
        val baseLeftPad = topbar.paddingLeft
        val baseRightPad = topbar.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(topbar) { v, insets ->
            val bars = insets.getInsets(
                SafeInsets.BARS or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(baseLeftPad + bars.left, baseTopPad + bars.top, baseRightPad + bars.right, v.paddingBottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreen) setFullscreen(false) else finish()
            }
        })

        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {}
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                if (gpxPreview) return
                val pipe = AaVideoBridge.pipeline
                if (pipe == null) {
                    LogBus.log("[HUD] no AA pipeline — preview will show once Android Auto is live")
                    return
                }
                if (!attached) {
                    pipe.setPreviewSurface(holder.surface, width, height)
                    attached = true
                    LogBus.log("[HUD] preview surface attached ${width}x$height")
                } else {
                    pipe.updatePreviewSize(width, height)
                }
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (attached) {
                    AaVideoBridge.pipeline?.clearPreviewSurface()
                    attached = false
                    LogBus.log("[HUD] preview surface detached")
                }
            }
        })

        surface.setOnTouchListener { _, e -> onSurfaceTouch(e); true }

        findViewById<View>(R.id.hud_close).setOnClickListener { finish() }
        findViewById<View>(R.id.hud_fullscreen).setOnClickListener { setFullscreen(!fullscreen) }
        padHandle.setOnClickListener { padOpen = !padOpen; applyPad() }
        modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || syncingMode) return@addOnButtonCheckedListener
            onModePicked(checkedId)
        }

        findViewById<View>(R.id.hud_knob_back).setOnClickListener { scroll(-1) }
        findViewById<View>(R.id.hud_knob_fwd).setOnClickListener { scroll(+1) }
        findViewById<View>(R.id.hud_left).setOnClickListener { key(AaInput.KEY_LEFT) }
        findViewById<View>(R.id.hud_right).setOnClickListener { key(AaInput.KEY_RIGHT) }
        findViewById<View>(R.id.hud_up).setOnClickListener { key(AaInput.KEY_UP) }
        findViewById<View>(R.id.hud_down).setOnClickListener { key(AaInput.KEY_DOWN) }
        findViewById<View>(R.id.hud_ok).setOnClickListener { key(AaInput.KEY_ENTER) }
        findViewById<View>(R.id.hud_back).setOnClickListener { key(AaInput.KEY_BACK) }
        findViewById<View>(R.id.hud_home).setOnClickListener { key(AaInput.KEY_HOME) }
        findViewById<View>(R.id.hud_voice).setOnClickListener {
            ensureMicPermission()
            key(AaInput.KEY_ASSISTANT)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra(EXTRA_OPEN_CONTROLS)) {
            padOpen = intent.getBooleanExtra(EXTRA_OPEN_CONTROLS, false)
            applyPad()
        }
        // Force rebuild when Hub opens a new GPX / nav session into an existing preview.
        refreshMode(forceReload = true)
    }

    override fun onResume() {
        super.onResume()
        refreshMode(forceReload = false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // We keep configChanges so the map isn't torn down — still reflow the Waze chrome.
        gpxUi?.onConfigurationChanged()
        applyOrientation()
    }

    /** Portrait: picture above the Mando. Landscape: picture left, Mando right. */
    private fun applyOrientation() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val stage = findViewById<View>(R.id.hud_stage)
        body.orientation = if (land) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        stage.layoutParams = LinearLayout.LayoutParams(
            if (land) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
            if (land) LinearLayout.LayoutParams.MATCH_PARENT else 0,
            1f,
        )
        controlsPanel.layoutParams = LinearLayout.LayoutParams(
            if (land) (320 * resources.displayMetrics.density).toInt() else LinearLayout.LayoutParams.MATCH_PARENT,
            if (land) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun applyPad() {
        pad.visibility = if (padOpen) View.VISIBLE else View.GONE
        padHandle.setText(if (padOpen) R.string.cuadro_pad_handle_open else R.string.cuadro_pad_handle_closed)
    }

    /** Lights the selector button for the current state; Android Auto / Dashboard are off without a bike. */
    private fun syncModeSelector() {
        val connected = AaVideoBridge.pipeline != null
        val enabled = CuadroLogic.modeButtonsEnabled(connected)
        findViewById<View>(R.id.hud_mode_aa).isEnabled = enabled
        findViewById<View>(R.id.hud_mode_dash).isEnabled = enabled
        findViewById<View>(R.id.hud_mode_map).isEnabled = true
        val id = when (CuadroLogic.selectedMode(DashboardMode.wanted)) {
            CuadroLogic.Mode.DASHBOARD -> R.id.hud_mode_dash
            CuadroLogic.Mode.ANDROID_AUTO -> R.id.hud_mode_aa
        }
        syncingMode = true
        try { modeGroup.check(id) } finally { syncingMode = false }
    }

    private fun onModePicked(id: Int) {
        when (id) {
            R.id.hud_mode_aa -> DashboardMode.set(this, false)
            R.id.hud_mode_dash -> if (!DashboardMode.set(this, true)) {
                Toast.makeText(this, R.string.cuadro_connect_first, Toast.LENGTH_SHORT).show()
            }
            R.id.hud_mode_map -> {
                // Same as MainActivity.onMapPressed.
                if (ConnectionState.phase != Phase.STREAMING && ConnectionState.phase != Phase.MIRRORING) {
                    GpxSession.abandonStaleNavigation("map hub")
                }
                GpxActivity.start(this)
            }
        }
        syncModeSelector()
    }

    override fun onPause() {
        if (isFinishing) stopGpxPreview()
        super.onPause()
    }

    override fun onDestroy() {
        stopGpxPreview()
        super.onDestroy()
    }

    private fun sessionKey(): String =
        "${GpxSession.mode}|${GpxSession.trackName}|${GpxSession.trackFile?.absolutePath}|${GpxSession.destination?.lat}|${GpxSession.destination?.lon}"

    private fun refreshMode(forceReload: Boolean) {
        // Live Android Auto video always wins: the phone Map preview is only a fallback for when
        // there's no bike pipeline. This keeps a previously-opened preview from sticking over the
        // real HUD once the bike connects.
        val live = AaVideoBridge.pipeline != null
        val wantGpx = !live && GpxSession.active
        if (wantGpx) {
            val key = sessionKey()
            val needReload = forceReload ||
                intent.getBooleanExtra(EXTRA_FORCE_RELOAD, false) ||
                key != boundSessionKey
            intent.removeExtra(EXTRA_FORCE_RELOAD)
            startGpxPreview(reload = needReload)
        } else {
            stopGpxPreview()
            val live = AaVideoBridge.pipeline != null
            hint.visibility = if (live) View.GONE else View.VISIBLE
            surface.visibility = View.VISIBLE
            gpxHost.visibility = View.GONE
            findViewById<View>(R.id.hud_topbar).visibility =
                if (fullscreen) View.GONE else View.VISIBLE
            controlsPanel.visibility = if (fullscreen) View.GONE else View.VISIBLE
            titleView.setText(R.string.cuadro_title)
            syncModeSelector()
        }
    }

    private fun startGpxPreview(reload: Boolean) {
        if (gpxPreview && gpxUi != null && !reload) {
            hint.visibility = View.GONE
            return
        }
        stopGpxPreview()
        gpxPreview = true
        gpxHost.removeAllViews()
        val root = try {
            LayoutInflater.from(this).inflate(R.layout.presentation_gpx, gpxHost, false)
        } catch (e: Exception) {
            gpxPreview = false
            LogBus.log("[HUD] inflate map chrome failed: $e")
            Toast.makeText(this, getString(R.string.cuadro_map_ui_failed), Toast.LENGTH_LONG).show()
            return
        }
        gpxHost.addView(
            root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        val ui = GpxDashUi(this, root, { LogBus.log(it) }, isAlive = { !isFinishing && gpxPreview })
        val ok = try {
            ui.bind()
        } catch (e: Exception) {
            LogBus.log("[HUD] bind map failed: $e")
            false
        } catch (oom: OutOfMemoryError) {
            LogBus.log("[HUD] bind map OOM: $oom")
            false
        }
        if (!ok) {
            gpxPreview = false
            try { ui.release() } catch (_: Exception) {}
            gpxHost.removeAllViews()
            Toast.makeText(this, getString(R.string.cuadro_map_load_failed), Toast.LENGTH_LONG).show()
            return
        }
        gpxUi = ui
        boundSessionKey = sessionKey()
        surface.visibility = View.GONE
        gpxHost.visibility = View.VISIBLE
        hint.visibility = View.GONE
        // Map chrome has its own search / Hub / close — hide Dash topbar so search
        // isn't crushed under the punch-hole + app bar.
        findViewById<View>(R.id.hud_topbar).visibility = View.GONE
        controlsPanel.visibility = View.GONE
        // Waze-style: ride immersive — no system status bar eating the map.
        insetsController.hide(SafeInsets.BARS)
        titleView.text = when (GpxSession.mode) {
            GpxSession.Mode.GPX -> "GPX · ${GpxSession.trackName}"
            GpxSession.Mode.NAV_TO -> "Nav · ${GpxSession.trackName}"
            GpxSession.Mode.FREE_RIDE -> getString(R.string.cuadro_map_preview)
        }
        LogBus.log("[HUD] Map preview bound (${GpxSession.mode} '${GpxSession.trackName}')")
    }

    private fun stopGpxPreview() {
        if (!gpxPreview && gpxUi == null) {
            boundSessionKey = null
            return
        }
        gpxPreview = false
        boundSessionKey = null
        try { gpxUi?.release() } catch (_: Exception) {}
        gpxUi = null
        gpxHost.removeAllViews()
        gpxHost.visibility = View.GONE
        insetsController.show(SafeInsets.BARS)
    }

    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        findViewById<View>(R.id.hud_topbar).visibility = if (on) View.GONE else View.VISIBLE
        if (!gpxPreview) {
            controlsPanel.visibility = if (on) View.GONE else View.VISIBLE
        }
        if (on) {
            insetsController.hide(SafeInsets.BARS)
            Toast.makeText(this, getString(R.string.cuadro_fullscreen_toast), Toast.LENGTH_SHORT).show()
        } else {
            insetsController.show(SafeInsets.BARS)
        }
    }

    private fun onSurfaceTouch(e: MotionEvent) {
        if (gpxPreview) return
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            val hit = AaVideoBridge.pipeline?.previewHit(e.x, e.y, surface.width, surface.height)
            // Taps on the black bars around the picture (x < 0) or on a card do nothing.
            gestureTarget = CuadroLogic.target(hit != null, hit?.onAndroidAuto == true, insideDash = hit == null || hit.x >= 0)
            downX = e.x
            downY = e.y
            dashMoved = false
            lastSrc.clear()
        }
        if (gestureTarget == CuadroLogic.Target.NONE) return
        if (gestureTarget == CuadroLogic.Target.DASHBOARD) {
            onDashboardTouch(e)
            return
        }
        val sink = AaVideoBridge.previewTouchSink
        if (sink == null) {
            val now = System.currentTimeMillis()
            if (now - noSessionToastAt > 3000) {
                noSessionToastAt = now
                Toast.makeText(this, R.string.cuadro_connect_first, Toast.LENGTH_SHORT).show()
            }
            return
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                forward(sink, e, e.actionIndex, AaInput.ACTION_DOWN)
            MotionEvent.ACTION_MOVE ->
                for (i in 0 until e.pointerCount) forward(sink, e, i, AaInput.ACTION_MOVE)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                forward(sink, e, e.actionIndex, AaInput.ACTION_UP)
            MotionEvent.ACTION_CANCEL ->
                for (i in 0 until e.pointerCount) forward(sink, e, i, AaInput.ACTION_UP)
        }
    }

    /** A touch that started on the dashboard: a clean tap opens the dashboard settings; nothing goes to AA. */
    private fun onDashboardTouch(e: MotionEvent) {
        val slop = CuadroLogic.TAP_SLOP_DP * resources.displayMetrics.density
        when (e.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> dashMoved = true
            MotionEvent.ACTION_MOVE ->
                if (!CuadroLogic.isTap(downX, downY, e.x, e.y, slop)) dashMoved = true
            MotionEvent.ACTION_UP -> {
                if (!dashMoved && CuadroLogic.isTap(downX, downY, e.x, e.y, slop)) {
                    startActivity(
                        Intent(this, ControlsActivity::class.java)
                            .putExtra(SettingsSection.EXTRA, SettingsSection.DASHBOARD.key),
                    )
                }
            }
        }
    }

    private fun forward(
        sink: (Int, Int, Int, Int) -> Unit,
        e: MotionEvent,
        index: Int,
        action: Int,
    ) {
        val id = e.getPointerId(index)
        val x = e.getX(index)
        val y = e.getY(index)
        val src: Pair<Int, Int>? = if (gestureTarget == CuadroLogic.Target.ANDROID_AUTO) {
            // Already in Android Auto coordinates; if the finger left the window, keep the last point.
            val hit = AaVideoBridge.pipeline?.previewHit(x, y, surface.width, surface.height)
            if (hit != null && hit.onAndroidAuto) hit.x to hit.y else null
        } else {
            mapToSource(x, y)
        }
        val point = src ?: if (action == AaInput.ACTION_UP) lastSrc[id] else null
        if (point == null) return
        if (action == AaInput.ACTION_UP) lastSrc.remove(id) else lastSrc[id] = point
        sink(action, id, point.first, point.second)
    }

    private fun mapToSource(vx: Float, vy: Float): Pair<Int, Int>? {
        val vw = surface.width
        val vh = surface.height
        val sw = BikeProfileHolder.aaUsableWidth
        val sh = BikeProfileHolder.aaUsableHeight
        if (vw == 0 || vh == 0 || sw == 0 || sh == 0) return null

        val srcAspect = sw.toFloat() / sh
        val viewAspect = vw.toFloat() / vh
        val rectW: Int
        val rectH: Int
        if (srcAspect < viewAspect) {
            rectH = vh; rectW = Math.round(vh * srcAspect)
        } else {
            rectW = vw; rectH = Math.round(vw / srcAspect)
        }
        val rectX = (vw - rectW) / 2
        val rectY = (vh - rectH) / 2

        val rx = vx - rectX
        val ry = vy - rectY
        if (rx < 0 || ry < 0 || rx >= rectW || ry >= rectH) return null
        val sx = (rx * sw / rectW).toInt().coerceIn(0, sw - 1)
        val sy = (ry * sh / rectH).toInt().coerceIn(0, sh - 1)
        return sx to sy
    }

    private fun key(code: Int) {
        if (gpxPreview) {
            Toast.makeText(this, R.string.cuadro_aa_needs_bike, Toast.LENGTH_SHORT).show()
            return
        }
        val sink = AaVideoBridge.keySink
        if (sink == null) {
            Toast.makeText(this, R.string.cuadro_connect_first, Toast.LENGTH_SHORT).show()
        } else {
            sink(code)
        }
    }

    private fun scroll(delta: Int) {
        if (gpxPreview) {
            Toast.makeText(this, R.string.cuadro_aa_needs_bike, Toast.LENGTH_SHORT).show()
            return
        }
        val sink = AaVideoBridge.scrollSink
        if (sink == null) {
            Toast.makeText(this, R.string.cuadro_connect_first, Toast.LENGTH_SHORT).show()
        } else {
            sink(delta)
        }
    }

    private fun ensureMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
    }

    companion object {
        private const val REQ_MIC = 72
        const val EXTRA_GPX_PREVIEW = "gpx_preview"
        const val EXTRA_FORCE_RELOAD = "gpx_force_reload"
        /** Boolean, default false: the Mando starts folded. */
        const val EXTRA_OPEN_CONTROLS = "open_controls"

        fun start(ctx: android.content.Context, openControls: Boolean) {
            ctx.startActivity(
                Intent(ctx, HudViewActivity::class.java).apply {
                    if (ctx !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_OPEN_CONTROLS, openControls)
                },
            )
        }

        fun startGpxPreview(ctx: android.content.Context) {
            ctx.startActivity(
                Intent(ctx, HudViewActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_GPX_PREVIEW, true)
                    putExtra(EXTRA_FORCE_RELOAD, true)
                },
            )
        }
    }
}
