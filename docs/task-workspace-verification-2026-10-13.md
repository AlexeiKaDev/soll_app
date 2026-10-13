# Android Chat / Tasks: проверка 13 октября 2026

Изменения Android ограничены чатом, рабочим пространством задач и необходимыми API/cache/sync слоями. Другие вкладки интерфейса не перерабатывались.

## Реализовано

- Команда «добавь задачу …» передаёт полный многострочный текст в явный task intake. Повторная отправка одного запроса не создаёт дублей; общие разрешения действий чата не расширены.
- Создание и редактирование задачи: название, описание, приоритет, существующий проект, дата. Ошибка сохраняет ввод; повторный запрос сохраняет идентификатор операции.
- Компактный список, поиск по серверному набору, фильтры, пагинация, детали по ID. Все действия статуса и вложения доступны в деталях.
- Источники получили доступные формы и подтверждения удаления. Roadmap поддерживает создание, изменение и архивирование с сохранением истории; правки передаются в Core через relay outbox. Рабочее пространство сохраняет фильтры и черновик.
- Кеш хранит детали выполнения и сведения о проекте. Миграция Room 26 → 27 добавляет только task_cache.details_json.
- Вложения задач передаются отдельным двоичным методом с пределом 25 МБ; очередь повторяет только подходящие временные ошибки.
- Relay возвращает зашифрованные сообщения; Android расшифровывает их своим ключом. Core проверяет ключ, устройство, scope, AAD и идентификатор запроса.

Основные файлы: `TaskBoardScreen.kt`, `TaskWorkspaceComponents.kt`, `WorkspaceEditors.kt`, `TaskBoardViewModel.kt`, `ChatTaskIntent.kt`, `ChatViewModel.kt`, `RelayChatEnvelope.kt`, `SollGateway.kt`, `SollApiService.kt`, `SollRepository.kt`, `SollSyncQueueRepository.kt`, `TaskCacheRepository.kt`, `TaskCacheEntity.kt`, `SollDatabase.kt`, `AppModule.kt`, схема `27.json`. Серверные изменения находятся в маршрутах chat/tasks, task intake, task service, relay/runtime manager, android delivery и task evidence; relay — в Soll V1 и соответствующих traits/routes.

## Проверки

- Android: 58 целевых JVM-тестов, 0 ошибок. Debug APK и test APK собраны.
- S200 Plus, serial S200PLUS20000101035: APK установлены с сохранением данных; 4 Compose UI-теста и 1 тест миграции прошли.
- Core: итоговый прогон — 241 тест прошёл (`test_android_roadmap_actions`, `test_android_task_workspace`, `test_android_delivery_callbacks`, `test_task_intake`, `test_relay_chat_ai_sidecar`, `test_runtime_service_manager`, `test_soll_relay`, `test_task_bridge_routes`, `test_system_search_chat`). Проверка прав чата применяется к уже распознанному действию: пробелы или вывод task-action из ID не обходят tasks:write. Промежуточный прогон согласования версии — 133 теста.
- Yii relay: 52 runtime-теста, 390 assertions прошли; отдельно 14 controller-тестов, 339 assertions. Bootstrap `tests/_bootstrap_unit.php`.
- Полный lint Android не прошёл: 6 существующих ошибок в BluetoothSpeechAudioRouter, gadget history в SollRepository и BookReaderScreen. Эти участки не менялись для данного задания.

APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256 последней сборки: `D443EAB874AD24B1F8B6829515C76BAF52B93BDAD34F0607B8B35EDBBB4563CC`.

## Развёрнутый сервер

Через настроенное авторизованное соединение подтверждены HTTP 200 для health, tasks/page, tasks/projects, sources и получения задачи по ID. Во время проверки: 19 открытых задач, 12 проектов. Без авторизации tasks/page возвращает 401.

На сервере отдельно проверены создание задачи и повтор запроса (один ID), явная команда чата и повтор turn (duplicate=true), а также зашифрованный AES-GCM task intake через relay и запущенный Core. Последний перешёл queued → answered примерно за 5 секунд. Три служебные задачи после проверки переведены в rejected; пользовательские задачи не изменялись.

В продолжении проверено двоичное вложение PDF: upload/download HTTP 200, точное совпадение байтов, HTTP 401 без токена, повторная загрузка сохраняет одну ссылку, ссылка остаётся после трёх проверок синхронизации с интервалом 5 секунд. Четвёртая служебная задача после этой проверки отклонена.

Это подтверждает live-сценарии создания задач и передачу вложений через relay. Полный live-аудит источников и исправленного roadmap не проведён: новый roadmap-контракт ещё не опубликован.

## Продолжение: сохранность roadmap

Проблема: add/update/delete меняли только runtime JSON relay; очередная публикация Core могла перезаписать эти изменения. Исправление использует существующий `soll_relay_actions`, обрабатывает операции в порядке отправки и сохраняет принятые операции в каноническом RoadmapService. Pending-правки защищены от замещения старым snapshot. Повторная операция подавляется durable receipt; тот же ID с другим payload отклоняется.

Архивирование ставит `declined` и сохраняет текст, комментарий и историю. Android показывает статус и комментарий, скрывает внутренний ID пункта и не предлагает создание задачи из архивного пункта. Relay также отклоняет такой запрос.

Для публикации по частям предусмотрено согласование: обновлённый Core передаёт `roadmap_edit_protocol=1` в planning snapshot; обновлённый relay возвращает `edit_supported=true` только при этом протоколе и наличии outbox. До согласования новая APK отключает редактирование roadmap и показывает подсказку обновить сервер. Создание обычных задач от этого не зависит.

