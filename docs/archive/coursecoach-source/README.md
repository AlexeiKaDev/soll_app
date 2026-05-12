# Course Coach Archive

Дата архивации: 2026-05-07.

Модуль `Курс` удален из Android-сборки приложения. Код оставлен только как донор UI-паттернов, карточек, таймера, виджетов прогресса и seed-контента на будущее.

Содержимое:

- `app-src-main-java/` - бывшие Kotlin-файлы Course Coach, репозитория, DAO, Room entities и напоминаний.
- `app-src-main-assets/course_packages/` - бывшие seed-пакеты курса.

Этот архив не должен импортироваться из `app/src/main/java` и не участвует в Hilt, Room, навигации, manifest или assets сборки.
