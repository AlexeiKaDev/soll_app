# Bonsai 27B named Android target preflight

Date: 2026-08-25 (Europe/Chisinau)

Task: `daab9ea04d794ad58bf903dd2bebfa05`

## Physical target

| Field | ADB evidence |
| --- | --- |
| Manufacturer / model | DOOGEE S200 Plus |
| Android device | `M24PST` |
| ABI | `arm64-v8a` |
| SoC / GPU family | `MT6878` / `mali` |
| Total RAM | `15,889,132 kB` |
| Available RAM at preflight | `11,215,128 kB` |
| `/data` | `466 GB` total, `383 GB` available |
| Power / battery | USB powered, `43%` |
| Battery temperature | `37.0 C` |

ADB state was `device`. The measurements came from `/proc/meminfo`, Android
properties, `df -h /data` and `dumpsys battery`; no customer data, device token
or account credential is stored in this artifact.

## Decision

The exact physical target and static capacity gate are now satisfied. This is
not an inference result: the model was not downloaded, no NDK/JNI runtime was
added, and load/generate/cancel/unload, PSS/LMK, speed, thermal, quality,
tool-safety, offline/privacy and rollback gates remain unmeasured.

The next action requires a separate explicit opt-in because it downloads about
3.8 GB of third-party model weights and adds a disposable native Android
harness. Production Soll remains unchanged.
