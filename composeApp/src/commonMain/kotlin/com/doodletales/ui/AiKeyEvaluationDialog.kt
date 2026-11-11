package com.doodletales.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.doodletales.model.AiConfig
import com.doodletales.model.AiProvider

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
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(2.dp, Color(0xFF38BDF8)),
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🔑 AI Model Evaluation & API Key Hub",
                            color = Color(0xFFFDE047),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Evaluating Gemini vs. Kimi vs. DeepSeek for Kids' Camera Drawing → Living Animation & Story",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("✖ Close", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // 3-Provider Comparison Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AiProvider.entries.forEach { provider ->
                        val isSelected = selectedProvider == provider
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF1E293B) else Color(0xFF090D16),
                            border = BorderStroke(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFFFDE047) else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedProvider = provider }
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    text = provider.displayName,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )

                                Text(
                                    text = "Fit Score: ${provider.overallScore}",
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
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
                }

                // Detailed Breakdown for Selected Provider
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📋 Why ${selectedProvider.displayName} (${selectedProvider.overallScore}):",
                            color = Color(0xFFFDE047),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        selectedProvider.strengths.forEach { s ->
                            Text("✅ $s", color = Color(0xFF4ADE80), fontSize = 12.sp)
                        }
                        selectedProvider.limitations.forEach { l ->
                            Text("ℹ️ $l", color = Color(0xFFFBBF24), fontSize = 12.sp)
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
                        Text("Enter ${selectedProvider.displayName} API Key (Leave blank for Instant 60 FPS Demo Mode)")
                    },
                    placeholder = {
                        Text(
                            when (selectedProvider) {
                                AiProvider.GEMINI -> "AIza... or AQ... (Gemini Interactions API Key)"
                                AiProvider.KIMI -> "sk-... (Moonshot Kimi API Key)"
                                AiProvider.DEEPSEEK -> "sk-... (DeepSeek API Key)"
                            }
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = generateNanoBanana,
                            onClick = { generateNanoBanana = !generateNanoBanana },
                            label = { Text("🎨 Nano Banana 2.1 Character Art Upgrade") }
                        )

                        FilterChip(
                            selected = generateTts,
                            onClick = { generateTts = !generateTts },
                            label = { Text("🎙️ Gemini 3.8 Flash TTS Storyteller") }
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
