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

## Follow-up after Bluetooth connection

The observations above describe the first inspection. Subsequent physical QA:

- EDIFIER X3 Lite is now connected as SCO input/output and A2DP output.
- At 14:21:12 the speech adapter selected EDIFIER (type 7). Russian text
  appeared in the Chat input; the route was released at 14:21:35. Offline
  recognition mode and the exact expected phrase were not confirmed.
- The user authorized pairing recovery. The ordinary Android credential was
  rotated and its saved fingerprint verified inside the installed app. The
  phone fetched exact server reply 10154. Server history was fetched again;
  deleted app-local data was not recovered.
- A controlled transient audio-focus test reproduced stale PLAYING state while
  ExoPlayer was paused. Added PAUSED state and retained the stop action; the
  same reply resumes on focus return. Three physical player tests now pass.
  Dictation cancels active reply audio and pending synthesis before recording.
- `VoicePeripheralDeviceQa` explicitly checks stored token fingerprint and
  fetches an exact reply over the phone's configured HTTPS connection. It
  passed; ordinary CI skips this opt-in physical-network test.
- Relay turn `chat-turn:8165d297701e030418608dbd433c6df8` answered: user
  10153 / assistant 10154, text `Голос работает.`. Both appeared in Android
  Chat. Policy: task_intake=false, allow_actions=false. Nine five-second polls
  were needed; this is coarse completion timing, not STT latency.
- One listen action produced one 24 kHz speech AudioTrack, 14:32:29–14:32:30,
  on port 4250, which AudioPolicy maps to EDIFIER A2DP. Text remained visible.
  This was Google system TTS fallback: public `/voice/synthesize` returned 404
  in 0.281 seconds. User confirmation of audible sound remains outstanding.
- Updated Android voice/chat JVM selection: 71 passed. APK updated with `-r`;
  manual instrumentation retained the app and its pairing.
- Battery later: 99%, USB charging, 43.0 C. No battery-drain claim.

Current remaining gates: deliberate Windows speech/STT, audible-output
confirmation, real music/call and headset-disconnect recovery, and battery
measurement unplugged. The server voice route remains unavailable. The task
stays open. Desktop capture fixes and observations are recorded in
`D:/Projects/Soll/docs/voice-peripheral-physical-2026-10-13.md`.
