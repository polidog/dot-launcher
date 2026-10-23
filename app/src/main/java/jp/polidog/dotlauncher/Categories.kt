package jp.polidog.dotlauncher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

/** ホームの右のページで並べるカテゴリ。並び順はこの順。 */
enum class Category(val label: String) {
    Contact("連絡・SNS"),
    Work("仕事"),
    Maps("地図・移動"),
    Money("お金"),
    Shopping("買い物"),
    News("ニュース・読みもの"),
    Music("音楽"),
    Video("動画"),
    Photo("写真・カメラ"),
    Game("ゲーム"),
    Tool("道具・設定"),
    Other("その他"),
}

/**
 * アプリをカテゴリに振り分ける。人は何もしない。
 * まずアプリの作者が申告したカテゴリ（ApplicationInfo.category）を使い、申告の無いアプリと「仕事」と申告したアプリは Jev に聞く。
 * Jev の答えは端末内（SharedPreferences）に残し、同じアプリについて聞くのは 1 回だけ。
 */
object Categories {
    private const val PREFS = "categories"
    private const val CHUNK = 20 // 1 リクエストで聞くアプリの数

    /** やり直した回数。増えるとランチャーが Jev に聞き直す。 */
    val generation = mutableIntStateOf(0)

    /** いま Jev に聞いている最中か。設定の行の表示に使う。 */
    val busy = mutableStateOf(false)

    /** Jev の答えを全部捨てて、聞き直す。分類が変だったときや、カテゴリの種類を変えたあとに人が設定から呼ぶ。 */
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        generation.intValue++
    }

    /** 通信しないで分かるぶん。申告も Jev の答えも無いアプリは Other。 */
    fun load(context: Context, apps: List<AppEntry>): Map<AppEntry, Category> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return apps.associateWith { app ->
            declared(app.category)
                ?: prefs.getString(app.packageName, null)?.let { runCatching { Category.valueOf(it) }.getOrNull() }
                ?: Category.Other
        }
    }

    /** 申告も Jev の答えも無いアプリ。 */
    fun unknown(context: Context, apps: List<AppEntry>): List<AppEntry> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return apps.filter { declared(it.category) == null && !prefs.contains(it.packageName) }
            .distinctBy { it.packageName }
    }

    /**
     * unknown を Jev に聞いて残す。答えが 1 件でも増えたら true。通信するので IO スレッドから。
     * 一覧の読み直しで重なって呼ばれても、後のほうは前の答えを待ってから残りだけ聞く。
     */
    @Synchronized
    fun classify(context: Context, apps: List<AppEntry>): Boolean {
        if (!JevSearch.available) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var added = false
        busy.value = true
        try {
            for (chunk in unknown(context, apps).chunked(CHUNK)) {
                val answers = ask(chunk) ?: break
                prefs.edit().apply { answers.forEach { (pkg, c) -> putString(pkg, c.name) } }.apply()
                added = added || answers.isNotEmpty()
            }
        } finally {
            busy.value = false
        }
        return added
    }

    /** アプリごとに Choice を 1 つ立て、まとめて 1 リクエストで聞く。packageName → カテゴリ。 */
    private fun ask(apps: List<AppEntry>): Map<String, Category>? {
        val list = JSONArray()
        apps.forEachIndexed { i, app -> list.put(JSONObject().put("id", "a$i").put("name", app.label).put("package", app.packageName)) }
        val categories = JSONArray()
        Category.entries.forEach { categories.put(JSONObject().put("id", it.name).put("name", it.label)) }
        val questions = JSONObject()
        apps.indices.forEach { i ->
            questions.put("a$i", JSONObject()
                .put("type", "choice")
                .put("instructions", "`apps` の id が a$i のアプリは、`categories` のどれに入れるのが自然か。アプリの主な用途で判断する。どれにも当てはまらなければ Other。")
                .put("criteria", JSONObject().apply { Category.entries.forEach { put(it.name, JSONObject.NULL) } }))
        }
        val body = JSONObject()
            .put("model", JevSearch.MODEL)
            .put("state", JSONObject()
                .put("context", "Android のホーム画面で、端末に入っているアプリをカテゴリ別に並べる。`apps` は振り分けるアプリ、`categories` は並べる先。")
                .put("apps", list)
                .put("categories", categories))
            .put("questions", questions)
            .toString()
        return try {
            val res = JevSearch.post(body) ?: return null
            val answers = res.getJSONObject("answers")
            apps.indices.associate { i ->
                val probs = answers.getJSONObject("a$i").getJSONObject("probabilities")
                val best = Category.entries.maxBy { probs.optDouble(it.name, 0.0) }
                apps[i].packageName to best
            }.also { Log.i("Categories", it.entries.joinToString { (p, c) -> "$p=${c.name}" }) }
        } catch (e: Exception) {
            Log.w("Categories", "failed: $e")
            null
        }
    }

    /**
     * アプリの作者が申告したカテゴリ。申告が無ければ null。
     * PRODUCTIVITY はブラウザ・メール・決済まで入って広すぎるので、申告が無いのと同じに扱い Jev に聞く。
     */
    private fun declared(category: Int): Category? = when (category) {
        ApplicationInfo.CATEGORY_SOCIAL -> Category.Contact
        ApplicationInfo.CATEGORY_MAPS -> Category.Maps
        ApplicationInfo.CATEGORY_NEWS -> Category.News
        ApplicationInfo.CATEGORY_AUDIO -> Category.Music
        ApplicationInfo.CATEGORY_VIDEO -> Category.Video
        ApplicationInfo.CATEGORY_IMAGE -> Category.Photo
        ApplicationInfo.CATEGORY_GAME -> Category.Game
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> Category.Tool
        else -> null
    }
}
