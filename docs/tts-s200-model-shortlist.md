# TTS model shortlist (internal) — Doogee S200–class devices

Краткий выбор оффлайн-движков для чтения книг. Подробнее см. `docs/deep-research-report.md`.

## Варианты (3–4 рабочих)

| Код | Движок | Интонация RU | Батарея / CPU | Примечание |
|-----|--------|----------------|---------------|------------|
| **A** | Piper / Sherpa-ONNX | Проще | **Лучше** | Стабильный baseline; голоса качаются с релизов sherpa-onnx. |
| **B** | Natasha VITS2 ONNX | **Лучше** | Тяжелее | Модель в APK (`assets/natasha_vits2/`). |
| **C** | Utrobin VITS ONNX | Средне | Средне | ONNX HF-стиль; чувствителен к входам графа — см. адаптивный маппинг в коде. |
| **D** | MMS-TTS-RUS (опционально) | Интересно | Зависит от INT8 | **Лицензия CC-BY-NC** — риск для продукта; не внедряли. |

## Рекомендуемый baseline под S200

- **По умолчанию (первый запуск на Doogee S200):** профиль **Balanced** + движок **Piper** (`SettingsRepository` + bootstrap в `BookReaderViewModel`).
- **Экономия батареи:** пресет **Battery** в настройках читалки — меньше потоков ORT/Sherpa и крупнее слияние коротких фраз.
- **Качество интонации:** **Natasha** + пресет **Quality** (больше потоков, мельче чанки).

## Где в коде

- Профили: `TtsBookPerformanceProfile.kt`
- Персистенция и комментарии к ключам: `SettingsRepository.kt` (`tts_book_perf_profile`, потоки Utrobin/Natasha/Sherpa)
- UI пресетов и подписи движков: `BookReaderScreen.kt`

## Лицензии (кратко)

- Piper / Sherpa / большинство публичных VITS: проверять лицензию конкретного голоса/архива.
- MMS-TTS: часто **NC** — не использовать как дефолт без юридической проверки.
