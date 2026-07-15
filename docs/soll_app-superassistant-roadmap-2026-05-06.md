---
tags:
  - type/raw
  - project/soll_app
  - area/mobile-assistant
  - area/roadmap
  - area/iot
  - area/security
  - area/books
  - area/music
  - source/project-audit
created: 2026-05-06
status: draft
intended_wiki_target: soll-app-superassistant-roadmap
source_projects:
  - D:\Projects\soll_app
  - D:\Projects\Android\MrF
  - D:\Projects\Aquik
  - D:\Projects\Aquik firmware
  - D:\Projects\Android\MonoSales
  - D:\Projects\ivaro
---

# Soll App Superassistant Roadmap

## Executive Summary

`soll_app` должен стать мобильным мультитул-ассистентом уровня "персональный Jarvis": телефонный агент, голосовой интерфейс, центр локальных инструментов, шлюз к Soll knowledge base, контроллер внешних ESP-устройств и безопасная лаборатория для hardware/security знаний.

Главное правило разработки: не строить "всё сразу". Сначала собрать устойчивое ядро: capability registry, tool jobs, logs, permissions, settings, Soll API sync и понятный UI. После этого добавлять модули как независимые инструменты: книги/TTS, voice, ESP connector, scanner, daily task board, proactive suggestions, security labs.

Самые ценные источники:

- `soll_app`: уже есть Android/Kotlin, Foreground `BotService`, Telegram long polling, Room DB, command handlers, Tools page, TTS/book reader, breathing, logs/settings. Course Coach удален из сборки 2026-05-07 и заархивирован как UI-донор.
- `MRF`: не переносить код напрямую, но перенести архитектурные механики: capability tiers, scenario detection, proactive AI, smart suggestions, memory, meta-coordinator, agent routing, security gates.
- `Aquik`: первый донор для headless ESP/external-device connector: специальный Android<->ESP протокол, WebSocket JSON, provisioning, discovery, BLE/BT варианты связи, sensors/actuators, logs/OTA/config.
- `MonoSales`: донор scanner/offline-field UX: CameraX + ML Kit barcode scanning, EAN checksum, multi-frame confirmation, duplicate suppression, pairing/session token, local history.
- `Ivaro`: донор CRM/workflow слоя: routes/visits/tasks, offline sync, Telegram relay, encrypted bundles, photo reports, questionnaire/checklist logic, map packs, AI photo analysis.
- Wiki/raw по Flipper Zero и hacking books: только safe labs, threat models, source policy, requested-only book ingestion, no payload mirror.

## Source Inventory

### Current app: `D:\Projects\soll_app`

Найдено:

- `CLAUDE.md`: описывает Soll как Android Telegram bot app с Foreground Service.
- `data/service/BotService.kt`: foreground service, long polling, wake lock, START_STICKY, webhook conflict handling, update processing.
- `domain/command/CommandProcessor.kt`: registry command handlers.
- `domain/command/handlers/*`: photo, SMS, location, files, download, contacts, calls, device control, logs, info/status.
- `presentation/screens/tools/ToolsScreen.kt`: инструменты: music, voice, raw note, book reader, guided breathing.
- `domain/tts/*`: несколько TTS engines and diagnostics.
- `domain/epub/EpubParser.kt`, `BookReaderScreen`, `BookRepository`.
- `data/local/SollDatabase.kt`: Room DB with command logs, message logs, bot config, books, breathing, assistant events, tool jobs, sync queue, task cache, devices, music.
- `docs/plan.md`: Android Telegram bot background reliability plan.
- `docs/deep-research-report.md`: TTS models and Android offline speech synthesis research.

Вывод: `soll_app` уже не пустой. Нельзя ломать текущую архитектуру. Нужно нарастить единый слой capabilities/tool-jobs поверх существующих handlers и Tools page.

### MRF donor: `D:\Projects\Android\MrF`

Полезные идеи:

- `ScenarioDetector`: сценарии по времени, контексту, активности, истории.
- `SmartSuggestionEngine`: suggestions with confidence, priority, feedback, daily limit.
- `ProactiveAIManager`: behavioral, temporal, environmental triggers, interruption manager, morning briefing, automation suggestions.
- `MetaCoordinator`: parallel specialized agents, memory system, knowledge base, response synthesis.
- Архитектурные docs: modular assistant with core system/security/database/network/sensors/features/voice/ml/plugins.

Решение: MRF использовать как donor concepts, не как прямой перенос. Прямой перенос кода рискован из-за рассинхронизации проекта и устаревших/неполных модулей.

### Aquik / Aquik firmware donor

Полезные идеи:

- Connection state machine: `INIT -> DISCOVERING -> PROVISIONING -> CONNECTING -> READY -> RECOVERY`.
- Transport fallback: WiFi STA, AP mode, BLE provisioning, SmartConfig, manual IP.
- WebSocket JSON protocol: `auth`, `getInfo`, `getConfig`, `setConfig`, `getSensors`, actuator commands.
- Security-first design: token auth, local-only service mode, encrypted WiFi credentials.
- Service panel: local AP/diagnostics/logs/OTA/config for ESP nodes.
- Device dashboard: sensors, actuators, schedules, alerts, automation.

Решение: Aquik protocol становится первым concrete `DeviceProfile` для `soll_app`. Но connector должен быть generic, чтобы подключать будущие Soll external devices.

### MonoSales donor

Полезные идеи:

- CameraX + ML Kit barcode scanner.
- EAN-13/EAN-8 checksum validation.
- Multi-frame validation: подтверждать код после нескольких одинаковых распознаваний.
- Duplicate scan suppression and scan history.
- Manual EAN input fallback.
- Pairing/session token for mobile scanner integration.
- Offline order draft model and local DB.
- Field-friendly UX: status, vibration, connection state, retry.

Решение: сделать в `soll_app` generic Scanner Tool: QR/EAN/barcode capture for books, assets, ESP labels, inventory, raw notes, device pairing.

### Ivaro donor

Полезные идеи:

- Daily route/tasks workflow.
- Visit/check-in/check-out style workflow can become generic task execution flow.
- Offline sync and delta bundles.
- Telegram Bot Relay for weak network.
- Encrypted bundles and conflict resolution.
- Photo/report ingestion and AI photo analysis.
- Questionnaires/checklists with required fields and checkout blocking.
- Map packs and offline map data.
- Admin/mobile/server separation.

Решение: взять не CRM как продукт, а workflow patterns: daily task board, offline sync queue, field reports, checklist forms, media evidence and moderation.

### Soll wiki/raw donor

Полезные темы:

- `soll-project`: project intelligence, daily deep audit, task board, capability tiers, source-to-project opportunities, health-aware routing.
- `arduino-a790a76e`: broad Arduino/DIY hardware idea signal. Route selected projects through the existing headless `Гаджеты`/ESP connector instead of adding a generic Arduino module; prioritize a networked environmental sensor or physical notification indicator for the first deep dive, and defer motor/thermal-printer work until a concrete workflow, schematic, BOM, firmware and power constraints are available.
- `pvs-studio-cmake-4db4899a`: official CMake 4.3 static-analysis integration signal. It has no direct placement in this Gradle/Kotlin Android project; retain it as a gated CI candidate for a separate CMake-based Aquik/ESP firmware repository, with license, generator, baseline and report-artifact checks before enforcement.
- `ai-developer-tools-a5895398`: broad review-only AI coding topic map and near-duplicate of `ai-0ad60b3f`. Merge both into one desktop/server KB taxonomy; evaluate one concrete context-engineering/coding-agent workflow on non-sensitive Soll tasks before adopting a tool, while Android stays an approval and observability client.
- `ai-0ad60b3f`: weak aggregated AI-development and agent-workflow signal. Keep it as a desktop/server KB and architecture-evaluation topic for assistants, agent runtimes, MCP, context engineering and controlled automation; Android remains an approval and observability client rather than an autonomous coding/browser runtime.
- `llm-a80dd931`: weak multi-provider Telegram-bot signal. Keep provider/model selection, credentials, rate limits, fallback and conversation isolation in the desktop/server meta-coordinator; Android should continue through the backend-mediated model-chat contract and only display server-reported provider/model metadata if that becomes useful.
- `claude-mythos-release`: Anthropic model-release signal. Keep Mythos/Fable evaluation in desktop/server LLM routing and release-monitoring; Android should not depend on direct Anthropic model IDs or keys.
- `claude-science-90d35df4`: scientific AI workbench signal. Keep it as a desktop/server research sandbox candidate after official-doc/access/privacy verification; Android should only consume validated summaries through existing Soll surfaces.
- `claude-managed-digest`: Claude Managed Agents source-monitoring signal. Implement any digest-agent as a desktop/server read-only prototype with allowlisted sources; Android only consumes digest cards, tasks and approval prompts through existing Soll surfaces.
- `multiplayer-interactive-world-models-with-repres-18709be4`: representation-autoencoder multiplayer world-model signal. Evaluate only in an isolated desktop/server simulation sandbox with synthetic data and joint-action rollout metrics; Android remains a result/approval client, and the current LLM meta-coordinator is unchanged unless the sandbox proves a concrete benefit.
- `omniopt-taxonomy-geometry-and-benchmarking-of-mo-2f762a3f`: modern-optimizer taxonomy and benchmark signal. Keep it as a desktop/server ML-training evaluation cookbook; only benchmark constraint-matched optimizers after Soll has a concrete training workload, while Android remains a result and approval client with no optimizer or training runtime.
- `kotlin-coroutines`: educational Android/Kotlin implementation note about continuation-passing style and compiler-generated state machines. Use it as engineering guidance, not a new runtime dependency: first audit the existing Camera2 and location callback bridges for cancellation, resource ownership and single-resume safety, while keeping low-level coroutine machinery compiler/library-owned.
- `agenticdatabench-a-comprehensive-benchmark-for-d-2763da91`: data-agent benchmark signal. Keep the eval harness in desktop/server Soll with synthetic/non-sensitive tasks, skill labels and gold outputs; Android only consumes resulting summaries, source cards and approvals.
- `deploy-automation-15e48b34`: deployment automation signal. Keep server provisioning, Nginx, SSL, fail2ban and GitHub/CD scripting in a desktop/server DevOps spike; Android only shows deploy status, tasks and explicit approvals.
- `startup-niche-ai-eea80802`: market-research/product-discovery agent signal. Keep niche discovery in a desktop/server evidence workflow with human approval; Android only reviews source cards, insight summaries and opportunity tasks.
- `clustmetalearn`: weak clustering/meta-learning signal. Keep Android as an insights consumer; run any evaluation first in a desktop/server sandbox on anonymized metadata.
- `codegraph-claude-code-grep-8a6a6a06`: local code graph/project-intelligence signal. Keep it as an isolated desktop/server spike for repository indexing and impact queries; do not make it an Android runtime dependency.
- `delegation-e723ba31`: task-brief quality signal. Apply the checklist in desktop/server task creation and agent orchestration; Android should keep consuming the resulting title/description/source metadata through the existing Tasks surface.
- `3d-a0eee0f5`: industrial 3D/CAD model QA and object-tagging signal. Keep CAD/model validation in desktop/server guidelines; Android only captures field evidence and reviews server-created QA tasks.
- `mrf-project`: MRF as architecture donor.
- `aquik-project`: headless ESP protocol, telemetry, alerts, provisioning, BLE/BT/Wi-Fi transport ideas. ESP-side web UI/portal/settings panel is out of scope.
- `monosales-project`: mobile scanner and field workflows.
- `ivaro-project`: routes, visits, photo reports, sync.
- `flipper-zero-security-research`: safe hardware security labs.
- `flipper-zero-unofficial-ecosystem`: dual-use topic handling, blocked payload mirror, tool gating.
- `telegram-book-bot-tool`: requested-only book download, topic bounded max 50, dedupe, process to raw/wiki.
- `хакинг-мобильных-телефонов`: current sample book metadata, archive fallback.
- `cv-retail-challenges`: retail CV opportunity signal for shelf audits, queue/customer-flow analytics and planogram checks. Keep Android scope to capture/review surfaces; CV inference and privacy-sensitive analytics belong to server-side prototypes first.
- `codex-sites-work`: Codex Sites is a weak/early OpenAI workflow signal, not an Android runtime feature. Keep it in desktop/Soll workspace-internal prototype and governance docs until official access, privacy and publishing limits are checked.
- `cloudflare-supabase`: serverless API/cache infrastructure signal. Keep Cloudflare Workers and Supabase behind the existing Soll server/API contract; Android must not hold Supabase keys or talk directly to a new datastore.

