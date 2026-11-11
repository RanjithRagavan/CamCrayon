package com.ztoais.CamCrayon.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ztoais.CamCrayon.model.*
import com.ztoais.CamCrayon.platform.SoundEffectType
import com.ztoais.CamCrayon.platform.SoundEngine
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

private class MagicParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var color: Color,
    var alpha: Float = 1f,
    var phase: Float = 0f,
    var isBurst: Boolean = false
)

@Composable
fun MagicalAnimationTheater(
    bundle: LivingCreationBundle,
    onBackToStudio: () -> Unit,
    onRequestSceneTts: (StoryScene) -> Unit,
    onRequestOmniVideo: (StoryScene) -> Unit,
    isGeneratingTts: Boolean,
    isGeneratingVideo: Boolean,
    omniVideoStatus: String?
) {
    val story = bundle.story
    var currentSceneIndex by remember(story) { mutableStateOf(0) }
    val currentScene = story.scenes.getOrElse(currentSceneIndex) { story.scenes.first() }

    // Live kid-controllable overrides per scene
    var activeMotion by remember(currentScene) { mutableStateOf(currentScene.motionStyle) }
    var activeParticleMagic by remember(currentScene) { mutableStateOf(currentScene.particleMagic) }
    var activeBiome by remember(currentScene) { mutableStateOf(currentScene.biome) }

    // Toggle between original kid cutout vs Gemini Nano Banana 2.1 enhanced cutout
    var showAiEnhancedPuppet by remember(bundle.aiEnhancedBitmap) {
        mutableStateOf(bundle.aiEnhancedBitmap != null)
    }
    var showLivingFaceOverlay by remember { mutableStateOf(true) }
    var autoPlayScenes by remember { mutableStateOf(true) }
    var isPaused by remember { mutableStateOf(false) }

    // Interactive touch/drag state for the character puppet
    var userDragOffset by remember { mutableStateOf<Offset?>(null) }
    var superJumpBoostTime by remember { mutableStateOf(-10f) }
    var magicWandBurstTime by remember { mutableStateOf(-10f) }
    var characterScreenCenter by remember { mutableStateOf(Offset(400f, 300f)) }

    // Trail history for rainbow/stardust contrails
    val trailPoints = remember { mutableStateListOf<Offset>() }

    // Ambient + burst particles
    val ambientParticles = remember {
        List(75) { i ->
            MagicParticle(
                x = (i * 37 % 100) / 100f,
                y = (i * 53 % 100) / 100f,
                vx = ((i % 7) - 3) * 0.04f,
                vy = -0.05f - (i % 5) * 0.02f,
                size = 5f + (i % 6) * 2.5f,
                color = listOf(
                    Color(0xFFFFD54F),
                    Color(0xFFF472B6),
                    Color(0xFF38BDF8),
                    Color(0xFF4ADE80),
                    Color(0xFFC084FC),
                    Color(0xFFFB7185)
                )[i % 6],
                phase = i * 0.45f
            )
        }
    }
    val burstParticles = remember { mutableStateListOf<MagicParticle>() }

    // Karaoke narration progress & auto-scene transition
    val words = remember(currentScene.narrationText) {
        currentScene.narrationText.split(" ").filter { it.isNotBlank() }
    }
    var highlightedWordIndex by remember(currentScene) { mutableStateOf(0) }

    // Play scene audio if cached when entering a scene
    LaunchedEffect(currentSceneIndex, bundle.sceneAudioWavBytes.size) {
        val wav = bundle.sceneAudioWavBytes[currentScene.sceneNumber]
        if (wav != null) {
            SoundEngine.playWav(wav)
        } else {
            SoundEngine.playEffect(SoundEffectType.MAGIC_WAND_CHIME)
        }
    }

    LaunchedEffect(currentSceneIndex, autoPlayScenes, isPaused) {
        highlightedWordIndex = 0
        val totalWords = words.size.coerceAtLeast(1)
        for (w in 0..totalWords) {
            while (isPaused) {
                delay(100)
            }
            highlightedWordIndex = w
            delay(320L)
        }
        if (autoPlayScenes && !isPaused) {
            delay(2400L)
            currentSceneIndex = (currentSceneIndex + 1) % story.scenes.size
        }
    }

    // 60 FPS continuous animation clock
    var timeSec by remember { mutableStateOf(0f) }
    LaunchedEffect(isPaused) {
        var startNanos = 0L
        val baseTime = timeSec
        while (!isPaused) {
            withFrameNanos { nanos ->
                if (startNanos == 0L) startNanos = nanos
                timeSec = baseTime + ((nanos - startNanos) / 1_000_000_000f)
            }
        }
    }

    val activePuppetBitmap = if (showAiEnhancedPuppet && bundle.aiEnhancedBitmap != null) {
        bundle.aiEnhancedBitmap
    } else {
        bundle.extractedCharacterBitmap
    }

    val triggerMagicBurst = {
        magicWandBurstTime = timeSec
        superJumpBoostTime = timeSec
        SoundEngine.playEffect(SoundEffectType.MAGIC_WAND_CHIME)
        val rng = Random((timeSec * 1000).toInt())
        repeat(40) {
            val angle = rng.nextFloat() * 2f * PI.toFloat()
            val speed = 150f + rng.nextFloat() * 320f
            burstParticles.add(
                MagicParticle(
                    x = characterScreenCenter.x,
                    y = characterScreenCenter.y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 60f,
                    size = 8f + rng.nextFloat() * 10f,
                    color = listOf(
                        Color(0xFFFFD54F),
                        Color(0xFFF472B6),
                        Color(0xFF38BDF8),
                        Color(0xFF4ADE80)
                    ).random(rng),
                    alpha = 1f,
                    isBurst = true
                )
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isCompact = maxWidth < 760.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Top Story Header Bar
            if (isCompact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FilledTonalButton(
                            onClick = {
                                SoundEngine.stop()
                                onBackToStudio()
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Scan", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                        }

                        Spacer(Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "✨ ${story.storyTitle}",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${story.characterName} • ${story.superpower}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { isPaused = !isPaused },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Play/Pause",
                                tint = Color.White
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                SoundEngine.stop()
                                onBackToStudio()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scan New Drawing", fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "✨ ${story.storyTitle}",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Starring ${story.characterName} (${story.characterSpeciesOrType}) • Superpower: ${story.superpower}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (bundle.aiEnhancedBitmap != null) {
                            FilterChip(
                                selected = showAiEnhancedPuppet,
                                onClick = { showAiEnhancedPuppet = !showAiEnhancedPuppet },
                                label = {
                                    Text(
                                        if (showAiEnhancedPuppet) "🎨 Nano Banana 2.1 Art" else "🖍️ Original Crayon Cutout",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                        }

                        FilterChip(
                            selected = showLivingFaceOverlay,
                            onClick = { showLivingFaceOverlay = !showLivingFaceOverlay },
                            label = { Text("👀 Living Eyes & Talk", fontSize = 12.sp) }
                        )

                        IconButton(
                            onClick = { isPaused = !isPaused }
                        ) {
                            Icon(
                                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Play/Pause",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

        // 2. Main 60 FPS Interactive Living Animation Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .border(3.dp, Color(0xFF38BDF8).copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        // Trigger super jump flip + confetti burst at tap location!
                        superJumpBoostTime = timeSec
                        SoundEngine.playEffect(SoundEffectType.JUMP_BOING)
                        val rng = Random((timeSec * 1000).toInt())
                        repeat(28) {
                            val angle = rng.nextFloat() * 2f * PI.toFloat()
                            val speed = 120f + rng.nextFloat() * 260f
                            burstParticles.add(
                                MagicParticle(
                                    x = tapOffset.x,
                                    y = tapOffset.y,
                                    vx = cos(angle) * speed,
                                    vy = sin(angle) * speed - 80f,
                                    size = 7f + rng.nextFloat() * 9f,
                                    color = listOf(
                                        Color(0xFFFFD54F),
                                        Color(0xFFF43F5E),
                                        Color(0xFF38BDF8),
                                        Color(0xFF4ADE80),
                                        Color(0xFFA855F7)
                                    ).random(rng),
                                    alpha = 1f,
                                    isBurst = true
                                )
                            )
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            userDragOffset = offset
                            SoundEngine.playEffect(SoundEffectType.BUBBLE_POP)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            userDragOffset = change.position
                        },
                        onDragEnd = {
                            superJumpBoostTime = timeSec
                            userDragOffset = null
                        },
                        onDragCancel = {
                            userDragOffset = null
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // A. Render Living Biome Backdrop & Parallax World
                drawBiomeWorld(
                    biome = activeBiome,
                    scene = currentScene,
                    timeSec = timeSec,
                    w = w,
                    h = h
                )

                // B. Compute Character 2D Puppet Pose (Position, Squash/Stretch, Rotation, Flip)
                val pose = computePuppetPose(
                    motionStyle = activeMotion,
                    scene = currentScene,
                    timeSec = timeSec,
                    superJumpBoostTime = superJumpBoostTime,
                    userDragOffset = userDragOffset,
                    stageW = w,
                    stageH = h
                )
                characterScreenCenter = Offset(pose.x, pose.y)

                // Record contrail points
                if (trailPoints.isEmpty() || (trailPoints.last() - characterScreenCenter).getDistance() > 8f) {
                    trailPoints.add(characterScreenCenter)
                    if (trailPoints.size > 24) {
                        trailPoints.removeAt(0)
                    }
                }

                // C. Draw Rainbow / Magic Contrail behind character
                drawCharacterContrail(
                    trailPoints = trailPoints,
                    motionStyle = activeMotion,
                    particleMagic = activeParticleMagic,
                    timeSec = timeSec
                )

                // D. Draw Dynamic Ground Shadow beneath character
                val groundY = h * 0.80f
                val heightAboveGround = (groundY - pose.y).coerceAtLeast(0f)
                val shadowScale = (1f - (heightAboveGround / (h * 0.55f))).coerceIn(0.25f, 1.1f)
                val basePuppetSize = minOf(w, h) * 0.36f
                drawOval(
                    color = Color.Black.copy(alpha = 0.26f * shadowScale),
                    topLeft = Offset(
                        pose.x - (basePuppetSize * 0.42f * shadowScale),
                        groundY + basePuppetSize * 0.14f
                    ),
                    size = Size(
                        basePuppetSize * 0.84f * shadowScale,
                        basePuppetSize * 0.18f * shadowScale
                    )
                )

                // E. Draw Companion Sidekicks behind/around character
                drawCompanionProps(
                    companions = currentScene.companions,
                    heroX = pose.x,
                    heroY = pose.y,
                    heroRadius = basePuppetSize * 0.52f,
                    timeSec = timeSec
                )

                // F. Draw the Child's Extracted Character as a 5-Part Articulated Living Puppet Rig!
                val isTalkingNow = highlightedWordIndex < words.size && !isPaused
                val talkPulse = if (isTalkingNow) 0.04f * sin(timeSec * 24f) else 0f

                // Ground footstep dust puffs / splashes when character bounces near the ground
                if (heightAboveGround < basePuppetSize * 0.25f) {
                    for (puffIdx in -1..1 step 2) {
                        val puffPhase = (timeSec * 4.8f + (if (puffIdx < 0) 0f else 0.5f)) % 1f
                        val puffX = pose.x + puffIdx * (basePuppetSize * 0.22f + puffPhase * 34f)
                        val puffY = groundY + basePuppetSize * 0.10f - puffPhase * 18f
                        drawCircle(
                            color = Color.White.copy(alpha = (1f - puffPhase) * 0.55f),
                            radius = (6f + puffPhase * 12f),
                            center = Offset(puffX, puffY)
                        )
                    }
                }

                withTransform({
                    translate(left = pose.x, top = pose.y)
                    rotate(degrees = pose.rotationDeg, pivot = Offset.Zero)
                    scale(
                        scaleX = pose.scaleX * (if (pose.facingLeft) -1f else 1f) * (1f - talkPulse * 0.5f),
                        scaleY = pose.scaleY * (1f + talkPulse),
                        pivot = Offset.Zero
                    )
                }) {
                    // Magical golden aura glow when super-jumping or in hero mode
                    val auraAlpha = if (timeSec - superJumpBoostTime < 1.2f || activeMotion == MotionStyle.SUPER_HERO_ZOOM) {
                        0.38f + 0.15f * sin(timeSec * 12f)
                    } else {
                        0.16f
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFDE047).copy(alpha = auraAlpha),
                                Color(0xFF38BDF8).copy(alpha = auraAlpha * 0.4f),
                                Color.Transparent
                            ),
                            center = Offset.Zero,
                            radius = basePuppetSize * 0.72f
                        ),
                        radius = basePuppetSize * 0.72f,
                        center = Offset.Zero
                    )

                    // Draw Articulated 5-Zone Skeletal Character Rig + Animated Appendages
                    drawArticulatedCharacterPuppet(
                        bitmap = activePuppetBitmap,
                        puppetSize = basePuppetSize,
                        motionStyle = activeMotion,
                        timeSec = timeSec,
                        isTalking = isTalkingNow,
                        showFaceOverlay = showLivingFaceOverlay
                    )
                }

                // G. Draw Ambient + Interactive Burst Particles
                drawParticleMagicSystem(
                    ambientParticles = ambientParticles,
                    burstParticles = burstParticles,
                    particleType = activeParticleMagic,
                    timeSec = timeSec,
                    w = w,
                    h = h
                )
            }

            // Floating Top-Left Scene Selector Pills
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (isCompact) 8.dp else 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.78f))
                    .padding(if (isCompact) 4.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                story.scenes.forEachIndexed { idx, s ->
                    val isSelected = idx == currentSceneIndex
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF334155),
                        modifier = Modifier.clickable {
                            currentSceneIndex = idx
                        }
                    ) {
                        Text(
                            text = if (isCompact) "S${s.sceneNumber}" else "Scene ${s.sceneNumber}",
                            color = if (isSelected) Color(0xFF0F172A) else Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (isCompact) 11.sp else 12.sp,
                            modifier = Modifier.padding(
                                horizontal = if (isCompact) 8.dp else 10.dp,
                                vertical = if (isCompact) 4.dp else 5.dp
                            )
                        )
                    }
                }
            }

            // Floating Top-Right Interactive Hint & Sound Onomatopoeia Badge
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (isCompact) 8.dp else 14.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = Color(0xFFFEF08A),
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = currentScene.soundEffectOnomatopoeia,
                        color = Color(0xFF9A3412),
                        fontWeight = FontWeight.Black,
                        fontSize = if (isCompact) 12.sp else 15.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(
                            horizontal = if (isCompact) 8.dp else 12.dp,
                            vertical = if (isCompact) 4.dp else 6.dp
                        )
                    )
                }

                if (!isCompact) {
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.75f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "👆 Tap or Drag ${story.characterName} to Jump & Fly!",
                            color = Color(0xFF7DD3FC),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Character Floating Comic Speech Bubble + Thumbnail of Original Kid's Paper Drawing
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(if (isCompact) 8.dp else 14.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mini Polaroid card of the original camera paper capture
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(
                        width = if (isCompact) 62.dp else 86.dp,
                        height = if (isCompact) 72.dp else 98.dp
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = bundle.originalPaperBitmap,
                            contentDescription = "Original Camera Drawing",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Text(
                            text = if (isCompact) "📸 Scan" else "📸 Camera Scan",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155),
                            maxLines = 1
                        )
                    }
                }

                // Speech bubble from character
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
                    color = Color.White.copy(alpha = 0.95f),
                    shadowElevation = 8.dp,
                    modifier = Modifier.widthIn(max = if (isCompact) 240.dp else 360.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = if (isCompact) 10.dp else 14.dp,
                            vertical = if (isCompact) 6.dp else 9.dp
                        )
                    ) {
                        Text(
                            text = "${story.characterName} says:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0284C7)
                        )
                        Text(
                            text = "\"${currentScene.characterSpeechBubble}\"",
                            fontSize = if (isCompact) 12.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Floating Bottom-Right Magic Action Buttons on Landscape/Desktop (Moved to bottom card on Portrait Phone to prevent overlap)
            if (!isCompact) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = triggerMagicBurst,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("🪄 Magic Burst!", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onRequestSceneTts(currentScene) },
                        enabled = !isGeneratingTts,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val hasCachedWav = bundle.sceneAudioWavBytes.containsKey(currentScene.sceneNumber)
                        Text(
                            when {
                                isGeneratingTts -> "🎙️ Recording..."
                                hasCachedWav -> "🔊 Replay Voiceover"
                                else -> "🎙️ Read Aloud (Gemini TTS)"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = { onRequestOmniVideo(currentScene) },
                        enabled = !isGeneratingVideo,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (isGeneratingVideo) "🎬 Omni 1.2 Flash..." else "🎬 Omni 1.2 Video",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 3. Karaoke Storybook Narration & Live Kid Remix Controls
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (isCompact) 12.dp else 16.dp,
                    vertical = if (isCompact) 8.dp else 10.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📖 ${currentScene.sceneTitle} (${activeBiome.emoji} ${activeBiome.label})",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isCompact) 13.sp else 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (!isCompact) {
                        Text(
                            text = bundle.providerUsed,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                // Karaoke Highlighted Story Text
                val annotatedNarration = buildAnnotatedString {
                    words.forEachIndexed { index, word ->
                        val isSpoken = index <= highlightedWordIndex
                        val isCurrentWord = index == highlightedWordIndex
                        withStyle(
                            SpanStyle(
                                color = when {
                                    isCurrentWord -> Color(0xFFFDE047)
                                    isSpoken -> Color.White
                                    else -> Color(0xFFCBD5E1)
                                },
                                fontWeight = if (isCurrentWord) FontWeight.Black else FontWeight.SemiBold,
                                background = if (isCurrentWord) Color(0xFFF59E0B).copy(alpha = 0.28f) else Color.Transparent
                            )
                        ) {
                            append(word)
                            append(" ")
                        }
                    }
                }

                Text(
                    text = annotatedNarration,
                    fontSize = if (isCompact) 14.sp else 16.sp,
                    lineHeight = if (isCompact) 19.sp else 22.sp
                )

                // On Compact Portrait Mobile, render the 3 Magic Action buttons here in a clean full-width row!
                if (isCompact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = triggerMagicBurst,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🪄 Burst!", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { onRequestSceneTts(currentScene) },
                            enabled = !isGeneratingTts,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            val hasCachedWav = bundle.sceneAudioWavBytes.containsKey(currentScene.sceneNumber)
                            Text(
                                when {
                                    isGeneratingTts -> "🎙️..."
                                    hasCachedWav -> "🔊 Replay"
                                    else -> "🎙️ Read Aloud"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = { onRequestOmniVideo(currentScene) },
                            enabled = !isGeneratingVideo,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (isGeneratingVideo) "🎬..." else "🎬 Video",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = !omniVideoStatus.isNullOrBlank()) {
                    Text(
                        text = omniVideoStatus ?: "",
                        color = Color(0xFF4ADE80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Live Kid Remix Bar: Switch Dance/Fly/Bounce Motion & Particle Magic on the fly!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎮 Moves:",
                        color = Color(0xFFFBBF24),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    MotionStyle.entries.forEach { style ->
                        val selected = activeMotion == style
                        Surface(
                            shape = CircleShape,
                            color = if (selected) Color(0xFF0284C7) else Color(0xFF334155),
                            modifier = Modifier.clickable {
                                activeMotion = style
                                SoundEngine.playEffect(SoundEffectType.JUMP_BOING)
                            }
                        ) {
                            Text(
                                text = "${style.emoji} ${style.label}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "✨ Magic:",
                        color = Color(0xFFF472B6),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    ParticleMagicType.entries.forEach { pm ->
                        val selected = activeParticleMagic == pm
                        Surface(
                            shape = CircleShape,
                            color = if (selected) Color(0xFFDB2777) else Color(0xFF334155),
                            modifier = Modifier.clickable {
                                activeParticleMagic = pm
                                SoundEngine.playEffect(SoundEffectType.BUBBLE_POP)
                            }
                        ) {
                            Text(
                                text = "${pm.emoji} ${pm.label}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

private data class PuppetPose(
    val x: Float,
    val y: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotationDeg: Float,
    val facingLeft: Boolean
)

private fun computePuppetPose(
    motionStyle: MotionStyle,
    scene: StoryScene,
    timeSec: Float,
    superJumpBoostTime: Float,
    userDragOffset: Offset?,
    stageW: Float,
    stageH: Float
): PuppetPose {
    // If kid is actively dragging their character with finger/mouse
    if (userDragOffset != null) {
        val wiggle = 8f * sin(timeSec * 14f)
        return PuppetPose(
            x = userDragOffset.x.coerceIn(stageW * 0.1f, stageW * 0.9f),
            y = userDragOffset.y.coerceIn(stageH * 0.12f, stageH * 0.82f),
            scaleX = 1.08f,
            scaleY = 1.08f,
            rotationDeg = wiggle,
            facingLeft = false
        )
    }

    val speed = scene.speedMultiplier.coerceIn(0.7f, 1.6f)
    val t = timeSec * speed
    val groundY = stageH * 0.64f
    val jumpAmp = stageH * scene.jumpHeightRatio.coerceIn(0.14f, 0.34f)
    val squashAmt = scene.squashStretchIntensity.coerceIn(0.12f, 0.32f)

    // Check if kid tapped for a Super Jump Backflip!
    val timeSinceTap = timeSec - superJumpBoostTime
    val tapJumpArc = if (timeSinceTap in 0f..1.1f) {
        val norm = timeSinceTap / 1.1f
        sin(norm * PI.toFloat()) * (stageH * 0.25f)
    } else {
        0f
    }
    val tapFlipDeg = if (timeSinceTap in 0f..1.1f) {
        (timeSinceTap / 1.1f) * 360f
    } else {
        0f
    }

    return when (motionStyle) {
        MotionStyle.BOUNCE_ADVENTURE -> {
            val horizPhase = t * 0.75f
            val x = stageW * (0.5f + 0.28f * sin(horizPhase))
            val facingLeft = cos(horizPhase) < 0f

            val bounceCycle = (t * 2.4f) % (PI.toFloat())
            val sinBounce = abs(sin(bounceCycle))
            val y = groundY - sinBounce * jumpAmp - tapJumpArc

            // Squash when close to ground, stretch near peak velocity
            val squashFactor = if (sinBounce < 0.22f) {
                (0.22f - sinBounce) / 0.22f
            } else {
                -0.35f * sinBounce
            }
            val scaleX = 1f + squashAmt * squashFactor
            val scaleY = 1f - squashAmt * squashFactor
            val tilt = 9f * cos(t * 2.4f) + tapFlipDeg

            PuppetPose(x, y, scaleX, scaleY, tilt, facingLeft)
        }

        MotionStyle.FLY_LOOP -> {
            // Sweeping figure-8 flight across the sky
            val flyT = t * 1.05f
            val x = stageW * (0.5f + 0.32f * sin(flyT))
            val y = stageH * (0.42f + 0.16f * sin(flyT * 2f)) - tapJumpArc * 0.5f
            val vx = cos(flyT)
            val vy = 2f * cos(flyT * 2f) * 0.25f
            val bankDeg = (vy * 28f).coerceIn(-24f, 24f) + tapFlipDeg
            val wingFlapStretch = 0.08f * sin(t * 11f)
            PuppetPose(
                x = x,
                y = y,
                scaleX = 1f + wingFlapStretch,
                scaleY = 1f - wingFlapStretch * 0.6f,
                rotationDeg = bankDeg,
                facingLeft = vx < 0f
            )
        }

        MotionStyle.SWIM_WAVE -> {
            val swimT = t * 0.85f
            val x = stageW * (0.5f + 0.28f * sin(swimT))
            val y = stageH * (0.52f + 0.12f * sin(swimT * 2.6f)) - tapJumpArc * 0.6f
            val undulate = 14f * cos(swimT * 2.6f) + tapFlipDeg
            val pulse = 0.10f * sin(t * 6f)
            PuppetPose(
                x = x,
                y = y,
                scaleX = 1f + pulse,
                scaleY = 1f - pulse * 0.7f,
                rotationDeg = undulate,
                facingLeft = cos(swimT) < 0f
            )
        }

        MotionStyle.DANCE_SPIN -> {
            val beat = t * 5.2f
            val beatBounce = abs(sin(beat))
            val x = stageW * (0.5f + 0.18f * sin(t * 1.4f))
            val y = groundY - beatBounce * (jumpAmp * 0.75f) - tapJumpArc

            // Every 4 seconds do a joyous 360 spin!
            val cycleWindow = (t % 4.0f)
            val spinDeg = if (cycleWindow > 3.0f) {
                (cycleWindow - 3.0f) * 360f
            } else {
                15f * sin(beat)
            } + tapFlipDeg

            val squash = (0.5f - beatBounce) * squashAmt * 1.2f
            PuppetPose(
                x = x,
                y = y,
                scaleX = 1f + squash,
                scaleY = 1f - squash,
                rotationDeg = spinDeg,
                facingLeft = sin(t * 2.8f) < 0f
            )
        }

        MotionStyle.FLOAT_SPACE -> {
            val floatT = t * 0.65f
            val x = stageW * (0.5f + 0.26f * sin(floatT))
            val y = stageH * (0.46f + 0.14f * cos(floatT * 1.5f)) - tapJumpArc * 0.5f
            val gentleRoll = 18f * sin(floatT * 1.8f) + tapFlipDeg
            val breathe = 0.06f * sin(t * 3.5f)
            PuppetPose(
                x = x,
                y = y,
                scaleX = 1f + breathe,
                scaleY = 1f + breathe,
                rotationDeg = gentleRoll,
                facingLeft = cos(floatT) < 0f
            )
        }

        MotionStyle.SUPER_HERO_ZOOM -> {
            val zoomT = t * 1.45f
            val x = stageW * (0.5f + 0.34f * sin(zoomT))
            val y = stageH * (0.44f + 0.14f * cos(zoomT * 2f)) - tapJumpArc * 0.5f
            val vx = cos(zoomT)
            val forwardLean = (if (vx >= 0f) 16f else -16f) + tapFlipDeg
            val sonicStretch = 0.14f * abs(vx)
            PuppetPose(
                x = x,
                y = y,
                scaleX = 1f + sonicStretch,
                scaleY = 1f - sonicStretch * 0.45f,
                rotationDeg = forwardLean,
                facingLeft = vx < 0f
            )
        }
    }
}

private fun DrawScope.drawBiomeWorld(
    biome: WorldBiome,
    scene: StoryScene,
    timeSec: Float,
    w: Float,
    h: Float
) {
    val skyTop = parseHexColor(scene.skyTopColorHex, Color(0xFF38BDF8))
    val skyBottom = parseHexColor(scene.skyBottomColorHex, Color(0xFFFEF08A))
    val groundColor = parseHexColor(scene.groundColorHex, Color(0xFF4ADE80))
    val accentColor = parseHexColor(scene.accentColorHex, Color(0xFFF472B6))

    // Continuous multi-layer parallax camera scroll so the world moves dynamically around the hero!
    val worldScroll = timeSec * 115f * scene.speedMultiplier.coerceIn(0.8f, 1.5f)

    // 1. Sky Gradient
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(skyTop, skyBottom),
            startY = 0f,
            endY = h * 0.82f
        ),
        size = Size(w, h)
    )

    // 2. Biome-Specific Dynamic Parallax Sky, Landmarks & Interactive Scene Actors
    when (biome) {
        WorldBiome.SUNNY_MEADOW -> {
            // Spinning sun with pulsing rays
            val sunCenter = Offset(w * 0.84f, h * 0.17f)
            for (rIdx in 0 until 12) {
                val angle = (rIdx * 30f + timeSec * 25f) * (PI.toFloat() / 180f)
                val rayLen = w * (0.095f + 0.02f * sin(timeSec * 5f + rIdx))
                drawLine(
                    color = Color(0xFFFDE047).copy(alpha = 0.55f),
                    start = sunCenter,
                    end = Offset(
                        sunCenter.x + cos(angle) * rayLen,
                        sunCenter.y + sin(angle) * rayLen
                    ),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
            drawCircle(color = Color(0xFFFACC15), radius = w * 0.048f, center = sunCenter)

            // Shimmering Rainbow Arch across the meadow sky
            val rainbowColors = listOf(
                Color(0xFFEF4444),
                Color(0xFFF97316),
                Color(0xFFFACC15),
                Color(0xFF22C55E),
                Color(0xFF38BDF8),
                Color(0xFFA855F7)
            )
            val rainbowShift = 18f * sin(timeSec * 1.4f)
            rainbowColors.forEachIndexed { idx, rc ->
                val pad = idx * 9f
                drawArc(
                    color = rc.copy(alpha = 0.34f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.10f + pad + rainbowShift, h * 0.12f + pad),
                    size = Size(w * 0.80f - pad * 2, h * 0.95f - pad * 2),
                    style = Stroke(width = 8.5f)
                )
            }

            drawParallaxClouds(timeSec, w, h, Color.White.copy(alpha = 0.90f))

            // Scrolling Midground Bouncing Magic Mushrooms & Spinning Golden Star-Gates
            for (m in 0 until 4) {
                val mx = ((m * (w * 0.34f) - worldScroll * 0.55f) % (w * 1.36f) + (w * 1.36f)) % (w * 1.36f) - w * 0.18f
                val bounce = abs(sin(timeSec * 4.5f + m)) * 12f
                val baseY = h * 0.72f
                // Mushroom stem
                drawRoundRect(
                    color = Color(0xFFFEF3C7),
                    topLeft = Offset(mx - 9f, baseY - 26f - bounce),
                    size = Size(18f, 30f + bounce),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                // Red spotted cap
                drawOval(
                    color = Color(0xFFEF4444),
                    topLeft = Offset(mx - 26f, baseY - 44f - bounce),
                    size = Size(52f, 26f)
                )
                drawCircle(Color.White, radius = 4.5f, center = Offset(mx - 10f, baseY - 33f - bounce))
                drawCircle(Color.White, radius = 5.5f, center = Offset(mx + 6f, baseY - 35f - bounce))
            }
        }

        WorldBiome.STARRY_SPACE -> {
            // Scrolling & twinkling starfield
            for (s in 0 until 52) {
                val rawX = ((s * 73) % 100) / 100f * w - worldScroll * (if (s % 2 == 0) 0.18f else 0.35f)
                val sx = ((rawX % w) + w) % w
                val sy = ((s * 41) % 72) / 100f * h
                val twinkle = 0.35f + 0.65f * abs(sin(timeSec * 3.8f + s))
                drawCircle(
                    color = Color.White.copy(alpha = twinkle),
                    radius = if (s % 4 == 0) 4.2f else 2.4f,
                    center = Offset(sx, sy)
                )
            }
            // Orbiting Ringed Saturn & Golden Cheese Moon
            val planetX = ((w * 0.80f - worldScroll * 0.14f) % (w * 1.2f) + w * 1.2f) % (w * 1.2f) - w * 0.1f
            val planetCenter = Offset(planetX, h * 0.24f + 10f * sin(timeSec * 1.8f))
            drawCircle(
                color = Color(0xFFF59E0B),
                radius = w * 0.055f,
                center = planetCenter
            )
            drawOval(
                color = Color(0xFFFDE047).copy(alpha = 0.80f),
                topLeft = Offset(planetCenter.x - w * 0.095f, planetCenter.y - h * 0.02f),
                size = Size(w * 0.19f, h * 0.04f),
                style = Stroke(width = 6f)
            )

            // Scrolling Cosmic Star-Rings that the character flies through
            for (ring in 0 until 3) {
                val rx = ((ring * (w * 0.45f) - worldScroll * 0.65f) % (w * 1.35f) + w * 1.35f) % (w * 1.35f) - w * 0.15f
                val ry = h * (0.42f + 0.08f * sin(timeSec * 2f + ring))
                drawOval(
                    color = Color(0xFF38BDF8).copy(alpha = 0.65f),
                    topLeft = Offset(rx - 18f, ry - 48f),
                    size = Size(36f, 96f),
                    style = Stroke(width = 5f)
                )
            }
        }

        WorldBiome.OCEAN_REEF -> {
            // Underwater sun-rays / caustics
            for (beam in 0 until 6) {
                val bx = w * (0.12f + beam * 0.16f) + 28f * sin(timeSec * 1.5f + beam)
                val path = Path().apply {
                    moveTo(bx - 20f, 0f)
                    lineTo(bx + 25f, 0f)
                    lineTo(bx + 90f, h * 0.85f)
                    lineTo(bx - 65f, h * 0.85f)
                    close()
                }
                drawPath(path, Color.White.copy(alpha = 0.12f))
            }
            // Scrolling Swaying Kelp & Leaping Friendly Dolphins
            for (k in 0 until 9) {
                val kx = ((k * (w * 0.14f) - worldScroll * 0.5f) % (w * 1.26f) + w * 1.26f) % (w * 1.26f) - w * 0.1f
                val sway = 22f * sin(timeSec * 2.8f + k)
                val path = Path().apply {
                    moveTo(kx, h * 0.85f)
                    quadraticTo(kx + sway, h * 0.68f, kx - sway * 0.6f, h * 0.52f)
                }
                drawPath(
                    path = path,
                    color = Color(0xFF10B981).copy(alpha = 0.78f),
                    style = Stroke(width = 12f, cap = StrokeCap.Round)
                )
            }
            // Swimming School of Colorful Reef Fish
            for (fish in 0 until 4) {
                val fx = ((fish * (w * 0.30f) + worldScroll * 0.75f) % (w * 1.2f)) - w * 0.1f
                val fy = h * (0.30f + (fish % 3) * 0.11f) + 12f * sin(timeSec * 4f + fish)
                drawOval(
                    color = if (fish % 2 == 0) Color(0xFFF97316) else Color(0xFFFACC15),
                    topLeft = Offset(fx - 16f, fy - 9f),
                    size = Size(32f, 18f)
                )
                drawCircle(Color.White, radius = 3f, center = Offset(fx + 8f, fy - 2f))
            }
        }

        WorldBiome.CANDY_KINGDOM -> {
            drawParallaxClouds(timeSec, w, h, Color(0xFFFBCFE8).copy(alpha = 0.92f))
            // Scrolling Spinning Peppermint Lollipops & Candy Towers
            for (lp in 0 until 5) {
                val lx = ((lp * (w * 0.25f) - worldScroll * 0.55f) % (w * 1.25f) + w * 1.25f) % (w * 1.25f) - w * 0.1f
                val ly = h * (0.54f + (lp % 2) * 0.06f)
                drawLine(
                    color = Color.White,
                    start = Offset(lx, ly),
                    end = Offset(lx, h * 0.82f),
                    strokeWidth = 7f
                )
                drawCircle(
                    color = if (lp % 2 == 0) Color(0xFFEC4899) else Color(0xFF38BDF8),
                    radius = 26f,
                    center = Offset(lx, ly)
                )
                // Spinning peppermint blades
                for (b in 0 until 4) {
                    val ang = (b * 90f + timeSec * 120f) * (PI.toFloat() / 180f)
                    drawLine(
                        color = Color.White,
                        start = Offset(lx, ly),
                        end = Offset(lx + cos(ang) * 22f, ly + sin(ang) * 22f),
                        strokeWidth = 4.5f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        WorldBiome.ENCHANTED_FOREST -> {
            // Glowing moon & scrolling mystical pine trees with swinging lanterns
            drawCircle(
                color = Color(0xFFFEF08A).copy(alpha = 0.92f),
                radius = w * 0.052f,
                center = Offset(w * 0.16f, h * 0.20f)
            )
            for (tree in 0 until 7) {
                val tx = ((tree * (w * 0.18f) - worldScroll * 0.48f) % (w * 1.26f) + w * 1.26f) % (w * 1.26f) - w * 0.1f
                val sway = 8f * sin(timeSec * 2.2f + tree)
                val treeTopY = h * (0.36f + (tree % 3) * 0.05f)
                val path = Path().apply {
                    moveTo(tx + sway, treeTopY)
                    lineTo(tx - 50f, h * 0.80f)
                    lineTo(tx + 50f, h * 0.80f)
                    close()
                }
                drawPath(path, Color(0xFF065F46).copy(alpha = 0.72f))
                // Glowing fairy lantern on each tree
                drawCircle(
                    color = Color(0xFFFDE047).copy(alpha = 0.75f + 0.25f * sin(timeSec * 5f + tree)),
                    radius = 6f,
                    center = Offset(tx + sway * 0.5f, treeTopY + 34f)
                )
            }
        }

        WorldBiome.VOLCANO_ISLAND -> {
            drawParallaxClouds(timeSec, w, h, Color.White.copy(alpha = 0.85f))
            // Scrolling Friendly Volcano puffing animated rising lava-bubbles/sparks!
            val vx = ((w * 0.78f - worldScroll * 0.35f) % (w * 1.3f) + w * 1.3f) % (w * 1.3f) - w * 0.15f
            val volcanoPath = Path().apply {
                moveTo(vx - 45f, h * 0.42f)
                lineTo(vx + 45f, h * 0.42f)
                lineTo(vx + 130f, h * 0.80f)
                lineTo(vx - 130f, h * 0.80f)
                close()
            }
            drawPath(volcanoPath, Color(0xFF78350F))
            drawRoundRect(
                color = Color(0xFFF97316),
                topLeft = Offset(vx - 48f, h * 0.40f),
                size = Size(96f, 18f),
                cornerRadius = CornerRadius(9f, 9f)
            )
            // Erupting golden lava puffs rising from volcano crater
            for (puff in 0 until 4) {
                val pCycle = (timeSec * 0.85f + puff * 0.25f) % 1f
                val py = h * 0.38f - pCycle * (h * 0.26f)
                val px = vx + sin(timeSec * 3f + puff * 2f) * (28f * pCycle)
                drawCircle(
                    color = Color(0xFFFACC15).copy(alpha = (1f - pCycle) * 0.85f),
                    radius = 8f + pCycle * 14f,
                    center = Offset(px, py)
                )
            }
        }
    }

    // 3. Rolling Parallax Mid-Ground Hills (Scrolling at 0.75x camera speed)
    val hillColorBack = groundColor.copy(alpha = 0.58f)
    for (hill in 0 until 5) {
        val span = w * 0.38f
        val hx = ((hill * span - worldScroll * 0.75f) % (span * 5f) + (span * 5f)) % (span * 5f) - span
        drawOval(
            color = hillColorBack,
            topLeft = Offset(hx, h * 0.65f),
            size = Size(w * 0.52f, h * 0.36f)
        )
    }

    // 4. Foreground Stage Ground + Fast-Scrolling Foreground Flowers / Pebbles (1.35x Parallax Speed)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(groundColor, groundColor.copy(red = groundColor.red * 0.72f, green = groundColor.green * 0.72f, blue = groundColor.blue * 0.72f)),
            startY = h * 0.76f,
            endY = h
        ),
        topLeft = Offset(0f, h * 0.76f),
        size = Size(w, h * 0.24f)
    )

    for (f in 0 until 14) {
        val fx = ((f * (w / 10f) - worldScroll * 1.35f) % w + w) % w
        val fy = h * (0.80f + (f % 3) * 0.055f)
        drawCircle(
            color = if (f % 2 == 0) accentColor else Color(0xFFFDE047),
            radius = 6.5f,
            center = Offset(fx, fy)
        )
        drawCircle(
            color = Color.White,
            radius = 2.5f,
            center = Offset(fx, fy)
        )
    }
}

private fun DrawScope.drawParallaxClouds(
    timeSec: Float,
    w: Float,
    h: Float,
    cloudColor: Color
) {
    for (c in 0 until 5) {
        val cx = ((c * w * 0.26f - timeSec * (32f + c * 7f)) % (w + 180f) + (w + 180f)) % (w + 180f) - 90f
        val cy = h * (0.13f + (c % 2) * 0.09f) + 5f * sin(timeSec * 1.5f + c)
        drawCircle(cloudColor, radius = 28f, center = Offset(cx, cy))
        drawCircle(cloudColor, radius = 36f, center = Offset(cx + 26f, cy - 6f))
        drawCircle(cloudColor, radius = 26f, center = Offset(cx + 54f, cy + 2f))
    }
}

/**
 * Renders the child's extracted character drawing as a 5-Part Articulated 2D Skeletal Puppet Rig!
 * - Splits the character into Head, Left Wing/Arm, Right Wing/Arm, Core Torso, and Left/Right Walking Legs
 * - Adds animated flapping golden wings, animated walking/kicking feet, and animated blinking eyes & talking mouth!
 */
private fun DrawScope.drawArticulatedCharacterPuppet(
    bitmap: ImageBitmap,
    puppetSize: Float,
    motionStyle: MotionStyle,
    timeSec: Float,
    isTalking: Boolean,
    showFaceOverlay: Boolean
) {
    val bw = bitmap.width.coerceAtLeast(1)
    val bh = bitmap.height.coerceAtLeast(1)
    val dstW = puppetSize.toInt().coerceAtLeast(48)
    val dstH = puppetSize.toInt().coerceAtLeast(48)
    val halfW = dstW / 2
    val halfH = dstH / 2

    val strideFreq = when (motionStyle) {
        MotionStyle.SUPER_HERO_ZOOM, MotionStyle.FLY_LOOP -> 12f
        MotionStyle.DANCE_SPIN -> 10.5f
        else -> 8.5f
    }
    val armFlapDeg = 26f * sin(timeSec * strideFreq)
    val legSwingDeg = 24f * sin(timeSec * strideFreq)
    val headNodDeg = (if (isTalking) 9f * sin(timeSec * 15f) else 5.5f * sin(timeSec * 5f))

    // 1. Animated Flowing Hero Cape + Flapping Golden Fairy Wings behind the character's shoulders
    val capeWave = 14f * sin(timeSec * 9f)
    val capePath = Path().apply {
        moveTo(-puppetSize * 0.14f, -puppetSize * 0.10f)
        lineTo(puppetSize * 0.14f, -puppetSize * 0.10f)
        lineTo(puppetSize * 0.28f + capeWave, puppetSize * 0.34f)
        lineTo(-puppetSize * 0.28f + capeWave, puppetSize * 0.34f)
        close()
    }
    drawPath(
        path = capePath,
        brush = Brush.verticalGradient(
            listOf(Color(0xFFF43F5E).copy(alpha = 0.85f), Color(0xFFA855F7).copy(alpha = 0.85f))
        )
    )

    for (side in listOf(-1f, 1f)) {
        val shoulderX = side * (puppetSize * 0.24f)
        val shoulderY = -puppetSize * 0.06f
        withTransform({
            translate(shoulderX, shoulderY)
            rotate(degrees = side * (armFlapDeg * 1.25f - 12f), pivot = Offset.Zero)
        }) {
            drawOval(
                color = Color(0xFFFDE047).copy(alpha = 0.85f),
                topLeft = Offset(if (side < 0f) -puppetSize * 0.28f else 0f, -puppetSize * 0.11f),
                size = Size(puppetSize * 0.28f, puppetSize * 0.18f)
            )
            drawOval(
                color = Color.White,
                topLeft = Offset(if (side < 0f) -puppetSize * 0.28f else 0f, -puppetSize * 0.11f),
                size = Size(puppetSize * 0.28f, puppetSize * 0.18f),
                style = Stroke(width = 3f)
            )
        }
    }

    // 2. Articulated Left & Right Leg Slices (Bottom 66%..100% of character bitmap, pivoting at hips!)
    val legSrcY = (bh * 0.66f).toInt()
    val legSrcH = (bh - legSrcY).coerceAtLeast(1)
    val legDstY = (-halfH + (dstH * 0.66f)).toInt()
    val legDstH = (dstH - (dstH * 0.66f).toInt()).coerceAtLeast(1)

    // Left Leg Slice + Animated Walking Foot
    withTransform({
        rotate(degrees = legSwingDeg, pivot = Offset(-puppetSize * 0.14f, puppetSize * 0.18f))
    }) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(0, legSrcY),
            srcSize = IntSize(bw / 2, legSrcH),
            dstOffset = IntOffset(-halfW, legDstY),
            dstSize = IntSize(halfW, legDstH)
        )
    }

    // Right Leg Slice + Opposite Phase Walking Foot
    withTransform({
        rotate(degrees = -legSwingDeg, pivot = Offset(puppetSize * 0.14f, puppetSize * 0.18f))
    }) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(bw / 2, legSrcY),
            srcSize = IntSize(bw - bw / 2, legSrcH),
            dstOffset = IntOffset(0, legDstY),
            dstSize = IntSize(dstW - halfW, legDstH)
        )
    }

    // 3. Articulated Left & Right Arm / Wing Slices (Outer 28% of middle section, flapping at shoulders!)
    val midSrcY = (bh * 0.28f).toInt()
    val midSrcH = (bh * 0.44f).toInt().coerceAtLeast(1)
    val armSrcW = (bw * 0.28f).toInt().coerceAtLeast(1)
    val midDstY = (-halfH + (dstH * 0.28f)).toInt()
    val midDstH = (dstH * 0.44f).toInt().coerceAtLeast(1)
    val armDstW = (dstW * 0.28f).toInt().coerceAtLeast(1)

    withTransform({
        rotate(degrees = armFlapDeg, pivot = Offset(-puppetSize * 0.22f, -puppetSize * 0.05f))
    }) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(0, midSrcY),
            srcSize = IntSize(armSrcW, midSrcH),
            dstOffset = IntOffset(-halfW, midDstY),
            dstSize = IntSize(armDstW, midDstH)
        )
    }

    withTransform({
        rotate(degrees = -armFlapDeg, pivot = Offset(puppetSize * 0.22f, -puppetSize * 0.05f))
    }) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(bw - armSrcW, midSrcY),
            srcSize = IntSize(armSrcW, midSrcH),
            dstOffset = IntOffset(halfW - armDstW, midDstY),
            dstSize = IntSize(armDstW, midDstH)
        )
    }

    // 4. Core Torso Slice (Center body with subtle breathing)
    val torsoSrcX = (bw * 0.16f).toInt()
    val torsoSrcW = (bw * 0.68f).toInt().coerceAtLeast(1)
    val torsoDstX = -halfW + (dstW * 0.16f).toInt()
    val torsoDstW = (dstW * 0.68f).toInt().coerceAtLeast(1)
    drawImage(
        image = bitmap,
        srcOffset = IntOffset(torsoSrcX, midSrcY),
        srcSize = IntSize(torsoSrcW, midSrcH),
        dstOffset = IntOffset(torsoDstX, midDstY),
        dstSize = IntSize(torsoDstW, midDstH)
    )

    // 5. Articulated Head & Crown Slice (Top 0%..38% of character bitmap, nodding & tilting with speech!)
    val headSrcH = (bh * 0.38f).toInt().coerceAtLeast(1)
    val headDstH = (dstH * 0.38f).toInt().coerceAtLeast(1)
    withTransform({
        rotate(degrees = headNodDeg, pivot = Offset(0f, -puppetSize * 0.14f))
    }) {
        drawImage(
            image = bitmap,
            srcOffset = IntOffset(0, 0),
            srcSize = IntSize(bw, headSrcH),
            dstOffset = IntOffset(-halfW, -halfH),
            dstSize = IntSize(dstW, headDstH)
        )

        if (showFaceOverlay) {
            drawLivingExpressionOverlay(
                puppetSize = puppetSize,
                timeSec = timeSec,
                isTalking = isTalking
            )
        }
    }
}

private fun DrawScope.drawCharacterContrail(
    trailPoints: List<Offset>,
    motionStyle: MotionStyle,
    particleMagic: ParticleMagicType,
    timeSec: Float
) {
    if (trailPoints.size < 2) return
    val rainbowPalette = listOf(
        Color(0xFFEF4444),
        Color(0xFFF97316),
        Color(0xFFFACC15),
        Color(0xFF4ADE80),
        Color(0xFF38BDF8),
        Color(0xFFC084FC)
    )
    for (i in 0 until trailPoints.size - 1) {
        val frac = (i + 1).toFloat() / trailPoints.size
        val c = if (particleMagic == ParticleMagicType.RAINBOW_TRAIL || motionStyle == MotionStyle.FLY_LOOP) {
            rainbowPalette[i % rainbowPalette.size]
        } else {
            Color(0xFFFDE047)
        }
        drawCircle(
            color = c.copy(alpha = frac * 0.45f),
            radius = 4f + frac * 12f,
            center = trailPoints[i]
        )
    }
}

private fun DrawScope.drawCompanionProps(
    companions: List<CompanionProp>,
    heroX: Float,
    heroY: Float,
    heroRadius: Float,
    timeSec: Float
) {
    companions.forEachIndexed { idx, comp ->
        val angle = timeSec * comp.orbitSpeed * 1.6f + idx * (PI.toFloat() * 0.85f)
        val cx = heroX + cos(angle) * (heroRadius * 1.45f)
        val cy = heroY - heroRadius * 0.35f + sin(angle * 2f) * (heroRadius * 0.45f)
        val propColor = parseHexColor(comp.colorHex, Color(0xFFFACC15))

        when (comp.type) {
            CompanionType.BUTTERFLY -> {
                val wingFlap = abs(sin(timeSec * 14f + idx)) * 14f + 4f
                drawOval(
                    color = propColor,
                    topLeft = Offset(cx - wingFlap - 4f, cy - 10f),
                    size = Size(wingFlap, 20f)
                )
                drawOval(
                    color = propColor,
                    topLeft = Offset(cx + 4f, cy - 10f),
                    size = Size(wingFlap, 20f)
                )
                drawCircle(Color(0xFF1E293B), radius = 4f, center = Offset(cx, cy))
            }

            CompanionType.FRIENDLY_UFO -> {
                drawOval(
                    color = Color(0xFFBAE6FD),
                    topLeft = Offset(cx - 14f, cy - 14f),
                    size = Size(28f, 18f)
                )
                drawOval(
                    color = propColor,
                    topLeft = Offset(cx - 24f, cy - 4f),
                    size = Size(48f, 14f)
                )
                drawCircle(Color(0xFFFDE047), radius = 3.5f, center = Offset(cx - 12f, cy + 3f))
                drawCircle(Color(0xFFF43F5E), radius = 3.5f, center = Offset(cx, cy + 3f))
                drawCircle(Color(0xFF4ADE80), radius = 3.5f, center = Offset(cx + 12f, cy + 3f))
            }

            CompanionType.JELLYFISH -> {
                val pulse = 3f * sin(timeSec * 6f + idx)
                drawArc(
                    color = propColor.copy(alpha = 0.85f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(cx - 16f - pulse, cy - 16f),
                    size = Size(32f + pulse * 2, 26f)
                )
                for (tIdx in -1..1) {
                    val tx = cx + tIdx * 8f
                    drawLine(
                        color = propColor,
                        start = Offset(tx, cy),
                        end = Offset(tx + 4f * sin(timeSec * 8f + tIdx), cy + 18f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
            }

            CompanionType.BALLOON -> {
                drawLine(
                    color = Color.White,
                    start = Offset(cx, cy + 16f),
                    end = Offset(cx + 5f * sin(timeSec * 4f), cy + 40f),
                    strokeWidth = 2f
                )
                drawOval(
                    color = propColor,
                    topLeft = Offset(cx - 15f, cy - 18f),
                    size = Size(30f, 36f)
                )
            }

            CompanionType.STAR_SPRITE, CompanionType.BABY_BIRD -> {
                drawCircle(
                    color = propColor.copy(alpha = 0.35f),
                    radius = 18f + 4f * sin(timeSec * 8f),
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = propColor,
                    radius = 9f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(cx - 2f, cy - 2f)
                )
            }
        }
    }
}

private fun DrawScope.drawLivingExpressionOverlay(
    puppetSize: Float,
    timeSec: Float,
    isTalking: Boolean
) {
    // 1. Animated Waving Magic Star-Wand next to the character's hand
    val wandAngle = 22f * sin(timeSec * 8f)
    withTransform({
        translate(puppetSize * 0.34f, -puppetSize * 0.04f)
        rotate(wandAngle, pivot = Offset.Zero)
    }) {
        drawLine(
            color = Color(0xFFF59E0B),
            start = Offset(0f, 14f),
            end = Offset(14f, -18f),
            strokeWidth = 4.5f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color(0xFFFDE047),
            radius = 8f + 2f * abs(sin(timeSec * 12f)),
            center = Offset(14f, -18f)
        )
        drawCircle(
            color = Color.White,
            radius = 4f,
            center = Offset(14f, -18f)
        )
    }

    // 2. Sparkling magical halo crown & animated talking sound-rings around the character
    val crownY = -puppetSize * 0.46f
    val bob = 4f * sin(timeSec * 6f)
    drawCircle(
        color = Color(0xFFFDE047).copy(alpha = 0.85f),
        radius = 6f + 2f * abs(sin(timeSec * 8f)),
        center = Offset(0f, crownY + bob)
    )
    drawCircle(
        color = Color(0xFFF472B6).copy(alpha = 0.75f),
        radius = 4.5f,
        center = Offset(-18f, crownY + 5f - bob)
    )
    drawCircle(
        color = Color(0xFF38BDF8).copy(alpha = 0.75f),
        radius = 4.5f,
        center = Offset(18f, crownY + 5f - bob)
    )

    if (isTalking) {
        val wavePhase = (timeSec * 3.5f) % 1f
        val ringRadius = puppetSize * (0.42f + wavePhase * 0.25f)
        drawCircle(
            color = Color(0xFFFDE047).copy(alpha = (1f - wavePhase) * 0.5f),
            radius = ringRadius,
            center = Offset.Zero,
            style = Stroke(width = 3f)
        )
    }
}

private fun DrawScope.drawParticleMagicSystem(
    ambientParticles: List<MagicParticle>,
    burstParticles: MutableList<MagicParticle>,
    particleType: ParticleMagicType,
    timeSec: Float,
    w: Float,
    h: Float
) {
    // 1. Ambient Magic Particles
    ambientParticles.forEachIndexed { idx, p ->
        val px = ((p.x * w + timeSec * (22f + (idx % 5) * 8f) + 18f * sin(timeSec * 2f + p.phase)) % w + w) % w
        val py = when (particleType) {
            ParticleMagicType.BUBBLES, ParticleMagicType.FIREFLIES, ParticleMagicType.HEARTS -> {
                ((p.y * h - timeSec * (28f + (idx % 6) * 7f)) % h + h) % h
            }
            else -> {
                ((p.y * h + timeSec * (26f + (idx % 6) * 7f)) % h + h) % h
            }
        }
        val twinkle = 0.45f + 0.55f * abs(sin(timeSec * 4f + p.phase))

        when (particleType) {
            ParticleMagicType.BUBBLES -> {
                drawCircle(
                    color = Color(0xFFBAE6FD).copy(alpha = 0.65f * twinkle),
                    radius = p.size * 1.2f,
                    center = Offset(px, py),
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.7f),
                    radius = p.size * 0.3f,
                    center = Offset(px - p.size * 0.35f, py - p.size * 0.35f)
                )
            }

            ParticleMagicType.FIREFLIES -> {
                drawCircle(
                    color = Color(0xFFFDE047).copy(alpha = 0.28f * twinkle),
                    radius = p.size * 2.1f,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = Color(0xFFFEF08A).copy(alpha = twinkle),
                    radius = p.size * 0.65f,
                    center = Offset(px, py)
                )
            }

            ParticleMagicType.METEORS -> {
                if (idx % 3 == 0) {
                    drawLine(
                        color = Color(0xFFFDE047).copy(alpha = 0.6f * twinkle),
                        start = Offset(px - 28f, py - 14f),
                        end = Offset(px, py),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(Color.White, radius = p.size * 0.6f, center = Offset(px, py))
                }
            }

            ParticleMagicType.SNOWFLAKES -> {
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f * twinkle),
                    radius = p.size * 0.65f,
                    center = Offset(px, py)
                )
            }

            else -> {
                // Sparkles, Confetti, Rainbow Dust, Hearts
                drawCircle(
                    color = p.color.copy(alpha = 0.82f * twinkle),
                    radius = p.size * 0.75f,
                    center = Offset(px, py)
                )
            }
        }
    }

    // 2. Interactive Tap / Magic Wand Burst Particles
    val dt = 0.016f
    val iter = burstParticles.iterator()
    while (iter.hasNext()) {
        val bp = iter.next()
        bp.x += bp.vx * dt
        bp.y += bp.vy * dt
        bp.vy += 280f * dt // gravity
        bp.alpha -= 0.75f * dt
        if (bp.alpha <= 0f) {
            iter.remove()
        } else {
            drawCircle(
                color = bp.color.copy(alpha = bp.alpha.coerceIn(0f, 1f)),
                radius = bp.size,
                center = Offset(bp.x, bp.y)
            )
        }
    }
}

private fun parseHexColor(hex: String, fallback: Color): Color {
    val clean = hex.trim().removePrefix("#")
    return try {
        when (clean.length) {
            6 -> {
                val rgb = clean.toLong(16)
                Color(0xFF000000 or rgb)
            }
            8 -> {
                val argb = clean.toLong(16)
                Color(argb)
            }
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}
