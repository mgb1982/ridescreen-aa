// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat

/**
 * Hook the lead sets from MainActivity (`HubHooks.stopEverything = { stopEverything() }`): the same Stop as
 * the main button. Needed by "Import camera clips" while the bike is projecting.
 */
object HubHooks {
    @Volatile var stopEverything: (() -> Unit)? = null
}

/** What one hub row looks like. */
class HubRow(@DrawableRes val icon: Int, val color: Int, val title: String, val sub: String = "")

/** Shared shell of the Trips and Bike tabs: a title, a scrolling list of big cards, the bottom bar. */
abstract class HubActivity : AppCompatActivity() {
    protected abstract val tab: NavTab
    protected abstract val titleRes: Int
    protected abstract fun slots(): List<HubSlot>
    protected abstract fun describe(item: HubItem): HubRow
    protected abstract fun onItem(item: HubItem)
    /** Long press (live share link). Default: nothing. */
    protected open fun onItemLong(item: HubItem): Boolean = false

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@HubActivity, R.color.bg))
        }
        val scroll = ScrollView(this).apply { isFillViewport = false }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(LayoutInflater.from(this).inflate(R.layout.include_bottom_nav, root, false))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(SafeInsets.BARS)
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        BottomNav.bind(this, tab)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    protected fun render() {
        list.removeAllViews()
        list.addView(TextView(this).apply {
            text = getString(titleRes)
            textSize = 28f
            setTextColor(color(R.color.text_primary))
            typeface = ResourcesCompat.getFont(this@HubActivity, R.font.archivo_black)
            setPadding(0, 0, 0, dp(14))
        })
        for (slot in slots()) list.addView(row(slot))
    }

    /** Same card as the Home tiles (bg_stat_tile, tinted icon bubble, medium title), laid out as a full-width row. */
    private fun row(slot: HubSlot): View {
        val d = describe(slot.item)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(72)
            setPadding(dp(16), dp(12), dp(14), dp(12))
            setBackgroundResource(R.drawable.bg_stat_tile)
            val tv = TypedValue()
            if (theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) foreground = ContextCompat.getDrawable(this@HubActivity, tv.resourceId)
            isClickable = true
            isFocusable = true
            alpha = if (slot.enabled) 1f else 0.4f
            if (slot.enabled) {
                setOnClickListener { onItem(slot.item) }
                setOnLongClickListener { onItemLong(slot.item) }
            }
        }
        val bubble = ImageView(this).apply {
            setImageResource(d.icon)
            setColorFilter((d.color and 0x00FFFFFF) or 0xFF000000.toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor((d.color and 0x00FFFFFF) or 0x33000000)
            }
        }
        box.addView(bubble, LinearLayout.LayoutParams(dp(46), dp(46)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = d.title
            textSize = 16f
            setTextColor(color(R.color.text_primary))
            typeface = ResourcesCompat.getFont(this@HubActivity, R.font.archivo_medium)
            maxLines = 2
        })
        if (d.sub.isNotBlank()) texts.addView(TextView(this).apply {
            text = d.sub
            textSize = 12f
            setTextColor(color(R.color.text_secondary))
            maxLines = 2
        })
        box.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
        box.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(10) }
        return box
    }

    protected fun clean(resId: Int) = HubEntries.cleanTitle(getString(resId))
    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