## Product Definition

### What "Jarvis-like" means for Soll App

Not a chatbot only. Soll App should be a commandable mobile runtime:

- Receives commands via UI, voice and Telegram.
- Runs local tools with progress and logs.
- Controls device features when allowed.
- Captures raw knowledge into Soll.
- Reads and speaks books/notes.
- Connects to ESP devices and future hardware.
- Suggests useful actions from context, but does not spam.
- Escalates complex reasoning to Soll/Forg/Ollama/server agents.
- Keeps audit trail for anything sensitive.

### Product principles

- Tool-first, not chat-first: every action is a typed tool job.
- Safe-by-default: risky capabilities require permissions, settings and confirmation.
- Offline-first where possible: capture, logs, books, scanner, task board cache.
- Server-assisted for heavy intelligence: mobile executes, Soll/Forg reason.
- No payload mirror: security/hacking content becomes labs, threat models and checklists.
- Everything observable: status, logs, progress, last error, retry state.

## Current Soll App Baseline

### Already useful

- Telegram bot service can run on Android as foreground service.
- Commands already cover many phone capabilities: status, info, logs, storage, files, download, SMS, calls, contacts, location, photo, record, notify, vibrate, flashlight, volume, alarm, brightness, Bluetooth, WiFi.
- TTS/book stack is unusually strong for a mobile personal assistant: EPUB parser, multiple engines, diagnostics, models, book reader UI.
- Tools page exists and can become the main launcher.
- Room DB already stores local operational data.

### Current gaps

- Commands are registered directly in `CommandProcessor`, without central risk/capability model.
- Tool execution is not unified as `ToolJob`.
- Long-running tools and media jobs need progress, cancellation and output folders.
- UI has Tools list, but not a real assistant control center.
- No Soll backend bridge yet for briefing/tasks/raw/wiki/project memory.
- No general device connector for ESP/hardware.
- No scanner module.
- No voice command loop beyond TTS work.
- No proactive/context engine.
- Settings do not yet expose capability tiers and privacy controls.

## Target Architecture

### High-level layers

```text
Presentation
  Home / Tools / Assistant / Tasks / Devices / Logs / Settings

Assistant Core
  CommandRouter
  CapabilityRegistry
  ToolJobRunner
  AssistantEventLog
  ConfirmationPolicy
  InterruptionPolicy

Domain Tools
  Books/TTS
  Voice/STT
  Device Control
  Files/Downloads
  Scanner
  ESP Device Connector
  Knowledge Capture
  Security Labs
  Daily Tasks

Data
  Room DB
  SettingsRepository
  SollApiClient
  TelegramRepository
  Local file/output storage

External
  Telegram Bot API
  Soll backend
  ESP WebSocket devices
  Android system services
  Optional server LLM/Forg/Ollama
```

### Required domain models

```kotlin
data class Capability(
    val id: String,
    val name: String,
    val description: String,
    val riskTier: RiskTier,
    val requiredAndroidPermissions: List<String>,
    val requiresConfirmation: Boolean,
    val enabledByDefault: Boolean,
    val auditRequired: Boolean
)

enum class RiskTier {
    SAFE_INFO,
    PERSONAL_DATA,
    DEVICE_CONTROL,
    COMMUNICATION,
    FILE_MEDIA,
    MONEY_OR_EXTERNAL_ACTION,
    DUAL_USE_HARDWARE,
    BLOCKED
}

data class ToolJob(
    val id: String,
    val toolId: String,
    val status: ToolJobStatus,
    val progressPercent: Int?,
    val inputJson: String,
    val outputJson: String?,
    val logText: String,
    val createdAt: Long,
    val finishedAt: Long?
)

enum class ToolJobStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_CONFIRMATION,
    SUCCESS,
    FAILED,
    CANCELLED,
    BLOCKED
}

data class DeviceProfile(
    val id: String,
    val name: String,
    val transport: DeviceTransport,
    val authMode: DeviceAuthMode,
    val commandSchemaVersion: String,
    val capabilities: List<String>
)

data class AssistantEvent(
    val id: String,
    val type: String,
    val source: String,
    val summary: String,
    val payloadJson: String?,
    val createdAt: Long
)
```

## Capability Tiers and Safety Gates

### Tier mapping for current commands

| Tier | Commands / tools | Default |
|---|---|---|
| SAFE_INFO | `/start`, `/help`, `/ping`, `/status`, `/info`, `/logs`, `/storage` | enabled |
| PERSONAL_DATA | `/contacts`, call logs, local files list, location history | confirm once / setting |
| DEVICE_CONTROL | flashlight, volume, brightness, WiFi, Bluetooth, vibrate, alarm | enabled with permissions |
| COMMUNICATION | SMS send, call, notify external targets | confirm each action |
| FILE_MEDIA | photo, record, download, upload raw, book import | confirm for first run, audit |
| DUAL_USE_HARDWARE | ESP control, Flipper/security labs, RF/GPIO-related labs | disabled until enabled |
| BLOCKED | payload execution, credential dumps, bypass instructions, public disruption workflows | always blocked |

### Required behavior

- Every command/tool checks `CapabilityRegistry` before execution.
- Blocked tool returns a clear explanation and logs an `AssistantEvent`.
- Risky tools require explicit UI/Telegram confirmation.
- Confirmation should include: action, target, data affected, risk tier, timeout.
- Settings must show all capabilities grouped by risk.
- Audit log cannot be disabled for risky tiers.

## Tool Modules Roadmap

### 1. Assistant Control Center

Goal: turn Home/Tools into a real operational dashboard.

Features:

- Service status: bot running, uptime, messages processed, last error.
- Permissions status: camera, microphone, SMS, contacts, location, storage, notifications, foreground service, battery optimization.
- Recent tool jobs.
- Recent assistant events.
- Daily tasks from Soll.
- Quick buttons: Speak, Capture raw, Start bot, Stop bot, Open tools, Sync.

Implementation tasks:

- [x] Add `AssistantDashboardScreen`.
- [x] Add `AssistantStatusViewModel`.
- [x] Read `BotService.isRunning`, `messagesProcessed`, `lastError`, settings.
- [x] Show permission/battery warnings with direct settings intents.
- [x] Link dashboard actions to tool runner, not direct ad-hoc calls.

### 2. Unified Tool Runner

Goal: all local actions become observable jobs.

Features:

- Queue, run, progress, cancel, retry.
- Tool-specific input and output JSON.
- Logs per job.
- Local DB persistence.
- UI job details screen.
- Telegram command can return job ID and later progress.

Implementation tasks:

- [x] Add `ToolJobEntity`, `ToolJobDao`, DB migration.
- [x] Add `ToolJobRunner`.
- [x] Add `ToolHandler` interface.
- [x] Wrap existing command handlers gradually.
- [x] Add progress UI component.
- [x] Add cancellation for long-running tools.

Progress 2026-05-07:

- Tool jobs can now be cancelled from the job log UI.
- Runner/store preserve `CANCELLED` status so a cancelled running handler cannot overwrite it with success/failure.
- Progress/log updates perform cooperative cancellation checks.

Progress 2026-05-07 update:

- Moved tool job history/progress/cancel UI from `Инструменты` to `Логи -> Задачи`.
- `Инструменты` is now only a clean launcher list for local tools.

### 2A. Notification Center

Goal: единый слой уведомлений для системных каналов Android, внутренних событий Soll, задач инструментов и медиа-модулей.

Implementation tasks:

- [x] Add central notification channel registry for bot service, TTS playback, music playback, app events, alerts and tool jobs.
- [x] Add `SollNotificationCenter` domain API.
- [x] Add Room storage for app notifications.
- [x] Add Android system notification dispatcher with `POST_NOTIFICATIONS`/channel checks.
- [x] Move `/notify` command to the central notification center.
- [x] Add notifications tab under `Логи`.
- [x] Add health indicator for system notification availability.
- [x] Emit tool-job completion/failure/cancel/block notifications.
- [x] Keep Media3 music notification on the dedicated media channel while sharing the same channel/id registry.
- [x] Add in-app device QA checklist for notifications, background work, music and NFC checks; physical phone validation remains explicit.
- [x] Add explicit Device QA rows for Android 13+ notification flow, notification tap routing and Media3 media-notification ownership.
- [ ] Device QA: confirm Android 13+ permission flow and channel toggles on target phone.
- [ ] Device QA: confirm notification tap opens Soll and media notification remains controlled by Media3.

Progress 2026-05-08:

- Created unified notification center with persistent in-app history and system notification posting.
- Added separate channels for normal events, important alerts and tool jobs.
- Added Russian UI for `Логи -> Увед.` with read state, payload details and system-show status.
- `/notify` now saves a notification record even when system notifications are disabled, and reports that state back to Telegram.
- Tool jobs now create notifications on terminal states without changing their execution path.
- Notification tap routing now carries an explicit launch target and opens `Логи -> Увед.` instead of only launching the app shell; real tap behavior still needs target-phone QA.
- Home health action for system notifications now opens Android app notification settings directly, so Android 13+ permission and channel toggles are quicker to verify on the target phone.
- Device QA manual pass/problem marks now store the checked phone model and Android/API version, so physical validation is tied to the actual target device.
- Settings -> Device QA can now generate a Markdown report and share it through Android share sheet for external/manual QA tracking.
- Device QA report now includes package/versionCode/versionName and status counters, so reports can be matched to a specific APK build.
- Device QA now exposes separate manual checks for Android 13+ notification permission/channel flow, notification tap routing and Media3 media-notification ownership.

### 2B. Quick Home Screen Widgets

Goal: быстрый доступ к личным инструментам без поиска внутри приложения.

Implementation tasks:

