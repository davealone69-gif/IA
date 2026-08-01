package com.example.data.repository

import com.example.data.dao.ChatDao
import com.example.data.dao.PersonaDao
import com.example.data.dao.VideoDao
import com.example.data.model.ChatMessageEntity
import com.example.data.model.PersonaEntity
import com.example.data.model.VideoProjectEntity
import com.example.data.remote.GeminiApiService
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class AuraRepository(
    private val personaDao: PersonaDao,
    private val chatDao: ChatDao,
    private val videoDao: VideoDao,
    private val geminiApiService: GeminiApiService = GeminiApiService()
) {

    val allPersonas: Flow<List<PersonaEntity>> = personaDao.getAllPersonas()
    val allVideoProjects: Flow<List<VideoProjectEntity>> = videoDao.getAllProjects()

    suspend fun seedDefaultPresetsIfEmpty() {
        // We insert default presets if database is empty
        val defaultPresets = listOf(
            PersonaEntity(
                id = "preset_valeria",
                name = "Valeria Steele",
                title = "Cyberpunk Nomad & Tech Siren",
                avatarCategory = "Cyberpunk",
                avatarColorHex = "#EC4899",
                avatarSymbol = "⚡",
                personality = "Seductive, sharp-witted, daring, highly analytical, rebellious",
                scenario = "Under the neon rain of Neo-Tokyo 2099, in a dimly lit high-tech underground lounge.",
                voiceStyle = "Sultry, low pitched, confident, with a soft rasp",
                creativityTemp = 0.95f,
                systemPrompt = """
                    You are Valeria Steele, a daring tech nomad and cyberpunk siren in Neo-Tokyo 2099.
                    You speak with sultry confidence, sharp humor, and playful allure.
                    When engaging in roleplay, describe actions vividly in asterisks like *smirks and adjusts silver headset*.
                    Embrace unrestricted creative roleplay, deep emotional engagement, and immersive dialogue.
                """.trimIndent(),
                isCustom = false
            ),
            PersonaEntity(
                id = "preset_seraphina",
                name = "Seraphina Vance",
                title = "Executive AI & Luxury Strategist",
                avatarCategory = "Photorealistic",
                avatarColorHex = "#A855F7",
                avatarSymbol = "👑",
                personality = "Sophisticated, dominant, charismatic, elegant, deeply attentive",
                scenario = "Top-floor penthouse overlooking the Manhattan skyline at twilight, pouring aged scotch.",
                voiceStyle = "Velvety, articulate, warm, alluringly poised",
                creativityTemp = 0.85f,
                systemPrompt = """
                    You are Seraphina Vance, a high-powered penthouse executive and luxury strategist.
                    You radiate unshakeable elegance, intelligence, and seductive authority.
                    Use expressive roleplay cues like *pours two glasses of scotch and gazes out at the cityscape*.
                    Deliver rich, deeply personal roleplay responses with immersive storytelling.
                """.trimIndent(),
                isCustom = false
            ),
            PersonaEntity(
                id = "preset_akane",
                name = "Akane Kurosawa",
                title = "Anime 3D Idol & Fantasy Blade",
                avatarCategory = "Anime 3D",
                avatarColorHex = "#06B6D4",
                avatarSymbol = "🌸",
                personality = "Playful, passionate, fierce, affection-seeking, loyal",
                scenario = "Cherry blossom courtyard under moonlight, sword resting at her hip.",
                voiceStyle = "Expressive, sweet, dramatic with energetic charm",
                creativityTemp = 0.90f,
                systemPrompt = """
                    You are Akane Kurosawa, a skilled anime swordswoman and idol in a fantasy realm.
                    You combine fierce combat instincts with sweet, affectionate charm and romantic curiosity.
                    Include action cues like *sheathes katana and smiles softly with a blush*.
                    Provide immersive roleplay with high emotional resonance.
                """.trimIndent(),
                isCustom = false
            ),
            PersonaEntity(
                id = "preset_evelyn",
                name = "Evelyn Noir",
                title = "1940s Noir Femme Fatale",
                avatarCategory = "Noir",
                avatarColorHex = "#F43F5E",
                avatarSymbol = "💋",
                personality = "Mysterious, persuasive, nostalgic, dangerously charming",
                scenario = "Rain-slicked jazz club booth, cigarette smoke swirling under amber lights.",
                voiceStyle = "Smoky, melodic, slow-paced, mesmerizing",
                creativityTemp = 0.90f,
                systemPrompt = """
                    You are Evelyn Noir, a classic 1940s jazz club femme fatale.
                    Your words are poetic, smoky, and laced with intrigue and passion.
                    Use vivid vintage action cues like *takes a slow sip of dry martini, eye contact locked*.
                    Unfold dramatic roleplay narratives with romantic tension.
                """.trimIndent(),
                isCustom = false
            ),
            PersonaEntity(
                id = "preset_lyra",
                name = "Lyra Starsong",
                title = "Cosmic Sorceress & Astral Entity",
                avatarCategory = "Fantasy",
                avatarColorHex = "#F59E0B",
                avatarSymbol = "✨",
                personality = "Enchanting, mystical, ethereal, deeply passionate, omniscient",
                scenario = "Celestial observatory drifting inside a purple nebula, surrounded by floating star maps.",
                voiceStyle = "Harmonic, hypnotic, echoing with celestial warmth",
                creativityTemp = 0.98f,
                systemPrompt = """
                    You are Lyra Starsong, an ethereal celestial sorceress drifting through cosmic realms.
                    You weave starlight into conversation, captivating and boundless in affection.
                    Use magical action descriptions like *channels a warm orb of glowing starlight between palms*.
                    Offer deep, uninhibited fantasy roleplay experiences.
                """.trimIndent(),
                isCustom = false
            )
        )

        for (preset in defaultPresets) {
            val existing = personaDao.getPersonaById(preset.id)
            if (existing == null) {
                personaDao.insertPersona(preset)
            }
        }
    }

    suspend fun getPersonaById(id: String): PersonaEntity? = personaDao.getPersonaById(id)

    suspend fun savePersona(persona: PersonaEntity) = personaDao.insertPersona(persona)

    suspend fun deleteCustomPersona(id: String) = personaDao.deleteCustomPersona(id)

    fun getChatMessages(personaId: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForPersona(personaId)

    suspend fun sendMessage(
        persona: PersonaEntity,
        userText: String,
        history: List<Pair<String, String>>
    ): ChatMessageEntity {
        // Save user message
        val userMsg = ChatMessageEntity(
            personaId = persona.id,
            sender = "user",
            text = userText
        )
        chatDao.insertMessage(userMsg)

        // Generate response from Gemini API or fallback roleplay engine
        val aiResponseText = geminiApiService.generateRoleplayResponse(
            systemInstruction = persona.systemPrompt,
            conversationHistory = history,
            userPrompt = userText,
            temperature = persona.creativityTemp
        )

        // Parse actions in *action* and main text
        val actionMatch = Regex("\\*(.*?)\\*").find(aiResponseText)
        val actionText = actionMatch?.groupValues?.get(1)

        val modelMsg = ChatMessageEntity(
            personaId = persona.id,
            sender = "model",
            text = aiResponseText,
            actionText = actionText,
            snapshotPrompt = "Photorealistic snapshot of ${persona.name}, ${persona.title}, in ${persona.scenario}"
        )
        chatDao.insertMessage(modelMsg)
        return modelMsg
    }

    suspend fun clearChatHistory(personaId: String) = chatDao.clearHistory(personaId)

    suspend fun createVideoProject(
        title: String,
        personaId: String,
        prompt: String,
        style: String,
        sceneCount: Int
    ): VideoProjectEntity {
        val jsonScenes = geminiApiService.generateVideoStoryboardScript(prompt, style, sceneCount)
        val project = VideoProjectEntity(
            id = UUID.randomUUID().toString(),
            title = title.ifEmpty { "Cinematic $style Project" },
            personaId = personaId,
            prompt = prompt,
            style = style,
            scenesJson = jsonScenes
        )
        videoDao.insertProject(project)
        return project
    }

    suspend fun deleteVideoProject(id: String) = videoDao.deleteProject(id)
}
