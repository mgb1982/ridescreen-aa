package dev.zanderp.opencfmoto

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

/**
 * Home ▸ dashboard card ▸ "Style": a bottom sheet with a large preview of the current style, the list of
 * styles (current one ticked), the accent colour and the Auto / Day / Night palette. It writes through the
 * same [DashboardMode] setters as the old picker in Settings ▸ Dashboard, which already push the change to
 * the dash when it is showing ([DashboardMode.setDesign] re-applies the saver; the live layer reads the rest).
 */
class DashStyleSheet : BottomSheetDialogFragment() {
    private lateinit var previewView: ImageView
    private lateinit var listView: LinearLayout
    private lateinit var accentsView: LinearLayout
    private lateinit var bgView: LinearLayout
    private var previewKey = ""
    private lateinit var pickBackground: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>
    private var rootView: View? = null

    /** Registered here, not in a click: the picker's result can arrive after the sheet was recreated. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickBackground = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) importBackground(uri)
        }
    }

    private fun importBackground(uri: Uri) {
        if (DashBackgroundStore.isImporting()) return   // one at a time: ignore another choice meanwhile
        val app = requireContext().applicationContext
        val bikeKey = BikeScope.suffix(app)   // captured before the thread: the bike can change meanwhile
        Thread({
            val ok = DashBackgroundStore.import(app, uri, bikeKey)
            if (ok) DashboardMode.setBgImage(app, System.currentTimeMillis(), bikeKey)
            LogBus.log("[DASHBOARD] background image ${if (ok) "saved" else "import failed"}")
            activity?.runOnUiThread {
                if (!isAdded) return@runOnUiThread
                if (!ok) Toast.makeText(app, R.string.dash_bg_failed, Toast.LENGTH_SHORT).show()
                rootView?.let { render(it) }
            }
        }, "dash-bg-import").start()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.sheet_dash_style, container, false)
        previewView = v.findViewById(R.id.style_sheet_preview)
        listView = v.findViewById(R.id.style_sheet_list)
        accentsView = v.findViewById(R.id.style_sheet_accents)
        bgView = v.findViewById(R.id.style_sheet_bg)
        rootView = v
        for ((id, t) in listOf(
            R.id.style_sheet_theme_auto to DashboardMode.Theme.AUTO,
            R.id.style_sheet_theme_day to DashboardMode.Theme.LIGHT,
            R.id.style_sheet_theme_night to DashboardMode.Theme.DARK,
        )) v.findViewById<MaterialButton>(id).setOnClickListener {
            DashboardMode.setTheme(requireContext(), t)   // the live layer reads it on its next tick
            render(v)
        }
        // v2.5 · Fields, card order and scale live in Settings ▸ Dashboard: link there from Home.
        // Opened from that screen already (its Background button), the link would only loop back: hide it.
        v.findViewById<MaterialButton>(R.id.style_sheet_more).apply {
            if (activity is ControlsActivity) visibility = View.GONE
            else setOnClickListener {
                startActivity(android.content.Intent(requireContext(), ControlsActivity::class.java)
                    .putExtra(SettingsSection.EXTRA, SettingsSection.DASHBOARD.key))
                dismissAllowingStateLoss()
            }
        }
        render(v)
        return v
    }

    private fun designName(design: DashboardMode.Design): String = getString(designLabelRes(design))

    private fun render(root: View) {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()

        // Style list
        listView.removeAllViews()
        for (row in DashStyleRows.rows(DashboardMode.Design.entries, DashboardMode.design(ctx))) {
            val item = TextView(ctx).apply {
                text = DashStyleRows.label(designName(row.item), row.selected)
                textSize = 16f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                if (row.selected) setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
                minHeight = dp(48)
                setPadding(dp(12), dp(4), dp(12), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(ContextCompat.getColor(ctx, if (row.selected) R.color.surface_high else R.color.surface))
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    DashboardMode.setDesign(ctx, row.item)
                    render(root)
                }
            }
            listView.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }

        // Accent swatches (same presets as the old accent dialog; 0 = the style's own colour)
        accentsView.removeAllViews()
        val current = DashboardMode.accent(ctx)
        val ink = ContextCompat.getColor(ctx, R.color.text_secondary)
        for (color in DashLayout.ACCENT_PRESETS) {
            val selected = color == current
            val swatch = View(ctx).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (color == 0) Color.TRANSPARENT else color)
                    setStroke(dp(if (selected) 3 else 1), if (selected) ContextCompat.getColor(ctx, R.color.text_primary) else ink)
                }
                contentDescription = if (color == 0) getString(R.string.controls_dash_accent_style) else null
                isClickable = true
                setOnClickListener {
                    DashboardMode.setAccent(ctx, color)
                    render(root)
                }
            }
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            cell.addView(swatch, LinearLayout.LayoutParams(dp(40), dp(40)))
            if (color == 0) cell.addView(TextView(ctx).apply {
                text = getString(R.string.controls_dash_accent_style)
                textSize = 11f
                gravity = Gravity.CENTER
                maxWidth = dp(64)
                setTextColor(ink)
            })
            accentsView.addView(cell)
        }

        // Palette buttons
        val onColor = ContextCompat.getColor(ctx, R.color.brand_orange)
        val onText = ContextCompat.getColor(ctx, R.color.on_brand)
        val offColor = ContextCompat.getColor(ctx, R.color.surface_high)
        val offText = ContextCompat.getColor(ctx, R.color.text_primary)
        val theme = DashboardMode.theme(ctx)
        for ((id, t) in listOf(
            R.id.style_sheet_theme_auto to DashboardMode.Theme.AUTO,
            R.id.style_sheet_theme_day to DashboardMode.Theme.LIGHT,
            R.id.style_sheet_theme_night to DashboardMode.Theme.DARK,
        )) {
            val btn = root.findViewById<MaterialButton>(id)
            val on = t == theme
            btn.backgroundTintList = ColorStateList.valueOf(if (on) onColor else offColor)
            btn.setTextColor(if (on) onText else offText)
        }

        renderBackground(ctx) { dp(it) }
        drawPreview(ctx)
    }

    /** "Background": Original / Image…, and with a picture: Fill / Fit, the veil and Remove. */
    private fun renderBackground(ctx: Context, dp: (Int) -> Int) {
        bgView.removeAllViews()
        val ink = ContextCompat.getColor(ctx, R.color.text_secondary)
        val onColor = ContextCompat.getColor(ctx, R.color.brand_orange)
        val onText = ContextCompat.getColor(ctx, R.color.on_brand)
        val offColor = ContextCompat.getColor(ctx, R.color.surface_high)
        val offText = ContextCompat.getColor(ctx, R.color.text_primary)
        val image = DashboardMode.bgMode(ctx) == DashBackground.Mode.IMAGE
        fun button(label: Int, on: Boolean, click: () -> Unit) = MaterialButton(ctx).apply {
            setText(label)
            isAllCaps = false
            backgroundTintList = ColorStateList.valueOf(if (on) onColor else offColor)
            setTextColor(if (on) onText else offText)
            setOnClickListener { click() }
        }
        fun row(vararg views: View) = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEachIndexed { i, v ->
                addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = dp(8) })
            }
        }
        bgView.addView(TextView(ctx).apply {
            setText(R.string.dash_bg_title); textSize = 13f; setTextColor(ink)
        })
        bgView.addView(TextView(ctx).apply {
            setText(R.string.dash_bg_hint); textSize = 11f; setTextColor(ink)
        })
        bgView.addView(row(
            button(R.string.dash_bg_original, !image) {
                DashboardMode.clearBgImage(ctx)
                rootView?.let { render(it) }
            },
            button(R.string.dash_bg_image, image) {
                pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        if (!image) return
        val fit = DashboardMode.bgFit(ctx)
        bgView.addView(row(
            button(R.string.dash_bg_fill, fit == DashBackground.Fit.FILL) {
                DashboardMode.setBgFit(ctx, DashBackground.Fit.FILL); rootView?.let { render(it) }
            },
            button(R.string.dash_bg_fit, fit == DashBackground.Fit.FIT) {
                DashboardMode.setBgFit(ctx, DashBackground.Fit.FIT); rootView?.let { render(it) }
            },
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val veil = DashboardMode.bgVeil(ctx)
        val label = TextView(ctx).apply {
            text = getString(R.string.dash_bg_veil, veil); textSize = 13f; setTextColor(ink)
        }
        bgView.addView(label, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        bgView.addView(SeekBar(ctx).apply {
            max = DashBackground.VEIL_MAX
            progress = veil
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) label.text = getString(R.string.dash_bg_veil, DashBackground.clampVeil(progress))
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    DashboardMode.setBgVeil(ctx, seekBar.progress)
                    drawPreview(ctx)
                }
            })
        }, LinearLayout.LayoutParams(-1, -2))
        bgView.addView(button(R.string.dash_bg_remove, false) {
            DashboardMode.clearBgImage(ctx)
            rootView?.let { render(it) }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    /** Big preview, drawn off the UI thread; a newer key makes an older result be dropped. */
    private fun drawPreview(ctx: Context) {
        val design = DashboardMode.design(ctx)
        val key = DashStyleRows.previewKey(design.id, DashboardMode.isLight(ctx), DashboardMode.accent(ctx), 800, 416,
            DashboardMode.background(ctx).key())
        if (key == previewKey) return
        previewKey = key
        val app = ctx.applicationContext
        Thread({
            val bmp = try { DashboardMode.preview(app, design, 800, 416) } catch (_: Exception) { null }
            activity?.runOnUiThread {
                if (bmp != null && isAdded && previewKey == key) previewView.setImageBitmap(bmp)
            }
        }, "dash-style-preview").start()
    }

    override fun onDestroyView() {
        rootView = null
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? MainActivity)?.refreshDashCard()
    }

    companion object {
        const val TAG = "dash_style_sheet"

        fun designLabelRes(design: DashboardMode.Design): Int = when (design) {
            DashboardMode.Design.SPORT -> R.string.dash_design_sport
            DashboardMode.Design.RACING -> R.string.dash_design_racing
            DashboardMode.Design.TOURING -> R.string.dash_design_touring
            DashboardMode.Design.CLEAN -> R.string.dash_design_clean
            DashboardMode.Design.PIT -> R.string.dash_design_pit
            DashboardMode.Design.CLASSIC -> R.string.dash_design_classic
            DashboardMode.Design.FOCUS -> R.string.dash_design_focus
            DashboardMode.Design.LED -> R.string.dash_design_led
            DashboardMode.Design.ANALOG -> R.string.dash_design_analog
            DashboardMode.Design.VFD -> R.string.dash_design_vfd
            DashboardMode.Design.MINIMAL -> R.string.dash_design_minimal
            DashboardMode.Design.COCKPIT -> R.string.dash_design_cockpit
            DashboardMode.Design.WINDOW -> R.string.dash_design_window
        }
    }
}
