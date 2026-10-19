package dev.polidog.simplelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 文字はドット絵の DotGothic16。 */
val Mono = FontFamily(Font(R.font.dotgothic16, FontWeight.Normal))

/** Nerd Font のアイコンは DotGothic16 に無いので UDEV Gothic NF で描く。 */
val Icons = FontFamily(
    Font(R.font.udev_gothic_nf, FontWeight.Normal),
    Font(R.font.udev_gothic_nf_bold, FontWeight.Bold),
)

class LauncherActivity : ComponentActivity() {
    /** ホームに戻るたびに増やす。最近のアプリ・許可状態の読み直しに使う。 */
    private val resumes = mutableIntStateOf(0)
    /** ホームボタンが押されるたびに増やす。開いている検索やメニューを閉じる。 */
    private val homePresses = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumes.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
        Demo.apply(this, intent)
        Settings.load(this)
        setContent { Launcher(resumes.intValue, homePresses.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Demo.apply(this, intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.intValue++
    }
}

@Composable
fun Launcher(resumes: Int, homePresses: Int) {
    val context = LocalContext.current
    val omarchy = remember { LauncherTheme.load(context) }
    val dark = isSystemInDarkTheme()
    val theme = if (dark) omarchy else LauncherTheme.GameBoy
    val status by rememberStatus()
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var walkerOpen by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf(emptyList<AppEntry>()) }
    var recentGranted by remember { mutableStateOf(true) }
    var homeMenu by remember { mutableStateOf<AppEntry?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var noticesGranted by remember { mutableStateOf(true) }
    val demo by Demo.enabled
    val notices = if (demo) Demo.notices.value else NoticeListener.notices.value

    LaunchedEffect(walkerOpen) { if (walkerOpen || apps.isEmpty()) apps = Apps.load(context) }
    LaunchedEffect(resumes, apps) {
        recentGranted = Recent.granted(context)
        if (recentGranted && apps.isNotEmpty()) recent = withContext(Dispatchers.IO) { Recent.load(context, apps) }
    }
    LaunchedEffect(resumes) {
        noticesGranted = NoticeListener.granted(context)
        withContext(Dispatchers.IO) { JevSearch.warmUp() }
    }
    BackHandler(enabled = walkerOpen) { walkerOpen = false }
    LaunchedEffect(homePresses) {
        walkerOpen = false
        homeMenu = null
        settingsOpen = false
    }
    BackHandler(enabled = homeMenu != null) { homeMenu = null }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    // インストール・削除されたら一覧を読み直す
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { apps = Apps.load(context) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ライトモードは壁紙を隠して画面ごとゲームボーイの液晶にする
            .then(if (dark) Modifier else Modifier.background(theme.background).lcdGrid(theme.foreground))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { walkerOpen = true }, onLongPress = { settingsOpen = true })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount -> if (dragAmount < -12f) walkerOpen = true }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(theme, status)
            HomeClock(theme, status, Modifier.weight(1f).fillMaxWidth())
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!noticesGranted && !demo) Hint(theme, "通知へのアクセスを許可すると、通知がここに出ます") { NoticeListener.requestAccess(context) }
                if (!recentGranted && !demo) Hint(theme, "使用状況へのアクセスを許可すると、最近のアプリが出ます") { Recent.requestAccess(context) }
                if (noticesGranted || demo) Notices(theme, notices.take(4),
                    onOpen = { NoticeListener.open(context, it) },
                    onDismiss = { n ->
                        if (demo) Demo.notices.value = Demo.notices.value - n else NoticeListener.dismiss(n)
                    })
                RecentApps(theme, recent,
                    onLaunch = { Apps.launch(context, it) },
                    onMenu = { homeMenu = it })
            }
        }
        homeMenu?.let { app -> AppMenu(theme, app, onDismiss = { homeMenu = null }) { action ->
            homeMenu = null
            when (action) {
                MenuAction.Info -> Apps.openInfo(context, app)
                MenuAction.Uninstall -> Apps.uninstall(context, app)
            }
        } }
        if (settingsOpen) HomeSettings(theme, onDismiss = { settingsOpen = false })
        if (walkerOpen) {
            Walker(theme, apps, onClose = { walkerOpen = false }) { app ->
                walkerOpen = false
                Apps.launch(context, app)
            }
        }
    }
}

