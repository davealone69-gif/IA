package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PersonaEntity
import com.example.ui.screens.LlmGuideScreen
import com.example.ui.screens.ModelStudioScreen
import com.example.ui.screens.RoleplayChatScreen
import com.example.ui.screens.VideoMakerScreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkObsidian
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.viewmodel.AuraViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: AuraViewModel by viewModels()
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Android TextToSpeech engine for voice narration
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }

        setContent {
            MyApplicationTheme {
                AuraStudioApp(
                    viewModel = viewModel,
                    onSpeakText = { text -> speakText(text) }
                )
            }
        }
    }

    private fun speakText(text: String) {
        val cleanText = text.replace(Regex("\\*.*?\\*"), "") // remove action asterisks for audio
        if (cleanText.isNotBlank()) {
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "aura_tts_id")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

enum class AuraTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    STUDIO("Model Studio", Icons.Default.AutoAwesome),
    CHAT("Roleplay Chat", Icons.Default.ChatBubble),
    VIDEO("Video Maker", Icons.Default.Movie),
    GUIDE("LLM Guide", Icons.Default.Psychology)
}

@Composable
fun AuraStudioApp(
    viewModel: AuraViewModel,
    onSpeakText: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(AuraTab.STUDIO) }
    val context = LocalContext.current

    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val selectedPersona by viewModel.selectedPersona.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatGenerating by viewModel.isChatGenerating.collectAsStateWithLifecycle()

    val videoProjects by viewModel.videoProjects.collectAsStateWithLifecycle()
    val activeVideoProject by viewModel.activeVideoProject.collectAsStateWithLifecycle()
    val parsedScenes by viewModel.parsedScenes.collectAsStateWithLifecycle()
    val isVideoGenerating by viewModel.isVideoGenerating.collectAsStateWithLifecycle()
    val isAudioPlaying by viewModel.isAudioPlaying.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkObsidian,
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = TextPrimary,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("aura_bottom_nav_bar")
            ) {
                AuraTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) NeonMagenta else TextMuted
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelected) NeonMagenta else TextMuted,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = NeonMagenta.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            when (selectedTab) {
                AuraTab.STUDIO -> {
                    ModelStudioScreen(
                        personas = personas,
                        onSelectPersonaForChat = { persona ->
                            viewModel.selectPersona(persona)
                            selectedTab = AuraTab.CHAT
                        },
                        onSelectPersonaForVideo = { persona ->
                            viewModel.selectPersona(persona)
                            selectedTab = AuraTab.VIDEO
                        },
                        onCreateCustomModel = { name, title, category, personality, scenario, voiceStyle, systemPrompt, creativity ->
                            viewModel.createCustomPersona(
                                name, title, category, personality, scenario, voiceStyle, systemPrompt, creativity
                            )
                            Toast.makeText(context, "Model '$name' created & saved!", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteCustomModel = { id ->
                            viewModel.deletePersona(id)
                            Toast.makeText(context, "Custom model deleted", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                AuraTab.CHAT -> {
                    RoleplayChatScreen(
                        persona = selectedPersona ?: personas.firstOrNull(),
                        messages = chatMessages,
                        isGenerating = isChatGenerating,
                        onSendMessage = { text ->
                            if (selectedPersona == null && personas.isNotEmpty()) {
                                viewModel.selectPersona(personas.first())
                            }
                            viewModel.sendMessage(text)
                        },
                        onClearHistory = {
                            viewModel.clearChatHistory()
                            Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                        },
                        onPlayAudio = { text ->
                            onSpeakText(text)
                        }
                    )
                }

                AuraTab.VIDEO -> {
                    VideoMakerScreen(
                        personas = personas,
                        selectedPersona = selectedPersona ?: personas.firstOrNull(),
                        videoProjects = videoProjects,
                        activeProject = activeVideoProject,
                        scenes = parsedScenes,
                        isGenerating = isVideoGenerating,
                        isAudioPlaying = isAudioPlaying,
                        onGenerateVideo = { title, personaId, prompt, style, sceneCount ->
                            viewModel.generateVideoScript(title, personaId, prompt, style, sceneCount)
                        },
                        onSelectProject = { proj ->
                            viewModel.setActiveProject(proj)
                        },
                        onToggleAudio = {
                            viewModel.toggleAudioNarration()
                            val currentNarration = parsedScenes.firstOrNull()?.narrationText ?: ""
                            if (currentNarration.isNotEmpty()) {
                                onSpeakText(currentNarration)
                            }
                        },
                        onPlaySceneAudio = { narration ->
                            onSpeakText(narration)
                        }
                    )
                }

                AuraTab.GUIDE -> {
                    LlmGuideScreen()
                }
            }
        }
    }
}
