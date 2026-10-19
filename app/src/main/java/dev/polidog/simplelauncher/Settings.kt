package dev.polidog.simplelauncher

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/** ホームの設定。端末内（SharedPreferences）に残す。 */
object Settings {
    private const val PREFS = "settings"

    /** 検索をドラクエのウィンドウ風にする。 */
    val spellSearch = mutableStateOf(true)

    private val keys = mapOf("spell_search" to spellSearch)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for ((key, state) in keys) state.value = prefs.getBoolean(key, state.value)
    }

    fun set(context: Context, state: MutableState<Boolean>, on: Boolean) {
        state.value = on
        val key = keys.entries.first { it.value === state }.key
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, on).apply()
    }
}
