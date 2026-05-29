package com.lastasylum.alliance.data.voice

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import java.nio.ByteBuffer

/** Opus encode/decode via MediaCodec (API 29+). */
class VoiceOpusCodec {
    private var encoder: MediaCodec? = null
    private var decoder: MediaCodec? = null
    private var decoderConfigured = false
    private var encoderConfigBytes: ByteArray? = null
    private var encoderConfigSent = false
    private val remoteConfigByUser = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private var activeDecoderUserId: String? = null

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Decoder for playback — works without a running mic / encoder. */
    fun ensureDecoder(): Boolean {
        if (!isSupported) return false
        if (decoderConfigured && decoder != null) return true
        return try {
            val dec = decoder ?: MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).also {
                decoder = it
            }
            if (!decoderConfigured) {
                val format = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_OPUS,
                    VoiceAudioPipeline.SAMPLE_RATE_HZ,
                    1,
                )
                dec.configure(format, null, null, 0)
                dec.start()
                decoderConfigured = true
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Opus decoder init failed", e)
            releaseDecoder()
            false
        }
    }

    /**
     * Apply sender Opus identification header (csd-0) before decoding their frames.
     * Required when encoder runs on a different device than the decoder.
     */
    fun applyRemoteDecoderConfig(csd: ByteArray): Boolean {
        if (!isSupported || csd.isEmpty()) return false
        releaseDecoder()
        return try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                VoiceAudioPipeline.SAMPLE_RATE_HZ,
                1,
            ).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(csd))
            }
            val dec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).also {
                decoder = it
            }
            dec.configure(format, null, null, 0)
            dec.start()
            decoderConfigured = true
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Opus decoder config failed", e)
            releaseDecoder()
            false
        }
    }

    fun startEncoder() {
        if (!isSupported) return
        if (encoder != null) return
        encoderConfigBytes = null
        encoderConfigSent = false
        try {
            val encFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_OPUS,
                VoiceAudioPipeline.SAMPLE_RATE_HZ,
                1,
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 24_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, VoiceAudioPipeline.FRAME_BYTES_PCM)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
                configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            primeEncoder()
        } catch (e: Throwable) {
            Log.e(TAG, "Opus encoder init failed", e)
            releaseEncoder()
        }
    }

    /** Starts encoder (capture) and ensures decoder is ready for playback. */
    fun start() {
        startEncoder()
        ensureDecoder()
    }

    /** Pending encoder csd-0 to send once before the first Opus payload. */
    fun takePendingEncoderConfig(): EncodedFrame? {
        if (encoderConfigSent) return null
        val config = encoderConfigBytes ?: return null
        encoderConfigSent = true
        return EncodedFrame(codec = CODEC_OPUS_CONFIG, payload = config.copyOf())
    }

    /** Re-broadcast encoder csd-0 so late joiners can decode our uplink. */
    fun buildEncoderConfigFrame(): EncodedFrame? {
        val config = encoderConfigBytes ?: return null
        return EncodedFrame(codec = CODEC_OPUS_CONFIG, payload = config.copyOf())
    }

    fun encodePcmFrame(pcm: ByteArray): EncodedFrame? {
        takePendingEncoderConfig()?.let { return it }
        val enc = encoder ?: return null
        return try {
            val info = MediaCodec.BufferInfo()
            drainEncoderOutputs(enc, info)?.let { return it }

            val inIndex = enc.dequeueInputBuffer(ENCODE_TIMEOUT_US)
            if (inIndex < 0) return null
            val inBuf = enc.getInputBuffer(inIndex) ?: return null
            inBuf.clear()
            inBuf.put(pcm)
            enc.queueInputBuffer(inIndex, 0, pcm.size, 0, 0)
            drainEncoderOutputs(enc, info)
        } catch (e: Throwable) {
            Log.w(TAG, "encode failed", e)
            null
        }
    }

    fun decodeToPcm(userId: String, codec: String, payload: ByteArray): ByteArray? {
        when (codec) {
            CODEC_OPUS_CONFIG -> {
                remoteConfigByUser[userId] = payload.copyOf()
                if (userId == activeDecoderUserId || activeDecoderUserId == null) {
                    applyRemoteDecoderConfig(payload)
                    activeDecoderUserId = userId
                }
                return null
            }
            CODEC_OPUS -> Unit
            else -> return null
        }
        if (!isSupported) return null
        val config = remoteConfigByUser[userId]
        if (config != null && activeDecoderUserId != userId) {
            applyRemoteDecoderConfig(config)
            activeDecoderUserId = userId
        }
        if (!ensureDecoder()) return null
        val dec = decoder ?: return null
        return try {
            val info = MediaCodec.BufferInfo()
            drainDecoderOutputs(dec, info)?.let { return it }

            val inIndex = dec.dequeueInputBuffer(DECODE_TIMEOUT_US)
            if (inIndex < 0) return null
            val inBuf = dec.getInputBuffer(inIndex) ?: return null
            inBuf.clear()
            inBuf.put(payload)
            dec.queueInputBuffer(inIndex, 0, payload.size, 0, 0)
            drainDecoderOutputs(dec, info)
        } catch (e: Throwable) {
            Log.w(TAG, "decode failed", e)
            null
        }
    }

    private fun captureEncoderConfig(enc: MediaCodec) {
        runCatching {
            val format = enc.outputFormat
            val csd = format.getByteBuffer("csd-0") ?: return@runCatching
            val bytes = ByteArray(csd.remaining())
            csd.get(bytes)
            csd.rewind()
            if (bytes.isNotEmpty()) {
                encoderConfigBytes = bytes
            }
        }
    }

    private fun drainEncoderOutputs(
        enc: MediaCodec,
        info: MediaCodec.BufferInfo,
    ): EncodedFrame? {
        var latest: EncodedFrame? = null
        while (true) {
            var outIndex = enc.dequeueOutputBuffer(info, ENCODE_TIMEOUT_US)
            while (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                captureEncoderConfig(enc)
                outIndex = enc.dequeueOutputBuffer(info, ENCODE_TIMEOUT_US)
            }
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return latest
                outIndex < 0 -> return latest
                else -> {
                    try {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            val outBuf = enc.getOutputBuffer(outIndex) ?: continue
                            val bytes = ByteArray(info.size)
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            outBuf.get(bytes)
                            if (bytes.isNotEmpty()) {
                                encoderConfigBytes = bytes
                            }
                            continue
                        }
                        if (info.size <= 0) continue
                        val outBuf = enc.getOutputBuffer(outIndex) ?: continue
                        val bytes = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.get(bytes)
                        latest = EncodedFrame(codec = CODEC_OPUS, payload = bytes)
                    } finally {
                        enc.releaseOutputBuffer(outIndex, false)
                    }
                }
            }
        }
    }

    private fun drainDecoderOutputs(
        dec: MediaCodec,
        info: MediaCodec.BufferInfo,
    ): ByteArray? {
        var latest: ByteArray? = null
        while (true) {
            var outIndex = dec.dequeueOutputBuffer(info, DECODE_TIMEOUT_US)
            while (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outIndex = dec.dequeueOutputBuffer(info, DECODE_TIMEOUT_US)
            }
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return latest
                outIndex < 0 -> return latest
                else -> {
                    try {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) continue
                        if (info.size <= 0) continue
                        val outBuf = dec.getOutputBuffer(outIndex) ?: continue
                        val pcm = ByteArray(info.size)
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.get(pcm)
                        latest = pcm
                    } finally {
                        dec.releaseOutputBuffer(outIndex, false)
                    }
                }
            }
        }
    }

    private fun primeEncoder() {
        val silence = ByteArray(VoiceAudioPipeline.FRAME_BYTES_PCM)
        encodePcmFrame(silence)
    }

    fun release() {
        releaseEncoder()
        releaseDecoder()
        encoderConfigBytes = null
        encoderConfigSent = false
        remoteConfigByUser.clear()
        activeDecoderUserId = null
    }

    private fun releaseEncoder() {
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        encoder = null
        encoderConfigBytes = null
        encoderConfigSent = false
    }

    private fun releaseDecoder() {
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        decoder = null
        decoderConfigured = false
        activeDecoderUserId = null
    }

    data class EncodedFrame(val codec: String, val payload: ByteArray)

    companion object {
        const val CODEC_OPUS = "opus"
        const val CODEC_PCM = "pcm"
        const val CODEC_OPUS_CONFIG = "opus-config"
        private const val TAG = "VoiceOpusCodec"
        private const val ENCODE_TIMEOUT_US = 10_000L
        private const val DECODE_TIMEOUT_US = 10_000L
    }
}
