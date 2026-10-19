package dev.polidog.simplelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Status(
    val time: String = "--:--",
    val date: String = "",
    val battery: Int = -1,
    val charging: Boolean = false,
    val wifi: Boolean = false,
    val cell: Boolean = false,
)

private fun snapshot(context: Context): Status {
    val now = Date()
    val b = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
    return Status(
        time = SimpleDateFormat("HH:mm", Locale.US).format(now),
        date = SimpleDateFormat("EEE MM-dd", Locale.US).format(now),
        battery = if (level >= 0) level * 100 / scale else -1,
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
        cell = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
    )
}

@Composable
fun rememberStatus(): State<Status> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(snapshot(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { state.value = snapshot(c) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}
