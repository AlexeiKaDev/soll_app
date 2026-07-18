# Gemini Nano / built-in web AI: прототип интеграции для Soll app

## Результат

В Android-модуль добавлен dependency-free прототип
`GeminiNanoWebPrototype`. Это не попытка запустить browser API внутри Android
`WebView`: текущий репозиторий является нативным Compose-приложением и не имеет
web-host/runtime контракта. Прототип фиксирует безопасную границу, которую
будущий web-клиент Soll сможет использовать после собственного feature probe.

Цель прототипа — проверить полезную часть сигнала "AI features for web apps"
без нового SDK, ключей провайдера, permission, скрытой загрузки модели и второго
чата.

## Контракт

Web-host передаёт в Soll нормализованный snapshot для одной из возможностей:

- `PROMPT`;
- `SUMMARIZE`;
- `REWRITE`.

Для каждой возможности host сообщает только состояние `READY`, `DOWNLOADABLE`,
`UNAVAILABLE` или `UNKNOWN`. Маршрутизатор возвращает одно из пяти решений:

| Условие | Решение |
| --- | --- |
| capability готова | выполнить on-device |
| модель доступна после загрузки и согласие дано | загрузить/создать локальную сессию |
| модель доступна после загрузки, но согласия нет | запросить явное согласие |
| capability недоступна, запрос public | существующий server fallback после `safeForServer()` |
| capability недоступна/неизвестна, запрос private | заблокировать fallback без server payload |

Инвариант `WebAiPrototypeDecision` не позволяет приложить server request к
локальному или заблокированному решению. Это делает ошибочную отправку private
turn наружу наблюдаемой как ошибка контракта.

## Точка интеграции

Прототип расположен рядом с существующими `ModelChatRequest` и
`ModelChatServerBridge`. Он переиспользует `ModelChatRequest.safeForServer()` и
не меняет `SollGateway.askModelChat(...)`, поэтому текущий backend-mediated путь
и публичные контракты остаются прежними.

Следующий host-specific шаг допустим только в репозитории, где реально есть web
runtime: adapter должен feature-detect API, сопоставить его availability с
`WebAiCapabilitySnapshot`, исполнить только выбранную локальную ветку и вернуть
явную ошибку при изменении browser API. Название конкретной модели нельзя
считать runtime-гарантией: решением управляет наблюдаемая capability.

## Проверяемая ценность

Focused unit smoke покрывает семь сценариев: local private prompt, независимый
probe каждой capability, consent gate, разрешённую загрузку, санитарный server
fallback, блокировку private fallback и инвариант отсутствия server payload на
local route.

Порог продолжения: host adapter можно подключать только если его собственный
browser smoke подтверждает availability и локальную обработку без network
request с пользовательским текстом. До этого runtime-ценность на Android равна
нулю, а наблюдаемая ценность этого slice — исполнимый routing/privacy контракт.
