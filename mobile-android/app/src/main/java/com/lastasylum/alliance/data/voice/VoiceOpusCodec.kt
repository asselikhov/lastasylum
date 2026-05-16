package com.lastasylum.alliance.data.voice

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log

/** Opus encode/decode via MediaCodec (API 29+). */
class VoiceOpusCodec {
    private var encoder: MediaCodec? = null
    private var decoder: MediaCodec? = null
    private var decoderConfigured = false

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun start() {
        if (!isSupported) return
        if (encoder != null) return
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
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        } catch (e: Throwable) {
            Log.e(TAG, "Opus init failed", e)
            release()
        }
    }

    fun encodePcmFrame(pcm: ByteArray): EncodedFrame? {
        val enc = encoder ?: return null
        return try {
            val inIndex = enc.dequeueInputBuffer(ENCODE_TIMEOUT_US)
            if (inIndex < 0) return null
            val inBuf = enc.getInputBuffer(inIndex) ?: return null
            inBuf.clear()
            inBuf.put(pcm)
            enc.queueInputBuffer(inIndex, 0, pcm.size, 0, 0)

            val info = MediaCodec.BufferInfo()
            var outIndex = enc.dequeueOutputBuffer(info, ENCODE_TIMEOUT_US)
            while (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                outIndex = enc.dequeueOutputBuffer(info, ENCODE_TIMEOUT_US)
            }
            if (outIndex < 0) return null
            val outBuf = enc.getOutputBuffer(outIndex) ?: return null
            val bytes = ByteArray(info.size)
            outBuf.position(info.offset)
            outBuf.limit(info.offset + info.size)
            outBuf.get(bytes)
            enc.releaseOutputBuffer(outIndex, false)
            EncodedFrame(codec = CODEC_OPUS, payload = bytes)
        } catch (e: Throwable) {
            Log.w(TAG, "encode failed", e)
            null
        }
    }

    fun decodeToPcm(codec: String, payload: ByteArray): ByteArray? {
        if (codec != CODEC_OPUS) return null
        if (!isSupported) return null
        val dec = decoder ?: return null
        return try {
            if (!decoderConfigured) {
                val csd = encoder?.outputFormat ?: return null
                dec.configure(csd, null, null, 0)
                dec.start()
                decoderConfigured = true
            }
            val inIndex = dec.dequeueInputBuffer(DECODE_TIMEOUT_US)
            if (inIndex < 0) return null
            val inBuf = dec.getInputBuffer(inIndex) ?: return null
            inBuf.clear()
            inBuf.put(payload)
            dec.queueInputBuffer(inIndex, 0, payload.size, 0, 0)

            val info = MediaCodec.BufferInfo()
            val outIndex = dec.dequeueOutputBuffer(info, DECODE_TIMEOUT_US)
            if (outIndex < 0) return null
            val outBuf = dec.getOutputBuffer(outIndex) ?: return null
            val pcm = ByteArray(info.size)
            outBuf.position(info.offset)
            outBuf.limit(info.offset + info.size)
            outBuf.get(pcm)
            dec.releaseOutputBuffer(outIndex, false)
            pcm
        } catch (e: Throwable) {
            Log.w(TAG, "decode failed", e)
            null
        }
    }

    private fun primeEncoder() {
        val silence = ByteArray(VoiceAudioPipeline.FRAME_BYTES_PCM)
        encodePcmFrame(silence)
    }

    fun release() {
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        encoder = null
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        decoder = null
        decoderConfigured = false
    }

    data class EncodedFrame(val codec: String, val payload: ByteArray)

    companion object {
        const val CODEC_OPUS = "opus"
        const val CODEC_PCM = "pcm"
        private const val TAG = "VoiceOpusCodec"
        private const val ENCODE_TIMEOUT_US = 5_000L
        private const val DECODE_TIMEOUT_US = 5_000L
    }
}
