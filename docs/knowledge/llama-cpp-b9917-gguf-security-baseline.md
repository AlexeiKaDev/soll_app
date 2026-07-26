# llama.cpp b9917+: безопасный baseline для GGUF

Дата проверки: 2026-07-19.

## Решение

В production Android-приложении нет `CMakeLists.txt`, `externalNativeBuild`,
JNI/libllama или встроенных GGUF-моделей. Пользовательский AI runtime остаётся
`soll-backend-route`, а Soll AI-core работает через WSL vLLM и safetensors.
Однако в репозитории есть отдельный `tools/llama-cpp` binary smoke, и его
последний проверенный manifest был закреплён на `b9895`, то есть ниже
security-fix `b9917`.

Активный standalone manifest обновлён до `b10068` commit
`571d0d540df04f25298d0e159e520d9fc62ed121`. Это новее `b9917` и поэтому
включает tokenizer fix. Android APK, production dependencies и backend route
не изменены.

## Upstream security evidence

- [официальный релиз b9917](https://github.com/ggml-org/llama.cpp/releases/tag/b9917)
  указывает на commit `4a7ee31`;
- [upstream PR #18750](https://github.com/ggml-org/llama.cpp/pull/18750)
  исправляет `GHSA-ppcr-mg43-5hq3` и `GHSA-4383-xr9f-c744`;
- fix проверяет минимум четыре байта перед чтением `xcda_blob_size` и заменяет
  неограниченный поиск конца строки в `precompiled_charsmap`;
- upstream прямо связывает обе ошибки с heap-buffer-overflow при разборе
  вредоносных T5/UGM GGUF. Возможные последствия: crash и information
  disclosure;
- [официальный релиз b10068](https://github.com/ggml-org/llama.cpp/releases/tag/b10068)
  опубликован позже и используется как текущий проверяемый baseline.

## Реализованные границы

1. `llama_cpp_active_defaults.json` закрепляет b10068 commit, размеры и
   официальные SHA-256 только для реально проверяемых Windows x64 CPU и Android
   arm64 CPU release archives.
2. `Test-LlamaCppActiveRelease.ps1` запрещает baseline ниже b9917, проверяет
   размер/hash archives, запускает Windows CLI/server `--version` и проверяет
   Android files как ELF64 little-endian AArch64.
3. `approved_models.json` использует `deny_unlisted`. С 2026-07-23 в нём есть
   ровно одна test-only запись `ggml-org/tiny-llamas` для b9945 chat-template
   и b9947 output-file smoke: immutable revision, размер и SHA-256 закреплены,
   файл скачивается только в ignored build cache и не входит в APK.
4. `Test-LlamaCppModelProvenance.ps1` пропускает только `.gguf` с точным
   file name + SHA-256 и требует HTTPS source URL плюс immutable revision.
5. `Invoke-LlamaCppVerifiedModel.ps1` остаётся общим repository entry point
   для model load. Узкий b9945 smoke также вызывает тот же provenance gate
   перед прямым однотокенным запуском CLI из активного checksummed cache.
6. Старые b9892/b9895 manifests оставлены как историческое evidence. Они не
   являются разрешённым путём загрузки модели.

## Добавление модели в будущем

Перед добавлением следующей записи в `approved_models.json` нужны отдельная review-задача
и четыре доказательства: доверенный HTTPS source, immutable revision, локально
пересчитанный SHA-256 и лицензионная/назначенческая применимость. Нельзя
автоматически одобрять GGUF из Android shared storage, download folders,
attachments или произвольного URL. Hash mismatch должен оставаться fail-closed.

## Измеримый результат

- security baseline повышен с b9895 до b10068 (минимум b9917);
- `2` release archives закреплены официальными размерами и SHA-256;
- `1` fail-closed model provenance gate и `1` gated model launcher добавлены;
- `0` GGUF было найдено при исходном аудите; сейчас одобрена `1` маленькая
  test-only GGUF-модель для воспроизводимых llama-cli smoke;
- `0` llama.cpp binaries/models добавлено в APK и `0` production runtime routes
  изменено.
