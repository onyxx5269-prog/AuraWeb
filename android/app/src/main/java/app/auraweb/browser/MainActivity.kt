package app.auraweb.browser

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.util.Patterns
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : BaseActivity() {

    /** A tab is cheap: only the few most recent tabs keep a live WebView, the rest "sleep". */
    private class Tab(var url: String, var title: String) {
        var web: WebView? = null
        var state: Bundle? = null
        var lastUsed = 0L
    }

    private val tabs = ArrayList<Tab>()
    private var current = -1

    private lateinit var store: Store
    private lateinit var root: FrameLayout
    private lateinit var container: FrameLayout
    private lateinit var bottomBar: View
    private lateinit var pill: View
    private lateinit var address: EditText
    private lateinit var siteIcon: ImageView
    private lateinit var actionBtn: ImageView
    private lateinit var tabCount: TextView
    private lateinit var progress: View
    private lateinit var scrim: View
    private lateinit var menuSheet: View
    private lateinit var tabsSheet: View
    private lateinit var tabsList: LinearLayout
    private lateinit var tabsScroll: ScrollView
    private lateinit var starBtn: ImageView
    private lateinit var backBtn: ImageView
    private lateinit var fwdBtn: ImageView
    private lateinit var desktopSwitch: Switch

    private var openSheet: View? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var returningFromScreen = false
    private var defaultUa: String? = null

    private val tab get() = tabs[current]

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = Store.get(this)
        bindViews()
        buildMenu()

        val session = savedInstanceState?.getString(STATE_TABS)?.let { parseSession(it) }
            ?: if (prefs.restoreTabs) store.loadSession() else null
        session?.first?.forEach { tabs.add(Tab(it.first, it.second)) }
        if (tabs.isEmpty()) tabs.add(Tab(HOME, ""))
        val active = (session?.second ?: 0).coerceIn(0, tabs.size - 1)

        if (!openFromIntent(intent)) switchTo(active, animate = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (appliedLook != currentLook()) { recreate(); return }
        tabs.getOrNull(current)?.web?.let { it.resumeTimers(); it.onResume() }
        tabs.forEach { t -> t.web?.let { applySettings(it) } }
        if (prefs.clearCachePending) {
            tabs.forEach { it.web?.clearCache(true) }
            prefs.clearCachePending = false
        }
        if (returningFromScreen && current >= 0 && isHome(tab.url)) tab.web?.let { load(it, HOME) }
        returningFromScreen = false
        updateTabCount()
    }

    override fun onPause() {
        super.onPause()
        // Pauses JS timers in every WebView while we're in the background — saves battery.
        tabs.getOrNull(current)?.web?.let { it.onPause(); it.pauseTimers() }
        CookieManager.getInstance().flush()
        store.saveSession(tabs.map { it.url to it.title }, current)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TABS, sessionJson())
    }

    override fun onDestroy() {
        tabs.forEach { t -> t.web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() } }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (prefs.theme == "system") {
            val nowDark = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            if (appliedLook.substringBefore('|').toBoolean() != nowDark) recreate()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // System is low on memory: put every background tab to sleep.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) trimLive(1)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            customView != null -> exitFullscreen()
            openSheet != null -> hideSheet()
            address.hasFocus() -> clearAddressFocus()
            tab.web?.canGoBack() == true -> tab.web?.goBack()
            tabs.size > 1 -> closeTab(current)
            else -> super.onBackPressed()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_FILE -> {
                fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
                fileCallback = null
            }
            REQ_LIST -> data?.getStringExtra(ListActivity.EXTRA_URL)?.let { navigate(it) }
        }
    }

    // ---------------------------------------------------------------- views

    private fun bindViews() {
        root = findViewById(R.id.root)
        container = findViewById(R.id.webContainer)
        bottomBar = findViewById(R.id.bottomBar)
        pill = findViewById(R.id.addressPill)
        address = findViewById(R.id.address)
        siteIcon = findViewById(R.id.siteIcon)
        actionBtn = findViewById(R.id.actionBtn)
        tabCount = findViewById(R.id.tabCount)
        progress = findViewById(R.id.progress)
        scrim = findViewById(R.id.scrim)
        menuSheet = findViewById(R.id.menuSheet)
        tabsSheet = findViewById(R.id.tabsSheet)
        tabsList = findViewById(R.id.tabsList)
        tabsScroll = findViewById(R.id.tabsScroll)

        address.setOnFocusChangeListener { _, focused ->
            pill.setBackgroundResource(if (focused) R.drawable.bg_pill_focus else R.drawable.bg_pill)
            if (focused) {
                val u = tab.url
                address.setText(if (isHome(u)) "" else u)
                address.selectAll()
                actionBtn.setImageResource(R.drawable.ic_close)
            } else updateAddress()
        }
        address.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                navigate(resolve(address.text.toString()))
                true
            } else false
        }
        actionBtn.setOnClickListener {
            if (address.hasFocus()) address.setText("")
            else {
                val w = tab.web ?: return@setOnClickListener
                if (w.progress < 100) w.stopLoading() else w.reload()
            }
        }
        findViewById<View>(R.id.tabsBtn).setOnClickListener { renderTabs(); showSheet(tabsSheet) }
        findViewById<View>(R.id.menuBtn).setOnClickListener { refreshMenu(); showSheet(menuSheet) }
        findViewById<View>(R.id.newTabBtn).setOnClickListener { hideSheet(); newTab(HOME) }
        scrim.setOnClickListener { hideSheet() }
    }

    private fun buildMenu() {
        val quick = findViewById<LinearLayout>(R.id.quickRow)
        fun quickBtn(icon: Int, desc: Int, action: () -> Unit) = ImageView(this).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(color(R.attr.auraText))
            contentDescription = getString(desc)
            background = context.getDrawable(R.drawable.bg_ripple_quick)
            val p = dp(15)
            setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(4); marginEnd = dp(4) }
            setOnClickListener { action() }
            quick.addView(this)
        }
        backBtn = quickBtn(R.drawable.ic_back, R.string.back) { tab.web?.goBack(); hideSheet() }
        fwdBtn = quickBtn(R.drawable.ic_forward, R.string.forward) { tab.web?.goForward(); hideSheet() }
        quickBtn(R.drawable.ic_reload, R.string.reload) { tab.web?.reload(); hideSheet() }
        starBtn = quickBtn(R.drawable.ic_star, R.string.add_bookmark) { toggleBookmark() }
        quickBtn(R.drawable.ic_share, R.string.share) { share(); hideSheet() }

        val list = findViewById<LinearLayout>(R.id.menuList)
        fun row(icon: Int, label: Int, trailing: View? = null, action: () -> Unit) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = context.getDrawable(R.drawable.bg_ripple)
            setPadding(dp(24), 0, dp(20), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(color(R.attr.auraAccent))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            })
            addView(text(getString(label), 16f).apply {
                setPadding(dp(18), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            trailing?.let { addView(it) }
            setOnClickListener { action() }
            list.addView(this)
        }
        row(R.drawable.ic_add, R.string.new_tab) { hideSheet(); newTab(HOME) }
        row(R.drawable.ic_home, R.string.home) { hideSheet(); navigate(HOME) }
        row(R.drawable.ic_bookmarks, R.string.bookmarks) { hideSheet(); openList(Store.KEY_BOOKMARKS) }
        row(R.drawable.ic_history, R.string.history) { hideSheet(); openList(Store.KEY_HISTORY) }
        desktopSwitch = Switch(this).apply { isClickable = false; isFocusable = false; tintSwitch(this) }
        row(R.drawable.ic_desktop, R.string.desktop_site, desktopSwitch) {
            prefs.desktopMode = !prefs.desktopMode
            desktopSwitch.isChecked = prefs.desktopMode
            tabs.forEach { t -> t.web?.let { applySettings(it) } }
            tab.web?.reload()
            hideSheet()
        }
        row(R.drawable.ic_settings, R.string.settings) { hideSheet(); openScreen(Intent(this, SettingsActivity::class.java)) }
        row(R.drawable.ic_exit, R.string.exit) { finishAndRemoveTask() }
    }

    private fun tintSwitch(s: Switch) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        s.thumbTintList = ColorStateList(states, intArrayOf(color(R.attr.auraAccent), color(R.attr.auraTextDim)))
        s.trackTintList = ColorStateList(states, intArrayOf(color(R.attr.auraRipple) or 0x66000000, color(R.attr.auraStroke)))
    }

    private fun refreshMenu() {
        val w = tab.web
        backBtn.alpha = if (w?.canGoBack() == true) 1f else 0.35f
        fwdBtn.alpha = if (w?.canGoForward() == true) 1f else 0.35f
        val canStar = !isHome(tab.url)
        starBtn.alpha = if (canStar) 1f else 0.35f
        starBtn.setImageResource(if (store.isBookmarked(tab.url)) R.drawable.ic_star_filled else R.drawable.ic_star)
        starBtn.imageTintList = ColorStateList.valueOf(color(if (store.isBookmarked(tab.url)) R.attr.auraAccent else R.attr.auraText))
        desktopSwitch.isChecked = prefs.desktopMode
    }

    // ---------------------------------------------------------------- sheets

    private fun showSheet(sheet: View) {
        if (openSheet === sheet) return
        clearAddressFocus()
        openSheet?.let { it.visibility = View.GONE }
        openSheet = sheet
        scrim.visibility = View.VISIBLE
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(dur(200)).start()
        sheet.translationY = resources.displayMetrics.heightPixels.toFloat()
        sheet.visibility = View.VISIBLE
        sheet.post {
            sheet.translationY = sheet.height.toFloat()
            sheet.animate().translationY(0f).setDuration(dur(260)).setInterpolator(DecelerateInterpolator(2.2f)).start()
        }
    }

    private fun hideSheet() {
        val sheet = openSheet ?: return
        openSheet = null
        scrim.animate().alpha(0f).setDuration(dur(180)).withEndAction { scrim.visibility = View.GONE }.start()
        sheet.animate().translationY(sheet.height.toFloat()).setDuration(dur(200))
            .setInterpolator(DecelerateInterpolator()).withEndAction { sheet.visibility = View.GONE }.start()
    }

    private fun renderTabs() {
        tabsList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        tabs.forEachIndexed { i, t ->
            val item = inflater.inflate(R.layout.item_tab, tabsList, false)
            val home = isHome(t.url)
            val title = when { home -> getString(R.string.new_tab); t.title.isNotBlank() -> t.title; else -> display(t.url) }
            item.findViewById<TextView>(R.id.title).text = title
            item.findViewById<TextView>(R.id.letter).text = (title.trim().firstOrNull() ?: 'A').uppercaseChar().toString()
            item.findViewById<TextView>(R.id.subtitle).text = when {
                i != current && t.web == null -> getString(R.string.sleeping)
                home -> getString(R.string.start_page)
                else -> display(t.url)
            }
            if (i == current) item.setBackgroundResource(R.drawable.bg_tab_item_active)
            item.setOnClickListener { hideSheet(); if (i != current) switchTo(i) }
            item.findViewById<View>(R.id.close).setOnClickListener {
                item.animate().alpha(0f).translationX(item.width * 0.3f).setDuration(dur(160)).withEndAction {
                    closeTab(i)
                    if (openSheet === tabsSheet) renderTabs()
                }.start()
            }
            tabsList.addView(item)
        }
        // Cap the list height so the sheet never covers the whole screen.
        val maxH = (resources.displayMetrics.heightPixels * 0.6f).toInt()
        val wanted = tabs.size * dp(72) + dp(14)
        tabsScroll.layoutParams = tabsScroll.layoutParams.apply { height = if (wanted > maxH) maxH else ViewGroup.LayoutParams.WRAP_CONTENT }
        tabsScroll.post { tabsList.getChildAt(current)?.let { tabsScroll.scrollTo(0, it.top - dp(8)) } }
    }

    // ---------------------------------------------------------------- tabs

    private fun newTab(url: String) {
        tabs.add(Tab(url, ""))
        switchTo(tabs.size - 1)
        tabCount.animate().cancel()
        tabCount.scaleX = 0.6f; tabCount.scaleY = 0.6f
        tabCount.animate().scaleX(1f).scaleY(1f).setDuration(dur(300)).setInterpolator(OvershootInterpolator(3f)).start()
    }

    private fun closeTab(index: Int) {
        val wasCurrent = index == current
        val t = tabs.removeAt(index)
        t.web?.let { container.removeView(it); it.destroy() }
        when {
            tabs.isEmpty() -> { current = -1; tabs.add(Tab(HOME, "")); switchTo(0) }
            wasCurrent -> { current = -1; switchTo(index.coerceAtMost(tabs.size - 1)) }
            index < current -> { current--; updateTabCount() }
            else -> updateTabCount()
        }
    }

    private fun switchTo(index: Int, animate: Boolean = true) {
        tabs.getOrNull(current)?.web?.let { it.onPause(); container.removeView(it) }
        current = index
        val t = tab
        t.lastUsed = SystemClock.elapsedRealtime()
        val w = t.web ?: wake(t)
        if (w.parent == null) container.addView(w, 0)
        w.onResume()
        if (animate && prefs.smoothMotion) {
            w.alpha = 0f
            w.animate().alpha(1f).setDuration(180).start()
        } else w.alpha = 1f
        trimLive(prefs.maxLiveTabs)
        updateAddress()
        updateTabCount()
        setProgress(w.progress)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun wake(t: Tab): WebView {
        val w = WebView(this)
        w.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        w.setBackgroundColor(color(R.attr.auraBg))
        w.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        applySettings(w)
        w.webViewClient = Client(t)
        w.webChromeClient = Chrome(t)
        w.setDownloadListener { url, ua, cd, mime, _ -> download(url, ua, cd, mime) }
        w.setOnLongClickListener { linkMenu(w) }
        // Background tabs get a lower renderer priority automatically.
        w.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        t.web = w
        val st = t.state
        t.state = null
        if (st == null || w.restoreState(st) == null) load(w, t.url)
        return w
    }

    /** Put a tab to sleep: keep its history, free the WebView. */
    private fun freeze(t: Tab) {
        val w = t.web ?: return
        val b = Bundle()
        w.saveState(b)
        t.state = b
        (w.parent as? ViewGroup)?.removeView(w)
        w.stopLoading()
        w.destroy()
        t.web = null
    }

    private fun trimLive(max: Int) {
        if (current < 0) return
        val active = tab
        val live = tabs.filter { it.web != null && it !== active }.sortedBy { it.lastUsed }
        var excess = live.size + 1 - max.coerceAtLeast(1)
        for (t in live) { if (excess <= 0) break; freeze(t); excess-- }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applySettings(w: WebView) {
        val s = w.settings
        if (defaultUa == null) defaultUa = s.userAgentString
        s.javaScriptEnabled = prefs.javaScript || isHome(w.url)
        s.domStorageEnabled = true
        s.blockNetworkImage = prefs.dataSaver
        s.loadsImagesAutomatically = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.textZoom = prefs.textZoom
        s.javaScriptCanOpenWindowsAutomatically = !prefs.blockPopups
        s.setSupportMultipleWindows(false)
        s.mediaPlaybackRequiresUserGesture = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.cacheMode = WebSettings.LOAD_DEFAULT
        val ua = if (prefs.desktopMode) desktopUa() else null
        if (s.userAgentString != (ua ?: defaultUa)) s.userAgentString = ua
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, !prefs.blockThirdPartyCookies)
    }

    private fun desktopUa() = (defaultUa ?: "")
        .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)")
        .replace(" Mobile", "")

    // ---------------------------------------------------------------- navigation

    private fun navigate(url: String) {
        clearAddressFocus()
        tab.web?.let { load(it, url) } ?: run { tab.url = url; switchTo(current, animate = false) }
    }

    /** Loads a URL; the built-in start page always gets JavaScript, websites follow the setting. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun load(w: WebView, url: String) {
        w.settings.javaScriptEnabled = prefs.javaScript || isHome(url)
        w.loadUrl(if (isHome(url)) homeUrl() else url)
    }

    private fun resolve(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return HOME
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(s) || s.startsWith("about:")) return s
        if (!s.contains(' ') && (Patterns.WEB_URL.matcher(s).matches() || s.startsWith("localhost"))) {
            return (if (s.startsWith("localhost") || Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+").containsMatchIn(s)) "http://" else "https://") + s
        }
        return searchUrl(s)
    }

    private fun searchUrl(q: String) =
        if (prefs.engine == "yandex") "https://yandex.ru/search/?text=" + Uri.encode(q)
        else "https://www.google.com/search?q=" + Uri.encode(q)

    private fun homeUrl(): String {
        val sites = JSONArray()
        store.bookmarks.take(8).forEach { sites.put(JSONObject().put("t", it.title).put("u", it.url)) }
        return "$HOME_ASSET?theme=${if (isDark()) "dark" else "light"}&lang=${prefs.lang}&engine=${prefs.engine}" +
            (if (prefs.smoothMotion) "" else "&calm=1") + "#" + Uri.encode(sites.toString())
    }

    private fun isHome(url: String?) = url.isNullOrBlank() || url == HOME || url.startsWith(HOME_ASSET)
    private fun normalize(url: String?) = if (isHome(url)) HOME else url!!

    private fun display(url: String) = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

    private fun openFromIntent(i: Intent?): Boolean {
        if (i?.action != Intent.ACTION_VIEW) return false
        val data = i.dataString ?: return false
        if (!data.startsWith("http://") && !data.startsWith("https://")) return false
        i.action = null
        newTab(data)
        return true
    }

    private fun handleAura(host: String?) {
        when (host) {
            "settings" -> openScreen(Intent(this, SettingsActivity::class.java))
            "bookmarks" -> openList(Store.KEY_BOOKMARKS)
            "history" -> openList(Store.KEY_HISTORY)
            "newtab" -> newTab(HOME)
        }
    }

    private fun openScreen(i: Intent) { returningFromScreen = true; startActivity(i) }

    private fun openList(kind: String) {
        returningFromScreen = true
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, ListActivity::class.java).putExtra(ListActivity.EXTRA_KIND, kind), REQ_LIST)
    }

    // ---------------------------------------------------------------- address bar & progress

    private fun updateAddress() {
        if (current < 0 || address.hasFocus()) return
        val u = tab.url
        address.setText(if (isHome(u)) "" else display(u))
        siteIcon.setImageResource(if (u.startsWith("https://")) R.drawable.ic_lock else R.drawable.ic_search)
        val loading = (tab.web?.progress ?: 100) < 100
        actionBtn.setImageResource(if (loading) R.drawable.ic_close else R.drawable.ic_reload)
    }

    private fun updateTabCount() {
        tabCount.text = if (tabs.size > 99) ":)" else tabs.size.toString()
    }

    private fun setProgress(p: Int) {
        progress.animate().cancel()
        if (p >= 100) {
            progress.animate().scaleX(1f).setDuration(dur(120)).withEndAction {
                progress.animate().alpha(0f).setDuration(dur(220)).withEndAction { progress.scaleX = 0f }.start()
            }.start()
        } else {
            progress.alpha = 1f
            progress.animate().scaleX(p.coerceAtLeast(8) / 100f).setDuration(dur(160)).start()
        }
        if (!address.hasFocus()) actionBtn.setImageResource(if (p < 100) R.drawable.ic_close else R.drawable.ic_reload)
    }

    private fun clearAddressFocus() {
        if (!address.hasFocus()) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.windowToken, 0)
        if (tabs.getOrNull(current)?.web?.requestFocus() != true) address.clearFocus()
    }

    // ---------------------------------------------------------------- actions

    private fun toggleBookmark() {
        if (isHome(tab.url)) return
        val added = store.toggleBookmark(tab.title, tab.url)
        refreshMenu()
        starBtn.scaleX = 0.7f; starBtn.scaleY = 0.7f
        starBtn.animate().scaleX(1f).scaleY(1f).setDuration(dur(320)).setInterpolator(OvershootInterpolator(3f)).start()
        Toast.makeText(this, if (added) R.string.bookmark_added else R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
    }

    private fun share() {
        if (isHome(tab.url)) return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tab.url)
        startActivity(Intent.createChooser(send, tab.title.ifBlank { getString(R.string.share) }))
    }

    private fun linkMenu(w: WebView): Boolean {
        val r = w.hitTestResult
        if (r.type != WebView.HitTestResult.SRC_ANCHOR_TYPE) return false
        val link = r.extra ?: return false
        AlertDialog.Builder(this)
            .setTitle(display(link))
            .setItems(arrayOf(getString(R.string.open_new_tab), getString(R.string.copy_link))) { _, which ->
                if (which == 0) newTab(link)
                else {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("url", link))
                    Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show()
                }
            }.show()
        return true
    }

    private fun download(url: String, ua: String?, disposition: String?, mime: String?) {
        try {
            val name = URLUtil.guessFileName(url, disposition, mime)
            val req = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mime)
                .addRequestHeader("User-Agent", ua)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (_: Exception) { Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show() }
        }
    }

    @Suppress("DEPRECATION")
    private fun exitFullscreen() {
        val v = customView ?: return
        root.removeView(v)
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and
            (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY).inv()
        bottomBar.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------- session

    private fun sessionJson(): String {
        val a = JSONArray()
        tabs.forEach { a.put(JSONObject().put("u", it.url).put("t", it.title)) }
        return JSONObject().put("tabs", a).put("active", current).toString()
    }

    private fun parseSession(json: String): Pair<List<Pair<String, String>>, Int>? = try {
        val o = JSONObject(json)
        val a = o.getJSONArray("tabs")
        List(a.length()) { i -> a.getJSONObject(i).let { it.optString("u") to it.optString("t") } } to o.optInt("active")
    } catch (e: Exception) { null }

    // ---------------------------------------------------------------- WebView clients

    private inner class Client(val t: Tab) : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when (uri.scheme?.lowercase()) {
                "http", "https" -> { view.settings.javaScriptEnabled = prefs.javaScript; false }
                "file", "about", "data", "blob", "javascript" -> false
                "aura" -> { handleAura(uri.host); true }
                "intent" -> {
                    try {
                        val i = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        i.addCategory(Intent.CATEGORY_BROWSABLE)
                        i.component = null
                        i.selector = null
                        try { startActivity(i) } catch (e: Exception) {
                            i.getStringExtra("browser_fallback_url")?.let { view.loadUrl(it) }
                        }
                    } catch (_: Exception) { }
                    true
                }
                else -> {
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)) }
                    catch (_: Exception) { Toast.makeText(this@MainActivity, R.string.no_app, Toast.LENGTH_SHORT).show() }
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            t.url = normalize(url)
            if (isHome(url)) t.title = ""
            if (isCurrent(t)) { updateAddress(); setProgress(8) }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            t.url = normalize(url)
            if (isCurrent(t)) { setProgress(100); updateAddress() }
            if (prefs.saveHistory && !isHome(url) && url != null && url.startsWith("http")) {
                store.addHistory(view.title ?: t.title, url)
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            t.url = normalize(url)
            if (isCurrent(t)) updateAddress()
        }

        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
            // The page's renderer crashed or was killed to free memory: rebuild the tab instead of crashing the app.
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            if (t.web === view) { t.web = null; t.state = null }
            if (isCurrent(t)) switchTo(current, animate = false)
            return true
        }
    }

    private inner class Chrome(val t: Tab) : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (isCurrent(t)) setProgress(newProgress)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!isHome(view.url)) t.title = title ?: ""
        }

        @Suppress("DEPRECATION")
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) { callback.onCustomViewHidden(); return }
            customView = view
            customCallback = callback
            view.setBackgroundColor(0xFF000000.toInt())
            root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            bottomBar.visibility = View.GONE
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        override fun onHideCustomView() = exitFullscreen()

        override fun onShowFileChooser(webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            return try {
                @Suppress("DEPRECATION")
                startActivityForResult(params.createIntent(), REQ_FILE)
                true
            } catch (e: Exception) {
                fileCallback = null
                false
            }
        }
    }

    private fun isCurrent(t: Tab) = current in tabs.indices && tabs[current] === t

    companion object {
        const val HOME = "aura://home"
        const val HOME_ASSET = "file:///android_asset/home.html"
        private const val STATE_TABS = "aura_tabs"
        private const val REQ_FILE = 11
        private const val REQ_LIST = 12

        /** Wipes cookies and web storage (used from Settings). Cache is cleared by the next live WebView. */
        fun clearWebData() {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
    }
}
