package dev.polidog.dotlauncher

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat

data class Notice(
    val key: String,
    val packageName: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val intent: PendingIntent?,
    val autoCancel: Boolean,
)

/**
 * 届いている通知をホームに出すための受け口。「通知へのアクセス」を許可すると system が bind する。
 * 消せない通知（再生中の音楽など）とグループのまとめ通知は出さない。
 */
class NoticeListener : NotificationListenerService() {
    override fun onListenerConnected() { instance = this; refresh() }
    override fun onListenerDisconnected() { instance = null; notices.value = emptyList() }
    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        val list = try { activeNotifications } catch (_: SecurityException) { null } ?: return
        notices.value = list
            .filter { it.isClearable && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .filter { it.packageName != packageName }
            .mapNotNull { toNotice(it) }
            .sortedByDescending { it.time }
    }

    private fun toNotice(sbn: StatusBarNotification): Notice? {
        val n = sbn.notification
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return Notice(sbn.key, sbn.packageName, appLabel(sbn.packageName), title, text, sbn.postTime,
            n.contentIntent, n.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) { pkg }

    companion object {
        val notices = mutableStateOf(emptyList<Notice>())
        private var instance: NoticeListener? = null

        fun granted(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        fun requestAccess(context: Context) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        fun dismiss(notice: Notice) { instance?.cancelNotification(notice.key) }

        /** 通知をタップしたときと同じく元のアプリを開く。 */
        fun open(context: Context, notice: Notice) {
            val pi = notice.intent
            if (pi == null) {
                context.packageManager.getLaunchIntentForPackage(notice.packageName)
                    ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            } else {
                // Android 14 以降は送り手（このランチャー）が前面にいることを明示しないと起動が止められる
                val options = if (Build.VERSION.SDK_INT >= 34) {
                    ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        .toBundle()
                } else null
                try {
                    pi.send(context, 0, null, null, null, null, options)
                } catch (_: PendingIntent.CanceledException) { }
            }
            if (notice.autoCancel) dismiss(notice)
        }
    }
}
