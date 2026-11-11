package com.ztoais.CamCrayon.ai

import com.ztoais.CamCrayon.imaging.DrawingProcessor
import com.ztoais.CamCrayon.imaging.KidDrawingPreset
import com.ztoais.CamCrayon.model.*
import com.ztoais.CamCrayon.platform.decodeImageBytesToRgba
import com.ztoais.CamCrayon.platform.rgbaBufferToImageBitmap
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Unified Multimodal AI Story & Animation Choreography Engine.
 *
 * Primary recommended backend: Google Gemini Interactions API (/v1beta/interactions, Api-Revision: 2026-05-20):
 *  - Vision + Structured JSON Storyboard & Physics Choreography: `gemini-3.8-flash`
 *  - Storybook Character Sprite Upgrade: `gemini-nano-banana-2.1`
 *  - Expressive Read-Aloud Storyteller Audio (WAV): `gemini-3.8-flash-tts`
 *  - First-Frame-to-Video MP4 Generation: `gemini-omni-1.2-flash`
 *
 * Also supports Kimi (Moonshot Vision) and DeepSeek (DeepSeek-V3) so the user can test any key!
 */
class GeminiStoryEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(this@GeminiStoryEngine.json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
    }

    companion object {
        private const val GEMINI_INTERACTIONS_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
        private const val GEMINI_API_REVISION = "2026-05-20"
        private const val KIMI_CHAT_URL = "https://api.moonshot.cn/v1/chat/completions"
        private const val DEEPSEEK_CHAT_URL = "https://api.deepseek.com/v1/chat/completions"
    }

    /**
     * Generates a complete 4-scene living animated story adventure from the child's drawing,
     * plus optional Nano Banana 2.1 character art and Gemini 3.8 Flash TTS narration!
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun bringDrawingToLife(
        originalPaperBuffer: RgbaBuffer,
        extractedCharacterBuffer: RgbaBuffer,
        pngBytes: ByteArray,
        kidHintPrompt: String,
        activePreset: KidDrawingPreset,
        config: AiConfig,
        onProgressStep: (String) -> Unit
    ): LivingCreationBundle {
        val dominantColors = DrawingProcessor.describeDominantColors(extractedCharacterBuffer)
        val originalBitmap = rgbaBufferToImageBitmap(originalPaperBuffer)
        val extractedBitmap = rgbaBufferToImageBitmap(extractedCharacterBuffer)
        val localStorybookBitmap = rgbaBufferToImageBitmap(
            DrawingProcessor.synthesizeLivingStorybookCharacter(extractedCharacterBuffer)
        )
        val apiKey = config.activeApiKey

        // If no API key is entered yet, run Instant Magic Demo Mode with full 60 FPS choreography + 3D Storybook Character
        if (apiKey.isBlank()) {
            onProgressStep("✨ Transforming your drawing into a 3D-shaded Storybook Character & 4-scene adventure...")
            val fallbackStory = customizeFallbackStory(
                base = DrawingProcessor.createRichStoryForPreset(activePreset, dominantColors),
                kidHint = kidHintPrompt,
                dominantColors = dominantColors
            )
            return LivingCreationBundle(
                originalPaperBuffer = originalPaperBuffer,
                originalPaperBitmap = originalBitmap,
                extractedCharacterBuffer = extractedCharacterBuffer,
                extractedCharacterBitmap = extractedBitmap,
                originalPngBytes = pngBytes,
                aiEnhancedBitmap = localStorybookBitmap,
                story = fallbackStory,
                sceneAudioWavBytes = emptyMap(),
                providerUsed = "Instant Magic Mode (3D Storybook Rig)"
            )
        }

        return when (config.selectedProvider) {
            AiProvider.GEMINI -> runGeminiInteractionsPipeline(
                apiKey = apiKey,
                originalPaperBuffer = originalPaperBuffer,
                originalBitmap = originalBitmap,
                extractedCharacterBuffer = extractedCharacterBuffer,
                extractedBitmap = extractedBitmap,
                pngBytes = pngBytes,
                kidHintPrompt = kidHintPrompt,
                dominantColors = dominantColors,
                activePreset = activePreset,
                config = config,
                onProgressStep = onProgressStep
            )

            AiProvider.KIMI -> runKimiPipeline(
                apiKey = apiKey,
                originalPaperBuffer = originalPaperBuffer,
                originalBitmap = originalBitmap,
                extractedCharacterBuffer = extractedCharacterBuffer,
                extractedBitmap = extractedBitmap,
                pngBytes = pngBytes,
                kidHintPrompt = kidHintPrompt,
                dominantColors = dominantColors,
                activePreset = activePreset,
                onProgressStep = onProgressStep
            )

            AiProvider.DEEPSEEK -> runDeepSeekPipeline(
                apiKey = apiKey,
                originalPaperBuffer = originalPaperBuffer,
                originalBitmap = originalBitmap,
                extractedCharacterBuffer = extractedCharacterBuffer,
                extractedBitmap = extractedBitmap,
                pngBytes = pngBytes,
                kidHintPrompt = kidHintPrompt,
                dominantColors = dominantColors,
                activePreset = activePreset,
                onProgressStep = onProgressStep
            )
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun runGeminiInteractionsPipeline(
        apiKey: String,
        originalPaperBuffer: RgbaBuffer,
        originalBitmap: androidx.compose.ui.graphics.ImageBitmap,
        extractedCharacterBuffer: RgbaBuffer,
        extractedBitmap: androidx.compose.ui.graphics.ImageBitmap,
        pngBytes: ByteArray,
        kidHintPrompt: String,
        dominantColors: String,
        activePreset: KidDrawingPreset,
        config: AiConfig,
        onProgressStep: (String) -> Unit
    ): LivingCreationBundle {
        val base64Image = Base64.encode(pngBytes)

        // 1. Vision + Structured Story & Animation Choreography via gemini-3.8-flash
        onProgressStep("🔍 Gemini 3.8 Flash is inspecting your camera drawing & choreographing 4 animated scenes...")
        val (story, interactionId) = try {
            generateStoryWithGemini38Flash(
                apiKey = apiKey,
                base64Png = base64Image,
                kidHintPrompt = kidHintPrompt,
                dominantColors = dominantColors
            )
        } catch (e: Exception) {
            onProgressStep("ℹ️ Using local story choreography (${e.message?.take(60) ?: "API fallback"})...")
            customizeFallbackStory(
                DrawingProcessor.createRichStoryForPreset(activePreset, dominantColors),
                kidHintPrompt,
                dominantColors
            ) to null
        }

        // 2. Optional Storybook Character Sprite Upgrade via gemini-nano-banana-2.1 (with 3D Storybook fallback)
        var aiEnhancedBitmap: androidx.compose.ui.graphics.ImageBitmap? = null
        if (config.generateNanoBananaSprite) {
            onProgressStep("🎨 Transforming your drawing into a living Storybook Character...")
            aiEnhancedBitmap = try {
                generateEnhancedPuppetWithNanoBanana21(
                    apiKey = apiKey,
                    base64Png = base64Image,
                    characterName = story.characterName,
                    characterType = story.characterSpeciesOrType
                )
            } catch (_: Exception) {
                null
            }
        }
        if (aiEnhancedBitmap == null) {
            aiEnhancedBitmap = rgbaBufferToImageBitmap(
                DrawingProcessor.synthesizeLivingStorybookCharacter(extractedCharacterBuffer)
            )
        }

        // 3. Optional Expressive Storyteller Voiceover for Scene 1 via gemini-3.8-flash-tts
        val sceneAudioMap = mutableMapOf<Int, ByteArray>()
        if (config.generateTtsNarration && story.scenes.isNotEmpty()) {
            onProgressStep("🎙️ Gemini 3.8 Flash TTS is recording expressive storyteller narration (Voice: ${config.storytellerVoice})...")
            val firstScene = story.scenes.first()
            try {
                val wavBytes = generateSpeechWithGemini38Tts(
                    apiKey = apiKey,
                    narrationText = "${firstScene.narrationText} ${firstScene.characterSpeechBubble}",
                    voiceName = config.storytellerVoice
                )
                if (wavBytes != null && wavBytes.isNotEmpty()) {
                    sceneAudioMap[firstScene.sceneNumber] = wavBytes
                }
            } catch (_: Exception) {
                // Non-fatal if TTS quota/network is unavailable
            }
        }

        val usedBadges = buildList {
            add("gemini-3.8-flash")
            if (aiEnhancedBitmap != null) add("gemini-nano-banana-2.1")
            if (sceneAudioMap.isNotEmpty()) add("gemini-3.8-flash-tts")
        }.joinToString(" + ")

        return LivingCreationBundle(
            originalPaperBuffer = originalPaperBuffer,
            originalPaperBitmap = originalBitmap,
            extractedCharacterBuffer = extractedCharacterBuffer,
            extractedCharacterBitmap = extractedBitmap,
            originalPngBytes = pngBytes,
            aiEnhancedBitmap = aiEnhancedBitmap,
            story = story,
            sceneAudioWavBytes = sceneAudioMap,
            interactionId = interactionId,
            providerUsed = "Gemini Interactions API ($usedBadges)"
        )
    }

    /**
     * Generates on-demand Gemini 3.8 Flash TTS WAV audio for any scene in the story.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun generateSpeechWithGemini38Tts(
        apiKey: String,
        narrationText: String,
        voiceName: String = "Kore"
    ): ByteArray? {
        if (apiKey.isBlank()) return null

        // Clean emojis from verbatim TTS transcript per Gemini 3.8 TTS best practices
        val cleanTranscript = narrationText
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val requestPayload = buildJsonObject {
            put("model", "gemini-3.8-flash-tts")
            putJsonArray("input") {
                addJsonObject {
                    put("type", "user_input")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", cleanTranscript)
                            putJsonArray("annotations") {
                                addJsonObject {
                                    put("type", "speech_metadata")
                                    put("style", "cheerful, warm, magical children's storybook narrator full of wonder")
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("response_format") {
                put("type", "audio")
            }
            putJsonObject("generation_config") {
                putJsonArray("speech_config") {
                    addJsonObject {
                        put("voice", voiceName)
                    }
                }
            }
        }

        val response = httpClient.post(GEMINI_INTERACTIONS_URL) {
            header("x-goog-api-key", apiKey)
            header("Api-Revision", GEMINI_API_REVISION)
            contentType(ContentType.Application.Json)
            setBody(requestPayload)
        }

        if (!response.status.isSuccess()) return null
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val base64Audio = extractLastContentField(root, contentType = "audio", fieldName = "data") ?: return null
        return Base64.decode(base64Audio)
    }

    /**
     * Calls `gemini-nano-banana-2.1` via the Interactions API to transform the child's raw sketch
     * into a vibrant storybook character isolated on a pure white background, then runs our
     * border flood-fill extractor so it becomes a transparent animated cutout puppet!
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun generateEnhancedPuppetWithNanoBanana21(
        apiKey: String,
        base64Png: String,
        characterName: String,
        characterType: String
    ): androidx.compose.ui.graphics.ImageBitmap? {
        val prompt = """
            Transform this child's drawing of $characterName ($characterType) into a vibrant, adorable 2D children's storybook character illustration while faithfully keeping the child's exact colors, pose, and charming features.
            CRITICAL: Render ONLY the single full-body character centered on a 100% solid pure white (#FFFFFF) background with no scenery, no floor shadow, and no frame so it can be cut out as an animated 2D puppet sticker.
        """.trimIndent()

        val requestPayload = buildJsonObject {
            put("model", "gemini-nano-banana-2.1")
            putJsonArray("input") {
                addJsonObject {
                    put("type", "text")
                    put("text", prompt)
                }
                addJsonObject {
                    put("type", "image")
                    put("mime_type", "image/png")
                    put("data", base64Png)
                }
            }
        }

        val response = httpClient.post(GEMINI_INTERACTIONS_URL) {
            header("x-goog-api-key", apiKey)
            header("Api-Revision", GEMINI_API_REVISION)
            contentType(ContentType.Application.Json)
            setBody(requestPayload)
        }

        if (response.status.isSuccess()) {
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val base64Out = extractLastContentField(root, contentType = "image", fieldName = "data")
            if (base64Out != null) {
                val imageBytes = Base64.decode(base64Out)
                val decodedRgba = decodeImageBytesToRgba(imageBytes)
                if (decodedRgba != null) {
                    val cutoutRgba = DrawingProcessor.extractCharacterFromPaper(decodedRgba, addStickerBorder = true)
                    return rgbaBufferToImageBitmap(cutoutRgba)
                }
            }
        }

        // Fallback to standard Google AI Studio generateContent image endpoint if /v1beta/interactions returned non-200
        val fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp-image-generation:generateContent?key=$apiKey"
        val fallbackBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "image/png")
                                put("data", base64Png)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") {
                    add("TEXT")
                    add("IMAGE")
                }
            }
        }
        val fbResp = httpClient.post(fallbackUrl) {
            contentType(ContentType.Application.Json)
            setBody(fallbackBody)
        }
        if (!fbResp.status.isSuccess()) return null
        val fbRoot = json.parseToJsonElement(fbResp.bodyAsText()).jsonObject
        val parts = fbRoot["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: return null
        for (part in parts) {
            val inlineData = part.jsonObject["inlineData"]?.jsonObject
            val b64 = inlineData?.get("data")?.jsonPrimitive?.contentOrNull
            if (!b64.isNullOrBlank()) {
                val decodedRgba = decodeImageBytesToRgba(Base64.decode(b64)) ?: continue
                val cutoutRgba = DrawingProcessor.extractCharacterFromPaper(decodedRgba, addStickerBorder = true)
                return rgbaBufferToImageBitmap(cutoutRgba)
            }
        }
        return null
    }

    /**
     * Calls `gemini-omni-1.2-flash` to generate a short animated video clip using the child's drawing
     * as the starting frame (via the Gemini Files API + Interactions API).
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun generateOmniFlashVideoPreview(
        apiKey: String,
        pngBytes: ByteArray,
        story: StoryAdventure,
        scene: StoryScene,
        onStatus: (String) -> Unit
    ): String {
        if (apiKey.isBlank()) {
            return "Add your Gemini API key in the AI Key Hub to generate an MP4 movie clip with gemini-omni-1.2-flash!"
        }

        onStatus("📤 Uploading drawing to Gemini Files API for gemini-omni-1.2-flash...")
        val uploadResp = httpClient.post("https://generativelanguage.googleapis.com/upload/v1beta/files?uploadType=media") {
            header("x-goog-api-key", apiKey)
            header("X-Goog-Upload-Protocol", "raw")
            header("X-Goog-Upload-Header-Content-Type", "image/png")
            contentType(ContentType.Image.PNG)
            setBody(pngBytes)
        }

        if (!uploadResp.status.isSuccess()) {
            return "Files API upload returned ${uploadResp.status.value}. Enjoy the live 60 FPS Compose Animation Theater!"
        }

        val uploadJson = json.parseToJsonElement(uploadResp.bodyAsText()).jsonObject
        val fileObj = uploadJson["file"]?.jsonObject ?: uploadJson
        val fileUri = fileObj["uri"]?.jsonPrimitive?.contentOrNull
            ?: return "Could not parse uploaded File URI."

        onStatus("🎬 Gemini Omni 1.2 Flash (gemini-omni-1.2-flash) is animating '${scene.sceneTitle}' (5s 720p video)...")

        // Per gemini-omni-flash-api skill: bind first frame using natural language ("Use this image as the starting frame.")
        val videoPrompt = "Use this image as the starting frame. In a single unbroken scene, ${story.characterName} (${story.characterSpeciesOrType}) comes to life in a joyful children's animated storybook world: ${scene.narrationText}. Include cheerful magical background music. No dialogue."

        val payload = buildJsonObject {
            put("model", "gemini-omni-1.2-flash")
            putJsonArray("input") {
                addJsonObject {
                    put("type", "image")
                    put("uri", fileUri)
                    put("mime_type", "image/png")
                }
                addJsonObject {
                    put("type", "text")
                    put("text", videoPrompt)
                }
            }
            putJsonObject("response_format") {
                put("type", "video")
                put("delivery", "uri")
                put("aspect_ratio", "16:9")
                put("duration", "5s")
                put("resolution", "720p")
            }
        }

        val response = httpClient.post(GEMINI_INTERACTIONS_URL) {
            header("x-goog-api-key", apiKey)
            header("Api-Revision", GEMINI_API_REVISION)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        if (!response.status.isSuccess()) {
            return "Gemini Omni 1.2 Flash status: ${response.status.value} — your 60 FPS interactive Compose animation is active!"
        }

        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val videoUri = extractLastContentField(root, contentType = "video", fieldName = "uri")
        return if (videoUri != null) {
            "✅ Gemini Omni 1.2 Flash Video Ready! URI: $videoUri"
        } else {
            "Gemini Omni 1.2 Flash completed interaction (${root["id"]?.jsonPrimitive?.contentOrNull ?: "ok"})."
        }
    }

    /**
     * Calls `gemini-3.8-flash` via the Interactions API with inline image input and Structured Output JSON Schema.
     */
    private suspend fun generateStoryWithGemini38Flash(
        apiKey: String,
        base64Png: String,
        kidHintPrompt: String,
        dominantColors: String
    ): Pair<StoryAdventure, String?> {
        val hintSection = if (kidHintPrompt.isNotBlank()) {
            "The child also told us about their drawing: \"$kidHintPrompt\"."
        } else {
            "Notice the colors ($dominantColors) and cute details the child drew."
        }

        val prompt = """
            You are a magical children's animation director and master storyteller!
            A child just drew this picture and held it up to the camera. $hintSection
            
            1. Recognize what the child drew with warmth and excitement. Give their character an adorable name, species/type, personality trait, and magical superpower.
            2. Create a 4-scene living animated story adventure ('scenes' 1 to 4) that brings their exact character to life!
            3. For each scene, choose a varied 'biome' (SUNNY_MEADOW, STARRY_SPACE, OCEAN_REEF, CANDY_KINGDOM, ENCHANTED_FOREST, VOLCANO_ISLAND),
               a dynamic 'motionStyle' (BOUNCE_ADVENTURE, FLY_LOOP, SWIM_WAVE, DANCE_SPIN, FLOAT_SPACE, SUPER_HERO_ZOOM),
               and a 'particleMagic' effect (SPARKLES, BUBBLES, RAINBOW_TRAIL, FIREFLIES, CONFETTI, METEORS, HEARTS, SNOWFLAKES).
            4. Make every 'narrationText' vivid, playful, and fun to read aloud (2-3 sentences per scene), and give the character an expressive 'characterSpeechBubble' with emojis!
            5. Include 1 or 2 cute 'companions' per scene (BUTTERFLY, FRIENDLY_UFO, JELLYFISH, BALLOON, STAR_SPRITE, BABY_BIRD) and vibrant hex colors (#RRGGBB).
        """.trimIndent()

        val payload = buildJsonObject {
            put("model", "gemini-3.8-flash")
            putJsonArray("input") {
                addJsonObject {
                    put("type", "text")
                    put("text", prompt)
                }
                addJsonObject {
                    put("type", "image")
                    put("mime_type", "image/png")
                    put("data", base64Png)
                }
            }
            putJsonObject("response_format") {
                put("type", "text")
                put("mime_type", "application/json")
                put("schema", buildStoryAdventureJsonSchema())
            }
        }

        val response = httpClient.post(GEMINI_INTERACTIONS_URL) {
            header("x-goog-api-key", apiKey)
            header("Api-Revision", GEMINI_API_REVISION)
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        if (response.status.isSuccess()) {
            val rawBody = response.bodyAsText()
            val root = json.parseToJsonElement(rawBody).jsonObject
            val interactionId = root["id"]?.jsonPrimitive?.contentOrNull
            val outputText = extractLastContentField(root, contentType = "text", fieldName = "text")
            if (outputText != null) {
                val cleanJson = stripMarkdownCodeFences(outputText)
                val parsed = json.decodeFromString<StoryAdventure>(cleanJson)
                return normalizeStoryAdventure(parsed) to interactionId
            }
        }

        // Standard Google AI Studio generateContent vision fallback so any Gemini key works seamlessly
        val fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val fbPayload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", "$prompt\n\n${buildOpenAiCompatiblePrompt(kidHintPrompt, dominantColors)}")
                        }
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "image/png")
                                put("data", base64Png)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
            }
        }
        val fbResp = httpClient.post(fallbackUrl) {
            contentType(ContentType.Application.Json)
            setBody(fbPayload)
        }
        if (!fbResp.status.isSuccess()) {
            throw RuntimeException("Gemini HTTP ${fbResp.status.value}")
        }
        val fbRoot = json.parseToJsonElement(fbResp.bodyAsText()).jsonObject
        val fbText = fbRoot["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull
            ?: throw RuntimeException("Empty Gemini vision response")
        val parsed = json.decodeFromString<StoryAdventure>(stripMarkdownCodeFences(fbText))
        return normalizeStoryAdventure(parsed) to null
    }

    /**
     * Kimi (Moonshot AI) vision + JSON choreography pipeline (`moonshot-v1-8k-vision-preview`).
     */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun runKimiPipeline(
        apiKey: String,
        originalPaperBuffer: RgbaBuffer,
        originalBitmap: androidx.compose.ui.graphics.ImageBitmap,
        extractedCharacterBuffer: RgbaBuffer,
        extractedBitmap: androidx.compose.ui.graphics.ImageBitmap,
        pngBytes: ByteArray,
        kidHintPrompt: String,
        dominantColors: String,
        activePreset: KidDrawingPreset,
        onProgressStep: (String) -> Unit
    ): LivingCreationBundle {
        onProgressStep("🌙 Kimi Vision (moonshot-v1-8k-vision-preview) is inspecting your drawing & writing a 4-scene animated story...")
        val base64Png = Base64.encode(pngBytes)
        val story = try {
            val payload = buildJsonObject {
                put("model", "moonshot-v1-8k-vision-preview")
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", "You are a children's animation director. Respond ONLY with valid JSON matching the StoryAdventure structure.")
                    }
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:image/png;base64,$base64Png")
                                }
                            }
                            addJsonObject {
                                put("type", "text")
                                put("text", buildOpenAiCompatiblePrompt(kidHintPrompt, dominantColors))
                            }
                        }
                    }
                }
            }
            val resp = httpClient.post(KIMI_CHAT_URL) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                ?: throw RuntimeException("Empty Kimi response")
            normalizeStoryAdventure(json.decodeFromString<StoryAdventure>(stripMarkdownCodeFences(content)))
        } catch (_: Exception) {
            customizeFallbackStory(
                DrawingProcessor.createRichStoryForPreset(activePreset, dominantColors),
                kidHintPrompt,
                dominantColors
            )
        }

        return LivingCreationBundle(
            originalPaperBuffer = originalPaperBuffer,
            originalPaperBitmap = originalBitmap,
            extractedCharacterBuffer = extractedCharacterBuffer,
            extractedCharacterBitmap = extractedBitmap,
            originalPngBytes = pngBytes,
            aiEnhancedBitmap = null,
            story = story,
            providerUsed = "Kimi Vision (moonshot-v1-8k-vision-preview) + 60 FPS Puppet Engine"
        )
    }

    /**
     * DeepSeek (`deepseek-chat`) JSON story & animation choreography pipeline.
     */
    private suspend fun runDeepSeekPipeline(
        apiKey: String,
        originalPaperBuffer: RgbaBuffer,
        originalBitmap: androidx.compose.ui.graphics.ImageBitmap,
        extractedCharacterBuffer: RgbaBuffer,
        extractedBitmap: androidx.compose.ui.graphics.ImageBitmap,
        pngBytes: ByteArray,
        kidHintPrompt: String,
        dominantColors: String,
        activePreset: KidDrawingPreset,
        onProgressStep: (String) -> Unit
    ): LivingCreationBundle {
        onProgressStep("🐋 DeepSeek (deepseek-chat) is crafting a 4-scene animated story from your drawing's colors ($dominantColors)...")
        val story = try {
            val payload = buildJsonObject {
                put("model", "deepseek-chat")
                putJsonObject("response_format") {
                    put("type", "json_object")
                }
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", "You are a children's animation director. Respond ONLY with valid JSON matching the StoryAdventure schema.")
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", buildOpenAiCompatiblePrompt(kidHintPrompt.ifBlank { activePreset.description }, dominantColors))
                    }
                }
            }
            val resp = httpClient.post(DEEPSEEK_CHAT_URL) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                ?: throw RuntimeException("Empty DeepSeek response")
            normalizeStoryAdventure(json.decodeFromString<StoryAdventure>(stripMarkdownCodeFences(content)))
        } catch (_: Exception) {
            customizeFallbackStory(
                DrawingProcessor.createRichStoryForPreset(activePreset, dominantColors),
                kidHintPrompt,
                dominantColors
            )
        }

        return LivingCreationBundle(
            originalPaperBuffer = originalPaperBuffer,
            originalPaperBitmap = originalBitmap,
            extractedCharacterBuffer = extractedCharacterBuffer,
            extractedCharacterBitmap = extractedBitmap,
            originalPngBytes = pngBytes,
            aiEnhancedBitmap = null,
            story = story,
            providerUsed = "DeepSeek (deepseek-chat) + 60 FPS Puppet Engine"
        )
    }

    private fun extractLastContentField(root: JsonObject, contentType: String, fieldName: String): String? {
        val steps = root["steps"]?.jsonArray ?: return null
        for (i in steps.size - 1 downTo 0) {
            val step = steps[i].jsonObject
            if (step["type"]?.jsonPrimitive?.contentOrNull == "model_output") {
                val contents = step["content"]?.jsonArray ?: continue
                for (j in contents.size - 1 downTo 0) {
                    val item = contents[j].jsonObject
                    if (item["type"]?.jsonPrimitive?.contentOrNull == contentType) {
                        val value = item[fieldName]?.jsonPrimitive?.contentOrNull
                        if (!value.isNullOrBlank()) return value
                    }
                }
            }
        }
        return null
    }

    private fun stripMarkdownCodeFences(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            val firstNewline = s.indexOf('\n')
            if (firstNewline != -1) {
                s = s.substring(firstNewline + 1)
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length - 3)
            }
        }
        return s.trim()
    }

    private fun normalizeStoryAdventure(story: StoryAdventure): StoryAdventure {
        val validScenes = if (story.scenes.isEmpty()) {
            DrawingProcessor.createRichStoryForPreset(KidDrawingPreset.FLYING_DINO, "colorful").scenes
        } else {
            story.scenes.mapIndexed { idx, scene ->
                scene.copy(
                    sceneNumber = idx + 1,
                    speedMultiplier = scene.speedMultiplier.coerceIn(0.6f, 1.8f),
                    jumpHeightRatio = scene.jumpHeightRatio.coerceIn(0.10f, 0.36f),
                    squashStretchIntensity = scene.squashStretchIntensity.coerceIn(0.08f, 0.35f)
                )
            }
        }
        return story.copy(scenes = validScenes)
    }

    private fun customizeFallbackStory(
        base: StoryAdventure,
        kidHint: String,
        dominantColors: String
    ): StoryAdventure {
        if (kidHint.isBlank()) return base
        val cleanHint = kidHint.trim()
        return base.copy(
            storyTitle = "${base.characterName}: $cleanHint",
            detectedDrawingSummary = "Your camera drawing in $dominantColors featuring '$cleanHint'!",
            scenes = base.scenes.mapIndexed { index, scene ->
                if (index == 0) {
                    scene.copy(
                        narrationText = "${scene.narrationText} Inspired by '$cleanHint', the whole world began to sparkle with magic!",
                        characterSpeechBubble = "Let's go on our '$cleanHint' adventure! ✨"
                    )
                } else {
                    scene
                }
            }
        )
    }

    private fun buildOpenAiCompatiblePrompt(kidHintPrompt: String, dominantColors: String): String {
        return """
            Create a 4-scene animated children's story JSON for a child's drawing (dominant colors: $dominantColors; child's description: "$kidHintPrompt").
            Return ONLY a JSON object with these exact keys:
            {
              "storyTitle": "...",
              "characterName": "...",
              "characterSpeciesOrType": "...",
              "detectedDrawingSummary": "...",
              "personalityTrait": "...",
              "superpower": "...",
              "moralOrSparkleMessage": "...",
              "scenes": [
                {
                  "sceneNumber": 1,
                  "sceneTitle": "...",
                  "narrationText": "...",
                  "characterSpeechBubble": "...",
                  "soundEffectOnomatopoeia": "BOING! ✨",
                  "biome": "SUNNY_MEADOW | STARRY_SPACE | OCEAN_REEF | CANDY_KINGDOM | ENCHANTED_FOREST | VOLCANO_ISLAND",
                  "motionStyle": "BOUNCE_ADVENTURE | FLY_LOOP | SWIM_WAVE | DANCE_SPIN | FLOAT_SPACE | SUPER_HERO_ZOOM",
                  "particleMagic": "SPARKLES | BUBBLES | RAINBOW_TRAIL | FIREFLIES | CONFETTI | METEORS | HEARTS | SNOWFLAKES",
                  "speedMultiplier": 1.1,
                  "jumpHeightRatio": 0.25,
                  "squashStretchIntensity": 0.22,
                  "skyTopColorHex": "#38BDF8",
                  "skyBottomColorHex": "#FEF08A",
                  "groundColorHex": "#4ADE80",
                  "accentColorHex": "#F472B6",
                  "companions": [
                    {"name": "Pip", "type": "BUTTERFLY | FRIENDLY_UFO | JELLYFISH | BALLOON | STAR_SPRITE | BABY_BIRD", "colorHex": "#FFD54F", "orbitSpeed": 1.2}
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    private fun buildStoryAdventureJsonSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("storyTitle") { put("type", "string") }
            putJsonObject("characterName") { put("type", "string") }
            putJsonObject("characterSpeciesOrType") { put("type", "string") }
            putJsonObject("detectedDrawingSummary") { put("type", "string") }
            putJsonObject("personalityTrait") { put("type", "string") }
            putJsonObject("superpower") { put("type", "string") }
            putJsonObject("moralOrSparkleMessage") { put("type", "string") }
            putJsonObject("scenes") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("sceneNumber") { put("type", "integer") }
                        putJsonObject("sceneTitle") { put("type", "string") }
                        putJsonObject("narrationText") { put("type", "string") }
                        putJsonObject("characterSpeechBubble") { put("type", "string") }
                        putJsonObject("soundEffectOnomatopoeia") { put("type", "string") }
                        putJsonObject("biome") {
                            put("type", "string")
                            putJsonArray("enum") {
                                WorldBiome.entries.forEach { add(it.name) }
                            }
                        }
                        putJsonObject("motionStyle") {
                            put("type", "string")
                            putJsonArray("enum") {
                                MotionStyle.entries.forEach { add(it.name) }
                            }
                        }
                        putJsonObject("particleMagic") {
                            put("type", "string")
                            putJsonArray("enum") {
                                ParticleMagicType.entries.forEach { add(it.name) }
                            }
                        }
                        putJsonObject("speedMultiplier") { put("type", "number") }
                        putJsonObject("jumpHeightRatio") { put("type", "number") }
                        putJsonObject("squashStretchIntensity") { put("type", "number") }
                        putJsonObject("skyTopColorHex") { put("type", "string") }
                        putJsonObject("skyBottomColorHex") { put("type", "string") }
                        putJsonObject("groundColorHex") { put("type", "string") }
                        putJsonObject("accentColorHex") { put("type", "string") }
                        putJsonObject("companions") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("name") { put("type", "string") }
                                    putJsonObject("type") {
                                        put("type", "string")
                                        putJsonArray("enum") {
                                            CompanionType.entries.forEach { add(it.name) }
                                        }
                                    }
                                    putJsonObject("colorHex") { put("type", "string") }
                                    putJsonObject("orbitSpeed") { put("type", "number") }
                                }
                                putJsonArray("required") {
                                    add("name")
                                    add("type")
                                    add("colorHex")
                                    add("orbitSpeed")
                                }
                            }
                        }
                    }
                    putJsonArray("required") {
                        add("sceneNumber")
                        add("sceneTitle")
                        add("narrationText")
                        add("characterSpeechBubble")
                        add("soundEffectOnomatopoeia")
                        add("biome")
                        add("motionStyle")
                        add("particleMagic")
                        add("speedMultiplier")
                        add("jumpHeightRatio")
                        add("squashStretchIntensity")
                        add("skyTopColorHex")
                        add("skyBottomColorHex")
                        add("groundColorHex")
                        add("accentColorHex")
                        add("companions")
                    }
                }
            }
        }
        putJsonArray("required") {
            add("storyTitle")
            add("characterName")
            add("characterSpeciesOrType")
            add("detectedDrawingSummary")
            add("personalityTrait")
            add("superpower")
            add("moralOrSparkleMessage")
            add("scenes")
        }
    }
}