/** バーと下の一覧のあいだに出す大きな時計。空きが狭いときは出さない。 */
@Composable
fun HomeClock(t: LauncherTheme, s: Status, modifier: Modifier = Modifier) {
    // 壁紙の上でも読めるように影をつける
    val shadow = Shadow(color = t.background.copy(alpha = 0.8f), offset = Offset(0f, 3f), blurRadius = 8f)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (maxHeight < 140.dp) return@BoxWithConstraints
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(s.time, style = TextStyle(fontFamily = Mono, fontSize = 88.sp, color = t.foreground, shadow = shadow))
            BasicText(s.longDate, style = TextStyle(fontFamily = Mono, fontSize = 18.sp, color = t.muted, shadow = shadow))
        }
    }
}

/** ホーム下部の最近のアプリ。タップで切り替え、長押しで操作メニュー。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentApps(
    t: LauncherTheme,
    recent: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    if (recent.isEmpty()) return
    val style = TextStyle(fontFamily = Mono, fontSize = 17.sp, color = t.foreground)
    Column(Modifier.fillMaxWidth()) {
        BasicText("recent", style = style.copy(fontSize = 13.sp, color = t.muted), modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
        Column(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(t.background.copy(alpha = 0.72f))
                .padding(4.dp),
        ) {
            for (app in recent) {
                BasicText(
                    text = app.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = style,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .combinedClickable(onLongClick = { onMenu(app) }) { onLaunch(app) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/** 許可を求める一行。タップで許可の画面へ。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Hint(t: LauncherTheme, text: String, onClick: () -> Unit) {
    BasicText(
        text = text,
        style = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = t.muted),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(t.background.copy(alpha = 0.72f))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    )
}

/** 届いている通知。タップで元のアプリを開き、横にスワイプで消す。 */
@Composable
fun Notices(t: LauncherTheme, notices: List<Notice>, onOpen: (Notice) -> Unit, onDismiss: (Notice) -> Unit) {
    if (notices.isEmpty()) return
    val hm = remember { SimpleDateFormat("HH:mm", Locale.US) }
    Column(Modifier.fillMaxWidth()) {
        BasicText("notifications", style = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = t.muted), modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
        Column(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(t.background.copy(alpha = 0.72f))
                .padding(4.dp),
        ) {
            for (n in notices) {
                androidx.compose.runtime.key(n.key) {
                    NoticeRow(t, n, hm.format(Date(n.time)), onOpen, onDismiss)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoticeRow(t: LauncherTheme, n: Notice, time: String, onOpen: (Notice) -> Unit, onDismiss: (Notice) -> Unit) {
    var dx by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val small = TextStyle(fontFamily = Mono, fontSize = 12.sp, color = t.muted)
    Column(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(dx.roundToInt(), 0) }
            .pointerInput(n.key) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (abs(dx) > threshold) onDismiss(n) else dx = 0f },
                    onDragCancel = { dx = 0f },
                ) { _, amount -> dx += amount }
            }
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable { onOpen(n) }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row {
            BasicText(n.app, maxLines = 1, overflow = TextOverflow.Ellipsis, style = small, modifier = Modifier.weight(1f))
            BasicText(time, style = small)
        }
        val body = listOf(n.title, n.text).filter { it.isNotBlank() }
        BasicText(
            text = body.first(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Mono, fontSize = 16.sp, color = t.foreground),
        )
        if (body.size > 1) BasicText(
            text = body[1],
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Mono, fontSize = 14.sp, color = t.muted),
        )
    }
}

/** waybar 風の一行バー。 */
@Composable
fun TopBar(t: LauncherTheme, s: Status) {
    val style = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = t.foreground)
    // パンチホール: 上端に食い込む切り欠きの右端まで左を空け、バーの高さは切り欠きの高さに合わせる
    val view = LocalView.current
    val density = LocalDensity.current
    val cutout = view.rootWindowInsets?.displayCutout
    val holeRight = cutout?.boundingRects
        ?.filter { it.top <= cutout.safeInsetTop }
        ?.maxOfOrNull { it.right } ?: 0
    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val startPad = with(density) { holeRight.toDp() } + 14.dp
    Column(Modifier.fillMaxWidth().background(t.background.copy(alpha = 0.92f))) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (cutoutTop > 0.dp) cutoutTop else 0.dp)
            .padding(start = startPad, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 時刻と日付はホームの大きな時計に出すので、バーには出さない
        Spacer(Modifier.weight(1f))
        val net = when { s.wifi -> "󰖩"; s.cell -> "󰤨"; else -> "󰖪" }
        BasicText(text = net, style = style.copy(fontFamily = Icons))
        Spacer(Modifier.width(10.dp))
        val bat = when {
            s.battery < 0 -> "󰂑"
            s.charging -> "󰂄"
            s.battery > 80 -> "󰁹"; s.battery > 60 -> "󰂀"; s.battery > 40 -> "󰁾"; s.battery > 20 -> "󰁼"
            else -> "󰁺"
        }
        val batColor = if (s.battery in 0..15 && !s.charging) t.red else t.foreground
        BasicText(text = "$bat ", style = style.copy(fontFamily = Icons, color = batColor))
        if (s.battery >= 0) BasicText(text = "${s.battery}%", style = style.copy(color = batColor))
    }
    }
}

