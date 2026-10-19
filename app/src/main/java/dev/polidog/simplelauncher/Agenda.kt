package dev.polidog.simplelauncher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Agenda(val title: String, val begin: Long, val end: Long) {
    /** 「14:00–15:00 定例」。明日以降は頭に曜日、始まっていれば「now」。 */
    fun label(now: Long = System.currentTimeMillis()): String {
        val hm = SimpleDateFormat("HH:mm", Locale.US)
        val day = when {
            begin <= now -> "now "
            sameDay(begin, now) -> ""
            else -> SimpleDateFormat("EEE ", Locale.US).format(Date(begin))
        }
        return "$day${hm.format(Date(begin))}–${hm.format(Date(end))} $title"
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val x = Calendar.getInstance().apply { timeInMillis = a }
        val y = Calendar.getInstance().apply { timeInMillis = b }
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
    }
}

/** 端末のカレンダーから次の予定（終日は除く、断ったものも除く）を引く。 */
object Agendas {
    private const val WINDOW_MS = 2 * 24 * 60 * 60 * 1000L

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun next(context: Context): Agenda? {
        val now = System.currentTimeMillis()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - WINDOW_MS)
            ContentUris.appendId(it, now + WINDOW_MS)
        }.build()
        val projection = arrayOf(Instances.TITLE, Instances.BEGIN, Instances.END)
        val selection = "${Instances.VISIBLE} = 1 AND ${Instances.ALL_DAY} = 0 AND ${Instances.END} > ? " +
            "AND ${Instances.SELF_ATTENDEE_STATUS} != ${Attendees.ATTENDEE_STATUS_DECLINED}"
        context.contentResolver.query(uri, projection, selection, arrayOf(now.toString()), "${Instances.BEGIN} ASC")?.use { c ->
            if (c.moveToFirst()) return Agenda(c.getString(0)?.takeIf { it.isNotBlank() } ?: "(無題)", c.getLong(1), c.getLong(2))
        }
        return null
    }
}
