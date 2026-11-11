package com.ztoais.CamCrayon.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.ztoais.CamCrayon.imaging.DrawingProcessor
import com.ztoais.CamCrayon.imaging.KidDrawingPreset
import com.ztoais.CamCrayon.model.RgbaBuffer
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

actual fun rgbaBufferToImageBitmap(buffer: RgbaBuffer): ImageBitmap {
    val img = BufferedImage(buffer.width, buffer.height, BufferedImage.TYPE_INT_ARGB)
    img.setRGB(0, 0, buffer.width, buffer.height, buffer.pixels, 0, buffer.width)
    return img.toComposeImageBitmap()
}

actual fun encodeRgbaToPngBytes(buffer: RgbaBuffer): ByteArray {
    val img = BufferedImage(buffer.width, buffer.height, BufferedImage.TYPE_INT_ARGB)
    img.setRGB(0, 0, buffer.width, buffer.height, buffer.pixels, 0, buffer.width)
    val baos = ByteArrayOutputStream()
    ImageIO.write(img, "png", baos)
    return baos.toByteArray()
}

actual fun decodeImageBytesToRgba(bytes: ByteArray): RgbaBuffer? {
    return try {
        val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        val w = img.width
        val h = img.height
        val pixels = IntArray(w * h)
        img.getRGB(0, 0, w, h, pixels, 0, w)
        RgbaBuffer(w, h, pixels)
    } catch (_: Exception) {
        null
    }
}

private var activeDesktopClip: Clip? = null

actual fun playPlatformWavBytes(wavBytes: ByteArray) {
    try {
        stopPlatformAudio()
        val audioStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(wavBytes))
        val clip = AudioSystem.getClip()
        clip.open(audioStream)
        activeDesktopClip = clip
        clip.start()
    } catch (_: Exception) {
        // Ignore if headless or no audio device attached
    }
}

actual fun stopPlatformAudio() {
    try {
        activeDesktopClip?.let {
            if (it.isRunning) it.stop()
            it.close()
        }
        activeDesktopClip = null
    } catch (_: Exception) {
    }
}

actual fun getEnvironmentVariable(name: String): String? {
    val sysEnv = System.getenv(name)
    if (!sysEnv.isNullOrBlank()) return sysEnv
    // Also check local.properties in the project root for convenience
    return try {
        val propsFile = listOf(File("local.properties"), File("../local.properties"))
            .firstOrNull { it.exists() }
        if (propsFile != null) {
            val props = java.util.Properties()
            propsFile.inputStream().use { props.load(it) }
            props.getProperty(name)?.takeIf { it.isNotBlank() }
        } else null
    } catch (_: Exception) {
        null
    }
}

@Composable
actual fun rememberPlatformMediaController(
    onImageCaptured: (RgbaBuffer, String) -> Unit,
    onStatusMessage: (String) -> Unit
): PlatformMediaController {
    return remember {
        object : PlatformMediaController {
            private var cameraCycleIndex = 0
            override val platformName: String = "Desktop JVM"
            override val canCaptureFromDeviceCamera: Boolean = true

            override fun captureFromCamera() {
                // Check if macOS imagesnap is installed for real webcam frame grab; otherwise cycle simulated camera capture or open picker
                val tempSnap = File(System.getProperty("java.io.tmpdir"), "doodletales_webcam.jpg")
                val grabbedFromWebcam = try {
                    val imagesnapBin = listOf("/opt/homebrew/bin/imagesnap", "/usr/local/bin/imagesnap")
                        .firstOrNull { File(it).canExecute() }
                    if (imagesnapBin != null) {
                        val proc = ProcessBuilder(imagesnapBin, "-w", "0.5", tempSnap.absolutePath).start()
                        proc.waitFor() == 0 && tempSnap.exists() && tempSnap.length() > 0
                    } else {
                        false
                    }
                } catch (_: Exception) {
                    false
                }

                if (grabbedFromWebcam) {
                    val decoded = decodeImageBytesToRgba(tempSnap.readBytes())
                    if (decoded != null) {
                        onImageCaptured(decoded, "Mac FaceTime Webcam")
                        return
                    }
                }

                // Instant simulated camera viewfinder capture cycling through all 4 kid drawings
                val presets = KidDrawingPreset.entries
                cameraCycleIndex = (cameraCycleIndex + 1) % presets.size
                val preset = presets[cameraCycleIndex]
                val simulatedPaper = DrawingProcessor.generateKidPaperDrawing(preset)
                onImageCaptured(simulatedPaper, "Camera Viewfinder (${preset.title})")
            }

            override fun pickFromGalleryOrFile() {
                try {
                    val dialog = FileDialog(null as Frame?, "Select Kid's Drawing Photo (PNG/JPG)", FileDialog.LOAD)
                    dialog.setFilenameFilter { _, name ->
                        val lower = name.lowercase()
                        lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    }
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val file = dialog.file
                    if (dir != null && file != null) {
                        val selectedFile = File(dir, file)
                        val bytes = selectedFile.readBytes()
                        val rgba = decodeImageBytesToRgba(bytes)
                        if (rgba != null) {
                            onImageCaptured(rgba, selectedFile.name)
                        } else {
                            onStatusMessage("⚠️ Could not decode image file: ${selectedFile.name}")
                        }
                    }
                } catch (e: Exception) {
                    onStatusMessage("ℹ️ File dialog closed: ${e.message ?: ""}")
                }
            }
        }
    }
}