/**
 * walker 風のランチャー。文字を打つと絞り込み、Enter か行タップで起動。長押しで操作メニュー。
 * 先頭一致・単語頭一致が無いときは、手が止まってから Jev に意味で選ばせて差し込む。
 * 何も打っていないときは、アプリを開くのに使った検索語の履歴を上に出す（タップで再検索、長押しで消す）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Walker(theme: LauncherTheme, apps: List<AppEntry>, onClose: () -> Unit, onLaunch: (AppEntry) -> Unit) {
    val context = LocalContext.current
    val spell by Settings.spellSearch
    // ゲーム風のウィンドウ。ダークモードはドラクエ（黒地に白）、ライトモードはゲームボーイポケット（液晶）
    val dark = isSystemInDarkTheme()
    val paper = if (dark) Color.Black else theme.background
    val ink = if (dark) Color.White else theme.foreground
    val t = if (spell) theme.copy(
        background = paper, foreground = ink, muted = ink.copy(alpha = 0.55f),
        accent = ink, selectionBg = Color.Transparent, selectionFg = ink,
    ) else theme
    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(spell) { while (spell) { delay(450); blink = !blink } }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val query = field.text
    var history by remember { mutableStateOf(SearchHistory.load(context)) }
    // 打った言葉で開いたら履歴に残す
    val launch: (AppEntry) -> Unit = { app ->
        if (query.isNotBlank()) history = SearchHistory.add(context, query)
        onLaunch(app)
    }
    var selected by remember { mutableStateOf(0) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    val focus = remember { FocusRequester() }
    val local = remember(query, apps) { Apps.scored(apps, query) }
    var jev by remember { mutableStateOf<JevSearch.Result?>(null) }
    var jevBusy by remember { mutableStateOf(false) }
    LaunchedEffect(query, apps) {
        jev = null
        val q = query.trim()
        if (!JevSearch.available || q.length < 2 || local.any { it.first <= 1 }) return@LaunchedEffect
        delay(400)
        jevBusy = true
        try {
            jev = withContext(Dispatchers.IO) { JevSearch.search(apps, q) }
        } finally {
            jevBusy = false
        }
    }
    // 強い一致 > Jev の候補 > 弱い一致
    val jevApps = jev?.apps?.map { it.first }.orEmpty()
    val results = remember(local, jevApps) {
        (local.filter { it.first <= 1 }.map { it.second } + jevApps + local.map { it.second }).distinct()
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(results) { selected = 0 }
    // 削除されたアプリのメニューは閉じる
    LaunchedEffect(apps) { if (menuFor != null && menuFor !in apps) menuFor = null }
    BackHandler(enabled = menuFor != null) { menuFor = null }

    Box(
        Modifier
            .fillMaxSize()
            .background(t.background.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 56.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (spell) Modifier.spellFrame(paper, ink, lcd = !dark) else Modifier.clip(RoundedCornerShape(6.dp)).background(t.background))
                .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!spell) BasicText("❯ ", style = TextStyle(fontFamily = Icons, fontSize = 20.sp, color = t.accent))
                BasicTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Mono, fontSize = 20.sp, color = t.foreground),
                    cursorBrush = SolidColor(t.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { results.getOrNull(selected)?.let(launch) }),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        if (query.isEmpty()) BasicText(if (spell) "アプリを さがす" else "Search…", style = TextStyle(fontFamily = Mono, fontSize = 20.sp, color = t.muted))
                        inner()
                    },
                )
                if (jevBusy) BasicText("󰚩", style = TextStyle(fontFamily = Icons, fontSize = 15.sp, color = t.muted))
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (query.isEmpty() && history.isNotEmpty()) {
                    item(key = "history-label") { SectionLabel(t, if (spell) "りれき" else "history") }
                    items(history, key = { "history:$it" }) { h ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .combinedClickable(onLongClick = { history = SearchHistory.remove(context, h) }) {
                                    field = TextFieldValue(h, TextRange(h.length))
                                }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicText("󰋚  ", style = TextStyle(fontFamily = Icons, fontSize = 15.sp, color = t.muted))
                            BasicText(h, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Mono, fontSize = 17.sp, color = t.foreground))
                        }
                    }
                    item(key = "apps-label") { SectionLabel(t, if (spell) "アプリ" else "apps") }
                }
                items(results, key = { it.packageName + it.activity }) { app ->
                    val isSel = results.indexOf(app) == selected
                    val fromJev = app in jevApps
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSel) t.selectionBg else Color.Transparent)
                            .combinedClickable(onLongClick = { menuFor = app }) { launch(app) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (spell) BasicText(
                            text = if (isSel && blink) "▶" else "",
                            style = TextStyle(fontFamily = Mono, fontSize = 15.sp, color = t.foreground),
                            modifier = Modifier.width(22.dp),
                        )
                        BasicText(
                            text = app.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                fontFamily = Mono, fontSize = 17.sp,
                                color = if (isSel) t.selectionFg else t.foreground,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        if (fromJev) BasicText("󰚩", style = TextStyle(fontFamily = Icons, fontSize = 15.sp, color = if (isSel) t.selectionFg else t.accent))
                    }
                }
            }
        }
        if (spell) BasicText(
            "アプリ検索",
            style = TextStyle(fontFamily = Mono, fontSize = 15.sp, color = ink),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-9).dp).background(paper).padding(horizontal = 8.dp),
        )
        }
        menuFor?.let { app -> AppMenu(theme, app, onDismiss = { menuFor = null }) { action ->
            menuFor = null
            when (action) {
                MenuAction.Info -> Apps.openInfo(context, app)
                MenuAction.Uninstall -> Apps.uninstall(context, app)
            }
        } }
    }
}

/**
 * ゲームのウィンドウ風の枠。paper の地に ink の太枠。外側にも paper を一周させ、壁紙の上でも枠が立つようにする。
 * lcd のときは液晶のドットの目を薄く重ねる。
 */
