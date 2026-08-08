package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AuraDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.PersonaEntity
import com.example.data.model.VideoProjectEntity
import com.example.data.model.VideoSceneItem
import com.example.data.repository.AuraRepository
import com.example.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID

class AuraViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AuraDatabase.getDatabase(application)
    private val repository = AuraRepository(db.personaDao(), db.chatDao(), db.videoDao())

    val personas: StateFlow<List<PersonaEntity>> = repository.allPersonas.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val videoProjects: StateFlow<List<VideoProjectEntity>> = repository.allVideoProjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active Selected Persona for Chat
    private val _selectedPersona = MutableStateFlow<PersonaEntity?>(null)
    val selectedPersona: StateFlow<PersonaEntity?> = _selectedPersona.asStateFlow()

    // Chat History for Active Persona
    private val _chatMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageEntity>> = _chatMessages.asStateFlow()

    // Chat Loading state
    private val _isChatGenerating = MutableStateFlow(false)
    val isChatGenerating: StateFlow<Boolean> = _isChatGenerating.asStateFlow()

    // Video Generator state
    private val _isVideoGenerating = MutableStateFlow(false)
    val isVideoGenerating: StateFlow<Boolean> = _isVideoGenerating.asStateFlow()

    private val _activeVideoProject = MutableStateFlow<VideoProjectEntity?>(null)
    val activeVideoProject: StateFlow<VideoProjectEntity?> = _activeVideoProject.asStateFlow()

    private val _parsedScenes = MutableStateFlow<List<VideoSceneItem>>(emptyList())
    val parsedScenes: StateFlow<List<VideoSceneItem>> = _parsedScenes.asStateFlow()

    // Audio narration simulation state
    private val _isAudioPlaying = MutableStateFlow(false)
    val isAudioPlaying: StateFlow<Boolean> = _isAudioPlaying.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedDefaultPresetsIfEmpty()
        }
    }

    fun selectPersona(persona: PersonaEntity) {
        _selectedPersona.value = persona
        viewModelScope.launch {
            repository.getChatMessages(persona.id).collect { messages ->
                _chatMessages.value = messages
            }
        }
    }

    fun sendMessage(userText: String) {
        val persona = _selectedPersona.value ?: return
        if (userText.isBlank() || _isChatGenerating.value) return

        viewModelScope.launch {
            _isChatGenerating.value = true
            try {
                val currentHistory = _chatMessages.value.map { it.sender to it.text }
                repository.sendMessage(persona, userText, currentHistory)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isChatGenerating.value = false
            }
        }
    }

    fun clearChatHistory() {
        val persona = _selectedPersona.value ?: return
        viewModelScope.launch {
            repository.clearChatHistory(persona.id)
        }
    }

    fun createCustomPersona(
        name: String,
        title: String,
        category: String,
        personality: String,
        scenario: String,
        voiceStyle: String,
        systemPrompt: String,
        creativity: Float
    ) {
        viewModelScope.launch {
            val colorHex = when (category) {
                "Photorealistic" -> "#EC4899"
                "Cyberpunk" -> "#06B6D4"
                "Anime 3D" -> "#F43F5E"
                "Noir" -> "#A855F7"
                else -> "#F59E0B"
            }
            val symbol = when (category) {
                "Photorealistic" -> "💋"
                "Cyberpunk" -> "⚡"
                "Anime 3D" -> "🌸"
                "Noir" -> "🍸"
                else -> "✨"
            }
            val newPersona = PersonaEntity(
                id = "custom_" + UUID.randomUUID().toString().take(8),
                name = name.ifEmpty { "Custom Persona" },
                title = title.ifEmpty { "AI Female Model" },
                avatarCategory = category,
                avatarColorHex = colorHex,
                avatarSymbol = symbol,
                personality = personality.ifEmpty { "Charismatic, expressive, attentive" },
                scenario = scenario.ifEmpty { "Sleek modern lounge scene" },
                voiceStyle = voiceStyle.ifEmpty { "Smooth, expressive voice" },
                creativityTemp = creativity,
                systemPrompt = systemPrompt.ifEmpty {
                    "You are $name, a $title. You are engaging in creative, deep roleplay dialogue. Use asterisks for actions."
                },
                isCustom = true
            )
            repository.savePersona(newPersona)
            selectPersona(newPersona)
        }
    }

    fun deletePersona(personaId: String) {
        viewModelScope.launch {
            repository.deleteCustomPersona(personaId)
            if (_selectedPersona.value?.id == personaId) {
                _selectedPersona.value = null
            }
        }
    }

    fun generateVideoScript(
        title: String,
        personaId: String,
        prompt: String,
        style: String,
        sceneCount: Int
    ) {
        viewModelScope.launch {
            _isVideoGenerating.value = true
            try {
                val project = repository.createVideoProject(title, personaId, prompt, style, sceneCount)
                setActiveProject(project)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isVideoGenerating.value = false
            }
        }
    }

    fun setActiveProject(project: VideoProjectEntity) {
        _activeVideoProject.value = project
        _parsedScenes.value = parseScenesFromJson(project.scenesJson)
    }

    fun toggleAudioNarration() {
        _isAudioPlaying.value = !_isAudioPlaying.value
    }

    private fun parseScenesFromJson(jsonStr: String): List<VideoSceneItem> {
        val list = mutableListOf<VideoSceneItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    VideoSceneItem(
                        sceneNumber = obj.optInt("sceneNumber", i + 1),
                        shotType = obj.optString("shotType", "Medium Shot"),
                        visualPrompt = obj.optString("visualPrompt", "Cinematic keyframe render"),
                        narrationText = obj.optString("narrationText", "Narrator line..."),
                        cameraMotion = obj.optString("cameraMotion", "Slow Zoom"),
                        audioMood = obj.optString("audioMood", "Atmospheric Ambient")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
