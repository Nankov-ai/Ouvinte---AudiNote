# Ouvinte — Guia do Projeto

App Android nativa em Kotlin para gravar palestras, webinars e masterclasses, transcrever com IA e gerar perguntas inteligentes para fazer ao palestrante.

## Stack

- **Linguagem**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **Arquitetura**: MVVM + Clean Architecture
- **DI**: Hilt
- **Base de dados**: Room
- **Rede**: Retrofit + OkHttp
- **IA**: Gemini (transcrição + análise + perguntas) — ver modelos abaixo
- **Pesquisa web**: Google Custom Search API
- **Min SDK**: 31 (Android 12) | **Target/Compile SDK**: 36 (Android 16)
- **Dispositivo de desenvolvimento**: Google Pixel 8 Pro (Android 16)
- **Idioma**: Português Europeu (pt-PT)

## Modelos Gemini por função

| Função | Modelo | Razão |
|--------|--------|-------|
| Transcrição de áudio (inline ≤19MB) | `gemini-3.5-flash` via SDK | SDK gere autenticação e timeout |
| Transcrição de áudio (Files API >19MB) | `gemini-3.5-flash` via REST | OkHttp timeout 15min; SDK tem timeout fixo 80s incompatível |
| Análise + fact-check + perguntas | `gemini-3.5-flash` via SDK | Único modelo confirmado funcionar nesta chave API |

**Nota importante**: modelos como `gemini-1.5-flash`, `gemini-2.0-flash`, `gemini-3.1-flash-lite`, `gemini-3-flash-preview` devolvem 404 nesta chave. Usar sempre `gemini-3.5-flash`.

## Funcionalidades

1. **Gravar** — grava o palestrante via `MediaRecorder` com `VOICE_RECOGNITION` audio source (cancelamento de ruído ativo). Waveform em tempo real via polling de `maxAmplitude`. Foreground service com notificação monochrome. **Auto-split**: ao atingir 17MB, a gravação pára e recomeça automaticamente numa nova sessão sem intervenção do utilizador. Todas as partes são agrupadas numa pasta criada automaticamente. Alerta ao atingir 3 horas no total. O utilizador pode também fazer stop manual a qualquer momento.
2. **Transcrição** — pós-gravação via Gemini com diarização de oradores (Orador A, B, C…). Ficheiros ≤19MB inline via SDK, >19MB via Gemini Files API (upload → poll ACTIVE → generateContent REST). WakeLock + WifiLock ativos durante toda a transcrição.
3. **Tradução** — botão "Traduzir para PT" na tab Transcrição. Traduz segmento a segmento para pt-PT via Gemini. Toggle "Ver original" para reverter.
4. **Análise** — extração de tópicos → pesquisa Google Custom Search → Gemini gera resumo + fact-check (VERIFIED / UNVERIFIED / INCORRECT) + fontes clicáveis.
5. **Perguntas** — Gemini gera 3 perguntas por lote (CHALLENGE / FUTURE / DEEPEN), organizadas por orador. Botão "+3" até 5 lotes (15 perguntas total).
6. **Exportar PDF** — transcrição + análise + perguntas exportados via `android.graphics.pdf.PdfDocument` (sem dependências externas).
7. **Histórico** — sessões persistidas em Room DB com nome automático (data/hora). Long press para eliminar.
8. **Retry de transcrição** — botão "Tentar novamente" em sessões que falharam (áudio guardado localmente). Sessões presas em `TRANSCRIBING` sem segmentos são automaticamente repostas para `RECORDED` ao abrir.
9. **Persistência total** — transcrição, análise e perguntas guardadas automaticamente na Room DB a cada passo. Dados disponíveis após fechar/reiniciar a app.
10. **PIN de acesso** — PIN de 4 dígitos configurado na primeira abertura. Pedido a cada abertura da app.
11. **Gestão de API keys** — ecrã de configuração acessível via ⚙️ na HomeScreen. BuildConfig (local.properties) tem sempre prioridade sobre DataStore. Botão "Testar chave Gemini" valida a chave via SDK.
12. **Tabs na sessão** — ecrã de sessão tem tabs "Transcrição" e "Perguntas". Texto seleccionável. Botões PDF, Partilhar e Partilhar Áudio no topo.
13. **Recuperar gravações** — ícone de pasta na HomeScreen escaneia `filesDir/recordings/` e lista ficheiros `.m4a` sem sessão correspondente. Toque importa e cria sessão.
14. **Cache de ficheiro Gemini** — após upload bem-sucedido, o URI do ficheiro é guardado na sessão (`geminiFileUri`, `geminiFileName`). Retries reutilizam o ficheiro em vez de re-fazer upload (ficheiros duram 48h na Files API).
15. **Pastas** — entidade `Project` na Room DB (versão 3). HomeScreen mostra pastas no topo e sessões sem pasta abaixo. Criar pasta via ícone na TopAppBar. Long press numa sessão → mover para pasta. Tap na pasta → ProjectScreen com sessões da pasta.
16. **PDF unificado de pasta** — ProjectScreen tem botão PDF que exporta todas as sessões da pasta num único documento ordenado por data.
17. **Partilhar áudio** — botão 🎵 na SessionScreen partilha o ficheiro `.m4a` via Android share intent (para NotebookLM, Google Drive, etc.).

