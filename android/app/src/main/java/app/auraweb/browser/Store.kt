package app.auraweb.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bookmarks, history and the saved tab session. Small JSON blobs — no database needed. */
class Store private constructor(ctx: Context) {

    data class Entry(val title: String, val url: String, val time: Long)

    private val sp = ctx.getSharedPreferences("aura_store", Context.MODE_PRIVATE)
    val bookmarks: MutableList<Entry> = load(KEY_BOOKMARKS)
    val history: MutableList<Entry> = load(KEY_HISTORY)

    fun list(kind: String) = if (kind == KEY_BOOKMARKS) bookmarks else history

    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    /** @return true if the page is now bookmarked. */
    fun toggleBookmark(title: String, url: String): Boolean {
        val i = bookmarks.indexOfFirst { it.url == url }
        val added = if (i >= 0) { bookmarks.removeAt(i); false }
        else { bookmarks.add(0, Entry(title.ifBlank { url }, url, now())); true }
        save(KEY_BOOKMARKS, bookmarks)
        return added
    }

    fun addHistory(title: String, url: String) {
        val first = history.firstOrNull()
        if (first != null && first.url == url) {
            history[0] = Entry(title.ifBlank { first.title }, url, now())
        } else {
            history.add(0, Entry(title, url, now()))
            if (history.size > MAX_HISTORY) history.subList(MAX_HISTORY, history.size).clear()
        }
        save(KEY_HISTORY, history)
    }

    fun remove(kind: String, index: Int) {
        val l = list(kind)
        if (index in l.indices) { l.removeAt(index); save(kind, l) }
    }

    fun clear(kind: String) { list(kind).clear(); save(kind, list(kind)) }

    fun saveSession(tabs: List<Pair<String, String>>, active: Int) {
        val a = JSONArray()
        tabs.forEach { a.put(JSONObject().put("u", it.first).put("t", it.second)) }
        sp.edit().putString(KEY_SESSION, a.toString()).putInt(KEY_ACTIVE, active).apply()
    }

    fun loadSession(): Pair<List<Pair<String, String>>, Int> = try {
        val a = JSONArray(sp.getString(KEY_SESSION, "[]"))
        List(a.length()) { i -> a.getJSONObject(i).let { it.optString("u") to it.optString("t") } } to sp.getInt(KEY_ACTIVE, 0)
    } catch (e: Exception) { emptyList<Pair<String, String>>() to 0 }

    private fun load(key: String): MutableList<Entry> = try {
        val a = JSONArray(sp.getString(key, "[]"))
        MutableList(a.length()) { i -> a.getJSONObject(i).let { Entry(it.optString("t"), it.optString("u"), it.optLong("d")) } }
    } catch (e: Exception) { mutableListOf() }

    private fun save(key: String, list: List<Entry>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("t", it.title).put("u", it.url).put("d", it.time)) }
        sp.edit().putString(key, a.toString()).apply()
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_HISTORY = "history"
        private const val KEY_SESSION = "session"
        private const val KEY_ACTIVE = "active"
        private const val MAX_HISTORY = 300

        @Volatile private var instance: Store? = null
        fun get(ctx: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(ctx.applicationContext).also { instance = it }
        }
    }
}
