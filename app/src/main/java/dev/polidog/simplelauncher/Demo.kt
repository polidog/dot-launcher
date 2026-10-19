package dev.polidog.simplelauncher

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf

/**
 * README の動画を撮るためのデモ表示。debug ビルドだけで効く。
 *   adb shell am start -n dev.polidog.simplelauncher/.LauncherActivity --ez demo true   # 入る
 *   adb shell am start -n dev.polidog.simplelauncher/.LauncherActivity --ez demo false  # 戻る
 * 通知・検索履歴・再生中の曲をダミーに差し替える。本物の通知や履歴には触らない。
 */
object Demo {
    val enabled = mutableStateOf(false)
    val notices = mutableStateOf(emptyList<Notice>())
    val track = mutableStateOf<Track?>(null)

    fun apply(context: Context, intent: Intent?) {
        if (!BuildConfig.DEBUG || intent?.hasExtra("demo") != true) return
        val on = intent.getBooleanExtra("demo", false)
        if (on) {
            notices.value = sampleNotices(context.packageName)
            track.value = Track("ひとやすみのうた", "デモ", context.packageName, null, playing = true, controls = null)
            SearchHistory.reset(context, demo = true, listOf("ちず", "しごと"))
        }
        enabled.value = on
    }

    private fun sampleNotices(pkg: String): List<Notice> {
        val now = System.currentTimeMillis()
        return listOf(
            Notice("demo:1", pkg, "メッセージ", "Aさん", "今日のランチどこにする？", now - 3 * 60 * 1000L, null, false),
            Notice("demo:2", pkg, "天気", "午後から雨", "傘を持っていきましょう", now - 25 * 60 * 1000L, null, false),
        )
    }
}
