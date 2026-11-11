package com.ztoais.CamCrayon.platform

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ztoais.CamCrayon.model.RgbaBuffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

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
        val tempFile = File.createTempFile("camcrayon_audio_", ".wav")
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
    var pendingOpenCameraAfterPermission by remember { mutableStateOf(false) }
    var pendingVoiceCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

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

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            onStatusMessage("✅ Camera permission granted!")
            if (pendingOpenCameraAfterPermission) {
                pendingOpenCameraAfterPermission = false
                try {
                    cameraLauncher.launch(null)
                } catch (e: Exception) {
                    onStatusMessage("⚠️ Could not launch camera: ${e.message ?: ""}")
                }
            }
        } else {
            pendingOpenCameraAfterPermission = false
            onStatusMessage("⚠️ Camera permission is required to scan drawings with the camera.")
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank()) {
                pendingVoiceCallback?.invoke(spoken)
                onStatusMessage("🎤 Heard: \"$spoken\"")
            } else {
                onStatusMessage("ℹ️ Didn't catch that — tap the mic to try speaking again!")
            }
        }
    }

    val launchSpeechIntent = {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "🎤 Speak your character's name & dream adventure!")
            }
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            onStatusMessage("⚠️ Voice speech recognition is not available on this device.")
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchSpeechIntent()
        } else {
            onStatusMessage("⚠️ Microphone permission is needed to speak your story idea.")
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

    return remember(cameraLauncher, cameraPermissionLauncher, galleryLauncher, speechRecognizerLauncher, audioPermissionLauncher) {
        object : PlatformMediaController {
            override val platformName: String = "Android"
            override val canCaptureFromDeviceCamera: Boolean = true

            override fun requestCameraPermissionIfNeeded() {
                val hasCameraPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasCameraPerm) {
                    pendingOpenCameraAfterPermission = false
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            override fun captureFromCamera() {
                val hasCameraPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasCameraPerm) {
                    try {
                        cameraLauncher.launch(null)
                    } catch (e: Exception) {
                        onStatusMessage("⚠️ Camera error: ${e.message ?: ""}")
                    }
                } else {
                    pendingOpenCameraAfterPermission = true
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            override fun pickFromGalleryOrFile() {
                galleryLauncher.launch("image/*")
            }

            override fun startVoiceInput(onSpokenText: (String) -> Unit) {
                pendingVoiceCallback = onSpokenText
                val hasAudioPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasAudioPerm) {
                    launchSpeechIntent()
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
}