- [x] Add Android home-screen widget for music with previous, play/pause, next and stop controls.
- [x] Add Android home-screen widget for book reader/TTS with previous chapter, play/pause, next chapter and stop controls.
- [x] Add Android home-screen widget for notes with direct jump into the notes tool.
- [x] Add explicit app launch targets for music, reader and notes so widgets open the right tool.
- [x] Keep widget text and labels in Russian.
- [x] Make widgets compact and show album/book artwork when media metadata is available.
- [x] Show last-read book and saved reading excerpt in the reader widget when TTS is not active.
- [x] Add widget launcher/media-control checks to Settings -> Device QA so target-phone results can be recorded in-app.
- [ ] Device QA: add widgets on the target launcher and verify taps while the app process is cold.
- [ ] Device QA: verify music/reader widgets update after notification/lockscreen controls.

Progress 2026-05-11:

- Implemented classic Android `AppWidgetProvider`/`RemoteViews` widgets for `Музыка`, `Читалка` and `Заметки`.
- Music and reader widgets reuse existing playback services instead of introducing separate playback state.
- Media notification taps and widget root taps now route directly into the corresponding tool screen.

Progress 2026-05-11 update:

- Reworked music and reader widgets into compact 1x3-style, single-cell-height controls with artwork slots.
- Music widgets now try to extract embedded MP3/FLAC artwork from the current track URI; reader widgets use the saved EPUB cover path.
- Notes widget was reduced to a compact open shortcut so it takes less launcher space.
- Reader widget now falls back to the last Room-saved book, cover and excerpt around `currentPosition` when the TTS service is stopped.
- Reader widget state is now cached in lightweight SharedPreferences and updated from the reader ViewModel on open/progress/pause/stop, so `AppWidgetProvider` no longer blocks on EPUB parsing.
- Reader progress is now autosaved during TTS highlighting and on screen pause/stop, keeping the launcher widget excerpt close to the actual playback position.
- Settings -> Device QA now includes widget presence/cold-start and widget media-control checks with manual pass/problem marks.

### 2C. Visual Theme System

Goal: оставить темный режим основным, но дать выбор более живой палитры без потери читаемости.

Implementation tasks:

- [x] Keep the current dark Soll theme as `Классика`.
- [x] Add a second dark Material 3 color scheme with green primary, amber secondary and blue tertiary accents.
- [x] Add Aquik-inspired dark aqua theme from the local Aquik Compose palette.
- [x] Add a theme switcher in Settings.
- [x] Persist the selected theme and apply it from `MainActivity`.
- [x] Use high-contrast on-colors for text over primary/secondary/tertiary/surface roles.
- [x] Add theme visual QA check to Settings -> Device QA for manual contrast/palette validation on the target phone.
- [ ] Device QA: visually check the new palette on the target phone in Home, Tools, Music, Reader, Notes and Settings.

Progress 2026-05-11:

- Added `Аврора` as an alternate dark theme; the previous green theme remains available.
- Settings now has a Russian theme selector and changes apply immediately through the shared settings flow.

Progress 2026-05-11 update:

- Added `Aquik` as a third dark theme based on `D:\Projects\Aquik\app\src\main\java\com\aquik\controller\ui\theme\Theme.kt`.
- Theme persistence now accepts `aquik`, and Settings still keeps Russian explanatory text while preserving the Aquik brand name.
- Settings -> Device QA now has a manual theme/contrast pass for Classic, Aurora and Aquik across Home, Tools, Music, Reader, Notes and Settings.

### 3. Soll Backend Bridge

Goal: make mobile app a live client/agent for the desktop Soll system.

Features:

- Fetch morning briefing.
- Fetch daily task board.
- Mark task done/defer/reject.
- Add raw text note.
- Upload raw file/media/audio transcript.
- Send tool job result to raw.
- Fetch project opportunities.
- Push mobile status into Soll project memory.

API client methods:

```kotlin
interface SollApiClient {
    suspend fun getHealth(): SollHealth
    suspend fun getTodayBriefing(): BriefingSummary
    suspend fun getDailyTasks(): DailyTaskBoard
    suspend fun updateTaskStatus(taskId: String, status: String)
    suspend fun createRawNote(title: String, content: String, tags: List<String>)
    suspend fun uploadRawFile(path: Uri, metadata: RawUploadMetadata)
    suspend fun appendAssistantEvent(event: AssistantEvent)
}
```

Implementation tasks:

- [x] Add Settings fields: Soll server URL, token, sync interval, WiFi-only upload.
- [x] Add Retrofit/Moshi client.
- [x] Add local sync queue for offline failures.
- [x] Add `/sync now` Telegram command and Settings UI sync button.
- [x] Add raw capture screen.

### 4. Voice Layer

Goal: voice command loop without destabilizing current TTS stack.

V1 scope:

- Push-to-talk only.
- Local Android speech recognizer or lightweight STT engine.
- Recognized text routes to `CommandRouter`.
- TTS reads response using current TTS manager.
- Voice history stored as assistant events.

Later:

- Wake word.
- Speaker verification.
- Offline STT model.
- Emotional/prosody tuning.

Implementation tasks:

- [x] Add Voice screen.
- [x] Add `VoiceCommandSession`.
- [x] Add STT adapter interface.
- [x] Add command parse layer for natural phrases.
- [x] Add TTS response playback.
- [x] Add setting: voice requires unlock / headset only / local only.

Progress 2026-05-08:

- Added Android on-device `SpeechRecognizer` path for local STT when the OS/device supports it; otherwise the app keeps the system recognizer with offline preference.
- Added optional manual wake-phrase guard: commands can require "Солл" at the beginning before routing to command execution.
- The wake phrase is not always-on background microphone mode; it only filters an explicitly started voice session.
- Added Russian settings/status chips and unit tests for wake-phrase behavior.

### 5. Books and Research Tool

Goal: mobile-controlled requested-only research ingestion.

Features:

- Search exact title/author/topic through Soll backend book tool.
- Show bounded results, max 50 for topic.
- User selects result(s), then format.
- Deduplicate by title/author/ISBN/hash.
- Download to `raw/books` through backend or queue request.
- Process metadata/full text into wiki.
- Handle no downloadable format with clear UI.

Rules:

- No auto-download whole Telegram group.
- No background monitor pulling every book.
- Duplicates: save one best copy, record alternatives in sidecar.
- If file is archive with wrong extension, write metadata fallback and mark handled.

Implementation tasks:

- [x] Remove Android Book Search screen from app navigation/build after scope change.
- [x] Remove mobile book-search Retrofit/domain/repository contracts; desktop Soll keeps this workflow.

Progress 2026-05-07:

- Added a mobile book-search prototype as a requested-only remote client for Soll `/api/v1/books`.
- Added Retrofit/domain/repository methods for status, search, current results, select, download, batch download, process and cancel.
- Added Russian UI for exact/topic search, max 50 cap, dedupe, result multi-select, format loading, preferred/specific format download and wiki processing.
- Wrapped download/process operations in `ToolJob` with visible progress/logs.
- Preserved server-side safety: no background group monitoring and no auto-download without explicit user action.

Progress 2026-05-07 update:

- Removed mobile Book Search from Android tools/navigation; the app no longer exposes this module.
- 2026-05-12: removed the remaining Android book-search API/domain/repository contracts; book search stays desktop-only.

### 5A. Music Player Tool

Goal: локальный музыкальный инструмент для личного использования, как читалка, но для папок с музыкой и отдельных композиций.

V1 scope:

- локальная медиатека без стриминга;
- выбор папки с музыкой через SAF;
- добавление отдельных композиций;
- фон и выключенный экран;
- управление через notification/lockscreen/headset controls;
- реакция на звонки и другие аудио-прерывания через Android audio focus;
- русский UI и голосовые команды.

Implementation tasks:

- [x] Add Media3 playback stack for music (`ExoPlayer` + Media3 library/session service).
- [x] Add Room models for music sources, source-track links, tracks and playback state.
- [x] Add SAF folder import and multi-track import.
- [x] Add metadata extraction and supported audio filter.
- [x] Add observable import as `ToolJob`.
- [x] Add Music tool screen with Russian library/player UI.
- [x] Add foreground/background playback service with audio focus.
- [x] Add notification/lockscreen/headset-compatible media session.
- [x] Add Music module settings for resume, TTS interaction, headset controls, rescan and strict format filtering.
- [x] Harden duplicate imports so one track can stay linked to multiple sources.
- [x] Add source management UI for rescan/remove and scan errors.
- [x] Add capability gate `music` under `FILE_MEDIA`.
- [x] Add assistant events for import/playback/policy blocks.
- [x] Add Russian voice commands for play/pause/next/previous music.
- [x] Pause music when book TTS starts, and stop TTS when music starts.
- [x] Add unit tests for audio filtering, queue behavior and voice parser.
- [x] Add playlist storage and playlist UI.
- [x] Add extended MP3 metadata extraction: album artist, genre, year, track/disc number, composer and bitrate.
- [x] Add library cards by artists, albums and genres.
- [x] Add local recommendation shelves inspired by collection/mix patterns: new tracks, frequent tracks, artist wave, genre wave and rediscovery.
- [x] Add collection playback queues so shelves, artists, albums, genres and playlists play as focused queues.
- [x] Add Media3 playback resumption, media-button receiver and explicit MediaLibraryService manifest action.
- [x] Add ExoPlayer wake mode for more reliable screen-off local playback.

Progress 2026-05-07:

- Music UI now has sections: overview, tracks, artists, albums, genres and playlists.
- Recommendations are local-only and based on file metadata plus listening history; no cloud profile or streaming dependency.
- Playlist creation moved to the Playlists tab; Tracks now keep the song list primary and only add single/selected songs to existing playlists.
- Theme secondary turquoise was replaced with Soll green while leaving the rest of the palette intact.
- Background playback hardening added: Media3 resumption callback, media button receiver, trusted controller policy and wake mode.

Progress 2026-05-08:

- Reworked music playback controls to use a Media3 `MediaController` connected to `MusicPlaybackService`, so the app UI, system notification and lockscreen controls now operate through the same media session.
- Added explicit Media3 notification provider for the Soll music channel/id with playback notification buttons for previous, play/pause, next plus shuffle/repeat/stop actions.
- Added custom media-session commands for shuffle, repeat-cycle and stop, and allowed the Media3 media notification controller explicitly.
- Repeat/shuffle changes now update the session media button preferences and persist back to music settings from player callbacks.
- Initial music playback commands now start the service as a foreground service when needed, improving screen-off/background survival before the Media3 notification promotes playback.
- Added a tested Media3 controller access policy: the app and system media notification are always allowed, headset/external controls require trusted controllers and the user headset-control setting, and the music service now returns `START_STICKY` after start commands.
- Local checks passed for `:app:compileDebugKotlin`, `:app:testDebugUnitTest` and `:app:assembleDebug`; real screen-off notification behavior still needs device QA.

Progress 2026-05-11 audit update:

