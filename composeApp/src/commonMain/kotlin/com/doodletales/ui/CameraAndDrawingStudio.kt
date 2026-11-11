package com.doodletales.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doodletales.imaging.DrawingProcessor
import com.doodletales.imaging.KidDrawingPreset
import com.doodletales.model.AiConfig
import com.doodletales.model.RgbaBuffer
import com.doodletales.platform.PlatformMediaController
import com.doodletales.platform.SoundEffectType
import com.doodletales.platform.SoundEngine
import com.doodletales.platform.rgbaBufferToImageBitmap
import kotlin.math.hypot

enum class StudioInputTab(val label: String, val emoji: String) {
    CAMERA_SCANNER("Camera Paper Scanner", "📸"),
    FINGER_CANVAS("Draw with Crayons", "🖍️")
}

private data class CrayonStroke(
    val points: List<Offset>,
    val colorArgb: Int,
    val radiusPx: Float
)

@Composable
fun CameraAndDrawingStudio(
    selectedPreset: KidDrawingPreset,
    currentPaperBuffer: RgbaBuffer,
    extractedCutoutBuffer: RgbaBuffer,
    kidHintPrompt: String,
    onKidHintChanged: (String) -> Unit,
    onSelectPreset: (KidDrawingPreset) -> Unit,
    onNewPaperDrawingCaptured: (RgbaBuffer, KidDrawingPreset, String) -> Unit,
    onBringToLifeClicked: () -> Unit,
    onOpenAiKeyHub: () -> Unit,
    aiConfig: AiConfig,
    mediaController: PlatformMediaController,
    statusBanner: String,
    isLoadingAnimation: Boolean
) {
    var activeTab by remember { mutableStateOf(StudioInputTab.CAMERA_SCANNER) }
    val paperBitmap = remember(currentPaperBuffer) { rgbaBufferToImageBitmap(currentPaperBuffer) }
    val cutoutBitmap = remember(extractedCutoutBuffer) { rgbaBufferToImageBitmap(extractedCutoutBuffer) }

    // Animated laser scanner line for Camera Viewfinder
    val infiniteTransition = rememberInfiniteTransition()
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Interactive Finger Drawing Canvas state
    val customStrokes = remember { mutableStateListOf<CrayonStroke>() }
    var currentStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var selectedBrushArgb by remember { mutableStateOf(0xFF22C55E.toInt()) }
    var selectedBrushSize by remember { mutableStateOf(9f) }
    var canvasSizePx by remember { mutableStateOf(Size(360f, 360f)) }

    val crayonPalette = remember {
        listOf(
            "Emerald" to 0xFF22C55E.toInt(),
            "Cherry Red" to 0xFFEF4444.toInt(),
            "Sky Blue" to 0xFF0EA5E9.toInt(),
            "Sunshine" to 0xFFFACC15.toInt(),
            "Tangerine" to 0xFFF97316.toInt(),
            "Berry Pink" to 0xFFEC4899.toInt(),
            "Royal Purple" to 0xFFA855F7.toInt(),
            "Outline Ink" to 0xFF1E293B.toInt()
        )
    }

    val storyIdeaChips = remember {
        listOf(
            "🌈 Flies to a Cotton-Candy Castle",
            "🪐 Races a Friendly Alien to the Moon",
            "🐠 Starts an Underwater Bubble Band",
            "🌋 Finds a Magical Crown on Dino Island"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Top Studio Header + AI Model Evaluation & Key Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1E293B))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🎨", fontSize = 22.sp)
                    }
                }
                Column {
                    Text(
                        text = "CamCrayon • Magic Living Drawings",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Hold your crayon drawing to the camera or draw below — watch it turn into a moving story!",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode Switcher Pills
                StudioInputTab.entries.forEach { tab ->
                    val isSelected = activeTab == tab
                    Button(
                        onClick = { activeTab = tab },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Color(0xFF0284C7) else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("${tab.emoji} ${tab.label}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // AI Key & Model Evaluation Button
                OutlinedButton(
                    onClick = onOpenAiKeyHub,
                    border = BorderStroke(1.5.dp, Color(0xFFFDE047)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    val hasKey = aiConfig.activeApiKey.isNotBlank()
                    Text(
                        text = if (hasKey) "🔑 ${aiConfig.selectedProvider.name} Key Active" else "🔑 AI Key & Evaluation (Gemini / Kimi / DeepSeek)",
                        color = Color(0xFFFDE047),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 2. Main Split Workspace: Left = Camera Viewfinder / Finger Canvas; Right = Cutout Puppet Preview & Presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LEFT PANEL: Camera Scanner Viewfinder OR Interactive Crayon Canvas
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .weight(1.25f)
                    .fillMaxHeight()
            ) {
                if (activeTab == StudioInputTab.CAMERA_SCANNER) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📸 Step 1: Show Paper Drawing to Camera",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        SoundEngine.playEffect(SoundEffectType.CAMERA_SCAN_WHOOSH)
                                        mediaController.captureFromCamera()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Snap Camera", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }

                                OutlinedButton(
                                    onClick = {
                                        mediaController.pickFromGalleryOrFile()
                                    },
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Upload Photo", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }

                        // Camera Viewfinder Box with Glowing Scanner Overlay & Cutout Comparison
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF090D16))
                                .border(2.dp, Color(0xFF334155), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Paper Drawing in Camera Viewfinder
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFFFAF7EE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = paperBitmap,
                                        contentDescription = "Scanned Paper Drawing",
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Viewfinder Target Brackets + Laser Scan Sweep
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val w = size.width
                                        val h = size.height
                                        val pad = 16f
                                        val bracketLen = 32f
                                        val bracketColor = Color(0xFF10B981)
                                        val stroke = 4.5f

                                        // Top-left bracket
                                        drawLine(bracketColor, Offset(pad, pad), Offset(pad + bracketLen, pad), stroke)
                                        drawLine(bracketColor, Offset(pad, pad), Offset(pad, pad + bracketLen), stroke)
                                        // Top-right bracket
                                        drawLine(bracketColor, Offset(w - pad, pad), Offset(w - pad - bracketLen, pad), stroke)
                                        drawLine(bracketColor, Offset(w - pad, pad), Offset(w - pad, pad + bracketLen), stroke)
                                        // Bottom-left bracket
                                        drawLine(bracketColor, Offset(pad, h - pad), Offset(pad + bracketLen, h - pad), stroke)
                                        drawLine(bracketColor, Offset(pad, h - pad), Offset(pad, h - pad - bracketLen), stroke)
                                        // Bottom-right bracket
                                        drawLine(bracketColor, Offset(w - pad, h - pad), Offset(w - pad - bracketLen, h - pad), stroke)
                                        drawLine(bracketColor, Offset(w - pad, h - pad), Offset(w - pad, h - pad - bracketLen), stroke)

                                        // Animated green/gold magic scan beam
                                        val scanY = h * scanLineProgress
                                        drawLine(
                                            brush = Brush.horizontalGradient(
                                                listOf(Color.Transparent, Color(0xFF10B981), Color(0xFFFDE047), Color(0xFF10B981), Color.Transparent)
                                            ),
                                            start = Offset(pad, scanY),
                                            end = Offset(w - pad, scanY),
                                            strokeWidth = 4f
                                        )
                                    }

                                    Surface(
                                        color = Color(0xFF0F172A).copy(alpha = 0.80f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "📄 Raw Camera Paper Capture",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                // Arrow indicator
                                Text("➡️", fontSize = 24.sp)

                                // Right: Live Extracted Transparent Puppet Cutout on Checkerboard/Stage Preview
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF1E1B4B), Color(0xFF312E81))
                                            )
                                        )
                                        .border(2.dp, Color(0xFFFDE047).copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = cutoutBitmap,
                                        contentDescription = "Extracted Character Cutout Puppet",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(18.dp)
                                    )

                                    Surface(
                                        color = Color(0xFFF59E0B),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "✨ Auto-Extracted 2D Puppet Cutout!",
                                            color = Color(0xFF0F172A),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 4 Built-in Kid Paper Drawing Camera Presets for 1-Tap Testing
                        Text(
                            text = "👧👦 Or tap a real kid's paper drawing below to test the camera scanner instantly:",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KidDrawingPreset.entries.forEach { preset ->
                                val isSelected = preset == selectedPreset
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFFDE047) else Color(0xFF334155)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            SoundEngine.playEffect(SoundEffectType.CAMERA_SCAN_WHOOSH)
                                            onSelectPreset(preset)
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(preset.emoji, fontSize = 22.sp)
                                        Text(
                                            text = preset.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = preset.kidArtistTag,
                                            color = Color(0xFFBAE6FD),
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // MODE 2: Interactive Finger & Crayon Drawing Canvas
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🖍️ Draw Your Own Character (or Add Wings/Crowns to Your Scan!)",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (customStrokes.isNotEmpty()) {
                                            customStrokes.removeAt(customStrokes.lastIndex)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("↩️ Undo", color = Color.White, fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        customStrokes.clear()
                                        // Start with blank warm paper
                                        val blankPaper = RgbaBuffer(360, 360).apply {
                                            for (i in pixels.indices) pixels[i] = 0xFFFAF7EE.toInt()
                                        }
                                        onNewPaperDrawingCaptured(blankPaper, selectedPreset, "Blank Crayon Canvas")
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("🧹 Clear Paper", color = Color(0xFFF87171), fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        val baked = bakeCustomStrokesOntoBuffer(
                                            baseBuffer = currentPaperBuffer,
                                            strokes = customStrokes,
                                            canvasSize = canvasSizePx
                                        )
                                        customStrokes.clear()
                                        SoundEngine.playEffect(SoundEffectType.MAGIC_WAND_CHIME)
                                        onNewPaperDrawingCaptured(baked, selectedPreset, "Updated Crayon Drawing!")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("✅ Apply Crayon Strokes to Puppet", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        // Crayon Color Picker Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            crayonPalette.forEach { (name, argb) ->
                                val isSelected = selectedBrushArgb == argb
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 34.dp else 28.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedBrushArgb = argb }
                                )
                            }

                            Spacer(Modifier.width(12.dp))
                            Text("Size:", color = Color.White, fontSize = 12.sp)
                            listOf("Fine" to 5f, "Medium" to 10f, "Chunky" to 16f).forEach { (label, sz) ->
                                FilterChip(
                                    selected = selectedBrushSize == sz,
                                    onClick = { selectedBrushSize = sz },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }

                        // Interactive Drawing Canvas
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFFFAF7EE))
                                .border(3.dp, Color(0xFFF59E0B), RoundedCornerShape(18.dp))
                                .pointerInput(selectedBrushArgb, selectedBrushSize) {
                                    detectTapGestures { tap ->
                                        customStrokes.add(
                                            CrayonStroke(
                                                points = listOf(tap, Offset(tap.x + 1f, tap.y + 1f)),
                                                colorArgb = selectedBrushArgb,
                                                radiusPx = selectedBrushSize
                                            )
                                        )
                                    }
                                }
                                .pointerInput(selectedBrushArgb, selectedBrushSize) {
                                    detectDragGestures(
                                        onDragStart = { startOffset ->
                                            currentStrokePoints = listOf(startOffset)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            currentStrokePoints = currentStrokePoints + change.position
                                        },
                                        onDragEnd = {
                                            if (currentStrokePoints.isNotEmpty()) {
                                                customStrokes.add(
                                                    CrayonStroke(
                                                        points = currentStrokePoints,
                                                        colorArgb = selectedBrushArgb,
                                                        radiusPx = selectedBrushSize
                                                    )
                                                )
                                                currentStrokePoints = emptyList()
                                            }
                                        }
                                    )
                                }
                        ) {
                            Image(
                                bitmap = paperBitmap,
                                contentDescription = "Drawing Paper Base",
                                modifier = Modifier.fillMaxSize()
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                canvasSizePx = size
                                val allStrokes = if (currentStrokePoints.isNotEmpty()) {
                                    customStrokes + CrayonStroke(currentStrokePoints, selectedBrushArgb, selectedBrushSize)
                                } else {
                                    customStrokes
                                }
                                for (stroke in allStrokes) {
                                    val c = Color(stroke.colorArgb)
                                    if (stroke.points.size == 1) {
                                        drawCircle(c, radius = stroke.radiusPx, center = stroke.points.first())
                                    } else {
                                        val path = Path().apply {
                                            moveTo(stroke.points.first().x, stroke.points.first().y)
                                            for (i in 1 until stroke.points.size) {
                                                lineTo(stroke.points[i].x, stroke.points[i].y)
                                            }
                                        }
                                        drawPath(
                                            path = path,
                                            color = c,
                                            style = Stroke(
                                                width = stroke.radiusPx * 2f,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // RIGHT PANEL: Story Spark Imagination Prompt & "BRING TO LIFE" Launchpad
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🪄 Step 2: Spark the Imagination Story!",
                        color = Color(0xFFFDE047),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )

                    val dominantColors = remember(extractedCutoutBuffer) {
                        DrawingProcessor.describeDominantColors(extractedCutoutBuffer)
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                bitmap = cutoutBitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E1B4B))
                            )
                            Column {
                                Text(
                                    text = "Detected Puppet Colors:",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = dominantColors.replaceFirstChar { it.uppercase() },
                                    color = Color(0xFF4ADE80),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = statusBanner,
                                    color = Color(0xFF7DD3FC),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = kidHintPrompt,
                        onValueChange = onKidHintChanged,
                        label = { Text("What is your character's name or dream adventure? (Optional)") },
                        placeholder = { Text("e.g., Barnaby flies to the Sugar Cloud Castle to find a magic star!") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedLabelColor = Color(0xFF38BDF8),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        maxLines = 2
                    )

                    Text(
                        text = "💡 Tap an Imagination Spark:",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        storyIdeaChips.forEach { idea ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF334155),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onKidHintChanged(idea)
                                    }
                            ) {
                                Text(
                                    text = idea,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Active AI Pipeline Summary Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "⚡ Active Animation & Story Pipeline: ${aiConfig.selectedProvider.displayName}",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "• Vision & 4-Scene Choreography: ${aiConfig.selectedProvider.visionModel}\n" +
                                    "• Storybook Art & Voice: ${aiConfig.selectedProvider.artModel} + ${aiConfig.selectedProvider.voiceModel}\n" +
                                    "• Real-Time 60 FPS Puppet Physics: Squash & Stretch + Parallax Biomes + Touch!",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    // BIG LAUNCH BUTTON
                    Button(
                        onClick = {
                            val finalPaper = if (customStrokes.isNotEmpty()) {
                                val baked = bakeCustomStrokesOntoBuffer(currentPaperBuffer, customStrokes, canvasSizePx)
                                customStrokes.clear()
                                onNewPaperDrawingCaptured(baked, selectedPreset, "Custom Crayon Drawing")
                                baked
                            } else {
                                currentPaperBuffer
                            }
                            SoundEngine.playEffect(SoundEffectType.FANFARE_CELEBRATION)
                            onBringToLifeClicked()
                        },
                        enabled = !isLoadingAnimation,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    ) {
                        if (isLoadingAnimation) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Animating Your Drawing...",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        } else {
                            Text(
                                text = "✨ BRING MY DRAWING TO LIFE! 🎬",
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun bakeCustomStrokesOntoBuffer(
    baseBuffer: RgbaBuffer,
    strokes: List<CrayonStroke>,
    canvasSize: Size
): RgbaBuffer {
    val out = baseBuffer.copy()
    val scaleX = out.width.toFloat() / canvasSize.width.coerceAtLeast(1f)
    val scaleY = out.height.toFloat() / canvasSize.height.coerceAtLeast(1f)

    for (stroke in strokes) {
        val rPx = (stroke.radiusPx * maxOf(scaleX, scaleY)).coerceAtLeast(2f)
        for (i in stroke.points.indices) {
            val p0 = stroke.points[i]
            val p1 = stroke.points.getOrElse(i + 1) { p0 }
            val x0 = p0.x * scaleX
            val y0 = p0.y * scaleY
            val x1 = p1.x * scaleX
            val y1 = p1.y * scaleY
            val steps = (hypot(x1 - x0, y1 - y0) * 1.5f).toInt().coerceAtLeast(1)
            for (s in 0..steps) {
                val t = s.toFloat() / steps
                val cx = (x0 + (x1 - x0) * t).toInt()
                val cy = (y0 + (y1 - y0) * t).toInt()
                val radInt = rPx.toInt()
                for (dy in -radInt..radInt) {
                    for (dx in -radInt..radInt) {
                        if (dx * dx + dy * dy <= rPx * rPx) {
                            out.setPixel(cx + dx, cy + dy, stroke.colorArgb)
                        }
                    }
                }
            }
        }
    }
    return out
}
