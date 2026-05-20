# soll_app Status

Last updated: 2026-05-20 11:54 Europe/Chisinau

## Current Changes

- Roadmap protocol work from Soll is implemented in `soll_app`:
  - Android parses Soll protocol bootstrap/worker contracts and keeps background sync on device-token flow.
  - `GadgetServerSyncWorker` executes only read-only server gadget commands (`getSystemInfo/getInfo/getConfig/getSettings/getSensors/getActuators`) and posts ACK/result back to Soll.
  - Mesh/outbox worker v0 claims one item per sync run, ACKs allowlisted local payload types, records local audit events, and reports failed attempt for unknown/command payloads instead of executing arbitrary actions.
  - `Гаджеты -> Сервер Soll` shows command history and now exposes a guarded `Вручную` button for `manual_ready` write commands.
  - Manual write execution is explicit UI only: Android resolves server gadget -> local KnownDevice, runs the local ESP/WebSocket command, then posts `manual-result` to Soll as `done` or `failed`.
- Shared AI model root policy applied: all AI model files should live under `D:\AI\Models`.
- `tools/tts/asr_audit_piper_dataset.py` now resolves faster-whisper models from `D:\AI\Models\audio\whisper` and passes that folder as Faster-Whisper `download_root`.
- No models were downloaded.

## Open Tasks / Plan

- Hardware smoke remains open: test the manual write path with a real ESP/Aquik target after the device is visible and binding QA passes.
- Keep any future ASR/Whisper downloads under `D:\AI\Models\audio\whisper`.
- If another local model cache is found in this project, move it to `D:\AI\Models` and update this status file.

## Verification Notes

- 2026-05-20 targeted Gradle check passed:
  - `.\gradlew.bat :app:testDebugUnitTest --tests com.soll.data.repository.SyncReliabilityTest --tests com.soll.domain.soll.SollProtocolSchemaTest --tests com.soll.project.ProjectStabilizationGuardTest`
- Syntax check passed via in-memory `compile(...)` for the patched script.
- Broad scan no longer finds an active `WhisperModel(args.model)` call in this script.