Первая попытка дополнительной ручной проверки подсказки прервалась из-за потери связи с ADB daemon (127.0.0.1:5037). После восстановления соединения повторная проверка подтвердила подсказку в реальном Android UI на текущем сервере; ADB не перезапускался принудительно. Скриншот: `%TEMP%/soll-roadmap-workspace.png`.

Большие карточки готовности свёрнуты по умолчанию в раскрываемый блок, чтобы не отодвигать пункты roadmap на следующие экраны. Статусы этапов отображаются по-русски; исходные сведения о готовности доступны при раскрытии.

После последней косметической правки сборка, 58 JVM-тестов и 5 instrumentation-тестов снова прошли. Дополнительная ручная проверка раскрытия готовности не завершилась: поверх приложения была открыта системная панель уведомлений. Подсказка согласования сервера проверена до этой косметической правки. Автоматизация усилена проверкой текущего окна, чтобы не нажимать и не снимать экран при системных наложениях.

Файлы текущего продолжения:

- Relay: `api/modules/soll/controllers/V1Controller.php`, `api/modules/soll/controllers/traits/SollRoadmapActions.php`, `api/modules/soll/controllers/traits/SollPlanningSnapshotActions.php`, `tests/unit/services/api/SollTaskPaginationTest.php`.
- Core: `server/app/services/android_roadmap_actions.py`, `server/app/services/android_delivery.py`, `server/app/services/soll_relay.py`, `server/app/api/routes/chat.py`, `tests/test_android_roadmap_actions.py`, `tests/test_soll_relay.py`.
- Android: `app/src/main/java/com/soll/domain/soll/SollGateway.kt`, `app/src/main/java/com/soll/data/api/SollApiService.kt`, `app/src/main/java/com/soll/data/repository/SollRepository.kt`, `app/src/main/java/com/soll/presentation/screens/tasks/TaskBoardScreen.kt`, `app/src/main/java/com/soll/presentation/screens/tasks/TaskBoardViewModel.kt`, `app/src/test/java/com/soll/data/api/TaskWorkspaceApiTest.kt`, `app/src/androidTest/java/com/soll/presentation/screens/tasks/TaskWorkspaceUiTest.kt`, этот отчёт.

## Git / публикация

При первой проверке checkout relay HEAD = origin/master = a6cd3d22f5e981c9ac3e5b380a8b7e637850a62c, файлы Soll API были чистые. Пользователь сообщил об upload и push; работа новых маршрутов проверена на настроенном сервере. Исправление roadmap добавлено после этого push и сейчас локально.

Изменения текущего продолжения в Android, Core и relay остаются локально незакоммиченными. Агент не выполнял commit/push или перезапуск серверов. Последняя Android APK установлена на подключённое устройство. У Core присутствует посторонняя незавершённая работа, которую необходимо сохранить при выборочном коммите.

Для live-приёмки roadmap требуется публикация трёх перечисленных PHP-файлов, новых и изменённых Core-файлов, reload Core по runbook и успешная публикация planning snapshot нового протокола. Новая миграция БД не требуется: используется имеющийся relay outbox. Server reload не выполнен; отдельное разрешение на него не запрашивалось в этом проходе.

## Команды итоговых проверок

В `D:/Projects/Soll`:

```powershell
.venv\Scripts\python.exe -m pytest tests/test_android_roadmap_actions.py tests/test_android_task_workspace.py tests/test_android_delivery_callbacks.py tests/test_task_intake.py tests/test_relay_chat_ai_sidecar.py tests/test_runtime_service_manager.py tests/test_soll_relay.py tests/test_task_bridge_routes.py tests/test_system_search_chat.py -q --disable-warnings
```

В `D:/OSPanel/home/monolith.loc`:

```powershell
& 'D:\OSPanel\modules\PHP-8.4\php.exe' vendor/phpunit/phpunit/phpunit --bootstrap tests/_bootstrap_unit.php tests/unit/services/api --filter 'TaskActionsRuntime|TaskAuthRuntime|ChatActionRuntime|TaskPagination|RelaySurfaceAuthRuntime|PlanningSnapshot'
& 'D:\OSPanel\modules\PHP-8.4\php.exe' vendor/phpunit/phpunit/phpunit --bootstrap tests/_bootstrap_unit.php tests/unit/controllers/soll --filter 'SollTaskActionsTest|SollChatActionsTest'
```

В `D:/Projects/soll_app`, JDK Android Studio jbr, Gradle home `.gradle-user`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.soll.data.api.ChatTurnRequestTest' --tests 'com.soll.data.api.RelayChatEnvelopeTest' --tests 'com.soll.data.api.TaskWorkspaceApiTest' --tests 'com.soll.data.local.entity.TaskCacheEntityTest' --tests 'com.soll.data.repository.SollRepositoryTest' --tests 'com.soll.domain.soll.SollChatActionPolicyTest' --tests 'com.soll.presentation.screens.chat.ChatTaskIntentTest' --tests 'com.soll.presentation.screens.tasks.*' :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
& 'C:\Users\kusoc\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s S200PLUS20000101035 shell am instrument -w -r -e class 'com.soll.presentation.screens.tasks.TaskWorkspaceUiTest,com.soll.data.local.TaskWorkspaceMigrationTest' com.soll.debug.test/androidx.test.runner.AndroidJUnitRunner
```
