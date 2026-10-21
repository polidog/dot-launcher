package jp.polidog.dotlauncher

import android.content.Context
import org.json.JSONArray

/**
 * アプリを開くのに使った検索語。新しい順に MAX 件まで、端末内（SharedPreferences）にだけ残す。
 * デモ表示の間は別の置き場を使い、本物の履歴には触らない。
 */
object SearchHistory {
    private val prefs get() = if (Demo.enabled.value) "search_history_demo" else "search_history"
    private const val KEY = "queries"
    private const val MAX = 10

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return List(arr.length()) { arr.getString(it) }
    }

    fun add(context: Context, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return load(context)
        return save(context, (listOf(q) + load(context).filter { it != q }).take(MAX))
    }

    fun remove(context: Context, query: String): List<String> = save(context, load(context).filter { it != query })

    /** デモに入るときに履歴を入れ直す。 */
    fun reset(context: Context, demo: Boolean, list: List<String>) {
        context.getSharedPreferences(if (demo) "search_history_demo" else "search_history", Context.MODE_PRIVATE).edit()
            .putString(KEY, JSONArray(list).toString())
            .apply()
    }

    private fun save(context: Context, list: List<String>): List<String> {
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit()
            .putString(KEY, JSONArray(list).toString())
            .apply()
        return list
    }
}