private fun Modifier.spellFrame(paper: Color, ink: Color, lcd: Boolean): Modifier = this
    .background(paper, RoundedCornerShape(10.dp))
    .padding(3.dp)
    .border(3.dp, ink, RoundedCornerShape(7.dp))
    .padding(3.dp)
    .then(if (lcd) Modifier.lcdGrid(ink) else Modifier)

/** ゲームボーイの液晶のドットの目。 */
private fun Modifier.lcdGrid(ink: Color): Modifier = drawBehind {
    val step = 3.dp.toPx()
    val line = ink.copy(alpha = 0.07f)
    var x = 0f
    while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
    var y = 0f
    while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += step }
}

@Composable
private fun SectionLabel(t: LauncherTheme, text: String) {
    BasicText(text, style = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = t.muted), modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 2.dp))
}

enum class MenuAction { Info, Uninstall }

/** 長押ししたアプリへの操作。画面下に出す。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppMenu(t: LauncherTheme, app: AppEntry, onDismiss: () -> Unit, onAction: (MenuAction) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(t.background.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(t.background)
                .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                .padding(14.dp),
        ) {
            BasicText(
                text = app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Mono, fontSize = 15.sp, color = t.muted),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            MenuRow(t, "󰋽", "アプリ情報", t.foreground) { onAction(MenuAction.Info) }
            if (app.removable) MenuRow(t, "󰆴", "アンインストール", t.red) { onAction(MenuAction.Uninstall) }
        }
    }
}

/** ホームの何もないところを長押しで出る設定。 */
@Composable
fun HomeSettings(t: LauncherTheme, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(t.background.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(t.background)
                .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                .padding(14.dp),
        ) {
            BasicText(
                text = "settings",
                style = TextStyle(fontFamily = Mono, fontSize = 15.sp, color = t.muted),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            SettingRow(t, Settings.spellSearch, "検索をゲーム風のウィンドウにする")
        }
    }
}

@Composable
private fun SettingRow(t: LauncherTheme, state: MutableState<Boolean>, label: String) {
    val context = LocalContext.current
    val on by state
    MenuRow(t, if (on) "󰄲" else "󰄱", label, if (on) t.foreground else t.muted) { Settings.set(context, state, !on) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuRow(t: LauncherTheme, icon: String, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("$icon  ", style = TextStyle(fontFamily = Icons, fontSize = 17.sp, color = color))
        BasicText(label, style = TextStyle(fontFamily = Mono, fontSize = 17.sp, color = color))
    }
}
