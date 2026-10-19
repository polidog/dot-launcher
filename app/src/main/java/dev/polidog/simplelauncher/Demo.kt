package dev.polidog.simplelauncher

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import java.util.Calendar

/**
 * README の動画を撮るためのデモ表示。debug ビルドだけで効く。
 *   adb shell am start -n dev.polidog.simplelauncher/.LauncherActivity --ez demo true   # 入る
 *   adb shell am start -n dev.polidog.simplelauncher/.LauncherActivity --ez demo false  # 戻る
 * 予定・通知・検索履歴をダミーに差し替える。本物のカレンダーや通知、履歴には触らない。
 */
object Demo {
    val enabled = mutableStateOf(false)
    val notices = mutableStateOf(emptyList<Notice>())

    fun apply(context: Context, intent: Intent?) {
        if (!BuildConfig.DEBUG || intent?.hasExtra("demo") != true) return
        val on = intent.getBooleanExtra("demo", false)
        if (on) {
            notices.value = sampleNotices(context.packageName)
            SearchHistory.reset(context, demo = true, listOf("ちず", "しごと"))
        }
        enabled.value = on
    }

    /** 次の正時から 1 時間の予定。 */
    fun agenda(): Agenda {
        val begin = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return Agenda("ランチ", begin, begin + 60 * 60 * 1000L)
    }

    private fun sampleNotices(pkg: String): List<Notice> {
        val now = System.currentTimeMillis()
        return listOf(
            Notice("demo:1", pkg, "メッセージ", "Aさん", "今日のランチどこにする？", now - 3 * 60 * 1000L, null, false),
            Notice("demo:2", pkg, "天気", "午後から雨", "傘を持っていきましょう", now - 25 * 60 * 1000L, null, false),
        )
    }
}