- Music teardown no longer blocks the service thread with `runBlocking`; the final playback snapshot is saved on a short-lived IO coroutine with failure logging.
- Book/TTS AudioTrack loops for Silero, Natasha, Utrobin, Kokoro and Chatterbox now use cancellable coroutine delays and release tracks from `finally`, reducing risk of stuck playback resources.
- Added a project guard test so blocking sleep patterns do not silently return to core playback paths.
- Local checks passed on 2026-05-11: `:app:testDebugUnitTest` and `:app:assembleDebug`.

Progress 2026-05-11 refactor update:

- Removed inert attachment chips from the Logs screen; document/photo/location markers are now passive badges instead of fake clickable controls.
- Restored an explicit TTS-folder import button in reader settings and removed the dead ONNX selector callback from the reader UI contract.
- Cleaned low-risk compile warnings in Alarm and EPUB parsing, and added a guard test against empty presentation `onClick = { }` handlers.
- Added shared `PassiveChip` and replaced static status chips across Voice, Devices, Tasks, Settings, Notes, NFC, Music, Ask Soll and Field Map so labels no longer pretend to be buttons.
- Cleaned additional low-risk deprecations in Russian locale usage, TTS temp folder naming, Settings send icon and Chatterbox tokenizer test temp directories.
- Moved Android system-bar color writes into a scoped compatibility helper so the theme keeps its current dark visual behavior without leaking compile deprecation warnings.
- Verified the Moshi codegen path is already on KSP and narrowed `hiltJavaCompile*` to Hilt/Dagger javac processors so Hilt no longer triggers Moshi's false-positive Kapt warning.
- Replaced the configuration-time `zipTree(singleFile)` ONNX AAR stripping with an explicit cache-safe Gradle task that removes only `jni/**/libonnxruntime.so`.
- Made long-running presentation loops in Home and Breathing explicitly coroutine-cancellable with `isActive`, then added a guard test to keep raw `while (true)` loops out of the presentation layer.
- Ran Android lint and fixed all 26 blocking errors without adding a baseline: notification permission guard, Media3 unstable API declarations, invalid media-session result code, Chatterbox half-float handling, API-27 theme split and optional telephony feature declaration.
- Removed force unwraps from main source, added a guard against new `!!` crashes, moved direct Gradle dependencies into `libs.versions.toml`, fixed locale-sensitive reader formatting, removed hardcoded `/sdcard`, and cleaned widget resource text/small-font lint warnings.
- Continued the lint audit down to non-migration warnings only: Android 12+ widget metadata now lives in `xml-v31`, media/browser service export is permission-gated, backup/transfer rules explicitly exclude app data, old minSdk branches were removed, Bluetooth status no longer exposes hardware MAC, unused legacy strings/drawable were deleted, and the voice recorder now rejects Android versions that cannot produce OGG/OPUS safely.
- Continued the Russian-first UX audit: translated `/ping`, command help placeholders, Telegram/EPUB/TTS fallback errors, reader TTS labels and Piper package wording; added Russian aliases for common flashlight/Bluetooth/Wi-Fi/brightness/photo command arguments and a guard test against returning legacy English fallback phrases.
- Hardened TTS pack downloads/imports against stuck background work: coroutine cancellation is no longer swallowed, active HTTP calls are cancelled, partial files are deleted, system `tar` is forcibly stopped on cancellation, archive extraction checks coroutine activity during long reads, and a guard test keeps raw `while (true)` loops out of `TtsPackLibrary`.
- Removed the remaining raw `while (true)` loop from the ONNX AAR stripping Gradle task and added a guard so the cache-safe build helper stays bounded.
- Hardened background cancellation beyond TTS: ToolJob completion notifications, Telegram API calls, Soll API calls and offline sync retry no longer convert `CancellationException` into ordinary failures; shared network calls now use `runSuspendCatching`.
- Continued Russian-first cleanup in Telegram job logging: command-backed tool jobs now store Russian status text instead of `ToolJob ...` / `Telegram handler ...` fragments, with a guard against those fallback phrases returning.
- Made the remaining physical Device QA items easier to execute later: each manual check in Settings -> Device QA now shows the expected result and its roadmap area, and the shared Markdown report includes the same context.
- Local checks passed on 2026-05-12: `:app:testDebugUnitTest`, `:app:assembleDebug` and `:app:lintDebug`; lint remains at 0 errors / 70 migration warnings.
- Remaining lint warnings on 2026-05-11 are scoped to dependency/AGP updates and `targetSdk`; treat them as a separate migration task with device regression testing rather than a cleanup patch.
- Local checks passed on 2026-05-11: `:app:testDebugUnitTest` and `:app:assembleDebug`.

Exit criteria:

- [x] User can add a folder or individual tracks.
- [x] User can play music from the tool UI.
- [ ] Device QA: playback continues with the screen off for 10+ minutes.
- [ ] Device QA: notification/lockscreen/headset controls work on the target phone.
- [ ] Device QA: calls/notifications use Android audio focus behavior instead of custom phone-state polling.
- [x] `:app:testDebugUnitTest` passes.

### 6. ESP / External Device Connector

Goal: connect Soll App to ESP devices, starting with Aquik-style protocol.

V1 profile: `aquik-v2`.

Commands:

- `auth`
- `getInfo`
- `getConfig`
- `setConfig`
- `getSensors`
- `getSensor`
- actuator control commands from profile schema
- diagnostics/logs if supported

Transport:

- Manual IP + WebSocket first.
- AP mode wizard second.
- Discovery third.
- BLE/SmartConfig later.

Implementation tasks:

- [x] Add `DeviceProfileEntity`, `KnownDeviceEntity`, `DeviceEventEntity`.
- [x] Add `DeviceConnector` interface.
- [x] Add `WebSocketDeviceConnector`.
- [x] Add `AquikDeviceProfile`.
- [x] Add Devices screen: list, connect, status, sensors, commands, logs.
- [x] Add auth token storage encrypted.
- [x] Add reconnect/backoff.
- [x] Add command gating as `DUAL_USE_HARDWARE` or `DEVICE_CONTROL`.
- [x] Add ESP provisioning wizard V1 inside Devices.
- [x] Add richer ESP actuator control V1.
- [x] Move Devices out of the bottom navigation and expose them as the `Гаджеты` tool to reduce bottom-menu load.
- [x] Turn Devices into a universal `Гаджеты` module where Aquik is the first profile, not a separate app.
- [x] Port the first Android-side Aquik model slice: profile metadata, aquarium/greenhouse use cases, known sensors/actuators and telemetry statuses.
- [x] Refactor gadget JSON parsing out of ViewModel into domain parsers before adding more Aquik Android features.
- [x] Add service read panel for config, schedules and I2C diagnostics through the profile command layer.
- [x] Port Aquik settings, sensor calibration, schedules and automation editor into `Гаджеты` without making a second Android app.

Progress 2026-05-08:

- Added Aquik provisioning flow in the existing Devices screen, not as a new top-level tool.
- Wizard uses the local Aquik AP fallback documented by firmware: `AQUIK-Setup`, `192.168.4.1`, `POST /api/wifi/configure`.
- Added SmartConfig start/status hooks for firmware builds exposing `/api/smartconfig/start` and `/api/connection/status`.
- Wi-Fi password is used only in the local HTTP request and is not written to device events.

Progress 2026-05-08 update:

- Added WebSocket actuator commands from Aquik protocol: `getActuators`, `setPump`, `setFan`, `setLED`.
- Devices screen now has Russian controls for air pump, water pump, fan, full-spectrum LED and white LED.
- Actuator actions reuse the existing `devices` capability gate and device event log.
- LED brightness is edited locally with a slider and sent only by explicit apply action.

Progress 2026-05-08 profile update:

- Added built-in multi-device profile catalog with `Aquik v2` and `ESP WebSocket`.
- Manual connection now lets the user choose a profile before connecting.
- Known devices restore their saved profile when selected.

Progress 2026-05-12 universal gadgets / Aquik Android migration:

- Audited local `D:\Projects\Aquik\android` and docs: useful Android slices are settings, calibration, sensor model/statuses, schedules, automation, notifications and discovery, while Soll already had WebSocket/provisioning/basic commands.
- Added `GadgetProfileCatalog`: `Aquik v2` is now described as an aquarium/greenhouse profile inside a universal gadget catalog; `ESP WebSocket` stays as the future generic profile.
- Added `GadgetSensorCatalog` with Russian sensor labels, units and status evaluation for Aquik-style telemetry.
- Devices UI is now presented as `Гаджеты`, with profile summary, expected sensors/actuators and Russian texts.
- `getActuators` now parses returned state back into switches/sliders, so the UI reflects the real gadget state after refresh.
- Scanner pairing can now carry `profileId`, so QR codes can add either Aquik or future generic ESP gadgets.

Progress 2026-05-12 service/refactor update:

- Architecture correction: ESP gadgets must be headless. No ESP Portal, no ESP Settings UI, no embedded web interface. Android is the only user interface; the controller talks to Android through a Soll/Aquik special protocol.
- Added generic `DeviceConnector.executeCommand(command, paramsJson)` so future gadget profiles can call profile-specific commands without adding a new Kotlin method each time.
- Added `GadgetPayloadParser` and tests for telemetry, actuators, config, schedules and I2C diagnostics.
- Devices ViewModel now delegates payload parsing to the domain layer; UI receives already-normalized summaries.
- Added `Гаджеты -> Сервис и автоматика` read panel with config, schedules and I2C scan actions. This is the base for Aquik settings/calibration/automation over the special protocol, without ESP-side UI.
- Added protocol/transport framing in the gadget catalog: Wi-Fi LAN/WebSocket, Wi-Fi AP/bootstrap, BLE/GATT and Bluetooth SPP where hardware supports it.

Progress 2026-05-12 editor update:

- `Гаджеты -> Сервис и автоматика` now has Android-side editors for Aquik settings, sensor calibration, schedules and automation rules.
- The editor uses the headless Soll/Aquik command protocol: `setSettings`, `calibrateSensor`, `addSchedule`, `updateSchedule`, `deleteSchedule`, `getAutomationRules`, `upsertAutomationRule` and `deleteAutomationRule`.
- Schedule and automation lists can load existing entries into the editor; settings reads prefill device name, timezone, sensor interval, display brightness and auto mode.
- Added domain payload builders and parser tests so editor JSON remains nested and top-level compatible with firmware variants.

Progress 2026-05-12 Soll server route:

- Added the third gadget communication route: `ESP -> Soll Server -> Android`.
- Android now has Soll API models/endpoints for `/api/v1/gadgets`, latest state, history, events and the future command queue.
- `Гаджеты` now includes a Russian `Сервер Soll` panel with refresh, remote snapshot list, latest telemetry and server events.
- Added periodic WorkManager refresh for server gadget snapshots using the existing Soll sync interval/network settings.
- Soll Server now has gadget telemetry storage endpoints for heartbeat, telemetry and events; remote command endpoints are present but disabled by default until the relay is explicitly enabled.

### 7. Hardware / Security Lab

Goal: use Flipper Zero and hacking materials only as safe educational lab planning.

Allowed:

- Owned-lab-only notes.
- Interface inventory: IR, NFC/RFID, GPIO/UART/I2C/SPI, U2F, WiFi dev board concepts.
- Threat model templates.
- Safe checklists.
- Device hardening notes.
- RF legality checklist.
- Links to official docs and safe app catalog notes.

