package app.auraweb.browser

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Settings built in code: segmented pickers, switches and a slider — no extra libraries. */
class SettingsActivity : BaseActivity() {

    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(32))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(color(R.attr.auraBg))
            addView(body)
        }
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.attr.auraBg))
            addView(header(getString(R.string.settings)))
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(screen)
        build()
    }

    private fun build() {
        section(R.string.s_appearance) {
            segmented(R.string.s_theme, listOf("dark" to R.string.s_theme_dark, "light" to R.string.s_theme_light, "system" to R.string.s_theme_system),
                prefs.theme) { prefs.theme = it; restartSmooth() }
            segmented(R.string.s_language, listOf("ru" to 0, "en" to 0), prefs.lang, labels = listOf("Русский", "English")) {
                prefs.language = it; restartSmooth()
            }
            toggle(R.string.s_motion, R.string.s_motion_sub, prefs.smoothMotion) { prefs.smoothMotion = it }
        }
        section(R.string.s_search) {
            segmented(null, listOf("google" to R.string.s_google, "yandex" to R.string.s_yandex), prefs.engine) { prefs.engine = it }
        }
        section(R.string.s_content) {
            toggle(R.string.s_js, null, prefs.javaScript) { prefs.javaScript = it }
            toggle(R.string.s_popups, null, prefs.blockPopups) { prefs.blockPopups = it }
            toggle(R.string.s_saver, R.string.s_saver_sub, prefs.dataSaver) { prefs.dataSaver = it }
            slider(R.string.s_text_size, 70, 160, 10, prefs.textZoom) { prefs.textZoom = it }
        }
        section(R.string.s_privacy) {
            toggle(R.string.s_cookies, null, prefs.blockThirdPartyCookies) { prefs.blockThirdPartyCookies = it }
            toggle(R.string.s_save_history, null, prefs.saveHistory) { prefs.saveHistory = it }
            toggle(R.string.s_restore, null, prefs.restoreTabs) { prefs.restoreTabs = it }
            action(R.string.s_clear_history, null) {
                Store.get(this@SettingsActivity).clear(Store.KEY_HISTORY)
                Toast.makeText(this@SettingsActivity, R.string.cleared, Toast.LENGTH_SHORT).show()
            }
            action(R.string.s_clear_data, R.string.s_clear_data_sub) {
                MainActivity.clearWebData()
                prefs.clearCachePending = true
                Toast.makeText(this@SettingsActivity, R.string.cleared, Toast.LENGTH_SHORT).show()
            }
        }
        section(R.string.s_performance) {
            info(getString(R.string.s_refresh), getString(R.string.s_refresh_value, refreshRate()))
            segmented(R.string.s_live_tabs, listOf("2" to 0, "3" to 0, "5" to 0), prefs.maxLiveTabs.toString(),
                labels = listOf("2", "3", "5"), sub = R.string.s_live_tabs_sub) { prefs.maxLiveTabs = it.toInt() }
        }
        section(R.string.s_about) {
            info(getString(R.string.s_version), "AuraWeb " + packageManager.getPackageInfo(packageName, 0).versionName)
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshRate(): Int {
        val d = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
        return Math.round(d?.refreshRate ?: 60f)
    }

    // ---------------------------------------------------------------- building blocks

    private var card: LinearLayout? = null
    private var rowIndex = 0

    private fun section(title: Int, content: () -> Unit) {
        body.addView(text(getString(title).uppercase(), 12f, dim = true, bold = true).apply {
            letterSpacing = 0.1f
            setPadding(dp(8), dp(22), 0, dp(8))
        })
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = context.getDrawable(R.drawable.bg_card)
            clipToOutline = true
        }
        card = c
        rowIndex = 0
        content()
        body.addView(c)
        c.alpha = 0f
        c.translationY = dp(10f)
        c.animate().alpha(1f).translationY(0f).setStartDelay(dur(30L * body.childCount)).setDuration(dur(280)).start()
    }

    private fun addRow(v: View) {
        val c = card ?: return
        if (rowIndex++ > 0) c.addView(View(this).apply {
            setBackgroundColor(color(R.attr.auraStroke))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { marginStart = dp(16) }
        })
        c.addView(v)
    }

    private fun rowBase(title: String, sub: String?): Pair<LinearLayout, LinearLayout> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), dp(10), dp(12), dp(10))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(text(title, 16f))
            sub?.let { addView(text(it, 13f, dim = true).apply { setPadding(0, dp(2), 0, 0) }) }
        }
        row.addView(texts)
        return row to texts
    }

    private fun toggle(title: Int, sub: Int?, value: Boolean, onChange: (Boolean) -> Unit) {
        val (row, _) = rowBase(getString(title), sub?.let { getString(it) })
        val sw = Switch(this).apply {
            isChecked = value
            isClickable = false
            isFocusable = false
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(color(R.attr.auraAccent), color(R.attr.auraTextDim)))
            trackTintList = ColorStateList(states, intArrayOf(color(R.attr.auraAccent) and 0x00FFFFFF or 0x66000000, color(R.attr.auraStroke)))
        }
        row.addView(sw)
        row.background = getDrawable(R.drawable.bg_ripple)
        row.setOnClickListener { sw.isChecked = !sw.isChecked; onChange(sw.isChecked) }
        addRow(row)
    }

    private fun action(title: Int, sub: Int?, onClick: () -> Unit) {
        val (row, texts) = rowBase(getString(title), sub?.let { getString(it) })
        (texts.getChildAt(0) as TextView).setTextColor(color(R.attr.auraAccent))
        row.background = getDrawable(R.drawable.bg_ripple)
        row.setOnClickListener { onClick() }
        addRow(row)
    }

    private fun info(title: String, value: String) {
        val (row, _) = rowBase(title, null)
        row.addView(text(value, 14f, dim = true))
        addRow(row)
    }

    private fun slider(title: Int, from: Int, to: Int, step: Int, value: Int, onChange: (Int) -> Unit) {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        val (row, _) = rowBase(getString(title), null)
        val label = text("$value%", 14f, dim = true)
        row.addView(label)
        wrap.addView(row)
        wrap.addView(SeekBar(this).apply {
            max = (to - from) / step
            progress = (value - from) / step
            progressTintList = ColorStateList.valueOf(color(R.attr.auraAccent))
            thumbTintList = ColorStateList.valueOf(color(R.attr.auraAccent))
            progressBackgroundTintList = ColorStateList.valueOf(color(R.attr.auraStroke))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val v = from + p * step
                    label.text = "$v%"
                    if (fromUser) onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })
        addRow(wrap)
    }

    /** iOS-style segmented control; the selected pill slides between options. */
    private fun segmented(
        title: Int?, options: List<Pair<String, Int>>, selected: String,
        labels: List<String>? = null, sub: Int? = null, onPick: (String) -> Unit,
    ) {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(if (title == null) 14 else 12), dp(16), dp(14))
        }
        title?.let { wrap.addView(text(getString(it), 16f)) }
        sub?.let { wrap.addView(text(getString(it), 13f, dim = true).apply { setPadding(0, dp(2), 0, 0) }) }

        val track = android.widget.FrameLayout(this).apply {
            background = context.getDrawable(R.drawable.bg_seg)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        val indicator = View(this).apply { background = context.getDrawable(R.drawable.bg_seg_on) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        track.addView(indicator, android.widget.FrameLayout.LayoutParams(0, dp(38)))
        track.addView(row, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))

        var current = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
        val cells = ArrayList<TextView>()
        options.forEachIndexed { i, (value, res) ->
            val cell = text(labels?.get(i) ?: getString(res), 14f, bold = true).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }
            cell.setOnClickListener {
                if (i == current) return@setOnClickListener
                current = i
                paint(cells, i)
                indicator.animate().translationX(cell.width * i.toFloat()).setDuration(dur(220))
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.8f))
                    .withEndAction { onPick(value) }.start()
            }
            cells.add(cell)
            row.addView(cell)
        }
        row.post {
            val w = row.width / options.size
            indicator.layoutParams = indicator.layoutParams.apply { width = w }
            indicator.translationX = (w * current).toFloat()
        }
        paint(cells, current)
        wrap.addView(track, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(if (title == null && sub == null) 0 else 10)
        })
        addRow(wrap)
    }

    private fun paint(cells: List<TextView>, selected: Int) {
        cells.forEachIndexed { i, tv -> tv.setTextColor(color(if (i == selected) R.attr.auraOnAccent else R.attr.auraTextDim)) }
    }
}
