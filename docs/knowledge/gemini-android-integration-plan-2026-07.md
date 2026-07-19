# Gemini для Soll app: Android integration plan

Дата проверки: 2026-07-19.

## Решение

Исходный сигнал смешивает два разных продукта Google:

- Gemini Nano выполняется на совместимом Android-устройстве через AICore и ML Kit GenAI;
- Gemini Flash/Pro выполняется в облаке через Gemini API или Firebase AI Logic.

Для Soll app это должны быть два независимых адаптера с общей политикой маршрутизации. Нативный
Android-клиент нельзя считать автономным background-исполнителем Gemini Nano: ML Kit GenAI разрешает
инференс только когда приложение находится на переднем плане. Фоновая автономность остаётся на Soll
Server и локальном WSL/vLLM.

Текущий сигнал `Gemini 3 Flash integration` нельзя превращать в жёстко заданную production-модель.
Официальный Gemini 3 guide уже помечен устаревающим, а актуальный каталог моделей рекомендует более
новую стабильную Flash-модель. Конкретный model ID должен приходить из управляемой конфигурации после
canary-проверки; текущий кандидат для такого canary — `gemini-3.5-flash`.

## Что уже есть в репозитории

| Контур | Текущее состояние |
| --- | --- |
| Android | `minSdk=26`, поэтому минимальное требование ML Kit Prompt API выполнено |
| Model chat | `ModelChatRequest`, `safeForServer()` и `SollGateway.askModelChat(...)` образуют существующий server-mediated путь |
| Private turns | `ModelChatMessage.private` уже позволяет запретить внешний fallback |
| Web prototype | `GeminiNanoWebPrototype` — изолированный контракт будущего web-host, не нативный Android adapter |
| Firebase | BoM `34.15.0` и Messaging подключены; `firebase-ai` и App Check не подключены |
| Конфигурация | debug `google-services.json` присутствует, release-конфигурации нет |
| Устройство | Android SDK содержит ADB, но подключённых устройств нет; поддержка AICore/Gemini Nano не измерена |

## Целевой routing contract

1. Private request разрешён только для готового on-device адаптера.
2. Если on-device capability `DOWNLOADABLE`, пользователь явно подтверждает загрузку модели.
3. Если capability недоступна, public request может использовать существующий Soll Server fallback.
4. Private request при недоступном локальном capability блокируется, а не очищается молча и не уходит в cloud.
5. Cloud Gemini не становится вторым чатом. Он является provider adapter существующего model-chat маршрута.
6. Background-задачи Android не запускают ML Kit GenAI; фоновые решения остаются на сервере.
7. Выбор provider/model управляется feature flag и Remote Config/server policy, а не UI-строкой или hardcode.

## P0: нативный Gemini Nano adapter

Область первого implementation slice:

- добавить отдельный `OnDeviceGenAiCapability`/adapter рядом с `domain/modelchat`;
- использовать ML Kit Prompt API `com.google.mlkit:genai-prompt:1.0.0-beta2` только за выключенным по умолчанию feature flag;
- вызывать `checkStatus()` перед каждым созданием сессии и обрабатывать `UNAVAILABLE`, `DOWNLOADABLE` и `AVAILABLE`;
- начинать `download()` только после явного пользовательского согласия и показывать его состояние в существующем UI;
- закрывать model/session resource при завершении ViewModel;
- обрабатывать `BUSY`, battery quota и `BACKGROUND_USE_BLOCKED` без бесконечных повторов;
- не добавлять INTERNET-зависимый fallback в on-device adapter;
- сохранить `GeminiNanoWebPrototype` отдельно: browser capability нельзя выдавать за AICore/ML Kit.

Первый пользовательский сценарий: локальное краткое резюме выбранного текста или текущего видимого
фрагмента чата. Это foreground-действие, не новый чат и не фоновая автоматизация.

## P1: device canary

Продвижение возможно только после подключения подходящего телефона и проверки:

- фактического результата `checkStatus()` и имени базовой Gemini Nano модели;
- размера/времени загрузки и явного consent flow;
- first-token latency, total latency, peak memory, температуры и расхода батареи;
- русского и английского качества на фиксированном наборе неперсональных примеров;
- отсутствия network request с пользовательским текстом для local route;
- корректного foreground-only поведения и понятного fallback при `BACKGROUND_USE_BLOCKED`;
- одинаковой политики для `private=true` при всех ошибках и состояниях capability.

До этого canary измеренное значение Android Gemini Nano inference равно `0`.

## P2: облачный Gemini adapter

Cloud slice не должен начинаться простым добавлением API key в APK. Допустимы два варианта:

1. Предпочтительный для текущей архитектуры: server-side provider за существующим
   `SollGateway.askModelChat(...)`, где секрет и политика остаются на Soll Server.
2. Firebase AI Logic в Android только после готовности Firebase release-конфигурации, App Check с Play
   Integrity, authenticated-users mode, budget/quota monitoring и Remote Config для model ID.

Для Firebase-варианта использовать совместимые зависимости через существующий BoM, но debug App Check
provider не должен попадать в release. До включения App Check и release-конфигурации runtime dependency
`firebase-ai` добавлять рано.

Cloud adapter принимает только результат `safeForServer()` и не получает private turns. Production
promotion использует стабильную конкретную версию модели, rate limit на пользователя, таймаут,
cancellation, bounded retry и kill switch.

## Метрики принятия

| Метрика | Ворота |
| --- | --- |
| Private payload sent to cloud | `0` |
| Скрытые загрузки модели | `0` |
| Background ML Kit calls | `0` |
| Device canary crashes/ANR | `0` |
| P95 local latency | измерить на целевом устройстве до promotion |
| Quality set | не хуже server baseline на утверждённом foreground-сценарии |
| Cloud abuse protection | App Check/auth/rate limit обязательны до release |
| Rollback | feature flag отключает каждый adapter независимо |

## Итог по сегодняшней задаче

Документация даёт измеримую пользу, но не подтверждает немедленное внедрение `Gemini 3 Flash`.
Правильный следующий кодовый slice — foreground Gemini Nano capability adapter за feature flag. Он
начинается только с подключённым совместимым устройством либо с compile-only интерфейсом без заявления
о runtime-ценности. Облачный Gemini остаётся отдельным, защищённым и отключённым по умолчанию fallback.

## Первичные источники

- [Android AI solution guide](https://developer.android.com/ai/overview)
- [ML Kit GenAI overview](https://developers.google.com/ml-kit/genai)
- [ML Kit Prompt API for Android](https://developers.google.com/ml-kit/genai/prompt/android/get-started)
- [Firebase AI Logic Android setup](https://firebase.google.com/docs/ai-logic/get-started?platform=android)
- [Firebase AI Logic App Check](https://firebase.google.com/docs/ai-logic/app-check)
- [Firebase AI Logic production checklist](https://firebase.google.com/docs/ai-logic/production-checklist)
- [Gemini model catalog](https://ai.google.dev/gemini-api/docs/models)
