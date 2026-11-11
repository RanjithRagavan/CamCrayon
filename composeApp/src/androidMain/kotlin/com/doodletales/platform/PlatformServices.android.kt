package com.doodletales.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.doodletales.model.RgbaBuffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

actual fun rgbaBufferToImageBitmap(buffer: RgbaBuffer): ImageBitmap {
    val bmp = Bitmap.createBitmap(buffer.pixels, buffer.width, buffer.height, Bitmap.Config.ARGB_8888)
    return bmp.asImageBitmap()
}

actual fun encodeRgbaToPngBytes(buffer: RgbaBuffer): ByteArray {
    val bmp = Bitmap.createBitmap(buffer.pixels, buffer.width, buffer.height, Bitmap.Config.ARGB_8888)
    val baos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
    return baos.toByteArray()
}

actual fun decodeImageBytesToRgba(bytes: ByteArray): RgbaBuffer? {
    return try {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val argbBmp = bmp.copy(Bitmap.Config.ARGB_8888, false)
        val w = argbBmp.width
        val h = argbBmp.height
        val pixels = IntArray(w * h)
        argbBmp.getPixels(pixels, 0, w, 0, 0, w, h)
        RgbaBuffer(w, h, pixels)
    } catch (_: Exception) {
        null
    }
}

private var activeMediaPlayer: MediaPlayer? = null

actual fun playPlatformWavBytes(wavBytes: ByteArray) {
    try {
        stopPlatformAudio()
        val tempFile = File.createTempFile("doodletales_audio_", ".wav")
        tempFile.deleteOnExit()
        FileOutputStream(tempFile).use { it.write(wavBytes) }
        val mp = MediaPlayer()
        mp.setDataSource(tempFile.absolutePath)
        mp.prepare()
        mp.setOnCompletionListener { player ->
            player.release()
            tempFile.delete()
        }
        activeMediaPlayer = mp
        mp.start()
    } catch (_: Exception) {
    }
}

actual fun stopPlatformAudio() {
    try {
        activeMediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        activeMediaPlayer = null
    } catch (_: Exception) {
    }
}

actual fun getEnvironmentVariable(name: String): String? {
    return System.getenv(name)
}

@Composable
actual fun rememberPlatformMediaController(
    onImageCaptured: (RgbaBuffer, String) -> Unit,
    onStatusMessage: (String) -> Unit
): PlatformMediaController {
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val argbBmp = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            val w = argbBmp.width
            val h = argbBmp.height
            val pixels = IntArray(w * h)
            argbBmp.getPixels(pixels, 0, w, 0, 0, w, h)
            onImageCaptured(RgbaBuffer(w, h, pixels), "Android Camera")
        } else {
            onStatusMessage("ℹ️ Camera capture cancelled.")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val rgba = decodeImageBytesToRgba(bytes)
                    if (rgba != null) {
                        onImageCaptured(rgba, "Android Photo Gallery")
                    }
                }
            } catch (e: Exception) {
                onStatusMessage("⚠️ Could not load image: ${e.message ?: ""}")
            }
        }
    }

    return remember(cameraLauncher, galleryLauncher) {
        object : PlatformMediaController {
            override val platformName: String = "Android"
            override val canCaptureFromDeviceCamera: Boolean = true

            override fun captureFromCamera() {
                try {
                    cameraLauncher.launch(null)
                } catch (e: Exception) {
                    onStatusMessage("⚠️ Camera error: ${e.message ?: "Check permission"}")
                }
            }

            override fun pickFromGalleryOrFile() {
                galleryLauncher.launch("image/*")
            }
        }
    }
}
