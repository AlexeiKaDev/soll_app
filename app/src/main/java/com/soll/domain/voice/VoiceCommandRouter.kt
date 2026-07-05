package com.soll.domain.voice

import com.soll.data.music.MusicVoiceController
import com.soll.data.local.entity.NoteEntity
import com.soll.data.repository.NoteRepository
import com.soll.domain.notes.NoteSyncStatus
import com.soll.domain.soll.SollGateway
import com.soll.domain.soll.SollHealth
import com.soll.domain.soll.SollTaskBoard
import javax.inject.Inject
import javax.inject.Singleton

data class VoiceCommandResult(
    val spokenText: String,
    val detailText: String = spokenText,
)

@Singleton
class VoiceCommandRouter @Inject constructor(
    private val sollGateway: SollGateway,
    private val noteRepository: NoteRepository,
    private val musicVoiceController: MusicVoiceController,
) {
    private val parser = VoiceCommandParser()

    suspend fun route(text: String): VoiceCommandResult {
        return when (val command = parser.parse(text)) {
            VoiceCommand.Health -> health()
            VoiceCommand.Sync -> sync()
            VoiceCommand.TaskSummary -> taskSummary()
            VoiceCommand.MusicPlay -> musicVoiceController.play().toVoiceResult()
            VoiceCommand.MusicPause -> musicVoiceController.pause().toVoiceResult()
            VoiceCommand.MusicNext -> musicVoiceController.next().toVoiceResult()
            VoiceCommand.MusicPrevious -> musicVoiceController.previous().toVoiceResult()
            is VoiceCommand.RawNote -> rawNote(command.text)
            is VoiceCommand.Unknown -> VoiceCommandResult(
                spokenText = "Не понял команду. Можно сказать: статус, задачи, синхронизация или заметка.",
                detailText = "Распознано: ${command.text}",
            )
        }
    }

    private suspend fun health(): VoiceCommandResult {
        return sollGateway.getHealth().fold(
            onSuccess = { health ->
                VoiceCommandResult(
                    spokenText = "Сервер Soll ${health.statusLabel()}. Открытых задач планировщика: ${health.jobsCount}.",
                    detailText = health.format(),
                )
            },
            onFailure = { error ->
                VoiceCommandResult("Сервер Soll недоступен: ${error.message ?: "ошибка"}")
            },
        )
    }

    private suspend fun sync(): VoiceCommandResult {
        val healthResult = sollGateway.getHealth()
        val boardResult = sollGateway.getTaskBoard()
        if (healthResult.isFailure && boardResult.isFailure) {
            return VoiceCommandResult("Синхронизация не выполнена. Сервер Soll недоступен.")
        }

        val healthText = healthResult.getOrNull()?.let { "Сервер ${it.statusLabel()}." }.orEmpty()
        val boardText = boardResult.getOrNull()?.summaryText().orEmpty()
        val spoken = listOf(healthText, boardText).filter { it.isNotBlank() }.joinToString(" ")
        return VoiceCommandResult(
            spokenText = spoken.ifBlank { "Синхронизация выполнена частично." },
            detailText = buildString {
                healthResult.getOrNull()?.let { append(it.format()).append("\n\n") }
                boardResult.getOrNull()?.let { append(it.detailText()) }
                healthResult.exceptionOrNull()?.let { append("Ошибка сервера: ${it.message}\n") }
                boardResult.exceptionOrNull()?.let { append("Ошибка задач: ${it.message}\n") }
            }.trim(),
        )
    }

    private suspend fun taskSummary(): VoiceCommandResult {
        return sollGateway.getTaskBoard().fold(
            onSuccess = { board ->
                VoiceCommandResult(
                    spokenText = board.summaryText(),
                    detailText = board.detailText(),
                )
            },
            onFailure = { error ->
                VoiceCommandResult("Не удалось получить задачи Soll: ${error.message ?: "ошибка"}")
            },
        )
    }

    private suspend fun rawNote(text: String): VoiceCommandResult {
        val title = text.lineSequence().firstOrNull()?.take(80)?.ifBlank { "Голосовая заметка" } ?: "Голосовая заметка"
        return runCatching {
            noteRepository.captureAndSend(
                title = title,
                content = text,
                tags = listOf("voice"),
                source = NoteEntity.SOURCE_VOICE,
            )
        }.fold(
            onSuccess = { result ->
                if (result.syncStatus == NoteSyncStatus.SYNCED) {
                    VoiceCommandResult(
                        spokenText = "Заметка сохранена и отправлена в Soll.",
                        detailText = "Создан файл: ${result.filename}",
                    )
                } else {
                    VoiceCommandResult(
                        spokenText = "Заметка сохранена на телефоне и поставлена в очередь.",
                        detailText = result.errorMessage ?: "Отправка повторится автоматически.",
                    )
                }
            },
            onFailure = { error ->
                VoiceCommandResult("Не удалось сохранить заметку: ${error.message ?: "ошибка"}")
            },
        )
    }

    private fun SollHealth.format(): String =
        "Сервер: ${statusLabel()}\n" +
            "Хранилище: ${if (vaultAccessible) "доступно" else "недоступно"}\n" +
            "Планировщик: ${if (schedulerRunning) "запущен" else "остановлен"}\n" +
            "Задач планировщика: $jobsCount"

    private fun SollHealth.statusLabel(): String =
        when (status.lowercase()) {
            "healthy" -> "работает"
            "degraded" -> "работает с проблемами"
            else -> status
        }

    private fun SollTaskBoard.summaryText(): String =
        "На сегодня ${today.size}, входящих ${inbox.size}, блок ${blocked.size}, отложенных ${deferred.size}, зависших ${stale.size}."

    private fun SollTaskBoard.detailText(): String = buildString {
        append(summaryText())
        today.take(5).forEachIndexed { index, task ->
            append("\n${index + 1}. ${task.title}")
        }
    }

    private fun com.soll.data.music.MusicVoiceControlResult.toVoiceResult(): VoiceCommandResult =
        VoiceCommandResult(
            spokenText = spokenText,
            detailText = detailText,
        )
}