## Estrutura de ficheiros

```
app/src/main/java/com/ouvinte/app/
├── audio/
│   ├── AudioRecorder.kt         — gravação + waveform + fileSizeMb StateFlow
│   └── RecordingService.kt      — foreground service (notificação durante gravação)
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt       — versão 3; MIGRATION_1_2, MIGRATION_2_3
│   │   ├── dao/SessionDao.kt
│   │   ├── dao/ProjectDao.kt    — CRUD projectos + assign session
│   │   └── entity/Entities.kt   — Project, Session, Speaker, TranscriptSegment, Analysis, Question
│   ├── remote/
│   │   ├── GeminiAuthInterceptor.kt  — adiciona ?key= a todos os pedidos REST Gemini
│   │   ├── api/                 — GeminiFilesApi, GeminiGenerateApi, GoogleSearchApi
│   │   └── dto/Dtos.kt          — todos os DTOs de request/response (incl. finishReason)
│   └── repository/
│       ├── GeminiRepository.kt  — transcrição + análise + perguntas + tradução
│       ├── ProjectRepository.kt — CRUD pastas + assign sessões + getFullSessionsForProject
│       ├── SearchRepository.kt  — Google Custom Search
│       ├── SessionRepository.kt — CRUD sessões + mappers domain
│       └── SettingsRepository.kt — DataStore: keys + PIN
├── di/AppModule.kt              — Hilt: OkHttpClient search/gemini, ProjectDao, migrations
├── domain/model/Models.kt       — Project, Session (incl. projectId), Speaker, etc.
├── presentation/
│   ├── analysis/                — AnalysisScreen + AnalysisViewModel
│   ├── home/                    — HomeScreen + HomeViewModel (pastas + sessões + órfãs)
│   ├── navigation/NavGraph.kt   — fluxo: PIN → Home; rotas Session, Project, Analysis, Questions
│   ├── pin/                     — PinScreen + PinViewModel
│   ├── project/                 — ProjectScreen + ProjectViewModel (sessões da pasta + PDF unificado)
│   ├── questions/               — QuestionsScreen + QuestionsViewModel
│   ├── recording/               — RecordingScreen + RecordingViewModel (auto-split)
│   ├── session/                 — SessionScreen + SessionViewModel (tabs + export + tradução + áudio)
│   ├── settings/                — SettingsScreen + SettingsViewModel (keys + teste)
│   └── theme/Theme.kt           — dark/light theme, cores azul/teal
└── util/PdfExporter.kt          — export(session) + exportProject(sessions)
```

## Distribuição pública

