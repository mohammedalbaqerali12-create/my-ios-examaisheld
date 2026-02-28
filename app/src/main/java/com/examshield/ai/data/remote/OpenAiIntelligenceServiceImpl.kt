package com.examshield.ai.data.remote

import com.examshield.ai.BuildConfig
import com.examshield.ai.domain.ai.AiIntelligenceService
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

interface OpenAiApi {
    @POST("v1/chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): ChatResponse
}

data class ChatRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<Message>,
    @SerializedName("max_tokens") val maxTokens: Int = 100,
    val temperature: Float = 0.5f
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

@Singleton
class OpenAiIntelligenceServiceImpl @Inject constructor() : AiIntelligenceService {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(OpenAiApi::class.java)

    override fun analyzeThreat(
        mac: String,
        name: String?,
        manufacturer: String?,
        rssi: Int,
        deviceType: String
    ): Flow<String> = flow {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            emit("LOCAL_ONLY")
            return@flow
        }

        val prompt = """
            [ASTRA V6 SURVEILLANCE CONTEXT]
            Determine if this signal belongs to an EXAM CHEATING category:
            - Target Categories: SMARTPHONE, SMARTWATCH, WIRELESS_EARBUD, NANO_EARPIECE.
            - Input Data: MAC: $mac | Name: ${name ?: "Unknown"} | Manufacturer: ${manufacturer ?: "Unknown"} | RSSI: $rssi dBm.
            
            STRICT RULES:
            1. If it's highly likely one of the target categories, reply EXCLUSIVELY with the Category Name and a short tactical reason (e.g., 'SMARTPHONE (APPLE_DNA)').
            2. If it's a generic signal (IoT, Bulb, PC, unknown), reply 'IGNORE_SIGNAL'.
            3. Max 5 words. Uppercase. Tactical tone.
        """.trimIndent()

        try {
            val response = api.getCompletion(
                auth = "Bearer $apiKey",
                request = ChatRequest(
                    messages = listOf(
                        Message(role = "system", content = "You are a tactical signal intelligence AI."),
                        Message(role = "user", content = prompt)
                    )
                )
            )
            emit(response.choices.firstOrNull()?.message?.content?.trim() ?: "AI_ANALYSIS_FAILED")
        } catch (e: Exception) {
            emit("AI_OFFLINE")
        }
    }
}
