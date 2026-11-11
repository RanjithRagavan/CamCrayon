package com.doodletales.model

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AI Provider options evaluated for turning kids' camera drawings into living animated stories.
 */
enum class AiProvider(
    val displayName: String,
    val badge: String,
    val visionModel: String,
    val artModel: String,
    val voiceModel: String,
    val videoModel: String,
    val overallScore: String,
    val whyFitSummary: String,
    val strengths: List<String>,
    val limitations: List<String>
) {
    GEMINI(
        displayName = "Google Gemini (Interactions API)",
        badge = "⭐ BEST FIT (#1 Recommended)",
        visionModel = "gemini-3.8-flash",
        artModel = "gemini-nano-banana-2.1",
        voiceModel = "gemini-3.8-flash-tts",
        videoModel = "gemini-omni-1.2-flash",
        overallScore = "10 / 10",
        whyFitSummary = "One single Gemini API key unlocks the complete end-to-end kids' animation pipeline: Vision understanding of messy crayon drawings + Structured JSON choreography + Nano Banana 2.1 character sprite magic + Expressive TTS Storyteller voice + Omni 1.2 Flash first-frame-to-video!",
        strengths = listOf(
            "gemini-3.8-flash: Best-in-class multimodal vision for kids' hand-drawn paper sketches + native JSON Schema output for animation choreography",
            "gemini-nano-banana-2.1: Turns the kid's raw camera sketch into a vibrant storybook character cutout while keeping their original design",
            "gemini-3.8-flash-tts: Expressive read-aloud storyteller voice ('Kore'/'Puck') with speech_metadata style control",
            "gemini-omni-1.2-flash: Native first-frame-to-video generation (3s–15s MP4) directly from the child's drawing"
        ),
        limitations = listOf(
            "Omni 1.2 Flash video generation takes ~20-45s, so pairing it with our instant 60 FPS Compose Puppet Theater gives kids zero-wait magic!"
        )
    ),
    KIMI(
        displayName = "Kimi (Moonshot AI)",
        badge = "Good Vision & Story Fallback",
        visionModel = "moonshot-v1-8k-vision-preview",
        artModel = "Procedural Compose Puppet Only",
        voiceModel = "Synthesized Chime Audio",
        videoModel = "60 FPS Compose Engine Only",
        overallScore = "6.5 / 10",
        whyFitSummary = "Kimi's vision model can inspect the child's camera drawing and write creative fairy-tale scenes + JSON animation choreography, but lacks native character image generation, native TTS storytelling, and image-to-video.",
        strengths = listOf(
            "moonshot-v1-8k-vision-preview understands camera photos of drawings well",
            "Strong creative storytelling and whimsical dialogue for children",
            "OpenAI-compatible REST API makes it easy to plug into our 60 FPS Compose Puppet Theater"
        ),
        limitations = listOf(
            "No native character illustration model (like Nano Banana 2.1)",
            "No native expressive storyteller TTS or image-to-video model"
        )
    ),
    DEEPSEEK(
        displayName = "DeepSeek (DeepSeek-V3 / R1)",
        badge = "Text Story Enrichment Only",
        visionModel = "deepseek-chat",
        artModel = "Procedural Compose Puppet Only",
        voiceModel = "Synthesized Chime Audio",
        videoModel = "60 FPS Compose Engine Only",
        overallScore = "5.0 / 10",
        whyFitSummary = "DeepSeek-V3/R1 is fantastic at writing imaginative stories and JSON animation scripts from character descriptions, but its standard chat API does not natively process raw camera images, generate character art, or produce TTS/video.",
        strengths = listOf(
            "Very low cost and fast JSON story & animation choreography generation",
            "Great at expanding a character prompt + color palette analysis into a 4-scene adventure"
        ),
        limitations = listOf(
            "Standard deepseek-chat API is text-only (relies on our local colour/shape analyzer + kid prompt to describe the drawing)",
            "No native image generation, TTS voiceover, or video generation"
        )
    )
}

data class AiConfig(
    val selectedProvider: AiProvider = AiProvider.GEMINI,
    val geminiApiKey: String = "",
    val kimiApiKey: String = "",
    val deepseekApiKey: String = "",
    val generateNanoBananaSprite: Boolean = true,
    val generateTtsNarration: Boolean = true,
    val storytellerVoice: String = "Kore"
) {
    val activeApiKey: String
        get() = when (selectedProvider) {
            AiProvider.GEMINI -> geminiApiKey.trim()
            AiProvider.KIMI -> kimiApiKey.trim()
            AiProvider.DEEPSEEK -> deepseekApiKey.trim()
        }
}

@Serializable
enum class MotionStyle(val label: String, val emoji: String) {
    @SerialName("BOUNCE_ADVENTURE")
    BOUNCE_ADVENTURE("Happy Bounce", "🦘"),

    @SerialName("FLY_LOOP")
    FLY_LOOP("Soaring Flight", "🦅"),

    @SerialName("SWIM_WAVE")
    SWIM_WAVE("Ocean Swim", "🐬"),

    @SerialName("DANCE_SPIN")
    DANCE_SPIN("Disco Dance", "💃"),

    @SerialName("FLOAT_SPACE")
    FLOAT_SPACE("Zero-G Float", "🚀"),

    @SerialName("SUPER_HERO_ZOOM")
    SUPER_HERO_ZOOM("Hero Zoom", "⚡")
}

