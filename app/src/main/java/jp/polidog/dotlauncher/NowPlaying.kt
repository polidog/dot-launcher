package jp.polidog.dotlauncher

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
    /** 音楽アプリのセッションが消えたあと、覚えていた曲を出しているとき true。再生だけ効く。 */
    val remembered: Boolean = false,
)

/**
 * いま再生している曲。音楽アプリの MediaSession を読む。
 * 読むには「通知へのアクセス」が要るので、通知と同じ許可で動く。
 * 再生中のものを優先し、無ければ一時停止中のものを出す（ホームから再開できるように）。
 * 曲が止まってセッションが消えても、設定がオンなら最後の曲を覚えておいて出し続ける。
 */
object NowPlaying {
    private const val PREFS = "now_playing"
    private const val RESUME_WAIT_MS = 1500L

    /** いま生きているセッションの曲。 */
    val live = mutableStateOf<Track?>(null)

    /** 最後に見た曲。端末に残し、次に起動したときも出す。 */
    val last = mutableStateOf<Track?>(null)

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

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = p.getString("title", null) ?: return
        val pkg = p.getString("package", null) ?: return
        last.value = Track(title, p.getString("artist", null).orEmpty(), pkg, null, playing = false, controls = null, remembered = true)
    }

    /** 見た曲を覚える。PendingIntent と操作は残せないので、曲名・アーティスト・アプリだけ。 */
    fun keep(context: Context, track: Track) {
        val l = last.value
        if (l != null && l.title == track.title && l.artist == track.artist && l.packageName == track.packageName) return
        last.value = track.copy(intent = null, playing = false, controls = null, remembered = true)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("title", track.title)
            .putString("artist", track.artist)
            .putString("package", track.packageName)
            .apply()
    }

    fun toggle(context: Context, track: Track) {
        val c = track.controls
        when {
            c != null -> if (track.playing) c.pause() else c.play()
            track.remembered -> resume(context, track)
        }
    }

    fun previous(track: Track) = track.controls?.skipToPrevious()
    fun next(track: Track) = track.controls?.skipToNext()

    /**
     * セッションが消えた曲を再生し直す。イヤホンの再生ボタンと同じ経路で「再生」を送ると、最後に鳴らしたアプリに届く。
     * しばらく待っても鳴らなければ（受け口を持たないアプリ）、アプリを開く。
     */
    private fun resume(context: Context, track: Track) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        Handler(Looper.getMainLooper()).postDelayed({ if (live.value == null) open(context, track) }, RESUME_WAIT_MS)
    }

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

/** 出す曲。生きているセッションがあればそれ、無ければ設定がオンのときだけ覚えていた曲。 */
@Composable
fun rememberNowPlaying(granted: Boolean): State<Track?> {
    val context = LocalContext.current
    DisposableEffect(granted) {
        if (!granted) { NowPlaying.live.value = null; return@DisposableEffect onDispose { } }
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val listener = ComponentName(context, NoticeListener::class.java)
        var controllers = emptyList<MediaController>()
        val update = {
            val t = NowPlaying.current(controllers)
            NowPlaying.live.value = t
            if (t != null) NowPlaying.keep(context, t)
        }
        // 曲が変わった・止まったを拾う
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(s: PlaybackState?) = update()
            override fun onMetadataChanged(m: MediaMetadata?) = update()
            override fun onSessionDestroyed() = update()
        }
        // 音楽アプリが増えた・減ったを拾う
        val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list ->
            controllers.forEach { it.unregisterCallback(callback) }
            controllers = list.orEmpty()
            controllers.forEach { it.registerCallback(callback) }
            update()
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
    return remember(granted) {
        derivedStateOf {
            NowPlaying.live.value ?: NowPlaying.last.value?.takeIf { granted && Settings.keepNowPlaying.value }
        }
    }
}
