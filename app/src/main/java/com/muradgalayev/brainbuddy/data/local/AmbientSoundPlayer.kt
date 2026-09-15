package com.muradgalayev.brainbuddy.data.local

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.muradgalayev.brainbuddy.domain.model.Plan
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SoundDownload {
    data object Missing : SoundDownload
    // null progress when the server didn't say how big the file is
    data class Downloading(val progress: Float?) : SoundDownload
    data object Ready : SoundDownload
    data object Failed : SoundDownload
}

// plays the Pomodoro ambient sound. files live in the public ambient_sounds bucket and are fetched
// the first time a sound is picked, then kept in filesDir so they play offline from then on.
// recordings are CC0 from BigSoundBank (Joseph Sardin), re-encoded to 96 kbps AAC.
// playback follows the timer: loops while it runs, pauses with it, stops on reset or finish
@Singleton
class AmbientSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
    private val timerManager: PomodoroTimerManager,
    private val planRepository: com.muradgalayev.brainbuddy.data.repository.PlanRepository,
) {
    private val dir = File(context.filesDir, "ambient_sounds")
    // main dispatcher, so MediaPlayer and the job map are only ever touched from one thread
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val started = AtomicBoolean(false)

    private val _downloads = MutableStateFlow(
        AmbientSound.entries.associateWith {
            if (fileFor(it).exists()) SoundDownload.Ready else SoundDownload.Missing
        }
    )
    val downloads: StateFlow<Map<AmbientSound, SoundDownload>> = _downloads.asStateFlow()

    // kept outside the collectLatest below, so a timer change mid-download doesn't throw away a
    // half-fetched file
    private val downloadJobs = mutableMapOf<AmbientSound, Deferred<Boolean>>()
    private var player: MediaPlayer? = null
    private var playing: AmbientSound? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(timerManager.state, planRepository.plan) { state, plan ->
                // a Plus sound on a Free account counts as no sound, so a lapsed plan can't keep one playing
                val sound = state.selectedAmbientSound?.takeIf { unlocked(it, plan) }
                sound to state.timerState
            }
                .distinctUntilChanged()
                .collectLatest { (sound, timer) -> follow(sound, timer) }
        }
    }

    // fetch ahead of Start, so the sound is already on the phone when the timer begins
    fun prepare(sound: AmbientSound?) {
        if (sound == null || !unlocked(sound, planRepository.plan.value)) return
        scope.launch { ensureDownloaded(sound) }
    }

    private fun unlocked(sound: AmbientSound, plan: Plan) = !sound.plus || plan == Plan.Plus

    private suspend fun follow(sound: AmbientSound?, timer: TimerState) {
        if (sound == null || timer == TimerState.IDLE || timer == TimerState.COMPLETED) {
            release()
            if (sound != null) ensureDownloaded(sound)
            return
        }
        if (!ensureDownloaded(sound)) return
        if (playing != sound) {
            release()
            player = create(sound) ?: return
            playing = sound
        }
        val p = player ?: return
        if (timer == TimerState.RUNNING) {
            if (!p.isPlaying) p.start()
        } else if (p.isPlaying) {
            p.pause()
        }
    }

    private suspend fun ensureDownloaded(sound: AmbientSound): Boolean {
        if (fileFor(sound).exists()) return true
        // a finished job that failed isn't reused, so picking the sound again is the retry
        val job = downloadJobs[sound]?.takeIf { it.isActive }
            ?: scope.async { download(sound) }.also { downloadJobs[sound] = it }
        return job.await()
    }

    private suspend fun download(sound: AmbientSound): Boolean = withContext(Dispatchers.IO) {
        val target = fileFor(sound)
        // written under a temp name and renamed at the end, so a killed download never leaves a
        // truncated file that looks ready
        val partial = File(dir, "${target.name}.part")
        setStatus(sound, SoundDownload.Downloading(null))
        runCatching {
            dir.mkdirs()
            val url = supabase.storage.from(BUCKET).publicUrl(target.name)
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastShown = 0f
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) {
                                val progress = done.toFloat() / total
                                // every 5%, not every buffer, or the chip recomposes hundreds of times
                                if (progress - lastShown >= .05f) {
                                    lastShown = progress
                                    setStatus(sound, SoundDownload.Downloading(progress))
                                }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (!partial.renameTo(target)) error("couldn't move the finished file into place")
        }.fold(
            onSuccess = {
                setStatus(sound, SoundDownload.Ready)
                true
            },
            onFailure = {
                partial.delete()
                Log.w(TAG, "Download failed for $sound: ${it.message}")
                setStatus(sound, SoundDownload.Failed)
                false
            },
        )
    }

    private fun create(sound: AmbientSound): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(fileFor(sound).absolutePath)
            isLooping = true
            // under the chime and anything else the phone is playing, it's a background texture
            setVolume(VOLUME, VOLUME)
            prepare()
        }
    }.onFailure {
        // an unreadable file is as good as missing: drop it so the next pick downloads it fresh
        Log.w(TAG, "Couldn't play $sound: ${it.message}")
        fileFor(sound).delete()
        setStatus(sound, SoundDownload.Missing)
    }.getOrNull()

    private fun release() {
        player?.release()
        player = null
        playing = null
    }

    private fun setStatus(sound: AmbientSound, status: SoundDownload) {
        _downloads.update { it + (sound to status) }
    }

    private fun fileFor(sound: AmbientSound) = File(dir, "${sound.name.lowercase()}.m4a")

    private companion object {
        const val TAG = "AmbientSoundPlayer"
        const val BUCKET = "ambient_sounds"
        const val VOLUME = .6f
    }
}
