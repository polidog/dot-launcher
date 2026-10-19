package dev.polidog.simplelauncher

import android.content.Context
import org.json.JSONArray

/** アプリを開くのに使った検索語。新しい順に MAX 件まで、端末内（SharedPreferences）にだけ残す。 */
object SearchHistory {
    private const val PREFS = "search_history"
    private const val KEY = "queries"
    private const val MAX = 10

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return List(arr.length()) { arr.getString(it) }
    }

    fun add(context: Context, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return load(context)
        return save(context, (listOf(q) + load(context).filter { it != q }).take(MAX))
    }

    fun remove(context: Context, query: String): List<String> = save(context, load(context).filter { it != query })

    private fun save(context: Context, list: List<String>): List<String> {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, JSONArray(list).toString())
            .apply()
        return list
    }
}
