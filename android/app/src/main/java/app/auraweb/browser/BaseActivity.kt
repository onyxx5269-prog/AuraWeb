package app.auraweb.browser

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Applies the chosen theme + language to every screen, and holds small view helpers
 * so screens can be built in code without extra libraries (keeps the APK tiny).
 */
abstract class BaseActivity : Activity() {

    protected lateinit var prefs: Prefs

    /** theme+language snapshot this activity was created with; used to detect changes. */
    protected var appliedLook = ""

    override fun attachBaseContext(base: Context) {
        val p = Prefs.get(base)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(Locale(p.lang))
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs.get(this)
        val dark = isDark()
        setTheme(if (dark) R.style.Theme_Aura_Dark else R.style.Theme_Aura_Light)
        super.onCreate(savedInstanceState)
        appliedLook = currentLook()
        // No refresh-rate preference: Android keeps the rate the user chose in system settings
        // (60/90/120 Hz, adaptive). All animations are vsync-driven, so they follow it.
        window.attributes = window.attributes.apply {
            preferredRefreshRate = 0f
            preferredDisplayModeId = 0
        }
        styleSystemBars(dark)
    }

    fun isDark(): Boolean = when (prefs.theme) {
        "light" -> false
        "dark" -> true
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    protected fun currentLook() = "${isDark()}|${prefs.lang}"

    @Suppress("DEPRECATION")
    private fun styleSystemBars(dark: Boolean) {
        window.statusBarColor = color(R.attr.auraBg)
        window.navigationBarColor = color(R.attr.auraSurface)
        var flags = window.decorView.systemUiVisibility
        flags = if (dark) flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() else flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 27) {
            flags = if (dark) flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv() else flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    /** Restart this screen with a soft cross-fade (used after theme/language change). */
    protected fun restartSmooth() {
        val i = intent
        finish()
        startActivity(i)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    // ---------- small UI helpers ----------

    fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    fun dp(v: Int) = dp(v.toFloat()).toInt()

    fun color(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    /** Animation duration that respects the "Smooth animations" setting. */
    fun dur(ms: Long) = if (prefs.smoothMotion) ms else 0L

    fun text(s: CharSequence, sizeSp: Float = 15f, dim: Boolean = false, bold: Boolean = false) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color(if (dim) R.attr.auraTextDim else R.attr.auraText))
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun iconButton(res: Int, desc: String, tintAttr: Int = R.attr.auraText, onClick: () -> Unit) = ImageView(this).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color(tintAttr))
        contentDescription = desc
        background = context.getDrawable(R.drawable.bg_ripple_round)
        val p = dp(11)
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        setOnClickListener { onClick() }
    }

    /** Header with back arrow + title, used by secondary screens. */
    fun header(title: String, trailing: View? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(8), dp(8), dp(8))
        addView(iconButton(R.drawable.ic_back, getString(R.string.back)) { finish() })
        addView(text(title, 22f, bold = true).apply {
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        trailing?.let { addView(it) }
    }
}
