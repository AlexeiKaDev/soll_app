# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Soll is an Android Telegram bot application that runs as a Foreground Service. It receives commands via Telegram long polling and executes device control functions (camera, SMS, location, etc.).

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean and rebuild
./gradlew clean assembleDebug

# Run lint checks
./gradlew lint

# Install on connected device
./gradlew installDebug
```

## Architecture

**Clean Architecture layers:**
- `data/` - API services, Room database, repositories, BotService
- `domain/` - Command handlers and business logic
- `presentation/` - Jetpack Compose UI (MainActivity, screens, ViewModels)
- `di/` - Hilt dependency injection (AppModule)

**Key components:**

1. **BotService** (`data/service/BotService.kt`) - Foreground Service running Telegram long polling loop. Uses WakeLock and START_STICKY for persistence. Handles service lifecycle and delegates message processing to CommandProcessor.

2. **CommandProcessor** (`domain/command/CommandProcessor.kt`) - Routes incoming commands to appropriate handlers. Maintains handler registry and logs command execution.

3. **CommandHandler** (`domain/command/CommandHandler.kt`) - Abstract base class for all command handlers. Provides `reply()` and `send()` helper methods.

4. **Command handlers** (`domain/command/handlers/`) - Individual handlers for each command (PhotoHandler, SmsHandler, LocationHandler, etc.). Each handler implements `execute(message, args)`.

## Adding New Commands

1. Create handler class in `domain/command/handlers/`:
```kotlin
class MyHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {
    override val command = "mycommand"
    override val description = "Description: /mycommand [args]"

    override suspend fun execute(message: Message, args: String?) {
        // Implementation
        reply(message, "Response text")
    }
}
```

2. Register in `CommandProcessor.kt` handlers map:
```kotlin
"mycommand" to MyHandler(context, telegramRepository),
```

## Tech Stack

- **UI**: Jetpack Compose, Material 3, Navigation Compose
- **DI**: Hilt
- **Database**: Room (SollDatabase)
- **Network**: Retrofit + OkHttp + Moshi
- **Security**: EncryptedSharedPreferences for bot token storage
- **Background**: Foreground Service with dataSync type
- **Target SDK**: 34, Min SDK: 26

## Important Considerations

- Bot token is stored encrypted via EncryptedSharedPreferences
- Service requires battery optimization exemption for reliable background operation
- OEM-specific restrictions (MIUI, EMUI) may kill background services - see `docs/plan.md` for mitigation strategies
- Long polling timeout is 30 seconds (configured in OkHttpClient)
- BootReceiver restarts service after device reboot if autostart enabled

## Task Completion Chat Protocol

Before reporting that a Soll task is complete, follow
[`docs/task-completion-chat-protocol.md`](docs/task-completion-chat-protocol.md).
Do not send a bare "task done" acknowledgement. The final chat message must say
what changed, list the affected files, report tests, state the actual commit and
push status, and say whether the server or Android app needs a reload/restart.
Use the documented reload procedure whenever a server reload is required, and
only report it as successful after health/readiness and task-specific smoke
checks pass.
