package jp.polidog.dotlauncher

import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 文字の一致で見つからないとき、言葉の意味でアプリを選ぶ（「写真」→ Google フォト）。
 * アプリ一覧を state に、Choice でどれを開きたいかを、Noul で該当するアプリがあるかを 1 リクエストで聞く。
 */
object JevSearch {
    /** probabilities: 選ばれたアプリの確率（高い順）。rttMs は通信込みの所要時間。 */
    class Result(val apps: List<Pair<AppEntry, Float>>, val rttMs: Long, val inputTokens: Int)

    private const val MAX_OPTIONS = 255 // Choice の選択肢の上限
    private const val READ_TIMEOUT_MS = 5000
    // 家の回線は IPv6 が api.typesafe.ai に届かず、IPv6 の各アドレスで接続待ちを使い切ってから IPv4 に回る。
    // 待ちを短くして、届かない経路は warmUp で先に踏んでおく
    private const val CONNECT_TIMEOUT_MS = 1500
    private const val WARM_INTERVAL_MS = 5 * 60 * 1000L
    private const val ENDPOINT = "https://api.typesafe.ai/v1/systemone"
    const val MODEL = "jev-1.14.0"
    @Volatile private var lastWarmMs = 0L

    /** キーはユーザーが設定で入れる。 */
    val available: Boolean get() = Settings.jevKey.value.isNotEmpty()

    private val cache = object : LinkedHashMap<String, Result>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Result>) = size > 64
    }

    /** 接続を張っておく（届かない経路を先に踏み、TLS を済ませる）。IO スレッドから。5 分に 1 回まで。 */
    fun warmUp() {
        val now = SystemClock.uptimeMillis()
        if (!available || (lastWarmMs != 0L && now - lastWarmMs < WARM_INTERVAL_MS)) return
        lastWarmMs = now
        try {
            val conn = open("GET")
            conn.responseCode // 中身は見ない。読み切って接続を使い回せるように残す
            (conn.errorStream ?: conn.inputStream)?.use { it.readBytes() }
            Log.i("JevSearch", "warm ${SystemClock.uptimeMillis() - now}ms")
        } catch (e: Exception) {
            lastWarmMs = 0L
            Log.w("JevSearch", "warm failed: $e")
        }
    }

    private fun open(method: String): HttpURLConnection =
        (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = method
            setRequestProperty("Authorization", "Bearer ${Settings.jevKey.value}")
        }

    /** body を送って返事の JSON を返す。HTTP が 200 でなければ null、通信の失敗は投げる。IO スレッドから。 */
    fun post(body: String): JSONObject? {
        // disconnect() はしない。読み切った接続は次のリクエストで使い回される
        val conn = open("POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toByteArray()) }
        if (conn.responseCode != 200) {
            Log.w("JevSearch", "HTTP ${conn.responseCode}: ${conn.errorStream?.bufferedReader()?.use { it.readText() }}")
            return null
        }
        return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    }

    /** 通信するので IO スレッドから呼ぶ。失敗したら null。 */
    fun search(apps: List<AppEntry>, query: String): Result? {
        val q = query.trim()
        val cacheKey = "${apps.size}:$q"
        synchronized(cache) { cache[cacheKey] }?.let { return Result(it.apps, 0, 0) }

        val candidates = apps.take(MAX_OPTIONS)
        val ids = candidates.indices.map { "a$it" }
        val list = JSONArray()
        candidates.forEachIndexed { i, app -> list.put(JSONObject().put("id", ids[i]).put("name", app.label).put("package", app.packageName)) }
        val criteria = JSONObject().apply { ids.forEach { put(it, JSONObject.NULL) } }
        val body = JSONObject()
            .put("model", MODEL)
            .put("state", JSONObject()
                .put("query", q)
                .put("context", "Android のホーム画面のアプリ検索欄に `query` が打たれた。`apps` は端末に入っているアプリ。")
                .put("apps", list))
            .put("questions", JSONObject()
                .put("app", JSONObject()
                    .put("type", "choice")
                    .put("instructions", "`query` と打った人が開こうとしているのは `apps` のどのアプリか。名前の一致だけでなく、アプリの用途から判断する。id で答える。")
                    .put("criteria", criteria))
                .put("exists", JSONObject()
                    .put("type", "noul")
                    .put("instructions", "`apps` の中に、`query` と打った人が開こうとしているアプリ、または `query` の用途に使えるアプリがあるか。")))
            .toString()

        val start = SystemClock.uptimeMillis()
        try {
            val res = post(body) ?: return null
            val rtt = SystemClock.uptimeMillis() - start
            val answers = res.getJSONObject("answers")
            val exists = answers.getJSONObject("exists").getDouble("noul").toFloat()
            val probs = answers.getJSONObject("app").getJSONObject("probabilities")
            // ponytail: 閾値は目分量。該当なしが 0.3 未満、確率 5% 未満は出さない
            val picked = if (exists < 0.3f) emptyList() else ids.indices
                .map { candidates[it] to probs.optDouble(ids[it], 0.0).toFloat() }
                .filter { it.second >= 0.05f }
                .sortedByDescending { it.second }
                .take(5)
            val tokens = res.optJSONObject("usage")?.optInt("input_tokens") ?: 0
            Log.i("JevSearch", "q=$q rtt=${rtt}ms tokens=$tokens exists=$exists top=${picked.joinToString { "${it.first.label}:${"%.2f".format(it.second)}" }}")
            return Result(picked, rtt, tokens).also { synchronized(cache) { cache[cacheKey] = it } }
        } catch (e: Exception) {
            Log.w("JevSearch", "failed: $e")
            return null
        }
    }
}
