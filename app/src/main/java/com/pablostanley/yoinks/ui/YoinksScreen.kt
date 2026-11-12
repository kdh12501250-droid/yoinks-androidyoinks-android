package com.pablostanley.yoinks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pablostanley.yoinks.data.YtDlpRepository
import com.pablostanley.yoinks.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val LOGO_ART = listOf(
    "▓ ▓ █▀█ ▀█▀ █▀▄█ █ █ █▀▀",
    "▀█▀ █ ▓  ▓  █  ▓ ▓▀▄ ▀▀▓",
    " ▀  ▀▀▀ ▀▀▀ ▀  ▀ ▀ ▀ ▀▀▀"
).joinToString("
")

private const val TAGLINE = "yoink any video. paste. yoink. done."
private const val SUBTITLE = "youtube · x · instagram · threads · tiktok · +1800 more"
private const val PROCESS_ID = "yoinks-active-dl"

@Composable
fun YoinksScreen(initialSharedUrl: String? = null) {
    val repo = remember { YtDlpRepository() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val systemDark = isSystemInDarkTheme()

    var themeMode by remember { mutableStateOf(ThemeMode.AUTO) }
    val palette = remember(themeMode, systemDark) { themePaletteFor(themeMode, systemDark) }

    var urlInput by remember { mutableStateOf(initialSharedUrl ?: "") }
    var activeUrl by remember { mutableStateOf(initialSharedUrl ?: "") }
    var platform by remember { mutableStateOf<PlatformInfo?>(null) }
    var phase by remember { mutableStateOf<YoinksPhase>(YoinksPhase.Input()) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val startProbe: (String) -> Unit = { rawUrl ->
        val trimmed = rawUrl.trim()
        if (!isProbablyUrl(trimmed)) {
            phase = YoinksPhase.Input(warning = "that doesn’t look like a link — paste a full url")
        } else {
            activeUrl = trimmed
            platform = detectPlatform(trimmed)
            activeJob?.cancel()
            activeJob = scope.launch {
                phase = YoinksPhase.Probing("fetching video info…")
                try {
                    val info = repo.probe(trimmed)
                    phase = YoinksPhase.Picking(info)
                } catch (e: Exception) {
                    phase = YoinksPhase.Error(e.message ?: "Failed to probe video info.")
                }
            }
        }
    }

    LaunchedEffect(initialSharedUrl) {
        if (!initialSharedUrl.isNullOrBlank() && isProbablyUrl(initialSharedUrl)) {
            urlInput = initialSharedUrl
            startProbe(initialSharedUrl)
        }
    }

    val resetToInput: () -> Unit = {
        activeJob?.cancel()
        repo.cancel(PROCESS_ID)
        phase = YoinksPhase.Input()
    }

    val startDownload: (VideoProbeInfo, DownloadChoice) -> Unit = { info, choice ->
        activeJob?.cancel()
        activeJob = scope.launch {
            phase = YoinksPhase.Downloading(
                info = info,
                choice = choice,
                progress = DownloadProgressState(0f, 0L, "starting download…")
            )
            try {
                val outDir = repo.download(activeUrl, choice, PROCESS_ID) { pct, eta, line ->
                    val processing = line.contains("[Merger]") || line.contains("[ExtractAudio]")
                    val status = when {
                        processing -> "processing…"
                        eta > 0 -> "${eta}s left"
                        else -> "downloading…"
                    }
                    phase = YoinksPhase.Downloading(
                        info = info,
                        choice = choice,
                        progress = DownloadProgressState(
                            percent = if (processing) 1f else pct,
                            etaSeconds = eta,
                            statusLine = status
                        )
                    )
                }
                phase = YoinksPhase.Done(outDir)
            } catch (e: Exception) {
                phase = YoinksPhase.Error(e.message ?: "Download failed.")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = 20.dp, vertical = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Header (Logo + Tagline)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { resetToInput() }
        ) {
            Text(
                text = LOGO_ART,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                color = palette.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = TAGLINE,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = palette.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = SUBTITLE,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = palette.gray
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            when (val current = phase) {
                is YoinksPhase.Input -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, palette.gray, RoundedCornerShape(10.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Paste a link",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = palette.gray
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            BasicTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                singleLine = true,
                                cursorBrush = SolidColor(palette.primary),
                                textStyle = TextStyle(
                                    color = palette.primary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(palette.surface, RoundedCornerShape(6.dp))
                                    .padding(12.dp),
                                decorationBox = { innerTextField ->
                                    if (urlInput.isEmpty()) {
                                        Text(
                                            text = "https://youtube.com/watch?v=…",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            color = palette.gray
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clipText = clipboard.getText()?.text?.trim().orEmpty()
                                        if (clipText.isNotEmpty()) {
                                            urlInput = clipText
                                            if (isProbablyUrl(clipText)) startProbe(clipText)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "paste clipboard",
                                        fontFamily = FontFamily.Monospace,
                                        color = palette.primary
                                    )
                                }
                                Button(
                                    onClick = { startProbe(urlInput) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.primary,
                                        contentColor = palette.background
                                    )
                                ) {
                                    Text(
                                        text = "↵ yoink",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (current.warning != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "✗ ${current.warning}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = palette.gray
                            )
                        }
                    }
                }

                is YoinksPhase.Probing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.gray, RoundedCornerShape(10.dp))
                            .padding(20.dp)
                    ) {
                        Text(
                            text = platform?.label ?: "Probing link",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = palette.gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = activeUrl,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = palette.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(
                            color = palette.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = current.status,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = palette.gray
                        )
                    }
                }

                is YoinksPhase.Picking -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.gray, RoundedCornerShape(10.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = current.info.title,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val metaParts = buildList {
                            add("▸ ${platform?.label ?: "Video"}")
                            current.info.durationSec?.let { add(formatDuration(it)) }
                            current.info.uploader?.let { add(it) }
                        }.joinToString(" · ")
                        Text(
                            text = metaParts,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = palette.gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Download",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = palette.gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        current.info.choices.forEach { choice ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(1.dp, palette.gray, RoundedCornerShape(8.dp))
                                    .clickable { startDownload(current.info, choice) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "❯ ${choice.displayLabel}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.primary
                                )
                            }
                        }
                    }
                }

                is YoinksPhase.Downloading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.gray, RoundedCornerShape(10.dp))
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "${current.info.title} · ${current.choice.label}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = palette.gray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { current.progress.percent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp),
                            color = palette.primary,
                            trackColor = palette.surface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${(current.progress.percent * 100).toInt()}% · ${current.progress.statusLine}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = palette.gray
                        )
                    }
                }

                is YoinksPhase.Done -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.gray, RoundedCornerShape(10.dp))
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "✓ yoinked! find your file in:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = current.savedPath,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = palette.gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = resetToInput,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "↵ yoink another",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = palette.primary
                            )
                        }
                    }
                }

                is YoinksPhase.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.gray, RoundedCornerShape(10.dp))
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "✗ ${current.message}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.primary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = resetToInput,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "↵ try again",
                                fontFamily = FontFamily.Monospace,
                                color = palette.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Footer Hints & Theme Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "esc back / cancel",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = palette.gray,
                modifier = Modifier
                    .clickable { resetToInput() }
                    .padding(6.dp)
            )
            Text(
                text = "^t theme:${themeMode.label}",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = palette.gray,
                modifier = Modifier
                    .border(1.dp, palette.gray, RoundedCornerShape(6.dp))
                    .clickable { themeMode = themeMode.next() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
