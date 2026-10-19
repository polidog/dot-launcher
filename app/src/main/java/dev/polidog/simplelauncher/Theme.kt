package dev.polidog.simplelauncher

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * テーマ色（Omarchy の colors.toml 形式）。既定は assets/colors.toml（Turbonite）。
 * 端末側の files/colors.toml（adb push で置ける）があればそれで上書きする。
 *   adb push ~/.local/state/omarchy/current/theme/colors.toml \
 *     /sdcard/Android/data/dev.polidog.simplelauncher/files/colors.toml
 */
data class LauncherTheme(
    val background: Color,
    val foreground: Color,
    val accent: Color,
    val muted: Color,
    val selectionBg: Color,
    val selectionFg: Color,
    val red: Color,
    val yellow: Color,
    val cyan: Color,
) {
    companion object {
        fun load(context: Context): LauncherTheme {
            val text = context.getExternalFilesDir(null)
                ?.let { File(it, "colors.toml") }
                ?.takeIf { it.isFile }
                ?.readText()
                ?: context.assets.open("colors.toml").bufferedReader().readText()
            return parse(text)
        }

        fun parse(toml: String): LauncherTheme {
            val m = HashMap<String, Color>()
            val re = Regex("""^\s*([A-Za-z0-9_]+)\s*=\s*"?(#[0-9A-Fa-f]{6})"?""")
            toml.lineSequence().forEach { line ->
                re.find(line)?.let { m[it.groupValues[1]] = hex(it.groupValues[2]) }
            }
            fun c(k: String, fallback: Color) = m[k] ?: fallback
            val bg = c("background", Color(0xFF0B0C0C))
            val fg = c("foreground", Color(0xFFEAE9E3))
            return LauncherTheme(
                background = bg,
                foreground = fg,
                accent = c("accent", c("color3", Color(0xFFED9A1D))),
                muted = c("color8", Color(0xFF31363A)),
                selectionBg = c("selection_background", fg),
                selectionFg = c("selection_foreground", bg),
                red = c("color1", Color(0xFFF24331)),
                yellow = c("color3", Color(0xFFED9A1D)),
                cyan = c("color6", Color(0xFFEBD698)),
            )
        }

        private fun hex(s: String): Color = Color(0xFF000000L or s.substring(1).toLong(16))
    }
}