@Serializable
enum class WorldBiome(val label: String, val emoji: String) {
    @SerialName("SUNNY_MEADOW")
    SUNNY_MEADOW("Sunny Rainbow Meadow", "🌈"),

    @SerialName("STARRY_SPACE")
    STARRY_SPACE("Cosmic Starlight Galaxy", "🪐"),

    @SerialName("OCEAN_REEF")
    OCEAN_REEF("Glowing Coral Kingdom", "🐠"),

    @SerialName("CANDY_KINGDOM")
    CANDY_KINGDOM("Sugar Cloud Castle", "🍭"),

    @SerialName("ENCHANTED_FOREST")
    ENCHANTED_FOREST("Firefly Whisper Woods", "🧚"),

    @SerialName("VOLCANO_ISLAND")
    VOLCANO_ISLAND("Dino Treasure Island", "🌋")
}

@Serializable
enum class ParticleMagicType(val label: String, val emoji: String) {
    @SerialName("SPARKLES")
    SPARKLES("Gold Starlight", "✨"),

    @SerialName("BUBBLES")
    BUBBLES("Shimmer Bubbles", "🫧"),

    @SerialName("RAINBOW_TRAIL")
    RAINBOW_TRAIL("Rainbow Dust", "🌈"),

    @SerialName("FIREFLIES")
    FIREFLIES("Glowing Fireflies", "🌟"),

    @SerialName("CONFETTI")
    CONFETTI("Party Confetti", "🎉"),

    @SerialName("METEORS")
    METEORS("Shooting Stars", "☄️"),

    @SerialName("HEARTS")
    HEARTS("Floating Hearts", "💖"),

    @SerialName("SNOWFLAKES")
    SNOWFLAKES("Crystal Snow", "❄️")
}

@Serializable
enum class CompanionType {
    @SerialName("BUTTERFLY")
    BUTTERFLY,

    @SerialName("FRIENDLY_UFO")
    FRIENDLY_UFO,

    @SerialName("JELLYFISH")
    JELLYFISH,

    @SerialName("BALLOON")
    BALLOON,

    @SerialName("STAR_SPRITE")
    STAR_SPRITE,

    @SerialName("BABY_BIRD")
    BABY_BIRD
}

@Serializable
data class WaypointNormalized(
    val x: Float,
    val y: Float
)

@Serializable
data class CompanionProp(
    val name: String,
    val type: CompanionType = CompanionType.STAR_SPRITE,
    val colorHex: String = "#FFD54F",
    val orbitSpeed: Float = 1.0f
)

@Serializable
data class StoryScene(
    val sceneNumber: Int,
    val sceneTitle: String,
    val narrationText: String,
    val characterSpeechBubble: String,
    val soundEffectOnomatopoeia: String = "WHOOSH! ✨",
    val biome: WorldBiome = WorldBiome.SUNNY_MEADOW,
    val motionStyle: MotionStyle = MotionStyle.BOUNCE_ADVENTURE,
    val particleMagic: ParticleMagicType = ParticleMagicType.SPARKLES,
    val speedMultiplier: Float = 1.0f,
    val jumpHeightRatio: Float = 0.22f,
    val squashStretchIntensity: Float = 0.20f,
    val skyTopColorHex: String = "#38BDF8",
    val skyBottomColorHex: String = "#FEF08A",
    val groundColorHex: String = "#4ADE80",
    val accentColorHex: String = "#F472B6",
    val waypoints: List<WaypointNormalized> = listOf(
        WaypointNormalized(0.22f, 0.62f),
        WaypointNormalized(0.50f, 0.42f),
        WaypointNormalized(0.78f, 0.60f),
        WaypointNormalized(0.50f, 0.65f)
    ),
    val companions: List<CompanionProp> = emptyList()
)

@Serializable
data class StoryAdventure(
    val storyTitle: String,
    val characterName: String,
    val characterSpeciesOrType: String,
    val detectedDrawingSummary: String,
    val personalityTrait: String,
    val superpower: String,
    val moralOrSparkleMessage: String,
    val scenes: List<StoryScene>
)

/**
 * Cross-platform RGBA pixel buffer for camera scanning, paper background removal,
 * sticker halo generation, and PNG encoding/decoding.
 */
class RgbaBuffer(
    val width: Int,
    val height: Int,
    val pixels: IntArray = IntArray(width * height) // ARGB_8888 format (0xAARRGGBB)
) {
    fun getPixel(x: Int, y: Int): Int {
        if (x !in 0 until width || y !in 0 until height) return 0
        return pixels[y * width + x]
    }

    fun setPixel(x: Int, y: Int, argb: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        pixels[y * width + x] = argb
    }

    fun copy(): RgbaBuffer = RgbaBuffer(width, height, pixels.copyOf())
}

/**
 * Complete bundle of a kid's drawing + extracted character cutouts + AI story & audio assets.
 */
data class LivingCreationBundle(
    val originalPaperBuffer: RgbaBuffer,
    val originalPaperBitmap: ImageBitmap,
    val extractedCharacterBuffer: RgbaBuffer,
    val extractedCharacterBitmap: ImageBitmap,
    val originalPngBytes: ByteArray,
    val aiEnhancedBitmap: ImageBitmap? = null,
    val story: StoryAdventure,
    val sceneAudioWavBytes: Map<Int, ByteArray> = emptyMap(),
    val generatedVideoPathOrUrl: String? = null,
    val interactionId: String? = null,
    val providerUsed: String = "Gemini 3.8 Flash + 60 FPS Puppet Engine"
)
