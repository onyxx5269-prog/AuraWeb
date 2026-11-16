package app.auraweb.browser

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Bookmarks or history list. Tapping an entry returns its URL to the browser. */
class ListActivity : BaseActivity() {

    private lateinit var store: Store
    private lateinit var kind: String
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store.get(this)
        kind = intent.getStringExtra(EXTRA_KIND) ?: Store.KEY_BOOKMARKS
        val title = getString(if (kind == Store.KEY_HISTORY) R.string.history else R.string.bookmarks)

        val clear = text(getString(R.string.clear_all), 14f, bold = true).apply {
            setTextColor(color(R.attr.auraAccent))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = context.getDrawable(R.drawable.bg_ripple)
            setOnClickListener { store.clear(kind); render() }
        }
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.attr.auraBg))
            addView(header(title, clear))
            addView(ScrollView(this@ListActivity).apply { addView(list) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        render()
    }

    private fun render() {
        list.removeAllViews()
        val items = store.list(kind)
        if (items.isEmpty()) {
            list.addView(text(getString(if (kind == Store.KEY_HISTORY) R.string.empty_history else R.string.empty_bookmarks), 15f, dim = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(80), dp(24), 0)
            })
            return
        }
        items.forEachIndexed { i, e ->
            val host = e.url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = context.getDrawable(R.drawable.bg_ripple)
                setPadding(dp(8), dp(8), 0, dp(8))
                setOnClickListener {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_URL, e.url))
                    finish()
                }
            }
            row.addView(TextView(this).apply {
                text = (e.title.ifBlank { host }.trim().firstOrNull() ?: '•').uppercaseChar().toString()
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(color(R.attr.auraAccent))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                background = context.getDrawable(R.drawable.bg_card)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            })
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, dp(8), 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(text(e.title.ifBlank { host }, 15f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                val meta = if (kind == Store.KEY_HISTORY)
                    "$host · " + DateUtils.getRelativeTimeSpanString(e.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                else host
                addView(text(meta, 12f, dim = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            })
            row.addView(iconButton(R.drawable.ic_delete, getString(R.string.close), R.attr.auraTextDim) {
                row.animate().alpha(0f).translationX(row.width * 0.3f).setDuration(dur(160)).withEndAction {
                    store.remove(kind, i)
                    render()
                }.start()
            })
            list.addView(row)
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_URL = "url"
    }
}
