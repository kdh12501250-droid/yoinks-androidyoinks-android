package com.pablostanley.yoinks.model

import androidx.compose.ui.graphics.Color
import java.net.URI
import kotlin.math.ln
import kotlin.math.pow

enum class ThemeMode(val label: String) {
    AUTO("auto"),
    LIGHT("light"),
    DARK("dark");

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

data class YoinksThemePalette(
    val mode: ThemeMode,
    val primary: Color,
    val gray: Color,
    val background: Color,
    val surface: Color
)

fun themePaletteFor(mode: ThemeMode, systemDark: Boolean): YoinksThemePalette {
    val isDark = when (mode) {
        ThemeMode.AUTO -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    return if (isDark) {
        YoinksThemePalette(
            mode = mode,
            primary = Color(0xFFFFFFFF),
            gray = Color(0xFFA1A1AA),
            background = Color(0xFF18181B),
            surface = Color(0xFF27272A)
        )
    } else {
        YoinksThemePalette(
            mode = mode,
            primary = Color(0xFF18181B),
            gray = Color(0xFF52525B),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF4F4F5)
        )
    }
}

data class PlatformInfo(val key: String, val label: String)

private val KNOWN_PLATFORMS = listOf(
    listOf("youtube.com", "youtu.be", "music.youtube.com") to PlatformInfo("youtube", "YouTube"),
    listOf("x.com", "twitter.com") to PlatformInfo("x", "X / Twitter"),
    listOf("instagram.com") to PlatformInfo("instagram", "Instagram"),
    listOf("threads.net", "threads.com") to PlatformInfo("threads", "Threads"),
    listOf("tiktok.com") to PlatformInfo("tiktok", "TikTok"),
    listOf("vimeo.com") to PlatformInfo("vimeo", "Vimeo"),
    listOf("twitch.tv") to PlatformInfo("twitch", "Twitch"),
    listOf("reddit.com") to PlatformInfo("reddit", "Reddit"),
    listOf("facebook.com", "fb.watch") to PlatformInfo("facebook", "Facebook")
)

fun detectPlatform(url: String): PlatformInfo {
    val host = try {
        URI(url.trim()).host?.lowercase() ?: return PlatformInfo("unknown", "Unknown site")
    } catch (_: Exception) {
        return PlatformInfo("unknown", "Unknown site")
    }
    for ((hosts, platform) in KNOWN_PLATFORMS) {
        if (hosts.any { h -> host == h || host.endsWith(".$h") }) {
            return platform
        }
    }
    return PlatformInfo("generic", host)
}

fun isProbablyUrl(input: String): Boolean {
    return try {
        val uri = URI(input.trim())
        val scheme = uri.scheme?.lowercase()
        (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}

enum class ChoiceKind { VIDEO, AUDIO }

data class DownloadChoice(
    val label: String,
    val kind: ChoiceKind,
    val formatSelector: String,
    val extractAudioMp3: Boolean = false
) {
    val displayLabel: String
        get() = "${if (kind == ChoiceKind.AUDIO) "♪ " else "▶ "}$label"
}

data class VideoProbeInfo(
    val title: String,
    val uploader: String?,
    val durationSec: Long?,
    val choices: List<DownloadChoice>
)

data class DownloadProgressState(
    val percent: Float,
    val etaSeconds: Long,
    val statusLine: String
)

sealed interface YoinksPhase {
    data class Input(val warning: String? = null) : YoinksPhase
    data class Probing(val status: String) : YoinksPhase
    data class Picking(val info: VideoProbeInfo) : YoinksPhase
    data class Downloading(
        val info: VideoProbeInfo,
        val choice: DownloadChoice,
        val progress: DownloadProgressState
    ) : YoinksPhase
    data class Done(val savedPath: String) : YoinksPhase
    data class Error(val message: String) : YoinksPhase
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return if (digitGroups <= 1) {
        "${value.toInt()} ${units[digitGroups]}"
    } else {
        String.format("%.1f %s", value, units[digitGroups])
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}
