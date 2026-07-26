---
task_id: 8f29544f0dc849eaa10e5027da012f80
source_ref: insight/21afd75d67de
verification_artifact: Soll/outputs/source-processing/task-8f29544f0dc849eaa10e5027da012f80-slash-path-verification.md
value_metric: "1 exact slash-path contract added; red reproduced with 1/8 failed tests; green verified with 8/8 focused and 355/355 mandatory Android unit tests passing"
verified_at: 2026-07-17 Europe/Chisinau
---

# Slash-path launch contract verification

## Contract

The single input path `/tasks` must resolve to the exact Compose navigation
destination `Routes.TASKS` (`tasks`). The contract is covered by
`AppLaunchTargetsTest.slash tasks path opens exact task board destination`.

The implementation changes only the existing launch-extra resolver: `/tasks`
is normalized to the already supported `tasks` section. It does not add a new
destination or accept other slash-prefixed values.

## Failing-to-green evidence

| Phase | Command | Observed result |
| --- | --- | --- |
| Red | `.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.presentation.navigation.AppLaunchTargetsTest" --console=plain` | Exit 1; 8 tests completed, 1 failed; `slash tasks path opens exact task board destination` failed at `AppLaunchTargetsTest.kt:47` because `/tasks` was ignored. |
| Green | same focused command after the resolver fix | Exit 0; `BUILD SUCCESSFUL`; 8 tests, 0 failures. |
| Mandatory Android unit checks | `.\gradlew.bat :app:testDebugUnitTest --console=plain` | Exit 0; `BUILD SUCCESSFUL`; 81 test classes, 355 tests, 0 failures, 0 errors, 0 skipped. |

## Value metric update

- exact input paths locked by this slice: `1` (`/tasks`);
- exact expected destinations locked: `1` (`Routes.TASKS`);
- reproduced regression before the fix: `1` focused failure out of `8` tests;
- focused result after the fix: `8/8` passed;
- mandatory Android unit result after the fix: `355/355` passed;
- new dependencies, screens, routes or external effects: `0`.