- **Nome público**: AudiNote
- **APK**: `APP Android/AudiNote-v1.0.0.apk` (pasta no projeto, excluída do git)
- **Flavor `distribution`**: chaves de API vazias — utilizador final introduz as suas próprias chaves no ecrã ⚙️
- **Flavor `dev`**: usa chaves do `local.properties` (desenvolvimento)
- **Compilar APK de distribuição**: `./gradlew assembleDistributionRelease` (com `JAVA_HOME` apontado para `C:\Program Files\Android\Android Studio\jbr`)
- **Keystore**: `app/audinate-release.keystore` — credenciais em `local.properties` (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Guardar em local seguro — necessário para assinar atualizações futuras.

## APIs e chaves

Em `local.properties` (não commitar). **BuildConfig tem sempre prioridade sobre DataStore.**

| Campo | Serviço |
|-------|---------|
| `GEMINI_API_KEY` | Google AI Studio — chave no formato `AIzaSy...` (REST e SDK). Chaves `AQ.` só funcionam no SDK, não no REST. |
| `GOOGLE_SEARCH_API_KEY` | Google Cloud Console → Credenciais (formato `AIzaSy...`) |
| `GOOGLE_SEARCH_ENGINE_ID` | programmablesearchengine.google.com — copiar só o valor após `cx=` |
| `KEYSTORE_FILE` | Caminho para `audinate-release.keystore` (relativo a `app/`) |
| `KEYSTORE_PASSWORD` | Palavra-passe do keystore |
| `KEY_ALIAS` | `audinate` |
| `KEY_PASSWORD` | Palavra-passe da chave |

## Decisões técnicas importantes

- **`VOICE_RECOGNITION` audio source** — cancelamento de ruído nativo do Android, melhor precisão de transcrição vs `MIC`. Consequência: não capta áudio da coluna do próprio smartphone (esse som é tratado como ruído de fundo e eliminado). Para gravar webinars no smartphone, o cenário recomendado é ver o webinar no computador e usar o Pixel ao lado.
- **Captura de áudio interno (não implementada)** — `MediaProjection` + `AudioPlaybackCaptureConfiguration` (Android 10+) permitiria captar áudio do sistema, mas apps de webinar (Zoom, Teams, YouTube) bloqueiam-na via `allowAudioPlaybackCapture=false`. Decidido não implementar.
- **Gemini Files API para áudio >19MB** — upload → poll a cada 5s (máx. 5 min) → `generateContent` via REST (não SDK). O SDK Gemini tem timeout fixo de 80s internamente (Ktor) que não é configurável via `RequestOptions`; o OkHttp do Retrofit tem 15min. `getFile()` devolve `GeminiFileInfo` directamente (sem wrapper `"file"`).
- **Modelos disponíveis nesta chave** — apenas `gemini-3.5-flash` confirmado. `gemini-1.5-flash`, `gemini-2.0-flash`, `gemini-3.1-flash-lite`, `gemini-3-flash-preview` devolvem 404.
- **WakeLock + WifiLock durante transcrição** — `PARTIAL_WAKE_LOCK` (90 min) + `WIFI_MODE_FULL_HIGH_PERF` adquiridos em `GeminiRepository.transcribeAudio()`, libertados no `finally`. O WakeLock evita suspensão de CPU; o WifiLock evita que o Android suspenda o WiFi quando o ecrã desliga — sem ele, o `generateContent` falha com `UnknownHostException`. Permissão `ACCESS_WIFI_STATE` necessária no manifesto.
- **Cache de URI Gemini** — `geminiFileUri` e `geminiFileName` guardados na sessão após upload. Retries verificam se o ficheiro ainda está `ACTIVE` antes de re-fazer upload, evitando erros 429 (rate limit).
- **Erro 429 Files API** — rate limit por minuto. Esperar 5-10 min. Ficheiros permanecem disponíveis 48h após upload.
- **Transcrição no idioma original** — o prompt instrui Gemini a transcrever fielmente no idioma dos oradores sem traduzir. Tradução para pt-PT é separada e opcional (botão na UI).
- **Notificação de gravação monochrome** — `ic_notification_recording.xml` sem `android:tint` (causa erro de build).
- **`cleanJson` com regex** — extrai JSON válido mesmo com markdown extra do Gemini. Necessário porque sem `responseMimeType` o modelo pode envolver o JSON em blocos de código markdown.
- **`responseMimeType` removido da transcrição** — `GeminiGenerationConfig.responseMimeType` é `String? = null` (Gson omite campos null por defeito). O modo JSON forçado (`"application/json"`) limita o output a ~2000 tokens neste modelo, causando `finishReason=MAX_TOKENS` e JSON truncado. Sem este campo, o modelo produz o output completo com `maxOutputTokens=65536`. O prompt já instrui a devolver JSON — `cleanJson` trata o markdown extra.
- **Gravações até 2 horas** suportadas (~43MB a 48kbps AAC).
- **Retry de transcrição** — o áudio fica sempre guardado localmente; se a transcrição falhar, o botão "Tentar novamente" reaparece na SessionScreen.
- **Recuperação de sessões interrompidas** — `SessionViewModel.loadSession()` detecta sessões com status `TRANSCRIBING` sem segmentos e repõe para `RECORDED` automaticamente.
- **BuildConfig priority** — `SettingsRepository.getGeminiApiKey()` usa BuildConfig primeiro; DataStore só como override quando BuildConfig está vazio.
- **Migration Room v1→v2** — `MIGRATION_1_2` adiciona `geminiFileUri` e `geminiFileName` à tabela `sessions`. Nunca usar só `fallbackToDestructiveMigration` sem migration explícita — apaga todos os dados.
- **Migration Room v2→v3** — `MIGRATION_2_3` cria tabela `projects` e adiciona coluna `projectId INTEGER DEFAULT NULL` à tabela `sessions`.
- **Auto-split de gravação** — `AudioRecorder` expõe `fileSizeMb: StateFlow<Float>`. `RecordingViewModel` monitoriza: ao atingir 17MB, para silenciosamente, guarda a sessão como `RECORDED`, cria um projecto automático (na primeira divisão), e inicia nova gravação. `Mutex` evita re-entrada. Ao atingir 3h no total, para e alerta o utilizador. Stop manual funciona sempre — se houve splits navega para a pasta, senão transcreve imediatamente.
- **`responseMimeType` removido da transcrição** — `GeminiGenerationConfig.responseMimeType` é `String? = null` (Gson omite campos null por defeito). O modo JSON forçado limita o output a ~2000 tokens neste modelo, causando `finishReason=MAX_TOKENS`. Sem este campo, o modelo produz output completo com `maxOutputTokens=65536`. O prompt já instrui a devolver JSON — `cleanJson` trata o markdown extra.
- **PDF paginação com `PageState`** — `PdfExporter` usa uma classe `PageState` que encapsula `canvas`, `page` e `y`. O método `drawWrapped` verifica `checkNewPage` antes de cada linha individual, evitando texto cortado no fundo das páginas. Bug anterior: `drawWrappedText` desenhava linhas sem verificar limites de página — só o bloco inteiro era verificado com estimativa fixa que não cobria textos longos.

## Versões Gradle (libs.versions.toml)

- AGP: 9.2.1
- Kotlin: 2.2.10
- KSP: 2.3.2
- Hilt: 2.56 (mínimo para suportar Kotlin metadata 2.2.0 gerado pelo Gradle 9.4.1)
- Room: 2.7.0
- Compose BOM: 2025.05.00

## Concorrentes identificados

- **Genspark SecondBrain** — memória persistente corporativa (email, reuniões, CRM) + hardware gravador $179. Nicho diferente: memória contínua passiva vs. aprendizagem ativa intencional do AudiNote. Não é concorrente direto.
- **NotebookLM (Google)** — concorrente mais relevante: upload de áudio → análise, mas sem workflow de gravação nativa nem perguntas ao vivo para o palestrante.

## Futuras versões

- **v2**: Modos de Sessão (Palestra / Reunião / Masterclass / Negociação / Entrevista) — ajusta prompts Gemini por contexto
- **v2**: Google Cloud Speech-to-Text v2 opcional para maior precisão (~$0.024/min) — avaliar se `VOICE_RECOGNITION` não for suficiente
- **v3**: Agente local Ollama (sem internet, sem custos de API)
- **v3**: iOS (SwiftUI)
