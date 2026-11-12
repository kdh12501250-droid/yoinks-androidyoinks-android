package com.pablostanley.yoinks.data

import android.os.Environment
import com.pablostanley.yoinks.model.ChoiceKind
import com.pablostanley.yoinks.model.DownloadChoice
import com.pablostanley.yoinks.model.VideoProbeInfo
import com.pablostanley.yoinks.model.formatBytes
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class YtDlpRepository {

    suspend fun probe(url: String): VideoProbeInfo = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
        }
        val info = YoutubeDL.getInstance().getInfo(request)
        val formats = info.formats ?: emptyList()

        val audioOnly = formats.filter {
            !it.acodec.isNullOrBlank() && it.acodec != "none" &&
                (it.vcodec.isNullOrBlank() || it.vcodec == "none")
        }
        val bestAudio = audioOnly.maxByOrNull { it.abr ?: it.tbr ?: 0 }
        val audioSize = bestAudio?.fileSize ?: bestAudio?.fileSizeApproximate ?: 0L

        val videoFormats = formats.filter {
            !it.vcodec.isNullOrBlank() && it.vcodec != "none" && it.height > 0
        }
        val heights = videoFormats.map { it.height }.distinct().sortedDescending().take(8)

        val choices = mutableListOf<DownloadChoice>()
        for (height in heights) {
            val candidates = videoFormats.filter { it.height == height }
            val best = candidates.maxByOrNull { scoreVideo(it) } ?: continue
            val muxed = !best.acodec.isNullOrBlank() && best.acodec != "none"
            val vSize = best.fileSize.takeIf { it > 0 } ?: best.fileSizeApproximate
            val totalSize = vSize + if (muxed) 0L else audioSize
            val sizeLabel = if (totalSize > 0) " · ~${formatBytes(totalSize)}" else ""

            choices.add(
                DownloadChoice(
                    label = "${height}p · mp4$sizeLabel",
                    kind = ChoiceKind.VIDEO,
                    formatSelector = "bv*[height=$height]+ba/b[height=$height]/bv*[height<=$height]+ba/b",
                    extractAudioMp3 = false
                )
            )
        }

        if (choices.isEmpty()) {
            choices.add(
                DownloadChoice(
                    label = "best available · mp4",
                    kind = ChoiceKind.VIDEO,
                    formatSelector = "bv*+ba/b",
                    extractAudioMp3 = false
                )
            )
        }

        val audioSizeLabel = if (audioSize > 0) " · ~${formatBytes(audioSize)}" else ""
        choices.add(
            DownloadChoice(
                label = "audio only · mp3$audioSizeLabel",
                kind = ChoiceKind.AUDIO,
                formatSelector = "ba/b",
                extractAudioMp3 = true
            )
        )

        VideoProbeInfo(
            title = info.title ?: "Untitled video",
            uploader = info.uploader,
            durationSec = info.duration.toLong().takeIf { it > 0 },
            choices = choices
        )
    }

    private fun scoreVideo(f: VideoFormat): Int {
        var score = f.tbr ?: 0
        if (f.ext == "mp4") score += 10_000
        if (f.vcodec?.startsWith("avc") == true) score += 5_000
        return score
    }

    suspend fun download(
        url: String,
        choice: DownloadChoice,
        processId: String,
        onProgress: (Float, Long, String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val outDir = File(downloadsRoot, "yoinks").apply { mkdirs() }

        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("-f", choice.formatSelector)
            if (choice.extractAudioMp3) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "0")
            } else {
                addOption("--merge-output-format", "mp4")
            }
            addOption("-o", File(outDir, "%(title).60s.%(ext)s").absolutePath)
        }

        YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
            onProgress(progress.coerceIn(0f, 100f) / 100f, etaInSeconds, line)
        }

        outDir.absolutePath
    }

    fun cancel(processId: String) {
        YoutubeDL.getInstance().destroyProcessById(processId)
    }
}
