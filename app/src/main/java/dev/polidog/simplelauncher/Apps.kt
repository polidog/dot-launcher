package dev.polidog.simplelauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/** removable: 端末から消せるか（プリインストールのシステムアプリは消せない）。 */
data class AppEntry(val label: String, val packageName: String, val activity: String, val removable: Boolean) {
    val key: String = label.lowercase()
}

object Apps {
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                val flags = it.activityInfo.applicationInfo.flags
                val system = flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == ApplicationInfo.FLAG_SYSTEM
                AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name, removable = !system)
            }
            // 起動口が複数あって名前も同じもの（東海道五十三次など）は見分けがつかないので 1 つにする
            .distinctBy { it.packageName to it.label }
            .sortedBy { it.key }
    }

    fun launch(context: Context, app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
    }

    /** システムの削除確認ダイアログを出す。消えたかどうかはパッケージ削除のブロードキャストで拾う。 */
    fun uninstall(context: Context, app: AppEntry) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openInfo(context: Context, app: AppEntry) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** walker 風: 先頭一致 > 単語頭一致 > 部分一致 > 部分列一致 の順で並べる。 */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> = scored(apps, query).map { it.second }

    /** filter の並びに一致の強さ（0: 先頭一致 〜 3: 部分列一致）を付けたもの。 */
    fun scored(apps: List<AppEntry>, query: String): List<Pair<Int, AppEntry>> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return apps.map { 0 to it }
        return apps.mapNotNull { app ->
            val k = app.key
            val score = when {
                k.startsWith(q) -> 0
                k.split(' ', '-', '_').any { it.startsWith(q) } -> 1
                k.contains(q) -> 2
                isSubsequence(q, k) -> 3
                else -> return@mapNotNull null
            }
            score to app
        }.sortedWith(compareBy({ it.first }, { it.second.key }))
    }

    private fun isSubsequence(q: String, s: String): Boolean {
        var i = 0
        for (ch in s) if (i < q.length && q[i] == ch) i++
        return i == q.length
    }
}
