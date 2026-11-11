package com.ztoais.CamCrayon.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.ztoais.CamCrayon.imaging.DrawingProcessor
import com.ztoais.CamCrayon.imaging.KidDrawingPreset
import com.ztoais.CamCrayon.model.RgbaBuffer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.NSProcessInfo
import platform.Foundation.dataWithBytes
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

private fun rgbaBufferToSkiaBitmap(buffer: RgbaBuffer): Bitmap {
    val w = buffer.width
    val h = buffer.height
    val bytes = ByteArray(w * h * 4)
    for (i in 0 until w * h) {
        val argb = buffer.pixels[i]
        val bIdx = i * 4
        bytes[bIdx] = (argb and 0xFF).toByte() // Blue
        bytes[bIdx + 1] = ((argb ushr 8) and 0xFF).toByte() // Green
        bytes[bIdx + 2] = ((argb ushr 16) and 0xFF).toByte() // Red
        bytes[bIdx + 3] = ((argb ushr 24) and 0xFF).toByte() // Alpha
    }
    val bitmap = Bitmap()
    val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    bitmap.allocPixels(info)
    bitmap.installPixels(info, bytes, w * 4)
    return bitmap
}

actual fun rgbaBufferToImageBitmap(buffer: RgbaBuffer): ImageBitmap {
    val skiaBmp = rgbaBufferToSkiaBitmap(buffer)
    return Image.makeFromBitmap(skiaBmp).toComposeImageBitmap()
}

actual fun encodeRgbaToPngBytes(buffer: RgbaBuffer): ByteArray {
    val skiaBmp = rgbaBufferToSkiaBitmap(buffer)
    val image = Image.makeFromBitmap(skiaBmp)
    val data = image.encodeToData(EncodedImageFormat.PNG, 100) ?: return ByteArray(0)
    return data.bytes
}

actual fun decodeImageBytesToRgba(bytes: ByteArray): RgbaBuffer? {
    return try {
        val image = Image.makeFromEncoded(bytes)
        val w = image.width
        val h = image.height
        val bmp = Bitmap()
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        bmp.allocPixels(info)
        if (!image.readPixels(bmp)) return null
        val rawBytes = bmp.readPixels(info, w * 4, 0, 0) ?: return null
        val pixels = IntArray(w * h)
        for (i in 0 until w * h) {
            val bIdx = i * 4
            val b = rawBytes[bIdx].toInt() and 0xFF
            val g = rawBytes[bIdx + 1].toInt() and 0xFF
            val r = rawBytes[bIdx + 2].toInt() and 0xFF
            val a = rawBytes[bIdx + 3].toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        RgbaBuffer(w, h, pixels)
    } catch (_: Exception) {
        null
    }
}

private var activeIosAudioPlayer: AVAudioPlayer? = null

@OptIn(ExperimentalForeignApi::class)
actual fun playPlatformWavBytes(wavBytes: ByteArray) {
    try {
        stopPlatformAudio()
        if (wavBytes.isEmpty()) return
        val nsData = wavBytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), wavBytes.size.toULong())
        }
        val player = AVAudioPlayer(data = nsData, error = null)
        player.prepareToPlay()
        activeIosAudioPlayer = player
        player.play()
    } catch (_: Exception) {
    }
}

actual fun stopPlatformAudio() {
    try {
        activeIosAudioPlayer?.stop()
        activeIosAudioPlayer = null
    } catch (_: Exception) {
    }
}

actual fun getEnvironmentVariable(name: String): String? {
    val env = NSProcessInfo.processInfo.environment
    return env[name] as? String
}

@OptIn(ExperimentalForeignApi::class)
private fun nsDataToByteArray(data: NSData): ByteArray {
    val length = data.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return bytes
}

@Composable
actual fun rememberPlatformMediaController(
    onImageCaptured: (RgbaBuffer, String) -> Unit,
    onStatusMessage: (String) -> Unit
): PlatformMediaController {
    return remember {
        object : PlatformMediaController {
            private var activeDelegate: NSObject? = null
            private var simIndex = 0
            override val platformName: String = "iOS"
            override val canCaptureFromDeviceCamera: Boolean = true

            override fun captureFromCamera() {
                if (UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
                    presentImagePicker(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera, "iOS Camera")
                } else {
                    // iOS Simulator fallback: cycle through simulated camera viewfinder scans
                    val presets = KidDrawingPreset.entries
                    simIndex = (simIndex + 1) % presets.size
                    val preset = presets[simIndex]
                    onImageCaptured(
                        DrawingProcessor.generateKidPaperDrawing(preset),
                        "iOS Simulator Camera (${preset.title})"
                    )
                }
            }

            override fun pickFromGalleryOrFile() {
                if (UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary)) {
                    presentImagePicker(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary, "iOS Photos")
                } else {
                    onStatusMessage("ℹ️ Photo library unavailable on this device.")
                }
            }

            private var voiceIdx = 0
            private val sampleVoiceIdeas = listOf(
                "Barnaby the Dino flies to the Rainbow Candy Castle to rescue a shooting star!",
                "Captain Whiskers zooms around the Golden Cheese Moon with friendly aliens!",
                "Princess Pippa starts a magical underwater bubble band with singing dolphins!",
                "Sparky the Robot dances on Volcano Island and discovers a glowing crown!"
            )

            override fun startVoiceInput(onSpokenText: (String) -> Unit) {
                val spoken = sampleVoiceIdeas[voiceIdx % sampleVoiceIdeas.size]
                voiceIdx++
                onSpokenText(spoken)
                onStatusMessage("🎤 Voice Idea: \"$spoken\"")
            }

            private fun presentImagePicker(sourceType: UIImagePickerControllerSourceType, label: String) {
                val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
                val picker = UIImagePickerController()
                picker.sourceType = sourceType
                val delegate = object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
                    override fun imagePickerController(
                        picker: UIImagePickerController,
                        didFinishPickingMediaWithInfo: Map<Any?, *>
                    ) {
                        val uiImage = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                        if (uiImage != null) {
                            val pngData = UIImagePNGRepresentation(uiImage)
                            if (pngData != null) {
                                val bytes = nsDataToByteArray(pngData)
                                val rgba = decodeImageBytesToRgba(bytes)
                                if (rgba != null) {
                                    onImageCaptured(rgba, label)
                                }
                            }
                        }
                        picker.dismissViewControllerAnimated(true, completion = null)
                    }

                    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                        picker.dismissViewControllerAnimated(true, completion = null)
                    }
                }
                activeDelegate = delegate
                picker.delegate = delegate
                rootVc.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}
