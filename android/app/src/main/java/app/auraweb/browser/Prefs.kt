package app.auraweb.browser

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources

/** All user settings, backed by a single SharedPreferences file. */
class Prefs private constructor(private val sp: SharedPreferences) {

    var theme: String              // "dark" | "light" | "system"
        get() = sp.getString("theme", "dark") ?: "dark"
        set(v) = sp.edit().putString("theme", v).apply()

    var language: String           // "ru" | "en" | "auto"
        get() = sp.getString("language", "auto") ?: "auto"
        set(v) = sp.edit().putString("language", v).apply()

    var engine: String             // "google" | "yandex"
        get() = sp.getString("engine", "google") ?: "google"
        set(v) = sp.edit().putString("engine", v).apply()

    var javaScript by bool("js", true)
    var blockPopups by bool("popups", true)
    var dataSaver by bool("saver", false)
    var blockThirdPartyCookies by bool("cookies3p", true)
    var saveHistory by bool("history", true)
    var restoreTabs by bool("restore", true)
    var desktopMode by bool("desktop", false)
    var smoothMotion by bool("motion", true)
    var clearCachePending by bool("clearCache", false)

    var textZoom: Int
        get() = sp.getInt("textZoom", 100)
        set(v) = sp.edit().putInt("textZoom", v).apply()

    var maxLiveTabs: Int
        get() = sp.getInt("liveTabs", 3)
        set(v) = sp.edit().putInt("liveTabs", v).apply()

    /** Effective UI language: explicit choice, or Russian if the phone is in Russian. */
    val lang: String
        get() = when (language) {
            "ru", "en" -> language
            else -> if (Resources.getSystem().configuration.locales[0].language == "ru") "ru" else "en"
        }

    private fun bool(key: String, def: Boolean) = object : kotlin.properties.ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = sp.getBoolean(key, def)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Boolean) {
            sp.edit().putBoolean(key, value).apply()
        }
    }

    companion object {
        @Volatile private var instance: Prefs? = null
        fun get(ctx: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs((ctx.applicationContext ?: ctx).getSharedPreferences("aura_prefs", Context.MODE_PRIVATE))
                .also { instance = it }
        }
    }
}
