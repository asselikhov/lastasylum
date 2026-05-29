package com.lastasylum.alliance.data.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * 20 ms Opus capture with VAD, AEC/NS, jitter-buffered multi-speaker playback.
 */
class VoiceAudioPipeline(
    private val onEncodedFrame: (VoiceOpusCodec.EncodedFrame) -> Unit,
    private val onLocalSpeechActivity: (Boolean) -> Unit = {},
) {
    private val opus = VoiceOpusCodec()
    private val vad = VoiceActivityDetector()
    private val jitter = VoiceJitterBuffer()
    private val captureRunning = AtomicBoolean(false)
    private var captureThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    private var audioTrack: AudioTrack? = null
    private val remoteMicOn = ConcurrentHashMap<String, Boolean>()
    private var playThread: Thread? = null
    private val playRunning = AtomicBoolean(false)
    @Volatile
    private var soundEnabled = false
    @Volatile
    private var playbackGain = 1f
    @Volatile
    private var captureGain = 1f

    fun startCapture() {
        if (!opus.isSupported) {
            Log.e(TAG, "Opus requires Android 10+ (API 29)")
            return
        }
        if (!captureRunning.compareAndSet(false, true)) return
        opus.start()
        vad.reset()
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = max(minBuf, FRAME_BYTES_PCM * 4)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized")
            captureRunning.set(false)
            record.release()
            return
        }
        VoiceAudioEffects.attachToCapture(record)
        audioRecord = record
        record.startRecording()
        captureThread = Thread(
            {
                val frame = ByteArray(FRAME_BYTES_PCM)
                while (captureRunning.get()) {
                    val read = record.read(frame, 0, frame.size)
                    if (read < frame.size) continue
                    val speaking = vad.isSpeechEnergy(frame)
                    onLocalSpeechActivity(speaking)
                    // Mic toggle is the uplink gate; VAD is UI-only (AGC/quiet mics were dropping all speech).
                    VoicePcmGain.applyInPlace(frame, captureGain)
                    val encoded = opus.encodePcmFrame(frame) ?: continue
                    onEncodedFrame(encoded)
                }
            },
            "voice-capture",
        ).apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stopCapture() {
        if (!captureRunning.compareAndSet(true, false)) return
        vad.reset()
        onLocalSpeechActivity(false)
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        captureThread?.join(300)
        captureThread = null
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        if (enabled) {
            if (opus.isSupported) opus.ensureDecoder()
            ensurePlayback()
        } else {
            jitter.clear()
            stopPlayback()
        }
    }

    fun setPlaybackGain(gain: Float) {
        playbackGain = gain.coerceIn(0f, 2f)
    }

    fun setCaptureGain(gain: Float) {
        captureGain = gain.coerceIn(0f, 2f)
    }

    /** Re-send Opus encoder config so peers who joined late can decode our voice. */
    fun resendEncoderConfig(): Boolean {
        val frame = opus.buildEncoderConfigFrame() ?: return false
        onEncodedFrame(frame)
        return true
    }

    fun setRemotePeerMic(userId: String, micOn: Boolean) {
        if (micOn) {
            remoteMicOn[userId] = true
        } else {
            remoteMicOn.remove(userId)
            jitter.removeSpeaker(userId)
        }
    }

    fun removeRemotePeer(userId: String) {
        remoteMicOn.remove(userId)
        jitter.removeSpeaker(userId)
    }

    fun enqueueRemoteFrame(userId: String, codec: String, payload: ByteArray) {
        if (!soundEnabled) return
        // Frames are only relayed when the sender has mic on; do not block on stale peer-state.
        remoteMicOn[userId] = true
        val pcm = opus.decodeToPcm(userId, codec, payload) ?: return
        val frame = normalizePlayFrame(pcm)
        jitter.push(userId, VoicePcmGain.apply(frame, playbackGain))
        ensurePlayback()
    }

    private fun stopPlayback() {
        if (!playRunning.compareAndSet(true, false)) return
        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        playThread?.join(400)
        playThread = null
    }

    private fun ensurePlayback() {
        if (!soundEnabled) return
        if (playRunning.get()) return
        if (!playRunning.compareAndSet(false, true)) return
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(max(minBuf, FRAME_BYTES_PCM * 8))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf, FRAME_BYTES_PCM * 8),
                AudioTrack.MODE_STREAM,
            )
        }
        audioTrack = track
        track.play()
        playThread = Thread(
            {
                val silence = ByteArray(FRAME_BYTES_PCM)
                var nextFrameAtNs = System.nanoTime()
                while (playRunning.get()) {
                    val mixed = jitter.pollMixedFrame() ?: silence
                    track.write(mixed, 0, mixed.size)
                    nextFrameAtNs += FRAME_MS * 1_000_000L
                    val waitMs = ((nextFrameAtNs - System.nanoTime()) / 1_000_000L).coerceIn(0L, FRAME_MS.toLong())
                    if (waitMs > 0L) {
                        Thread.sleep(waitMs)
                    }
                }
            },
            "voice-playback",
        ).apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun release() {
        stopCapture()
        stopPlayback()
        jitter.clear()
        remoteMicOn.clear()
        opus.release()
    }

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE_HZ * FRAME_MS / 1000
        const val FRAME_BYTES_PCM = FRAME_SAMPLES * 2
        private const val TAG = "VoiceAudioPipeline"

        /** Pad/truncate decoder output to one 20 ms playout frame. */
        fun normalizePlayFrame(pcm: ByteArray, frameBytes: Int = FRAME_BYTES_PCM): ByteArray {
            if (pcm.size == frameBytes) return pcm
            val out = ByteArray(frameBytes)
            System.arraycopy(pcm, 0, out, 0, minOf(pcm.size, frameBytes))
            return out
        }
    }
}
