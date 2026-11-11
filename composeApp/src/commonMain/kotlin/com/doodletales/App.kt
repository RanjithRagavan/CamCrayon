package com.doodletales

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.doodletales.ai.GeminiStoryEngine
import com.doodletales.imaging.DrawingProcessor
import com.doodletales.imaging.KidDrawingPreset
import com.doodletales.model.AiConfig
import com.doodletales.model.AiProvider
import com.doodletales.model.LivingCreationBundle
import com.doodletales.platform.*
import com.doodletales.ui.AiKeyEvaluationDialog
import com.doodletales.ui.CameraAndDrawingStudio
import com.doodletales.ui.MagicalAnimationTheater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    CAMERA_AND_DRAWING_STUDIO,
    MAGICAL_ANIMATION_THEATER
}

@Composable
fun App() {
    val coroutineScope = rememberCoroutineScope()
    val storyEngine = remember { GeminiStoryEngine() }

    // Auto-detect environment keys if available
    var aiConfig by remember {
        val envGemini = getEnvironmentVariable("GEMINI_API_KEY").orEmpty()
        val envKimi = getEnvironmentVariable("MOONSHOT_API_KEY").orEmpty()
        val envDeepseek = getEnvironmentVariable("DEEPSEEK_API_KEY").orEmpty()
        val defaultProvider = when {
            envGemini.isNotBlank() -> AiProvider.GEMINI
            envKimi.isNotBlank() -> AiProvider.KIMI
            envDeepseek.isNotBlank() -> AiProvider.DEEPSEEK
            else -> AiProvider.GEMINI
        }
        mutableStateOf(
            AiConfig(
                selectedProvider = defaultProvider,
                geminiApiKey = envGemini,
                kimiApiKey = envKimi,
                deepseekApiKey = envDeepseek
            )
        )
    }

    var currentScreen by remember { mutableStateOf(AppScreen.CAMERA_AND_DRAWING_STUDIO) }
    var showAiKeyDialog by remember { mutableStateOf(false) }

    var selectedPreset by remember { mutableStateOf(KidDrawingPreset.FLYING_DINO) }
    var currentPaperBuffer by remember {
        mutableStateOf(DrawingProcessor.generateKidPaperDrawing(KidDrawingPreset.FLYING_DINO))
    }
    var extractedCutoutBuffer by remember(currentPaperBuffer) {
        mutableStateOf(DrawingProcessor.extractCharacterFromPaper(currentPaperBuffer, addStickerBorder = true))
    }

    var kidHintPrompt by remember { mutableStateOf("") }
    var statusBanner by remember {
        mutableStateOf("📸 Ready! Scanned 'Barnaby the Winged Dino' from paper drawing.")
    }
    var isLoadingAnimation by remember { mutableStateOf(false) }
    var isGeneratingTts by remember { mutableStateOf(false) }
    var isGeneratingVideo by remember { mutableStateOf(false) }
    var omniVideoStatus by remember { mutableStateOf<String?>(null) }

    var activeCreationBundle by remember { mutableStateOf<LivingCreationBundle?>(null) }

    val mediaController = rememberPlatformMediaController(
        onImageCaptured = { capturedBuffer, sourceLabel ->
            currentPaperBuffer = capturedBuffer
            extractedCutoutBuffer = DrawingProcessor.extractCharacterFromPaper(capturedBuffer, addStickerBorder = true)
            statusBanner = "📸 Captured from $sourceLabel! Character cutout extracted."
            SoundEngine.playEffect(SoundEffectType.MAGIC_WAND_CHIME)
        },
        onStatusMessage = { msg ->
            statusBanner = msg
        }
    )

    val doodleTheme = darkColorScheme(
        primary = Color(0xFF38BDF8),
        secondary = Color(0xFFF59E0B),
        tertiary = Color(0xFFEC4899),
        background = Color(0xFF0F172A),
        surface = Color(0xFF1E293B)
    )

    MaterialTheme(colorScheme = doodleTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
            Crossfade(targetState = currentScreen) { screen ->
                when (screen) {
                    AppScreen.CAMERA_AND_DRAWING_STUDIO -> {
                        CameraAndDrawingStudio(
                            selectedPreset = selectedPreset,
                            currentPaperBuffer = currentPaperBuffer,
                            extractedCutoutBuffer = extractedCutoutBuffer,
                            kidHintPrompt = kidHintPrompt,
                            onKidHintChanged = { kidHintPrompt = it },
                            onSelectPreset = { preset ->
                                selectedPreset = preset
                                val newPaper = DrawingProcessor.generateKidPaperDrawing(preset)
                                currentPaperBuffer = newPaper
                                extractedCutoutBuffer = DrawingProcessor.extractCharacterFromPaper(newPaper, addStickerBorder = true)
                                statusBanner = "📸 Scanned '${preset.title}' (${preset.kidArtistTag})!"
                            },
                            onNewPaperDrawingCaptured = { newBuf, preset, label ->
                                selectedPreset = preset
                                currentPaperBuffer = newBuf
                                extractedCutoutBuffer = DrawingProcessor.extractCharacterFromPaper(newBuf, addStickerBorder = true)
                                statusBanner = "✨ $label"
                            },
                            onBringToLifeClicked = {
                                isLoadingAnimation = true
                                omniVideoStatus = null
                                coroutineScope.launch {
                                    val pngBytes = withContext(Dispatchers.Default) {
                                        encodeRgbaToPngBytes(extractedCutoutBuffer)
                                    }
                                    val bundle = storyEngine.bringDrawingToLife(
                                        originalPaperBuffer = currentPaperBuffer,
                                        extractedCharacterBuffer = extractedCutoutBuffer,
                                        pngBytes = pngBytes,
                                        kidHintPrompt = kidHintPrompt,
                                        activePreset = selectedPreset,
                                        config = aiConfig,
                                        onProgressStep = { stepMsg ->
                                            statusBanner = stepMsg
                                        }
                                    )
                                    activeCreationBundle = bundle
                                    isLoadingAnimation = false
                                    currentScreen = AppScreen.MAGICAL_ANIMATION_THEATER
                                }
                            },
                            onOpenAiKeyHub = { showAiKeyDialog = true },
                            aiConfig = aiConfig,
                            mediaController = mediaController,
                            statusBanner = statusBanner,
                            isLoadingAnimation = isLoadingAnimation
                        )
                    }

                    AppScreen.MAGICAL_ANIMATION_THEATER -> {
                        val bundle = activeCreationBundle
                        if (bundle != null) {
                            MagicalAnimationTheater(
                                bundle = bundle,
                                onBackToStudio = {
                                    currentScreen = AppScreen.CAMERA_AND_DRAWING_STUDIO
                                },
                                onRequestSceneTts = { scene ->
                                    val cached = bundle.sceneAudioWavBytes[scene.sceneNumber]
                                    if (cached != null) {
                                        SoundEngine.playWav(cached)
                                    } else if (aiConfig.geminiApiKey.isNotBlank()) {
                                        isGeneratingTts = true
                                        coroutineScope.launch {
                                            val wav = storyEngine.generateSpeechWithGemini38Tts(
                                                apiKey = aiConfig.geminiApiKey.trim(),
                                                narrationText = "${scene.narrationText} ${scene.characterSpeechBubble}",
                                                voiceName = aiConfig.storytellerVoice
                                            )
                                            isGeneratingTts = false
                                            if (wav != null && wav.isNotEmpty()) {
                                                val updatedMap = bundle.sceneAudioWavBytes.toMutableMap()
                                                updatedMap[scene.sceneNumber] = wav
                                                activeCreationBundle = bundle.copy(sceneAudioWavBytes = updatedMap)
                                                SoundEngine.playWav(wav)
                                            } else {
                                                SoundEngine.playEffect(SoundEffectType.MAGIC_WAND_CHIME)
                                            }
                                        }
                                    } else {
                                        SoundEngine.playEffect(SoundEffectType.MAGIC_WAND_CHIME)
                                        omniVideoStatus = "🎙️ Tip: Add your Gemini API key in the Studio Key Hub for live Gemini 3.8 Flash TTS storyteller audio!"
                                    }
                                },
                                onRequestOmniVideo = { scene ->
                                    if (aiConfig.geminiApiKey.isBlank()) {
                                        omniVideoStatus = "🎬 Add your Gemini API key in the Key Hub to generate a 5s 720p MP4 clip with gemini-omni-1.2-flash!"
                                        SoundEngine.playEffect(SoundEffectType.MAGIC_WAND_CHIME)
                                    } else {
                                        isGeneratingVideo = true
                                        coroutineScope.launch {
                                            val msg = storyEngine.generateOmniFlashVideoPreview(
                                                apiKey = aiConfig.geminiApiKey.trim(),
                                                pngBytes = bundle.originalPngBytes,
                                                story = bundle.story,
                                                scene = scene,
                                                onStatus = { s -> omniVideoStatus = s }
                                            )
                                            omniVideoStatus = msg
                                            isGeneratingVideo = false
                                        }
                                    }
                                },
                                isGeneratingTts = isGeneratingTts,
                                isGeneratingVideo = isGeneratingVideo,
                                omniVideoStatus = omniVideoStatus
                            )
                        }
                    }
                }
            }

            if (showAiKeyDialog) {
                AiKeyEvaluationDialog(
                    currentConfig = aiConfig,
                    onSaveConfig = { newConfig ->
                        aiConfig = newConfig
                        statusBanner = "✅ Active AI Provider set to ${newConfig.selectedProvider.displayName}"
                    },
                    onDismiss = { showAiKeyDialog = false }
                )
            }
        }
    }
}
