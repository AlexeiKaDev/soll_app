# Android Telegram Bot Archive

Status: archived on 2026-07-01.

The Android Telegram bot is no longer the primary Soll control channel. The Android app should use the Soll server API, encrypted chat turns, mesh/outbox chat delivery, and local push notifications.

Kept for fallback/reference:
- `TelegramApiService`
- `TelegramRepository`
- old command handlers under `domain/command`
- `BotService` source code

No longer active:
- `BotService` is not registered in `AndroidManifest.xml`.
- `BootReceiver` does not start the Telegram bot.
- Settings no longer show Telegram token setup or bot autostart controls.
- Active navigation starts in `Чат Soll`; the bottom bar is `Чат`, `Задачи`, `Утилиты`, `Настройки`.
- Active notification channels are limited to Soll chat, activity, events, alerts, and tool jobs.

Primary replacement:
- `ChatScreen` / `ChatViewModel`
- `SollGateway.sendChatTurn`
- `SollGateway.executeChatAction`
- `GadgetServerSyncWorker` chat payload delivery through `mesh/outbox`
