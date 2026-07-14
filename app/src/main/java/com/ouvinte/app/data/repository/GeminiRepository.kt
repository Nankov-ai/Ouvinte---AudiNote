package com.ouvinte.app.data.repository

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.ouvinte.app.data.remote.GeminiAuthInterceptor
import com.ouvinte.app.data.remote.api.GeminiFilesApi
import com.ouvinte.app.data.remote.api.GeminiGenerateApi
import com.ouvinte.app.data.remote.dto.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val geminiFilesApi: GeminiFilesApi,
    private val geminiGenerateApi: GeminiGenerateApi,
    private val settingsRepository: SettingsRepository,
    private val authInterceptor: GeminiAuthInterceptor
) {
    companion object {
        private const val MODEL_TRANSCRIPTION = "gemini-3.5-flash"
        private const val MODEL_ANALYSIS = "gemini-3.5-flash"
        private const val MAX_INLINE_BYTES = 19 * 1024 * 1024L
        private const val AUDIO_MIME = "audio/mp4"
    }

    private suspend fun apiKey(): String {
        val key = settingsRepository.getGeminiApiKey()
        authInterceptor.updateKey(key)
        return key
    }

    // ── Transcrição ───────────────────────────────────────────────────────────

    suspend fun transcribeAudio(
        audioFile: File,
        existingFileUri: String = "",
        existingFileName: String = "",
        onProgress: (String) -> Unit = {},
        onFileUploaded: suspend (uri: String, name: String) -> Unit = { _, _ -> }
    ): Result<TranscriptionResult> = withContext(Dispatchers.IO) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ouvinte:transcription").also {
            it.acquire(90 * 60 * 1000L)
        }
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ouvinte:transcription").also {
            it.acquire()
        }
        try {
            runCatching {
                val prompt = buildTranscriptionPrompt()
                val responseText = if (audioFile.length() <= MAX_INLINE_BYTES) {
                    onProgress("A enviar áudio para transcrição…")
                    transcribeInline(audioFile, prompt)
                } else {
                    transcribeViaFilesApi(audioFile, prompt, existingFileUri, existingFileName, onProgress, onFileUploaded)
                }
                parseJson<TranscriptionResult>(responseText)
            }.also { result ->
                result.onFailure { e ->
                    Log.e("GeminiRepo", "transcribeAudio failed: ${e.javaClass.simpleName}: ${e.message}", e)
                }
            }
        } finally {
            if (wl.isHeld) wl.release()
            if (wifiLock.isHeld) wifiLock.release()
        }
    }

    private suspend fun transcribeInline(file: File, prompt: String): String {
        val model = GenerativeModel(
            modelName = MODEL_TRANSCRIPTION,
            apiKey = apiKey(),
            requestOptions = RequestOptions(timeout = 15 * 60 * 1000L)
        )
        val response = model.generateContent(
            content {
                blob(AUDIO_MIME, file.readBytes())
                text(prompt)
            }
        )
        return response.text ?: error("Gemini não devolveu texto na transcrição")
    }

    private suspend fun transcribeViaFilesApi(
        file: File,
        prompt: String,
        existingFileUri: String,
        existingFileName: String,
        onProgress: (String) -> Unit,
        onFileUploaded: suspend (uri: String, name: String) -> Unit
    ): String {
        val fileSizeMb = file.length() / (1024 * 1024)
        Log.d("GeminiRepo", "transcribeViaFilesApi: ${fileSizeMb}MB")

        // Reutiliza ficheiro já carregado se ainda estiver ACTIVE (evita 429 e custos)
        val (fileUri, fileName) = if (existingFileUri.isNotBlank() && existingFileName.isNotBlank()) {
            Log.d("GeminiRepo", "checkExistingFile: $existingFileName")
            val result = runCatching { geminiFilesApi.getFile(existingFileName) }
            val info = result.getOrNull()
            if (info?.state == "ACTIVE") {
                Log.d("GeminiRepo", "reusingFile: uri=$existingFileUri state=ACTIVE")
                onProgress("A usar áudio já carregado…")
                Pair(existingFileUri, existingFileName)
            } else {
                Log.d("GeminiRepo", "existingFile state=${info?.state}, re-uploading")
                val pair = uploadAndNotify(file, fileSizeMb, onProgress, onFileUploaded)
                pair
            }
        } else {
            uploadAndNotify(file, fileSizeMb, onProgress, onFileUploaded)
        }
        Log.d("GeminiRepo", "using file: uri=$fileUri name=$fileName")

        onProgress("A aguardar processamento do áudio…")
        val active = waitForFileActive(fileName)
        Log.d("GeminiRepo", "waitForFileActive=$active")
        if (!active) error("O ficheiro de áudio não ficou disponível no Gemini. Tenta novamente.")

        onProgress("A transcrever — pode demorar alguns minutos para gravações longas…")
        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(parts = listOf(
                    GeminiPart(fileData = GeminiFileData(AUDIO_MIME, fileUri)),
                    GeminiPart(text = prompt)
                ))
            ),
            generationConfig = GeminiGenerationConfig(temperature = 0.1f)
        )
        Log.d("GeminiRepo", "generateContent start (REST, model=$MODEL_TRANSCRIPTION)")
        val response = geminiGenerateApi.generateContent(MODEL_TRANSCRIPTION, request)
        val candidate = response.candidates?.firstOrNull()
        Log.d("GeminiRepo", "generateContent done, candidates=${response.candidates?.size} finishReason=${candidate?.finishReason}")
        val rawText = candidate?.content?.parts?.firstOrNull()?.text
        Log.d("GeminiRepo", "rawText length=${rawText?.length} preview=${rawText?.take(300)}")
        if (rawText.isNullOrBlank()) error("Gemini não devolveu texto (finishReason=${candidate?.finishReason})")
        return rawText
    }

    private suspend fun uploadAndNotify(
        file: File,
        fileSizeMb: Long,
        onProgress: (String) -> Unit,
        onFileUploaded: suspend (uri: String, name: String) -> Unit
    ): Pair<String, String> {
        onProgress("A carregar áudio (${fileSizeMb}MB)…")
        val pair = uploadFileToGemini(file)
        onFileUploaded(pair.first, pair.second)
        return pair
    }

    private suspend fun uploadFileToGemini(file: File): Pair<String, String> {
        Log.d("GeminiRepo", "uploadFile: ${file.length()} bytes")
        val body = file.readBytes().toRequestBody(AUDIO_MIME.toMediaType())
        apiKey()
        val response = geminiFilesApi.uploadFile(
            mimeType = AUDIO_MIME,
            contentLength = file.length(),
            body = body
        )
        Log.d("GeminiRepo", "uploadFile response: file=${response.file?.name}")
        val info = response.file ?: error("Falha ao carregar ficheiro (resposta vazia)")
        Log.d("GeminiRepo", "upload OK: uri=${info.uri} name=${info.name}")
        return Pair(info.uri, info.name)
    }

    private suspend fun waitForFileActive(fileName: String): Boolean {
        apiKey()
        repeat(60) { attempt ->
            delay(5000)
            val result = runCatching { geminiFilesApi.getFile(fileName) }
            val info = result.getOrNull()
            Log.d("GeminiRepo", "poll #$attempt state=${info?.state} err=${result.exceptionOrNull()?.message}")
            when (info?.state) {
                "ACTIVE" -> return true
                "FAILED" -> return false
            }
        }
        return false
    }

    // ── Tradução ──────────────────────────────────────────────────────────────

    suspend fun translateSegments(
        segments: List<com.ouvinte.app.domain.model.TranscriptSegment>
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val numbered = segments.mapIndexed { i, s -> "${i + 1}. ${s.text}" }.joinToString("\n")
            val prompt = """
                Traduz cada linha numerada para português europeu (pt-PT).
                Mantém exactamente o mesmo número de linhas e a numeração.
                Retorna APENAS as linhas traduzidas numeradas, sem mais nada.

                $numbered
            """.trimIndent()
            val result = generateText(MODEL_ANALYSIS, prompt)
            result.lines()
                .filter { it.matches(Regex("^\\d+\\..*")) }
                .map { it.replaceFirst(Regex("^\\d+\\.\\s*"), "") }
        }
    }

    // ── Análise ───────────────────────────────────────────────────────────────

    suspend fun analyseTranscript(
        transcript: String,
        searchResults: List<SearchItem>
    ): Result<AnalysisResult> = withContext(Dispatchers.IO) {
        runCatching {
            val sourcesText = if (searchResults.isNotEmpty()) {
                searchResults.joinToString("\n\n") {
                    "Título: ${it.title}\nURL: ${it.link}\nExcerto: ${it.snippet}"
                }
            } else {
                "Nenhuma fonte web disponível. Usa o teu conhecimento interno."
            }
            parseJson<AnalysisResult>(generateText(MODEL_ANALYSIS, buildAnalysisPrompt(transcript, sourcesText)))
        }
    }

    suspend fun extractTopics(transcript: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val prompt = """
                    Lê esta transcrição e extrai os 3 tópicos principais abordados.
                    Retorna APENAS um array JSON de strings em português europeu.
                    Exemplo: ["inteligência artificial", "mercado de trabalho", "produtividade"]

                    Transcrição:
                    $transcript
                """.trimIndent()
                val text = generateText(MODEL_ANALYSIS, prompt)
                gson.fromJson(cleanJson(text), Array<String>::class.java).toList()
            }
        }

    suspend fun generateQuestions(
        transcript: String,
        speakers: List<SpeakerResult>,
        batch: Int = 1
    ): Result<QuestionsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val speakersDesc = speakers.joinToString("\n") {
                "- ${it.name} (id: ${it.id})${if (it.isMain) " — orador principal" else ""}"
            }
            parseJson<QuestionsResult>(generateText(MODEL_ANALYSIS, buildQuestionsPrompt(transcript, speakersDesc, batch)))
        }
    }

    // SDK lida com qualquer formato de chave — não usar REST para texto
    private suspend fun generateText(model: String, prompt: String): String {
        val sdkModel = GenerativeModel(modelName = model, apiKey = apiKey())
        val response = sdkModel.generateContent(content { text(prompt) })
        return response.text ?: error("Gemini não devolveu texto")
    }

    // ── Prompts ───────────────────────────────────────────────────────────────

    private fun buildTranscriptionPrompt() = """
        Transcreve este áudio fielmente no idioma original dos oradores — não traduzas.
        Identifica os diferentes oradores e rotula-os como "A", "B", "C", etc.
        O orador que mais fala (orador principal / palestrante) deve ter isMain=true.

        Retorna EXCLUSIVAMENTE o seguinte JSON sem texto adicional:
        {
          "speakers": [
            {"id": "A", "name": "Palestrante A", "isMain": true},
            {"id": "B", "name": "Orador B", "isMain": false}
          ],
          "segments": [
            {"speakerId": "A", "text": "texto transcrito...", "timestamp": "00:00:05"}
          ]
        }
    """.trimIndent()

    private fun buildAnalysisPrompt(transcript: String, sourcesText: String) = """
        Analisa a seguinte transcrição de uma palestra em português europeu.

        TRANSCRIÇÃO:
        $transcript

        RESULTADOS DE PESQUISA NA WEB:
        $sourcesText

        Retorna EXCLUSIVAMENTE este JSON:
        {
          "topics": ["tópico1", "tópico2", "tópico3"],
          "summary": "Resumo detalhado do que foi apresentado (3-4 parágrafos em português europeu).",
          "factCheck": [
            {
              "claim": "Afirmação específica feita pelo palestrante",
              "verdict": "VERIFIED",
              "evidence": "Evidência que confirma ou refuta a afirmação."
            }
          ],
          "sources": [
            {"title": "Título da fonte", "url": "https://...", "description": "Relevância para o tema"}
          ]
        }

        verdict deve ser: VERIFIED, UNVERIFIED, ou INCORRECT.
        Inclui apenas afirmações factuais verificáveis (máx. 5).
    """.trimIndent()

    private fun buildQuestionsPrompt(transcript: String, speakersDesc: String, batch: Int) = """
        Com base nesta transcrição de palestra, gera exatamente 3 perguntas inteligentes.
        ${if (batch > 1) "IMPORTANTE: Este é o lote $batch — as perguntas devem ser completamente diferentes das anteriores." else ""}

        ORADORES:
        $speakersDesc

        TRANSCRIÇÃO:
        $transcript

        Gera perguntas variadas usando estes tipos:
        - CHALLENGE: questionar pressupostos ou afirmações do palestrante
        - FUTURE: explorar implicações futuras do tema
        - DEEPEN: pedir mais detalhe sobre um ponto específico

        Retorna EXCLUSIVAMENTE este JSON:
        {
          "questions": [
            {
              "text": "Pergunta em português europeu direto ao orador?",
              "speakerId": "A",
              "type": "CHALLENGE"
            }
          ]
        }

        As perguntas devem ser curtas (máx. 2 linhas), diretas e baseadas no conteúdo real da transcrição.
    """.trimIndent()

    private inline fun <reified T> parseJson(raw: String): T =
        gson.fromJson(cleanJson(raw), T::class.java)

    private fun cleanJson(text: String): String {
        val stripped = text.trim()
            .replace(Regex("^```(?:json)?\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("```\\s*$", RegexOption.MULTILINE), "")
            .trim()
        val jsonStart = stripped.indexOfFirst { it == '{' || it == '[' }
        val jsonEnd = stripped.indexOfLast { it == '}' || it == ']' }
        return if (jsonStart >= 0 && jsonEnd > jsonStart)
            stripped.substring(jsonStart, jsonEnd + 1)
        else stripped
    }
}
