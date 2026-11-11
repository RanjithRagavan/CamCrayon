package com.ztoais.CamCrayon.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ztoais.CamCrayon.model.AiConfig
import com.ztoais.CamCrayon.model.AiProvider

@Composable
fun AiKeyEvaluationDialog(
    currentConfig: AiConfig,
    onSaveConfig: (AiConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedProvider by remember { mutableStateOf(currentConfig.selectedProvider) }
    var geminiKey by remember { mutableStateOf(currentConfig.geminiApiKey) }
    var kimiKey by remember { mutableStateOf(currentConfig.kimiApiKey) }
    var deepseekKey by remember { mutableStateOf(currentConfig.deepseekApiKey) }
    var generateNanoBanana by remember { mutableStateOf(currentConfig.generateNanoBananaSprite) }
    var generateTts by remember { mutableStateOf(currentConfig.generateTtsNarration) }
    var selectedVoice by remember { mutableStateOf(currentConfig.storytellerVoice) }
    var showKeyText by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            val isCompact = maxWidth < 720.dp

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(2.dp, Color(0xFF38BDF8)),
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxWidth(0.95f)
                    .heightIn(max = maxHeight * 0.92f)
                    .padding(if (isCompact) 8.dp else 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(if (isCompact) 14.dp else 20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔑 AI Model & API Key Hub",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.Black,
                                fontSize = if (isCompact) 17.sp else 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Evaluating Gemini vs. Kimi vs. DeepSeek for Living Drawings",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = onDismiss) {
                            Text("✖ Close", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    @Composable
                    fun ProviderCard(provider: AiProvider, modifier: Modifier) {
                        val isSelected = selectedProvider == provider
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) Color(0xFF1E293B) else Color(0xFF090D16),
                            border = BorderStroke(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFFFDE047) else Color(0xFF334155)
                            ),
                            modifier = modifier.clickable { selectedProvider = provider }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (provider == AiProvider.GEMINI) Color(0xFF10B981) else Color(0xFF334155),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = provider.badge,
                                            color = if (provider == AiProvider.GEMINI) Color(0xFF0F172A) else Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    Text(
                                        text = provider.overallScore,
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                Text(
                                    text = provider.displayName,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )

                                Text(
                                    text = provider.whyFitSummary,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    // 3-Provider Comparison Cards (Stacked vertically on portrait phone, 3 columns on desktop)
                    if (isCompact) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AiProvider.entries.forEach { provider ->
                                ProviderCard(provider = provider, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AiProvider.entries.forEach { provider ->
                                ProviderCard(provider = provider, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // Detailed Breakdown for Selected Provider
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "📋 Why ${selectedProvider.displayName} (${selectedProvider.overallScore}):",
                                color = Color(0xFFFDE047),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                            selectedProvider.strengths.forEach { s ->
                                Text("✅ $s", color = Color(0xFF4ADE80), fontSize = 11.sp, lineHeight = 15.sp)
                            }
                            selectedProvider.limitations.forEach { l ->
                                Text("ℹ️ $l", color = Color(0xFFFBBF24), fontSize = 11.sp, lineHeight = 15.sp)
                            }
                        }
                    }

                    // API Key Input for the Selected Provider
                    val currentKeyValue = when (selectedProvider) {
                        AiProvider.GEMINI -> geminiKey
                        AiProvider.KIMI -> kimiKey
                        AiProvider.DEEPSEEK -> deepseekKey
                    }

                    OutlinedTextField(
                        value = currentKeyValue,
                        onValueChange = { newVal ->
                            when (selectedProvider) {
                                AiProvider.GEMINI -> geminiKey = newVal
                                AiProvider.KIMI -> kimiKey = newVal
                                AiProvider.DEEPSEEK -> deepseekKey = newVal
                            }
                        },
                        label = {
                            Text(
                                "Enter ${selectedProvider.displayName} API Key (Blank = Demo Mode)",
                                fontSize = 11.sp
                            )
                        },
                        placeholder = {
                            Text(
                                when (selectedProvider) {
                                    AiProvider.GEMINI -> "AIza... (Gemini API Key)"
                                    AiProvider.KIMI -> "sk-... (Moonshot Kimi API Key)"
                                    AiProvider.DEEPSEEK -> "sk-... (DeepSeek API Key)"
                                },
                                fontSize = 12.sp
                            )
                        },
                        visualTransformation = if (showKeyText) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showKeyText = !showKeyText }) {
                                Text(if (showKeyText) "Hide" else "Show", color = Color(0xFF38BDF8))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFDE047),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedLabelColor = Color(0xFFFDE047),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    // Extra Gemini Interactions API Toggles (Nano Banana 2.1 + Gemini 3.8 Flash TTS)
                    if (selectedProvider == AiProvider.GEMINI) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = generateNanoBanana,
                                onClick = { generateNanoBanana = !generateNanoBanana },
                                label = { Text("🎨 Nano Banana 2.1 Art", fontSize = 11.sp) }
                            )

                            FilterChip(
                                selected = generateTts,
                                onClick = { generateTts = !generateTts },
                                label = { Text("🎙️ Gemini 3.8 TTS", fontSize = 11.sp) }
                            )

                            Text("Voice:", color = Color.White, fontSize = 12.sp)
                            listOf("Kore", "Puck", "Aoede").forEach { v ->
                                FilterChip(
                                    selected = selectedVoice == v,
                                    onClick = { selectedVoice = v },
                                    label = { Text(v, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                onSaveConfig(
                                    AiConfig(
                                        selectedProvider = selectedProvider,
                                        geminiApiKey = geminiKey,
                                        kimiApiKey = kimiKey,
                                        deepseekApiKey = deepseekKey,
                                        generateNanoBananaSprite = generateNanoBanana,
                                        generateTtsNarration = generateTts,
                                        storytellerVoice = selectedVoice
                                    )
                                )
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("💾 Save AI Settings", color = Color(0xFF0F172A), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}
