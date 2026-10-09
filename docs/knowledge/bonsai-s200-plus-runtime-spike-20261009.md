# Bonsai Q1_0: физический runtime spike на S200 Plus, 2026-10-09

## Дополнение: Vulkan и русский чат

Два отдельных CLI-теста Vulkan на Mali-G615 MC2 не прошли. При batch 128 модель загрузилась за 14,172 s, но запрос превысил 180 s; после неуспешного TERM экспериментальный процесс остановлен адресно через KILL, отсутствие PID подтверждено. При batch 16 и отключённом flash attention загрузка заняла 18,203 s; затем сервер разорвал соединение и процесс исчез. Причина исчезновения не установлена: подтверждения OOM или ошибки драйвера нет. Низкий PSS этих запусков не учитывает всю память GPU.

Для проверки влияния видимого приложения собран и установлен отдельный `com.soll.bonsaispike`: без INTERNET и других разрешений, с JNI, PerformanceHint и удержанием экрана только в собственной Activity. Производственный Soll APK не заменён. Нативный foreground-тест пока не запускался: Android exit-info зафиксировал USER REQUESTED / REMOVE TASK. Для продолжения требуется свободный телефон; это закрытие пользователем, не доказательство падения приложения. CLI-процессы тестов выгружены.

В Soll исправлены короткие английские ответы и отображение ошибок: commit `4454a48`, 45 профильных тестов passed. Backend перезапущен под Desktop supervisor, health и AI-ready HTTP 200; PID основной модели и embeddings сохранены. Это исправление локализации, а не прохождение восьми ворот Android-модели.

Модель действительно запущена на телефоне: загрузка, генерация, отмена и запрос после отмены проверены в отдельном CLI harness. Производственная интеграция не разрешена результатами этого эксперимента: измеренная CPU-скорость слишком низкая, а полный набор восьми ворот ещё не пройден.

## Воспроизводимость

- Устройство: DOOGEE S200 Plus, Android 15 / API 35, MT6878, arm64-v8a, 15 888 996 kB RAM. Fingerprint: `DOOGEE/M24PST_EEA/M24PST:15/AP3A.240905.015.A2/1789870570:user/release-keys`.
- llama.cpp: `8ae386707bed05f02354dbfc6fa5ee0cf58e5f0f`, официальный upstream; исходники не изменялись.
- NDK `30.0.16248370`, Clang 21, CMake `4.1.2`, Android platform 28, Release, CPU/NEON. OpenMP, Vulkan и OpenSSL выключены.
- Модель: `prism-ml/Bonsai-27B-gguf`, revision `f10afb355f104535e3e3e98cf7ab7795c72bd292`, `Bonsai-27B-Q1_0.gguf`, 3 803 452 480 bytes.
- SHA-256: `17ef842e47450caeb8eaa3ebfbbab5d2f2278b62b79be107985fb69a2f819aa0`. Проверен на ПК и телефоне до первой загрузки.
- Context 4096, один slot, text-only; vision, drafter и исполнение инструментов не включались. Все prompts синтетические.
- Baseline: portable arm64, 6 threads. Второй профиль: `armv8.2-a+dotprod`, 4 threads, affinity `f0` на четырёх ядрах 2,5 GHz. Это сравнение профилей с несколькими изменениями, а не изолированный benchmark одной инструкции.

## Первые измерения

| Показатель | Baseline | ARM dot-product / big cores |
| --- | ---: | ---: |
| Загрузка до ready | 5,219 s | 5,235 s |
| Prompt tokens / output tokens | 45 / 32 | 45 / 32 |
| Prefill | 0,606 tok/s | 0,811 tok/s |
| Decode | 0,372 tok/s | 0,601 tok/s |
| Время первого полного запроса | 157,547 s | 107,110 s |
| Наибольший sampled PSS | 4 339 958 kB | 4 872 325 kB |
| Первое содержательное событие перед отменой | 33,610 s | 39,312 s |
| Запрос после отмены | success | success |
| Температура батареи, до → после | 43 → 44 °C | 43 → 44 °C |

Первый ответ в обоих профилях завершился по лимиту: все 32 токена ушли во внутреннее рассуждение, пользовательское `content` осталось пустым. Это успешно завершённая генерация runtime, но не успешный ответ пользователю. Replay первого физического ответа через реальный `LocalModelClient` Soll вернул `incomplete_model_output`; внутреннее рассуждение не возвращается как ответ. Профильные тесты русской локализации и обработки ответов: 20 passed.

Третий smoke использовал ARM-профиль и `chat_template_kwargs.enable_thinking=false`. Модель вернула правильный пользовательский ответ `4`, `finish_reason=stop`, без поля рассуждения. На 47 prompt tokens и 2 output tokens потребовалось 65,015 s: prefill 0,744 tok/s, decode 0,551 tok/s. Отмена, запрос после отмены и unload снова прошли. Отключение thinking исправило пустой ответ в данном синтетическом случае, но не устранило медленный prefill и не доказывает общее качество.

Процессы выгружены. В baseline обнаружена гонка диагностического polling `/proc` после TERM; отдельная проверка подтвердила отсутствие процесса PID 8439. Диагностика исправлена, во втором и третьем профилях `unloaded=true`. OS page cache не сбрасывался: это свежие процессы с потенциально закешированными весами, а не подтверждённые полные cold-device циклы.

## Восемь ворот: что осталось

1. Совместимость: отдельные smoke выполнены; обязательные 20 cold-циклов не выполнены.
2. Память: sampled PSS записан; 30-минутный 20-turn soak, plateau и LMK gate не выполнены. MemAvailable меняется из-за остальных процессов и не считается самостоятельным доказательством возврата памяти.
3. Скорость: короткие CPU-пробы показывают менее 1 tok/s. Формальный frozen 1K-input / 256-output benchmark и p95 TTFT не выполнены; цель 8 tok/s не доказана. Два CLI-теста Vulkan не дали ответа; foreground-проверка ожидает свободного телефона.
4. Тепло/батарея: recorded temperatures, USB charging / battery 100%; расход Wh и непрерывный 10-минутный thermal benchmark не измерены.
5. Качество: слепая оценка 60 Soll prompts отсутствует; пользовательский ответ в первых двух коротких пробах пустой.
6. Tool safety: CLI не исполняет инструменты; это не заменяет 50 adversarial cases через production parser/policy.
7. Privacy: синтетические данные, локальный adb-forward/HTTP; network-denied проверка и embedded local-only Android engine не выполнены.
8. Доставка/откат: версии и хеш зафиксированы, процессы останавливаются адресно; corrupted-download, remove и rollback integration gates не выполнены.

Артефакты сохранены приватно в `D:/Projects/Soll/scratchpad/android-bonsai-spike-20261009/`: манифест, model card, сборочные логи, device preflight, физические receipts, server logs и replay. Модель и бинарники находятся только в экспериментальном каталоге `/data/local/tmp/soll-bonsai-spike-20261009` и локальном scratchpad; в APK и Git веса не добавлялись. Основной Soll model runtime не переключался.

## Источники

- [Официальные Android build instructions llama.cpp](https://github.com/ggml-org/llama.cpp/blob/8ae386707bed05f02354dbfc6fa5ee0cf58e5f0f/docs/android.md).
- [Зафиксированная model card PrismML](https://huggingface.co/prism-ml/Bonsai-27B-gguf/tree/f10afb355f104535e3e3e98cf7ab7795c72bd292).
- [Официальное описание Bonsai 1](https://github.com/PrismML-Eng/Bonsai-demo/blob/main/Bonsai1_README.md).