Blocked:

- BadUSB payload collections.
- Credential/card dumps.
- Bypass instructions.
- Public WiFi/BLE disruption workflows.
- Instructions to attack third-party systems.
- Any raw/wiki mirror of payload repositories.

Implementation tasks:

- [x] Add `DualUsePolicy` model.
- [x] Add security lab note template.
- [x] Add source analyzer field `dual_use_topic`.
- [x] Add UI warning and owned-lab confirmation.
- [x] Add audit event contract for every security lab tool action.

Progress 2026-05-08:

- NFC write mode now requires an explicit "my tag / allowed lab" confirmation before writing NDEF data.
- NFC write attempts pass through `DualUsePolicy` and emit security-lab audit events.
- The UI warning stays inside the existing NFC tool; no separate Security Lab tool was added.

Implementation notes:

- Added internal Security Lab guardrails only; no new menu item, tool screen or Telegram command is exposed until explicitly approved.
- Policy blocks payload storage/execution, credential/card/key dumps, access bypass, public disruption and non-owned targets.
- Safe output is limited to owned-lab notes, threat models, hardening notes and RF/legal checklists.

### 8. Scanner Tool

Goal: general barcode/QR scanner for assistant workflows.

Use cases:

- Book ISBN capture.
- Inventory / asset labels.
- Retail shelf-audit evidence capture via barcode/photo/raw note, not local YOLO counting.
- Industrial asset tag/photo/location evidence capture for server-side 3D/CAD deliverable QA, not mobile CAD validation.
- ESP device QR pairing.
- Raw note metadata.
- MonoSales-like field scanning.

Features:

- CameraX + ML Kit scanner.
- EAN-13/EAN-8 checksum validation.
- QR support.
- Multi-frame confirmation.
- Duplicate suppression.
- Manual input fallback.
- Scan history with export to raw.
- Optional vibration/sound feedback.

Implementation tasks:

- [x] Add Scanner screen.
- [x] Port EAN checksum as tested Kotlin utility.
- [x] Add `ScanSessionEntity` and `ScanItemEntity`.
- [x] Add duplicate suppression for repeated values inside the active session.
- [x] Add "send to raw" action for selected scans.
- [x] Add camera barcode recognition.
- [x] Add multi-frame confirmation.
- [x] Add duplicate policy settings.
- [x] Add "attach to task" and "pair device" actions.

### 8A. NFC Tools

Goal: safe NFC/NDEF utility for owned tags and assistant workflows.

Features:

- Detect NFC availability and enabled state.
- Read tag UID, technologies and NDEF records.
- Write text or URL NDEF records to writable owned tags.
- Format empty NDEF-formatable tags during write.
- Explain phone/NFC limits for LF 125 kHz apartment fobs.
- Block cloning, emulation and access-bypass workflows.
- Diagnose apartment key compatibility: detected family, 13.56 MHz NFC/HF, Android HCE support and official mobile-access path.

Implementation tasks:

- [x] Add NFC permission/feature declarations.
- [x] Add NFC tool screen.
- [x] Add reader mode for foreground tag handling.
- [x] Add NDEF read and decode.
- [x] Add NDEF text/URL write.
- [x] Add capability gate `nfc` under `DUAL_USE_HARDWARE`.
- [x] Add safe warning for apartment/access keys.
- [x] Add access-key diagnostic card with HCE/device support and non-cloning guidance.
- [ ] Device QA: read/write test with owned NFC Forum Type 2/4 tags.
- [ ] Device QA: verify target phone behavior with available apartment fob without cloning.

### 9. Ivaro-style Daily Task Execution

Goal: make Soll App usable as daily execution board.

Features:

- Inbox / Today / In Progress / Done / Deferred.
- Sync with Soll daily task board.
- Take task into work.
- Attach photo/audio/file/tool output.
- Checklist/questionnaire mode for recurring tasks.
- Offline edits and retry sync.
- Task can launch tool jobs.

Implementation tasks:

- [x] Add Tasks screen.
- [x] Add local task cache.
- [x] Add task status transitions.
- [x] Add "attach evidence" action.
- [x] Add conflict handling: server newer wins unless local has unsynced attachment.

Progress 2026-05-07:

- Task evidence queued during server failures now stores explicit task metadata.
- Task board shows pending task evidence separately from server task state.
- Failed status actions refresh from server/cache and report the conflict in Russian UI.

### 9A. Map / Offline Field V1

Goal: lightweight personal field mode without adding a heavy online map dependency.

Implementation tasks:

- [x] Add local Room storage for field points with status, source, coordinates, accuracy and task link.
- [x] Add coordinate parser for plain `lat,lon`, `geo:` and Google Maps `q=lat,lon` style text.
- [x] Add field map tool under `Инструменты`.
- [x] Add current GPS capture using foreground location permission only.
- [x] Add manual coordinate entry.
- [x] Add import from cached Soll tasks when task text contains coordinates.
- [x] Add compact offline route preview canvas without online map tiles.
- [x] Add external route opening through Android `geo:` intent and Google Maps web fallback.
- [x] Add export of a field point into local-first notes for Soll sync.
- [x] Add capability gate `field_map` under `PERSONAL_DATA`.

Progress 2026-05-11:

- Implemented `Карта` as an offline-first field tool inspired by Ivaro route/check-in patterns, but scoped for personal Soll use.
- No background location permission and no Google Maps/Mapbox/osmdroid SDK dependency were added; V1 stores points locally and delegates navigation to installed map apps.

### 10. Proactive Assistant

Goal: use MRF ideas carefully without spam.

Signals:

- Time of day.
- Day of week.
- App/service health.
- Battery/charging.
- Pending tasks.
- Recently used tools.
- Missed sync.
- Accepted/dismissed suggestions.

V1 suggestions:

- Morning briefing available.
- Tasks pending for today.
- Sync failed and needs attention.
- Battery optimization blocks bot reliability.
- Book processing completed.
- ESP device offline.
- Too many failed commands from Telegram.

Implementation tasks:

- [x] Add `ScenarioDetector` with simple deterministic scenarios.
- [x] Add `SuggestionEngine` with confidence, priority, daily cap.
- [x] Add feedback: accept/dismiss/snooze.
- [x] Add interruption policy.
- [x] Show suggestions on dashboard.
- [x] Add optional Telegram/system delivery setting only if the dashboard signal proves useful.

Progress 2026-05-08:

- Added local `ScenarioDetector` for Telegram token/service health, battery limits, notification permission, pending tasks, missed sync, recent tool failures and morning context.
- Added `SuggestionEngine` sorting by priority/confidence with daily cap and suppression after accept/dismiss/snooze.
- Added dashboard cards with Russian text and actions for accept, hide and postpone.
- Interruption policy V1 is intentionally quiet: no Telegram spam and no system push notifications; suggestions stay on the main screen.
- Added settings to enable/disable suggestions and tune daily limit.
- Added opt-in delivery toggles for Android system notifications and Telegram. Both are off by default and each suggestion is delivered at most once per day per channel.

### 11. Memory and Personalization

Goal: assistant learns preferences without hidden surveillance.

Local memory:

- Accepted suggestions.
- Favorite tools.
- Recent commands.
- Common task times.
- Device profiles.
- Preferred TTS voice/settings.

Server memory:

- Important assistant events sent to Soll project memory.
- Raw notes and task outputs connected to wiki/project graph.
- Long-term analysis handled by Soll daily deep audit.

Implementation tasks:

- [x] Add memory settings: enabled, export, clear.
- [x] Add visible memory viewer.
- [x] Add local memory export.
- [x] Add memory summary before sending to server.
- [x] Add assistant event summarization before sending to server.
- [x] Never upload personal media automatically without setting.

Progress 2026-05-08:

- Added local assistant memory table and repository for explicit, user-approved memories.
- Accepted proactive suggestions are saved as local memory when memory is enabled.
- Added Settings toggle for assistant memory.
- Added Logs -> Memory tab with Russian viewer, per-entry delete, full clear and Markdown export dialog.
- Added explicit "В Soll" action that sends only a safe memory summary through existing raw-note sync and queues it offline if the server is unavailable.
- Added explicit "События" sync action for assistant events. It sends only sanitized summary/type/source/time counters, never event payloads or raw Telegram text.
- No personal media, raw payload JSON or raw private logs are uploaded automatically.

### 12. Server Meta-Coordinator

Goal: heavy reasoning via Soll/Forg/Ollama, not on Android.

Flow:

```text
User asks complex request
  -> Android parses intent
  -> CapabilityRegistry checks possible actions
  -> Soll/Forg meta-coordinator reasons and proposes plan
  -> Android asks confirmation if action needed
  -> ToolJobRunner executes
  -> Results logged to Soll/raw/wiki/tasks
```

Implementation tasks:

- [x] Add "Ask Soll" tool.
- [x] Add Android API bridge to existing Soll `/assistant/ask` endpoint without exposing a new tool.
- [x] Add server request/response schema with suggested tool calls.
- [x] Add confirmation before executing server-suggested actions.
- [x] Add fallback when server unavailable.

Implementation notes:

- Added visible Russian `Спросить` tool for explicit Ask Soll requests; it uses the existing safe server bridge, local fallback and audit logging, and does not auto-execute suggested actions.
- Requests can strip private context before server sync.
- Suggested actions are checked against local capability decisions and require explicit confirmation for risky actions.
- Fallback returns a Russian local response with no suggested actions when the server is unavailable.
- Android now has a repository/API bridge for the current Soll backend `/api/v1/assistant/ask`; it sends only a safe mobile summary/context and maps the answer into `MetaCoordinatorResponse`.
- The current backend endpoint returns an answer, not executable mobile tool calls, so suggested actions remain empty until a server-side action-plan contract exists.

## Phase-by-Phase Implementation Plan

### Phase 0: Repo audit and stabilization

Goal: protect current working app before adding new modules.

Tasks:

- [x] Run current `soll_app` Gradle tests/build.
- [x] Document current package structure.
- [x] Identify DB schema/migration strategy.
- [x] Remove local build/IDE/audio artifacts from Git tracking and harden `.gitignore`.
- [x] Remove Course Coach from the app build while keeping archived source as a donor.
- [x] Translate legacy resource strings and Telegram command user-facing text to Russian.
- [x] Add centralized Telegram command safety gate for permissions and risky-action confirmation.
- [x] Remove destructive Room fallback and add explicit missing migration hops.
- [x] Harden manifest defaults: no backup, build-type cleartext policy, no unused background location.
- [x] Add missing tests around `CommandProcessor`.
- [x] Add service health display if not already enough.

Exit criteria:

- App builds.
- Current bot commands still work.
- No architecture rewrite yet.

Implementation notes:

- 2026-05-08: `.gradle`, `.idea` and personal `voice/` audio were removed from Git index only; local files stay on disk and ignore rules now cover Android/Gradle/Codex local state.
- 2026-05-08: added `CommandSafetyGate`, `CapabilityPermissionChecker` and `CommandConfirmationParser`; risky Telegram commands now require trailing `--confirm`, and Android permissions/special access are checked before handlers run.
- 2026-05-08: Room now exports schema, has explicit `1->2` and `3->4` migrations, and no longer uses `fallbackToDestructiveMigration()`.
- 2026-05-08: manifest now disables backup, removes unused `ACCESS_BACKGROUND_LOCATION`, and uses debug/release cleartext placeholders.
- 2026-05-11: started the global stabilization/refactor pass by removing blocking `runBlocking` from music service teardown, replacing TTS playback polling `Thread.sleep` with coroutine `delay`, and adding guard tests for these regressions.
- 2026-05-12: completed the next stabilization pass: foreground music/TTS/bot services no longer restart themselves unnecessarily, offline note/sync queues retry reliably, gadgets use normalized endpoints/auth rules, scanner/field-map actions are capability-gated, Android book search contracts are removed, navigation now uses a single destination registry, and the repo cleanup removes tracked build/IDE/temp artifacts.
- 2026-05-12: verification for this pass is green: `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, plus Soll server `tests/test_gadget_telemetry.py`.

### Phase 1: Capability Registry

Goal: all commands have risk tier and permission contract.

Tasks:

- [x] Add domain model `Capability`.
- [x] Add registry with current commands.
- [x] Add settings toggles.
- [x] Enforce registry in `CommandProcessor`.
- [x] Add audit event when command blocked.
- [x] Add tests for allowed/blocked commands.

Exit criteria:

- Existing commands execute only through capability check.
- Risky actions can be disabled globally.
- Unknown commands are logged.

### Phase 2: ToolJob Runner

Goal: make every action observable.

Tasks:

- [x] Add ToolJob DB tables.
- [x] Add runner and handler interface.
- [x] Convert at least 3 current commands to jobs: photo, record, download.
- [x] Add Tools job list UI.
- [x] Add progress/log details.
- [x] Add user cancellation for queued/running jobs.

Exit criteria:

- User can see running/completed/failed jobs.
- Telegram can return job status.
- Failed jobs contain useful error text.

### Phase 3: Soll Backend Sync

Goal: connect app to desktop/server Soll.

Tasks:

- [x] Add server URL/token settings.
- [x] Add Retrofit client.
- [x] Health check.
- [x] Fetch daily tasks.
- [x] Create raw note.
- [x] Upload a file to raw.
- [x] Add offline retry queue.
- [x] Upgrade raw note screen into local-first mobile notes: list, editor, tags, filters, settings, status and WorkManager retry.
- [x] Route UI, Telegram `/raw` and voice notes through the same local note store before Soll upload.

Exit criteria:

- From phone: create a raw note in Soll.
- From phone: see today's tasks.
- Sync failure does not lose data.
- Notes remain visible on the phone even when the Soll server is offline.

### Phase 4: Task Board

Goal: daily execution like Jira-lite.

Tasks:

- [x] Add task board UI.
- [x] Status transitions.
- [x] Task detail with attachments.
- [x] Start/finish/defer flow.
- [x] Sync with Soll daily tasks.
- [x] Preserve and show queued task evidence during server refresh/conflicts.

Exit criteria:

- User can take a task for today, work it, attach output, mark done or defer.

### Phase 5: Voice V1

Goal: push-to-talk command loop.

Tasks:

- [x] Add voice screen.
- [x] Add STT adapter.
- [x] Route recognized text to command parser.
- [x] Speak response via current TTS.
- [x] Log voice command events.

Exit criteria:

- User can say a safe command and get spoken response.
- Risky voice actions require confirmation.

### Phase 6: ESP Connector V1

Goal: connect to an Aquik-style ESP manually.

Tasks:

- [x] Add device profile schema.
- [x] Add WebSocket connector.
- [x] Add manual IP connection UI.
- [x] Implement `auth`, `getInfo`, `getSensors`.
- [x] Show telemetry.
- [x] Add reconnect/backoff.

Exit criteria:

- Phone can connect to test ESP/WebSocket mock and show sensor data.

Progress 2026-05-12:

- Redesigned `Гаджеты` around the device-first flow: saved device list, explicit discovery/add screen, then per-device detail.
- Archived the previous all-in-one service editor as an internal source component and split its useful parts into per-device tabs: sensors, control, parameters, schedules, automation, diagnostics and events.
- Added first reliable discovery slice for Soll/Aquik devices: mDNS/NSD, SSDP, manual host/IP, QR/code import and Wi-Fi AP scan/provisioning entry point. BLE and SmartConfig stay prepared but not exposed as primary working flows until firmware/GATT validation.
- Aligned discovery with the local Aquik Android/firmware sources: support `AQUIK-*` SSDP headers, `aquik://setup?...` QR payloads, `ws_port/http_port` mDNS TXT records and `/device.json` enrichment from explicit HTTP endpoints.
- Synced the Android discovery constants with the Soll server contract `soll-gadget-discovery-v1`; protocol changes that affect Android must be reflected in `soll_app` and the Soll project together.
- Added parsing tests for `device.json`, QR pairing payloads and SSDP headers.
- Added Android-side `/api/v1/protocol/schema` compatibility check for Soll server discovery contract, with Russian UI status in `Гаджеты -> Сервер Soll` and unit coverage for schema mismatch warnings.
- Added Device QA row for Soll server protocol schema compatibility, so phone/server verification is tracked in Settings -> Device QA.
- [ ] Device QA: verify `Гаджеты -> Сервер Soll -> Контракт` against the target Soll server from the target phone.
- Added Android -> Soll server command relay for selected server gadgets: Android can enqueue safe read commands (`getSensors`, `getActuators`, `getSystemInfo`) through `POST /api/v1/gadgets/{device_id}/commands`, and the protocol compatibility check now validates command routes too.
- Added command history/status loop for server gadgets: Soll exposes `GET /api/v1/gadgets/{device_id}/commands`, Android loads recent command states after selection/refresh/send, and `Гаджеты -> Сервер Soll` shows Russian command history.
- Added read-only server command worker: WorkManager claims one command, ACKs it, resolves server gadget to local KnownDevice, executes only read-only profile commands through a short-lived WebSocket connector, posts result, and records a local audit event.
- Added explicit manual write flow: `manual_ready` commands show a guarded `Вручную` button, execute only after UI confirmation and local binding resolution, then post `manual-result` back to Soll as `done` or `failed`. Background sync does not claim or execute write commands.
- Added mesh/outbox worker v0 in the existing sync path: it claims one outbox item per run, ACKs allowlisted JSON payloads and sends failed attempt for unknown/command payloads without arbitrary execution.
- Expanded Settings -> Device QA for the protocol/hardware path: separate manual checks now cover Soll contract worker schemas, server-local binding, mesh/outbox claim/ACK/retry, read-only command worker result reporting, and explicit manual write execution on a real ESP/Aquik target.

### Phase 7: Scanner V1

Goal: generic QR/EAN capture.

Tasks:

- [x] Add scanner UI.
- [x] Add EAN checksum tests.
- [x] Add multi-frame confirmation.
- [x] Add history and duplicate suppression.
- [x] Add export selected scans to raw.

Exit criteria:

- Scanner reliably captures EAN/QR and can create raw note.

### Phase 8: Books / Research Tool

Goal: requested-only book ingestion from mobile.

Tasks:

- [x] Add Books search UI connected to Soll.
- [x] Show deduped results.
- [x] Select format/result.
- [x] Download/process via backend tool.
- [x] Show progress and metadata.
- [x] Removed from Android app scope on 2026-05-07; backend API code remains as archived integration surface.

Exit criteria:

- User can request topic/title, select a result, and get a raw/wiki-processing job.

### Phase 9: Security Lab V1

Goal: safe dual-use knowledge workflow.

Tasks:

- [x] Add security lab templates.
- [x] Add dual-use policy enforcement.
- [x] Add owned-lab-only confirmation.
- [x] Add RF/legal checklist note generator.
- [x] Block payload storage/execution.

Implementation notes:

- Implemented as internal domain policy and templates without adding a new visible app tool.
- Future UI must call `DualUsePolicy.review()` first and log through the Security Lab audit event contract.

Exit criteria:

- Flipper/hardware/security research becomes safe notes and tasks, not payload automation.

### Phase 10: Proactive Suggestions

Goal: useful suggestions without spam.

Tasks:

- [x] Add simple scenario detector.
- [x] Add suggestion engine with daily cap.
- [x] Add accept/dismiss/snooze feedback.
- [x] Add dashboard card.
- [x] Add Telegram notification setting.

Exit criteria:

- App suggests only high-confidence useful actions and learns from feedback.

### Phase 11: Memory

Goal: visible, controllable personalization.

Tasks:

- [x] Add assistant memory tables.
- [x] Add memory settings.
- [x] Add memory viewer/clear/export.
- [x] Sync important summaries to Soll, not raw private logs.

Exit criteria:

- User can inspect and delete what assistant remembers.

### Phase 12: Server Meta-Coordinator

Goal: complex requests use Soll/Forg/server agents.

Tasks:

- [x] Add Ask Soll UI/command.
- [x] Add request schema with context and allowed capabilities.
- [x] Add response schema with proposed answer and optional tool calls.
- [x] Confirm tool calls before execution.
- [x] Log full decision chain.

Implementation notes:

- Implemented as domain schema, local confirmation gate, fallback response, local decision-chain Markdown log and visible Russian `Спросить` UI/command.

Exit criteria:

- Complex request can be planned by server and executed locally only after gates.

## Imported Signals From Current Soll Tasks

These items are already present in the current Soll daily/task-board flow and must be preserved when this raw note is processed into wiki/tasks.

### Already implemented in desktop Soll and should influence mobile Soll App

