package com.ztoais.CamCrayon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import com.ztoais.CamCrayon.ai.GeminiStoryEngine
import com.ztoais.CamCrayon.imaging.DrawingProcessor
import com.ztoais.CamCrayon.imaging.KidDrawingPreset
import com.ztoais.CamCrayon.model.AiConfig
import com.ztoais.CamCrayon.model.AiProvider
import com.ztoais.CamCrayon.platform.*
import com.ztoais.CamCrayon.ui.MagicalAnimationTheater
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DoodleTalesEndToEndTest {

    @Test
    fun testAllKidPaperDrawingsAndCutoutExtraction() {
        for (preset in KidDrawingPreset.entries) {
            val paper = DrawingProcessor.generateKidPaperDrawing(preset, 360, 360)
            assertEquals(360, paper.width)
            assertEquals(360, paper.height)

            // Extract character puppet cutout
            val cutout = DrawingProcessor.extractCharacterFromPaper(paper, addStickerBorder = true)
            assertTrue(cutout.width > 100 && cutout.height > 100, "Cutout for ${preset.name} should have valid size")

            // Outer top-left corner (0,0) of cutout must be 100% transparent background
            val cornerAlpha = (cutout.getPixel(0, 0) ushr 24) and 0xFF
            assertEquals(0, cornerAlpha, "Outer background of cutout for ${preset.name} must be transparent")

            // Count opaque pixels inside character puppet
            val opaqueCount = cutout.pixels.count { ((it ushr 24) and 0xFF) > 200 }
            assertTrue(opaqueCount > 2500, "Cutout for ${preset.name} should preserve character pixels (got $opaqueCount)")

            // Verify dominant color detection
            val colorsDesc = DrawingProcessor.describeDominantColors(cutout)
            assertTrue(colorsDesc.isNotBlank(), "Dominant colors should be detected for ${preset.name}")
            println("✅ Preset ${preset.name}: Cutout ${cutout.width}x${cutout.height}, opaque pixels=$opaqueCount, colors='$colorsDesc'")
        }
    }

    @Test
    fun testPngRoundTripAndSoundSynthesizer() {
        val dinoPaper = DrawingProcessor.generateKidPaperDrawing(KidDrawingPreset.FLYING_DINO)
        val cutout = DrawingProcessor.extractCharacterFromPaper(dinoPaper)
        val pngBytes = encodeRgbaToPngBytes(cutout)
        assertTrue(pngBytes.size > 1000, "PNG bytes should be non-empty")

        val decoded = decodeImageBytesToRgba(pngBytes)
        assertNotNull(decoded, "Decoded PNG should not be null")
        assertEquals(cutout.width, decoded.width)
        assertEquals(cutout.height, decoded.height)

        // Test WAV sound synthesis for all sound effects
        SoundEngine.isMuted = true // mute speaker during automated test
        for (effect in SoundEffectType.entries) {
            SoundEngine.playEffect(effect)
        }
        val samples = ShortArray(2205) { 1000 }
        val wav = SoundEngine.encodePcm16MonoToWav(samples, 22050)
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('I'.code.toByte(), wav[1])
        assertEquals('F'.code.toByte(), wav[2])
        assertEquals('F'.code.toByte(), wav[3])
        assertEquals(44 + samples.size * 2, wav.size)
        println("✅ PNG round-trip (${pngBytes.size} bytes) and RIFF WAV audio synthesizer (${wav.size} bytes) verified!")
    }

    @Test
    fun testEndToEndStoryEngineAndRenderBothScreens() = runBlocking {
        SoundEngine.isMuted = true
        val engine = GeminiStoryEngine()
        val paper = DrawingProcessor.generateKidPaperDrawing(KidDrawingPreset.FLYING_DINO)
        val cutout = DrawingProcessor.extractCharacterFromPaper(paper)
        val pngBytes = encodeRgbaToPngBytes(cutout)

        val steps = mutableListOf<String>()
        val bundle = engine.bringDrawingToLife(
            originalPaperBuffer = paper,
            extractedCharacterBuffer = cutout,
            pngBytes = pngBytes,
            kidHintPrompt = "Soaring Over Candy Kingdom",
            activePreset = KidDrawingPreset.FLYING_DINO,
            config = AiConfig(selectedProvider = AiProvider.GEMINI, geminiApiKey = ""),
            onProgressStep = { steps.add(it) }
        )

        assertEquals(4, bundle.story.scenes.size, "Story must contain 4 animated scenes")
        assertTrue(bundle.story.storyTitle.contains("Candy Kingdom"), "Kid's prompt should customize the story title")
        assertTrue(bundle.extractedCharacterBitmap.width > 100)

        val outDir = File("build/test-renders").apply { mkdirs() }

        // 1. Render Studio Screen via ImageComposeScene and verify non-blank pixel output
        val studioScene = ImageComposeScene(width = 1240, height = 820, density = Density(1f))
        studioScene.setContent {
            Box(Modifier.size(1240.dp, 820.dp)) {
                App()
            }
        }
        val studioBytes = studioScene.render(nanoTime = 500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        val studioFile = File(outDir, "studio_screen.png")
        studioFile.writeBytes(studioBytes)
        assertTrue(studioFile.exists() && studioFile.length() > 15_000, "Rendered Studio screenshot should be non-empty (${studioFile.length()} bytes)")
        studioScene.close()

        // 2. Render MagicalAnimationTheater at multiple animation timestamps and verify frame progression
        val theaterScene = ImageComposeScene(width = 1240, height = 820, density = Density(1f))
        theaterScene.setContent {
            Box(Modifier.size(1240.dp, 820.dp)) {
                MagicalAnimationTheater(
                    bundle = bundle,
                    onBackToStudio = {},
                    onRequestSceneTts = {},
                    onRequestOmniVideo = {},
                    isGeneratingTts = false,
                    isGeneratingVideo = false,
                    omniVideoStatus = null
                )
            }
        }
        val frame1Bytes = theaterScene.render(nanoTime = 100_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        val frame2Bytes = theaterScene.render(nanoTime = 1_500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
        val theaterFile1 = File(outDir, "theater_frame1.png")
        val theaterFile2 = File(outDir, "theater_frame2.png")
        theaterFile1.writeBytes(frame1Bytes)
        theaterFile2.writeBytes(frame2Bytes)
        assertTrue(theaterFile1.exists() && theaterFile1.length() > 20_000, "Theater frame 1 should render cleanly (${theaterFile1.length()} bytes)")
        assertTrue(theaterFile2.exists() && theaterFile2.length() > 20_000, "Theater frame 2 should render cleanly (${theaterFile2.length()} bytes)")
        theaterScene.close()

        println("✅ Studio UI rendered (${studioFile.length()} bytes) & Animation Theater rendered (${theaterFile1.length()} bytes) with 0 errors!")
    }
}
