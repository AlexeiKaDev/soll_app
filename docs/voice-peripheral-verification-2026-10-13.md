# Voice peripheral verification — 2026-10-13

Canonical task: `ec4c63867df543fd94a02c972a928adf`.

## Fix and device verification

`AssistantVoicePlayer` used `USAGE_ASSISTANT` with automatic Media3 audio focus.
The installed Media3 1.10.0 `AudioFocusManager.setAudioAttributes` rejects
this combination: automatic focus supports `USAGE_MEDIA` and `USAGE_GAME`.
Changed playback to media usage with speech content and kept automatic focus
and becoming-noisy handling enabled. The Bluetooth API 31 helper now declares
its API requirement; its caller already checks the OS version.

Two instrumentation tests on S200 Plus / Android 15 verify actual transition
to PLAYING, stop/replay, and recovery after an invalid WAV. Test audio is
generated silence: these tests do not prove audible headset playback.

- APK + test APK build: passed.
- `testDebugUnitTest --tests 'com.soll.domain.voice.*' --tests 'com.soll.data.voice.*'`:
  15 tests passed.
- Core `.venv/Scripts/python.exe -m pytest tests/test_voice_capture.py -q`:
  4 tests passed.
- `lintDebug`: four existing errors outside voice (gadget indentation and book
  reader), 132 warnings. Both Bluetooth helper API errors are resolved.
- `git diff --check`: passed.

## Physical observations

Windows Qt diagnostics now identify a real default capture input:
`Головной телефон (Doogee Dopods-L)`, backend `qt`, count 1. WinMM also lists
the headset input. A announced bounded 10-second WinMM capture produced
320,000 PCM bytes at 16 kHz, peak 206, RMS 33.24. Local Whisper base returned
empty text in 0.47 seconds. The user phrase was not verified; this is capture
availability evidence only. Recording stays in ignored local runtime storage,
not in Git.

Android ADB serial: `S200PLUS20000101035`. AudioManager connected devices were
empty at inspection; audio history shows Bluetooth disconnected at 09:37.
Recognition service is Google TTS RecognitionService. Battery observation:
100%, USB charging, 41.0 C. This cannot measure unplugged voice battery drain.

## Test runner incident and safe rerun

The first `connectedDebugAndroidTest` used Gradle UTP defaults and uninstalled
both `com.soll.debug` and its test package after completing the tests. This
removed app-local data and pairing. `allowBackup=false`; local data recovery
is not proven. The corrected APK was reinstalled. Server-side task/chat
records were not deleted. Pairing recovery requires a new ordinary Android
Remote QR, which revokes the previous Android credential; user approval was
requested before that rotation.

The safe rerun below passed two tests and left both packages installed:

```powershell
adb -s S200PLUS20000101035 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s S200PLUS20000101035 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s S200PLUS20000101035 shell am instrument -w -e class com.soll.domain.tts.AssistantVoicePlayerTest com.soll.debug.test/androidx.test.runner.AndroidJUnitRunner
adb -s S200PLUS20000101035 shell pm list packages com.soll
```

Do not use the default connected Gradle test runner on the user's paired
installation. Build test APKs separately and use the manual instrumentation
command without uninstalling the target app.

## Remaining acceptance

Reconnect Android Bluetooth, restore pairing with approval, verify real Russian
speech on both headsets, one audible answer with retained chat text, music/call
focus and headset disconnect recovery. Measure latency on real speech and
battery while unplugged. The canonical task remains open; hardware presence
and silent playback tests alone are insufficient to close it.

Server restart is not required for these Android changes. Corrected debug APK
is installed; full paired user-flow verification remains outstanding.
