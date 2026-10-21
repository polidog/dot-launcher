package jp.polidog.dotlauncher

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

data class Track(
    val title: String,
    val artist: String,
    val packageName: String,
    val intent: PendingIntent?,
    val playing: Boolean,
    /** デモ表示のダミーの曲は null（ボタンを押しても何もしない）。 */
    val controls: MediaController.TransportControls?,
)

/**
 * いま再生している曲。音楽アプリの MediaSession を読む。
 * 読むには「通知へのアクセス」が要るので、通知と同じ許可で動く。
 * 再生中のものを優先し、無ければ一時停止中のものを出す（ホームから再開できるように）。
 */
object NowPlaying {
    fun current(controllers: List<MediaController>): Track? {
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
            ?: return null
        val m = c.metadata ?: return null
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        if (title.isBlank()) return null
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        return Track(title, artist, c.packageName, c.sessionActivity, playing, c.transportControls)
    }

    fun toggle(track: Track) = if (track.playing) track.controls?.pause() else track.controls?.play()
    fun previous(track: Track) = track.controls?.skipToPrevious()
    fun next(track: Track) = track.controls?.skipToNext()

    /** 再生しているアプリの画面を開く。 */
    fun open(context: Context, track: Track) {
        val pi = track.intent
        if (pi == null) {
            context.packageManager.getLaunchIntentForPackage(track.packageName)
                ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        val options = if (Build.VERSION.SDK_INT >= 34) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else null
        try {
            pi.send(context, 0, null, null, null, null, options)
        } catch (_: PendingIntent.CanceledException) { }
    }
}

@Composable
fun rememberNowPlaying(granted: Boolean): State<Track?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Track?>(null) }
    DisposableEffect(granted) {
        if (!granted) { state.value = null; return@DisposableEffect onDispose { } }
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val listener = ComponentName(context, NoticeListener::class.java)
        var controllers = emptyList<MediaController>()
        // 曲が変わった・止まったを拾う
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(s: PlaybackState?) { state.value = NowPlaying.current(controllers) }
            override fun onMetadataChanged(m: MediaMetadata?) { state.value = NowPlaying.current(controllers) }
            override fun onSessionDestroyed() { state.value = NowPlaying.current(controllers) }
        }
        // 音楽アプリが増えた・減ったを拾う
        val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list ->
            controllers.forEach { it.unregisterCallback(callback) }
            controllers = list.orEmpty()
            controllers.forEach { it.registerCallback(callback) }
            state.value = NowPlaying.current(controllers)
        }
        try {
            msm.addOnActiveSessionsChangedListener(sessions, listener)
            sessions.onActiveSessionsChanged(msm.getActiveSessions(listener))
        } catch (_: SecurityException) { }
        onDispose {
            msm.removeOnActiveSessionsChangedListener(sessions)
            controllers.forEach { it.unregisterCallback(callback) }
        }
    }
    return state
}
