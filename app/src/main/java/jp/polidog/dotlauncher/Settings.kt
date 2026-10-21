package jp.polidog.dotlauncher

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** ホームの設定。端末内（SharedPreferences）に残す。 */
object Settings {
    private const val PREFS = "settings"

    /** 検索をドラクエのウィンドウ風にする。 */
    val spellSearch = mutableStateOf(true)

    /** ダークモードにするか。null なら端末の設定に合わせる。 */
    val darkMode = mutableStateOf<Boolean?>(null)

    /** Jev（TypeSafe）の API キー。ユーザーが自分で取ってきて入れる。空なら Jev を使う機能は出ない。 */
    val jevKey = mutableStateOf("")

    private val keys = mapOf("spell_search" to spellSearch)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for ((key, state) in keys) state.value = prefs.getBoolean(key, state.value)
        darkMode.value = when (prefs.getString("dark_mode", null)) { "dark" -> true; "light" -> false; else -> null }
        // debug ビルドだけ local.properties のキーを初期値にする（release には入れない）
        jevKey.value = prefs.getString("jev_key", null) ?: BuildConfig.TYPESAFE_API_KEY
    }

    fun set(context: Context, state: MutableState<Boolean>, on: Boolean) {
        state.value = on
        val key = keys.entries.first { it.value === state }.key
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, on).apply()
    }

    fun setJevKey(context: Context, key: String) {
        jevKey.value = key.trim()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("jev_key", jevKey.value).apply()
    }

    fun setDarkMode(context: Context, dark: Boolean?) {
        darkMode.value = dark
        val value = when (dark) { true -> "dark"; false -> "light"; null -> null }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("dark_mode", value).apply()
    }
}

/** いまダークモードか。ホームで選んでいればそれ、無ければ端末の設定。 */
@Composable
fun isDark(): Boolean = Settings.darkMode.value ?: isSystemInDarkTheme()