- Safety + Intelligence Foundation v1 is already implemented in Soll desktop/backend: capability tiers, scenario detector, preflight, environment status, daily deep audit, project memory, insight ranking and briefing opportunity block. In `soll_app`, the matching mobile work is not to reimplement all desktop logic locally, but to expose these concepts through mobile capabilities, task sync, confirmations and event logs.
- Health-aware LLM routing is already implemented server-side: scenarios use deterministic provider order and cooldown/fallback. In `soll_app`, complex reasoning should call the server meta-coordinator instead of embedding multiple provider routes inside Android.
- `ai-developer-tools-a5895398` is a review-only topic index and substantially overlaps `ai-0ad60b3f`; it does not justify a second Android or server feature. Consolidate both records in the desktop/server AI-development KB, then choose one concrete non-sensitive Soll development workflow and collect primary documentation for the relevant candidate tools. The first spike should evaluate context packaging and coding-agent assistance on 3-5 representative tasks against the current `rg`/manual baseline, recording task completion, relevant-context precision, unnecessary edits, tests, time/cost, least-privilege tool scope, audit trail and rollback. Keep generated changes behind review/test gates with no automatic deploy. Android should only show server-produced summaries, diffs, status and explicit approve/reject actions through existing Chat, Tasks, `Ask Soll`, `Источники` and automation surfaces; create separate Computer Use, Deep Research, MCP or IDE work only after a primary source and concrete Soll use case exist.
- `ai-0ad60b3f` is a research-only aggregated overview, not an implementation-ready Android feature. Its themes belong in the existing desktop/server meta-coordinator and project-intelligence direction: use it as a taxonomy for a later evidence-backed comparison of AI assistants, agent environments, MCP connectors, context-engineering practices and controlled browser/system tools. Require primary documentation and a concrete workflow before implementation, then evaluate least-privilege connector scopes, explicit confirmation gates, context/data boundaries, action audit logs, rollback and failure handling. Android should expose only server-produced summaries, tasks, status and approve/reject actions through existing Chat, Tasks, `Ask Soll`, `Источники` and automation surfaces; do not put connector credentials, unrestricted MCP hosts, autonomous coding or browser/system execution on the phone.
- `ios-media-feed-yandex` is a research-only mobile-performance signal, not a request to add autoplay video now. The current Chat and `Источники` text/image cards do not justify a player subsystem; if a concrete media-feed scenario is approved, place it in the existing `Источники` lane, extract the growing UI from `TaskBoardScreen` into a dedicated sources/media-feed presentation module, keep pagination and media metadata server-driven, and gate Media3 player reuse/preloading with lifecycle, memory, cancellation and observability checks. Chat should remain a digest + article-card surface rather than an autoplay feed.
- `llm-a80dd931` is a research-only, weak-summary multi-provider bot signal, not a new Android provider adapter. Do not add Groq/Google SDKs, provider keys, client-side rate-limit handling or prompt-only persona switching to `soll_app`; if the idea is refined, do it in the existing desktop/server provider router with per-conversation context isolation, deterministic routing/fallback and server-returned provider/model metadata. The current backend-mediated `askModelChat` path is already the correct Android integration point.
- `claude-mythos-release` is a model-release monitoring signal, not an Android implementation request. Official docs now make the safe split explicit: Claude Fable 5 is the generally available high-capability route, while Claude Mythos 5 is limited/invitation-only through Project Glasswing and has model-specific retention constraints. If Soll evaluates it later, implement only in desktop/server provider routing with access checks, benchmark gates, fallback/refusal handling, cost and data-retention policy; Android should consume only existing `Ask Soll`/`Инсайты`/`Roadmap`/`Источники` summaries through the current Soll API.
- Smart commit watchdog v2 is already implemented server-side as dry-run-first safe auto-commit. In `soll_app`, this should appear as a task/notification/action card, not as direct mobile git automation.
- Automation page already exists in desktop Soll for preflight, LLM health, commit watchdog and daily deep audit. In `soll_app`, the related UI should be lightweight: status, approvals, brief summaries and links to run safe server jobs.
- `claude-science-90d35df4` is a research-only external scientific-workbench signal, not an Android feature request. Do not add Claude Science, Anthropic-specific scientific connectors, Jupyter/R/PubMed workflows or local scientific data processing to `soll_app`; first evaluate official docs, access/privacy terms and reproducibility behavior in a desktop/server sandbox, then expose only validated summaries/tasks through `Инсайты`/`Roadmap`/`Источники`/`Ask Soll`.
- `claude-managed-digest` is a source-monitoring/digest-agent signal, not a reason to add Claude Managed Agents SDKs, credentials, shell access or autonomous wiki mutation to Android. If explored, implement it as a desktop/server read-only prototype after official-doc/access verification: allowlisted sources only, no secrets, no shell, hard cost/time limits, markdown output with citations into raw/outputs, and manual confirmation before writing wiki/tasks. `soll_app` should only show server-produced digest cards, source items, tasks and approve/reject prompts through existing Chat, Tasks, Sources and Ask Soll surfaces.
- `multiplayer-interactive-world-models-with-repres-18709be4` is a review-only world-model research signal, not an Android ML feature or a drop-in replacement for Soll's LLM agents. First verify the full paper, code, dataset terms, license, hardware requirements and reported baselines. If they hold, place a bounded spike in an isolated desktop/server research sandbox next to the internal agent-evaluation contour: use one synthetic, non-sensitive cooperative environment, record synchronized observations plus each participant's actions, and compare joint learned rollouts with a deterministic scenario/test baseline on state/frame prediction, per-participant controllability, cross-agent consistency, long-rollout stability, latency, memory and GPU cost. Keep training, model weights and simulation execution off Android and disconnected from production tasks/device control; Android may consume only summaries, status and explicit approve/reject tasks through existing `Источники`/`Инсайты`/`Roadmap`/Tasks/Chat/`Ask Soll` surfaces. Integrate with the meta-coordinator only after a concrete simulation use case shows a reproducible advantage over the simpler baseline.
- `omniopt-taxonomy-geometry-and-benchmarking-of-mo-2f762a3f` is an optimizer-selection framework for ML training, not an Android inference feature. Keep its five-stage meta-pipeline and six effect objectives as a desktop/server KB and evaluation cookbook, and do not add PyTorch, optimizer packages, fine-tuning or benchmark execution to `soll_app`. Open a bounded spike only for a concrete Soll-owned training/fine-tuning workload: retain AdamW as the reference, select 2-3 alternatives by the binding quality/runtime/memory/stability constraint, and compare them on identical model, data, initialization/seeds, schedule and tuning budget. Record validation quality, steps/time-to-target, peak optimizer memory, per-step cost, divergence/gradient stability, hyperparameter sensitivity and held-out transfer; verify code/license and target-architecture support, and promote only a reproducible workload-specific gain rather than importing the paper's tiers as global defaults. Android may consume server-produced summaries, status and explicit approve/reject tasks through existing `Источники`/`Инсайты`/`Roadmap`/Tasks/Chat/`Ask Soll` surfaces, but must not choose or run training optimizers.
- `kotlin-coroutines` is an educational explanation of `suspend`, `Continuation`, continuation-passing style, `COROUTINE_SUSPENDED` and compiler-generated state machines, not a feature or a reason to replace the project's existing `kotlinx-coroutines` stack. The Kotlin specification confirms the core transformation, but production guidance must come from the official coroutine API because the article intentionally simplifies dispatcher and cancellation behavior. Apply it first as a focused engineering review of the single-shot callback adapters in `PhotoHandler` and `LocationHandler`, using the more cancellation-aware `ActivityTrackingService` and `FieldMapRepository` bridges as comparison points. Check single-resume safety across success/error/disconnect races, prompt cancellation, callback/token unregistration, timeout behavior, closeable camera/image resource ownership, and propagation of `CancellationException`; add cancellation/race tests before changing those bridges. This review-only item must not hand-write continuations or compiler state machines, introduce another coroutine abstraction/dependency, or trigger a coroutine-version upgrade by itself.
- `pvs-studio-cmake-4db4899a` documents CMake 4.3's official PVS-Studio hook, but it has no executable target in the current Gradle/Kotlin Android project: the repository has no `CMakeLists.txt`, `.cmake` files, C/C++ sources, NDK setup or `externalNativeBuild`, and the JNI libraries packaged in AARs are prebuilt upstream artifacts. Therefore this review-only signal must not add CMake, NDK or PVS-Studio to `soll_app`. Place a future bounded evaluation in a separate CMake-based C/C++ firmware repository such as Aquik/ESP only when it can pin CMake 4.3 and PVS-Studio, supply license material outside version control, and build a representative target with a Makefile or Ninja generator. Start in non-blocking build-log mode using `CMAKE_<LANG>_PVS_STUDIO` or target-level `<LANG>_PVS_STUDIO`; record analyzer/runtime versions, elapsed CI time, warning counts by severity, a reviewed baseline and suppression rationale. Promote to a required quality gate only after setting an explicit new-warning/severity threshold and proving acceptable runtime and licensing. The integrated mode's build log is not a standalone full report artifact, so archival or SARIF output requires a separately verified reporting step. Android may consume a server-produced result summary or approval task later, but it must not run the analyzer or own its license.
- `agenticdatabench-a-comprehensive-benchmark-for-d-2763da91` is implementation-ready for server-side Soll agent evaluation, not for Android data-agent execution. Build a desktop/server internal eval harness with 5-10 synthetic/non-sensitive source-monitoring and KB tasks, skill labels, gold outputs, regression metrics and manual review gates; Android should only consume eval summaries, source cards, status and approve/reject tasks through existing Soll API surfaces.
- `deploy-automation-15e48b34` is a high-usefulness DevOps signal, but not an Android deployment feature. Keep server provisioning, Nginx, SSL, fail2ban/user setup, GitHub/CD scripts, credentials and production mutation in a desktop/server deployment spike: first audit the current Soll/Soll_app deploy path, verify the source scripts/repo/license, adapt only non-secret automation in a test environment, and expose Android-facing state only as deploy status, checklist tasks, alerts and explicit approve/reject prompts through existing Soll API surfaces.
- `startup-niche-ai-eea80802` is a research-only market-discovery signal, not a mobile feature request. Do not add automatic niche selection, SaaS-validator SDKs, Reddit/app-review mining, or autonomous business-decision logic into Android; first verify the full article and tool list, then prototype a desktop/server workflow that records inputs, scoring criteria, evidence links, risk notes and explicit human approval before creating roadmap lines or tasks. Android should only consume the resulting source cards, insights, opportunity tasks and approve/reject prompts through `Источники`/`Инсайты`/`Roadmap`/`Tasks`/`Ask Soll`.
- `codegraph-claude-code-grep-8a6a6a06` belongs in a desktop/server dev-only project-intelligence spike, not in Android app code. Evaluate it by indexing `D:\Projects\soll_app` or a smaller non-sensitive repo in an isolated local environment, checking CodeGraph `status/search/context/impact` against plain `rg` on 3-5 real Soll questions, and only then decide whether the server meta-coordinator should expose summarized project insights/tasks back to Android.
- `delegation-e723ba31` is a research-only, weak-summary process note, not a new Android feature. Implement its useful part in the desktop/server task-creation and meta-coordinator templates used for source, roadmap and agent-generated tasks: context, goal, required/desirable outcomes, constraints, available tools, explicit tradeoffs, stages, acceptance criteria and a separate verification step. The current Android task contract already carries `title`, `description`, `sourceRef`, status and routing metadata, so the template can initially be serialized into `description` and shown/edited through the existing Tasks screen; add optional structured checklist fields only after the server contract needs them and can remain backward-compatible.
- `cursor-iphone-app-idea` should be implemented as a read-only agent-control lane inside the existing Tasks/assistant-control surfaces: show server agent status, logs, PR/task result summaries and explicit approve/reject actions, but do not add mobile code-generation, local agent launch, or automatic dangerous command execution.
- `cursor-iphone-app-1` confirms the same mobile-agent UX direction, but its evidence is still unverified. Implement it only inside the existing Chat/Tasks/Assistant control path: Android may show server agent status, logs, artifacts, diffs/PR summaries and approval/reject actions; sandboxing, code edits, PR creation, secret isolation and audit history stay server-side.
- `dark-factory-agent` is a research-only, weak-evidence signal. Do not build an autonomous mobile code-generation/deploy pipeline from it; any useful patterns belong in the existing server meta-coordinator/action-plan contract, with Android limited to local capability checks, explicit approvals, tool-job execution and audit/decision logs.
- `crashprobe-python-92848d5f` is a research-only, weak-summary Python debugging signal. Do not add it to the Android/Kotlin runtime or make it a `soll_app` dependency; if repo/license/thread behavior is verified later, place it only in desktop/server dev diagnostics or one-shot Python tool wrappers.
- `content-pipeline-monogame-da3a9972` is research-only external gamedev knowledge. Do not add MonoGame/FNA, XNB, asset pipeline tooling or asset-conversion UI to `soll_app`; keep the takeaway as a KB note about preferring raw asset loading for small MonoGame/FNA prototypes, and revisit libraries such as XNAssets/SpriteFontPlus/FontStashSharp only if a separate game prototype becomes real.
- `arcade-fpga-diy-1` is a weak/medium hardware research signal, not a reason to build a dedicated arcade or FPGA module in `soll_app`. Reuse only safe engineering lessons through the existing `Gadgets`/hardware-lab and wiki surfaces: component inventory, prototype checklist, video/SCART constraints and FPGA/NES terminology; do not surface coin/payment bypasses or mains-TV modification steps as mobile tool instructions.
- `arduino-a790a76e` is a review-only overview of Arduino boards, sensors, motors, thermal printers, weather stations and household DIY devices, not an implementation-ready feature. Keep it as a desktop/server KB and candidate-discovery signal; do not add a generic Arduino screen, direct board/USB-serial support or Gyver UI dependencies to `soll_app`. The best first follow-up is one networked sensor or physical notification-indicator project because it can reuse the existing `Generic ESP WebSocket` profile, Soll gadget telemetry/events and confirmation-gated controls. Firmware should own timing and hardware I/O while Android remains the headless setup, telemetry and control surface. Before creating an implementation task, require a primary project link, reproducible schematic, BOM, firmware source and library licenses, voltage/current and power-supply limits, Soll protocol payloads, failure/safe-state behavior and real-device QA steps. Thermal printing and motorized devices remain deferred until a concrete user workflow justifies a dedicated profile or actuator contract.
- `cv-retail-challenges` is an early product-opportunity signal, not a direct Android CV module request. Implement first through the existing Scanner/Raw Note/task-evidence lane: Android can capture EAN/photo/shelf-audit inputs and show server-returned findings, but YOLO/VLM inference, multi-camera tracking, customer-path analytics, self-checkout anti-fraud, queue/staffing optimization and hot-zone heatmaps belong in desktop/server research prototypes with explicit privacy, retention and model-evaluation gates.
- `3d-a0eee0f5` is a research-only industrial 3D/CAD deliverable QA signal, not an Android CAD module. Do not add AVEVA connectors, 3D model viewers, model diffing, or engineering-data registries to `soll_app`; first turn the methodology into a desktop/server checklist for tag completeness, object naming, contractor evidence and engineering-data handoff, then expose only mobile capture/review actions through Scanner, Raw Note, Field Map, Tasks and `Источники`.
- `codex-sites-work` should not create a visible Android module now. The implementation point is a desktop/server governance checklist for Codex Sites prototypes: use only mock or non-sensitive data, do not pass secrets or PII, require manual review of generated code, and publish only workspace-internal prototypes; Android can later show prototype review/tasks through existing Tasks/Assistant control surfaces if needed.
- `clustmetalearn` is a weak-summary clustering/meta-learning signal, not a mobile ML feature. Implement first as a desktop/server sandbox: verify the paper/repo/license, run it only on anonymized CSV metadata for notes/sources/projects, compare CVI/algorithm recommendations against existing Soll source/RAG/learning grouping, and expose any validated result to Android only through existing `Инсайты`/`Roadmap`/`Источники`/`Ask Soll` surfaces.
- `cloudflare-supabase` is an infrastructure/serverless pattern, not an Android module. If used, implement it as a server-side spike behind the existing Soll API: Cloudflare Worker may proxy/cache selected Supabase-backed data with env-managed secrets, CORS policy, TTL/fallback tests and audit logging; `soll_app` should keep using the current `SettingsRepository`/`SollRepository` endpoint flow and consume results through existing `Источники`, `Инсайты`, `Roadmap`, `Tasks` or `Ask Soll` screens.
- Books desktop page already exists for requested-only Telegram book workflow: exact/topic query, max 50 topic results, dedupe, select result, choose format, optional process after download and process last downloaded. Mobile `soll_app` should become a remote client for the same controlled backend tool.
- Book processing fallback for wrong extension archives is already handled server-side through metadata sidecar. Mobile must show clear status such as "metadata only" or "no downloadable format", not retry blindly.

