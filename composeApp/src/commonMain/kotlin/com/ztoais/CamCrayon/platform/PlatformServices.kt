package com.ztoais.CamCrayon.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.ztoais.CamCrayon.model.RgbaBuffer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class SoundEffectType {
    JUMP_BOING,
    MAGIC_WAND_CHIME,
    CAMERA_SCAN_WHOOSH,
    FANFARE_CELEBRATION,
    BUBBLE_POP
}

interface PlatformMediaController {
    val platformName: String
    val canCaptureFromDeviceCamera: Boolean
    fun captureFromCamera()
    fun pickFromGalleryOrFile()
    fun startVoiceInput(onSpokenText: (String) -> Unit)
    fun requestCameraPermissionIfNeeded() {}
}

expect fun rgbaBufferToImageBitmap(buffer: RgbaBuffer): ImageBitmap

expect fun encodeRgbaToPngBytes(buffer: RgbaBuffer): ByteArray

expect fun decodeImageBytesToRgba(bytes: ByteArray): RgbaBuffer?

expect fun playPlatformWavBytes(wavBytes: ByteArray)

expect fun stopPlatformAudio()

expect fun getEnvironmentVariable(name: String): String?

@Composable
expect fun rememberPlatformMediaController(
    onImageCaptured: (RgbaBuffer, String) -> Unit,
    onStatusMessage: (String) -> Unit
): PlatformMediaController

/**
 * Pure Kotlin synthesizer for joyful kids' sound effects + Gemini 3.8 TTS WAV audio player!
 */
object SoundEngine {
    var isMuted: Boolean = false

    fun playWav(wavBytes: ByteArray) {
        if (isMuted || wavBytes.isEmpty()) return
        playPlatformWavBytes(wavBytes)
    }

    fun stop() {
        stopPlatformAudio()
    }

    fun playEffect(effect: SoundEffectType) {
        if (isMuted) return
        val wavBytes = synthesizeEffectWav(effect)
        playPlatformWavBytes(wavBytes)
    }

    private fun synthesizeEffectWav(effect: SoundEffectType): ByteArray {
        val sampleRate = 22050
        val durationSec = when (effect) {
            SoundEffectType.JUMP_BOING -> 0.36
            SoundEffectType.MAGIC_WAND_CHIME -> 0.85
            SoundEffectType.CAMERA_SCAN_WHOOSH -> 0.55
            SoundEffectType.FANFARE_CELEBRATION -> 1.10
            SoundEffectType.BUBBLE_POP -> 0.22
        }
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val normalized = t / durationSec
            val value: Double = when (effect) {
                SoundEffectType.JUMP_BOING -> {
                    // Rising cartoon spring boing with vibrato
                    val freq = 240.0 + 480.0 * normalized + 35.0 * sin(2.0 * PI * 22.0 * t)
                    val env = exp(-4.5 * t)
                    sin(2.0 * PI * freq * t) * env * 0.65
                }
                SoundEffectType.MAGIC_WAND_CHIME -> {
                    // Sparkling arpeggio chord (C6 - E6 - G6 - B6 - C7)
                    val notes = doubleArrayOf(1046.5, 1318.5, 1568.0, 1975.5, 2093.0)
                    var sum = 0.0
                    for (nIdx in notes.indices) {
                        val noteStart = nIdx * 0.11
                        if (t >= noteStart) {
                            val localT = t - noteStart
                            val env = exp(-4.2 * localT)
                            sum += sin(2.0 * PI * notes[nIdx] * localT) * env * 0.22
                            sum += sin(2.0 * PI * (notes[nIdx] * 2.0) * localT) * env * 0.08
                        }
                    }
                    sum
                }
                SoundEffectType.CAMERA_SCAN_WHOOSH -> {
                    // Sci-fi harmonic scan sweep
                    val freq = 320.0 + 880.0 * sin(PI * normalized)
                    val env = sin(PI * normalized)
                    (sin(2.0 * PI * freq * t) * 0.45 + sin(2.0 * PI * (freq * 1.5) * t) * 0.2) * env
                }
                SoundEffectType.FANFARE_CELEBRATION -> {
                    // Happy 4-note fanfare: G5 -> C6 -> E6 -> G6
                    val noteFreq = when {
                        t < 0.16 -> 783.99
                        t < 0.32 -> 1046.50
                        t < 0.48 -> 1318.51
                        else -> 1567.98
                    }
                    val localT = when {
                        t < 0.16 -> t
                        t < 0.32 -> t - 0.16
                        t < 0.48 -> t - 0.32
                        else -> t - 0.48
                    }
                    val env = exp(-2.8 * localT)
                    (sin(2.0 * PI * noteFreq * t) * 0.45 +
                        sin(2.0 * PI * noteFreq * 2.0 * t) * 0.18 +
                        sin(2.0 * PI * noteFreq * 3.0 * t) * 0.08) * env
                }
                SoundEffectType.BUBBLE_POP -> {
                    val freq = 420.0 + 900.0 * (normalized * normalized)
                    val env = exp(-8.0 * t)
                    sin(2.0 * PI * freq * t) * env * 0.6
                }
            }
            val clamped = value.coerceIn(-0.98, 0.98)
            samples[i] = (clamped * 32767.0).toInt().toShort()
        }

        return encodePcm16MonoToWav(samples, sampleRate)
    }

    fun encodePcm16MonoToWav(samples: ShortArray, sampleRate: Int): ByteArray {
        val byteRate = sampleRate * 2
        val dataSize = samples.size * 2
        val totalSize = 44 + dataSize
        val out = ByteArray(totalSize)

        fun writeAscii(offset: Int, s: String) {
            for (i in s.indices) out[offset + i] = s[i].code.toByte()
        }

        fun writeIntLE(offset: Int, v: Int) {
            out[offset] = (v and 0xFF).toByte()
            out[offset + 1] = ((v ushr 8) and 0xFF).toByte()
            out[offset + 2] = ((v ushr 16) and 0xFF).toByte()
            out[offset + 3] = ((v ushr 24) and 0xFF).toByte()
        }

        fun writeShortLE(offset: Int, v: Int) {
            out[offset] = (v and 0xFF).toByte()
            out[offset + 1] = ((v ushr 8) and 0xFF).toByte()
        }

        writeAscii(0, "RIFF")
        writeIntLE(4, totalSize - 8)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeIntLE(16, 16) // PCM chunk size
        writeShortLE(20, 1) // AudioFormat = 1 (PCM)
        writeShortLE(22, 1) // NumChannels = 1 (Mono)
        writeIntLE(24, sampleRate)
        writeIntLE(28, byteRate)
        writeShortLE(32, 2) // BlockAlign
        writeShortLE(34, 16) // BitsPerSample
        writeAscii(36, "data")
        writeIntLE(40, dataSize)

        var pos = 44
        for (s in samples) {
            val v = s.toInt()
            out[pos++] = (v and 0xFF).toByte()
            out[pos++] = ((v ushr 8) and 0xFF).toByte()
        }
        return out
    }
}
