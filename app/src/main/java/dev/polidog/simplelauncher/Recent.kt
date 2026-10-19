package dev.polidog.simplelauncher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * 最近前面に出たアプリ。他アプリのプロセスは見えないので、使用状況（UsageStats）から近いものを拾う。
 * 使用状況へのアクセスは設定画面でユーザーが許可する必要がある。
 */
object Recent {
    private const val WINDOW_MS = 6 * 60 * 60 * 1000L

    fun granted(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestAccess(context: Context) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 新しい順。ランチャーから起動できるアプリだけ、最大 limit 件。 */
    fun load(context: Context, apps: List<AppEntry>, limit: Int = 6): List<AppEntry> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - WINDOW_MS, now)
        val last = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last[e.packageName] = e.timeStamp
        }
        val byPackage = apps.associateBy { it.packageName }
        return last.entries
            .filter { it.key != context.packageName }
            .sortedByDescending { it.value }
            .mapNotNull { byPackage[it.key] }
            .take(limit)
    }
}