### Active backlog signals from `task-board`

- Aquik firmware direction is now headless: remove `AQUIK_SETTINGS_PANEL`/ESP web UI assumptions from the mobile plan. Any config, diagnostics, schedules and OTA controls must be exposed through the special Android<->ESP protocol.
- Aquik/Gyver references are firmware-only implementation inspiration at most. `soll_app` must not depend on GyverPortal, Settings, GyverHub, or an ESP-hosted UI.
- MonoSales has a pending Monolith/Android-agent sync-contract task. The useful part for `soll_app` is scanner/session/offline workflow patterns; business CRM assumptions should remain in MonoSales, not pollute the assistant core.
- Monolith has a pending backend sync assumptions task. It matters indirectly only because MonoSales/Ivaro-style workflows may share field-task and sync concepts with Soll App.

### Current daily notes that must remain as constraints

- Telegram book bot must stay requested-only: exact book/author or bounded topic query such as `Хакинг`, max 50 current search results, no whole-group download, no duplicates across sources.
- Flipper Zero and hacking/security material must remain safe-lab knowledge: official/safe references, owned-lab checklists and threat models. Do not store payload dumps, bypass instructions or offensive automation.
- If a book result only exposes `Читать онлайн` and no downloadable formats, the UI should explain that no downloadable format was returned by the bot.
- Run only one Telegram userbot/server process for live checks; parallel process starts can hit `database is locked`.

## Initial Backlog

### P0

- [x] Build `soll_app` and run tests.
- [x] Add `CapabilityRegistry`.
- [x] Map all current commands to risk tiers.
- [x] Add settings for enabling/disabling risky capabilities.
- [x] Add audit event table.
- [x] Add service health dashboard card.

### P1

- [x] Add `ToolJobRunner`.
- [x] Convert photo/record/download to ToolJobs.
- [x] Add Soll backend health/settings.
- [x] Add raw note upload.
- [x] Add daily tasks fetch.
- [x] Add Tasks screen with basic statuses.

### P2

- [x] Add push-to-talk voice commands.
- [x] Add music player V1.
- [x] Add scanner V1 foundation.
- [x] Add NFC tools V1.
- [x] Add ESP manual WebSocket connector.
- [x] Archive/remove Android books search UI; not needed in current app scope.
- [x] Add file/media upload to raw.

Progress 2026-05-07:

- Verified ESP manual WebSocket connector is already implemented in the Devices module: manual host/port/path input, token storage, OkHttp WebSocket connection, auth/info/config/sensors commands, reconnect/backoff, status and event log UI.
- Localized the existing Android Logs screen and `/logs` Telegram command to Russian as part of the Russian-first app constraint.

### P3

- [x] Add proactive suggestions.
- [x] Add security lab templates.
- [x] Add ESP provisioning wizard.
- [x] Add task attachments and offline retry.
- [x] Add memory viewer.

### P4

- [x] Add server meta-coordinator integration.
- [x] Add multi-device profiles.
- [x] Add advanced local STT/wake-word.
- [x] Add richer ESP actuator control.
- [x] Add map/offline field modules if still useful.

Progress 2026-05-08:

- Advanced voice V1 uses Android on-device recognition when available and adds a manual "Солл" phrase gate. Always-on wake-word remains intentionally out of scope until battery and privacy behavior are tested on the target phone.
- Meta-coordinator integration V1 connects Android to the existing Soll `/assistant/ask` endpoint through `SollGateway.askMetaCoordinator()`, with safe context stripping, local fallback and the visible Russian `Спросить` entry point. Suggested actions are shown as decision context and are not executed automatically.

## Acceptance Criteria

The roadmap is working when:

- `soll_app` can act as a reliable Android foreground assistant.
- Every command/tool has a capability tier and audit behavior.
- User can see tool progress and logs.
- User can send raw notes/files from phone to Soll.
- User can work daily tasks on phone and sync them to Soll.
- User can use voice for safe commands.
- User can connect at least one ESP-style device and read telemetry.
- User can scan barcodes/QR and attach scans to raw/tasks.
- User can search/download/process books only by explicit request.
- Hardware/security content is safe-lab gated and cannot become payload automation.

## Risks

- Android background restrictions can still kill bot service on some OEM firmware.
- Too many permissions can make the app look dangerous; explain each permission at point of use.
- Direct code port from MRF can waste time; port concepts, not files.
- ESP protocols can diverge; use profiles and schemas.
- Voice/wake-word can drain battery; start with push-to-talk.
- Proactive suggestions can become annoying; use daily caps and feedback.
- Hacking/security content can become unsafe; enforce blocked actions and owned-lab-only policy.
- Book downloads can create duplicates or bad metadata; enforce requested-only and dedupe.

## Non-Goals

- No full rewrite of current app before stabilizing existing features.
- No root/Magisk dependency in initial phases.
- No offensive automation or payload repository.
- No automatic download of whole Telegram book groups.
- No always-on wake-word in V1.
- No heavy local LLM on Android in early phases.
- No direct dependency on Aquik as the only device type.

## Source Links

Local projects:

- `D:\Projects\soll_app`
- `D:\Projects\Android\MrF`
- `D:\Projects\Aquik`
- `D:\Projects\Aquik firmware`
- `D:\Projects\Android\MonoSales`
- `D:\Projects\ivaro`

Soll wiki:

- `daily/2026-05-06.md`
- `outputs/briefing-2026-05-06.md`
- `outputs/daily-deep-audit-2026-05-06.md`
- `wiki/soll-project.md`
- `wiki/task-board.md`
- `wiki/mrf-project.md`
- `wiki/aquik-project.md`
- `wiki/monosales-project.md`
- `wiki/ivaro-project.md`
- `wiki/flipper-zero-security-research.md`
- `wiki/flipper-zero-unofficial-ecosystem.md`
- `wiki/telegram-book-bot-tool.md`
- `wiki/хакинг-мобильных-телефонов.md`
- `wiki/settings-esp-library.md`

Key raw:

- `raw/telegram-book-bot-tool-audit-2026-05-06.md`
- `raw/flipper-zero-security-research-2026-05-05.md`
- `raw/flipper-zero-unofficial-ecosystem-2026-05-06.md`
- `raw/books/Хакинг мобильных телефонов (pdf).pdf.md`

## Next Processing Recommendation

После добавления этого raw-файла:

1. Обработать его в wiki как `soll-app-superassistant-roadmap.md`.
2. Извлечь P0/P1 задачи в `task-board`.
3. Связать wiki entry с `soll-project`, `mrf-project`, `aquik-project`, `monosales-project`, `ivaro-project`, `flipper-zero-security-research`, `telegram-book-bot-tool`.
4. В утренний briefing добавлять только P0/P1 задачи, а не весь roadmap.
5. Не запускать implementation всех фаз сразу; двигаться по фазам с тестами.
