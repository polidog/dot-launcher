package dev.polidog.simplelauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * バーに出す現在地（緯度経度）。電池を食わないよう、ホームが前に出ているあいだ数分に一回だけ測る。
 * GPS は使わずネットワークの位置で、新しめの位置が手元にあれば測り直さない。
 */
object Place {
    const val INTERVAL_MS = 5 * 60 * 1000L

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    val permissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

    /** 35.6812°N 139.7671°E */
    fun format(l: Location): String {
        val lat = String.format(Locale.US, "%.4f°%s", abs(l.latitude), if (l.latitude >= 0) "N" else "S")
        val lon = String.format(Locale.US, "%.4f°%s", abs(l.longitude), if (l.longitude >= 0) "E" else "W")
        return "$lat $lon"
    }

    @SuppressLint("MissingPermission")
    suspend fun locate(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < INTERVAL_MS) return last
        if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return last
        val fresh = withTimeoutOrNull(30_000L) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, LocationManager.NETWORK_PROVIDER, signal, ContextCompat.getMainExecutor(context)) {
                    cont.resume(it)
                }
            }
        }
        return fresh ?: last
    }
}

@Composable
fun rememberPlace(granted: Boolean): State<Location?> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { mutableStateOf<Location?>(null) }
    LaunchedEffect(granted, lifecycle) {
        if (!granted) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                Place.locate(context)?.let { state.value = it }
                delay(Place.INTERVAL_MS)
            }
        }
    }
    return state
}
