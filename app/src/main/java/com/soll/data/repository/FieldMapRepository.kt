package com.soll.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.soll.data.local.dao.FieldPointDao
import com.soll.data.local.entity.FieldPointEntity
import com.soll.domain.field.FieldCoordinateParser
import com.soll.domain.field.FieldLocationSnapshot
import com.soll.domain.field.FieldPoint
import com.soll.domain.field.FieldPointSource
import com.soll.domain.field.FieldPointStatus
import com.soll.domain.field.GeoCoordinate
import com.soll.domain.soll.SollTask
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@Singleton
class FieldMapRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fieldPointDao: FieldPointDao,
    private val taskCacheRepository: TaskCacheRepository,
    private val noteRepository: NoteRepository,
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _currentLocation = MutableStateFlow<FieldLocationSnapshot?>(null)

    val currentLocation: StateFlow<FieldLocationSnapshot?> = _currentLocation.asStateFlow()

    fun observePoints(): Flow<List<FieldPoint>> =
        fieldPointDao.observeAll().map { points -> points.map { it.toDomain() } }

    suspend fun refreshCurrentLocation(): FieldLocationSnapshot = withContext(Dispatchers.IO) {
        ensureLocationPermission()
        ensureLocationEnabled()

        val candidates = listOfNotNull(
            requestCurrentLocation(
                priority = Priority.PRIORITY_HIGH_ACCURACY,
                timeoutMs = PRIMARY_LOCATION_TIMEOUT_MS,
                provider = "gps/high",
                fallbackUsed = false,
            ),
            requestCurrentLocation(
                priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                timeoutMs = FALLBACK_LOCATION_TIMEOUT_MS,
                provider = "balanced/fallback",
                fallbackUsed = true,
            ),
            requestLastKnownLocation(),
        )

        val best = candidates
            .minWithOrNull(compareBy<FieldLocationCandidate> { it.accuracyRank }.thenBy { it.ageMs })
            ?: error("Не удалось получить геолокацию")
        val snapshot = best.toSnapshot()
        _currentLocation.value = snapshot
        snapshot
    }

    suspend fun saveCurrentLocationPoint(title: String, note: String): FieldPoint = withContext(Dispatchers.IO) {
        val snapshot = currentLocation.value ?: refreshCurrentLocation()
        val now = System.currentTimeMillis()
        val point = FieldPointEntity(
            title = title.trim().ifBlank { "Моя точка ${timeLabel(now)}" },
            note = note.trim(),
            latitude = snapshot.coordinate.latitude,
            longitude = snapshot.coordinate.longitude,
            accuracyMeters = snapshot.accuracyMeters,
            source = FieldPointSource.CURRENT_LOCATION.storageKey,
            status = FieldPointStatus.PLANNED.storageKey,
            createdAt = now,
            updatedAt = now,
        )
        fieldPointDao.upsert(point)
        point.toDomain()
    }

    suspend fun saveManualPoint(
        title: String,
        note: String,
        latitude: String,
        longitude: String,
    ): FieldPoint = withContext(Dispatchers.IO) {
        val coordinate = FieldCoordinateParser.parseManual(latitude, longitude)
        val now = System.currentTimeMillis()
        val point = FieldPointEntity(
            title = title.trim().ifBlank { "Точка ${coordinate.formatted()}" },
            note = note.trim(),
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            source = FieldPointSource.MANUAL.storageKey,
            status = FieldPointStatus.PLANNED.storageKey,
            createdAt = now,
            updatedAt = now,
        )
        fieldPointDao.upsert(point)
        point.toDomain()
    }

    suspend fun importTaskPoints(): Int = withContext(Dispatchers.IO) {
        val board = taskCacheRepository.getCachedBoard()
        val tasks = (board.today + board.inbox + board.stale + board.doneRecent)
            .distinctBy { it.id }
        val now = System.currentTimeMillis()
        val imported = tasks.mapNotNull { task ->
            val coordinate = FieldCoordinateParser.parseFirst(task.coordinateSearchText()) ?: return@mapNotNull null
            val id = "task-${task.id}"
            val existing = fieldPointDao.getById(id)
            FieldPointEntity(
                id = id,
                title = task.title.ifBlank { "Задача ${task.id}" },
                note = task.toFieldNote(),
                latitude = coordinate.latitude,
                longitude = coordinate.longitude,
                source = FieldPointSource.TASK.storageKey,
                status = existing?.status ?: FieldPointStatus.PLANNED.storageKey,
                taskId = task.id,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                visitedAt = existing?.visitedAt,
            )
        }
        fieldPointDao.upsertAll(imported)
        imported.size
    }

    suspend fun setStatus(id: String, status: FieldPointStatus) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        fieldPointDao.updateStatus(
            id = id,
            status = status.storageKey,
            updatedAt = now,
            visitedAt = if (status == FieldPointStatus.DONE) now else null,
        )
    }

    suspend fun deletePoint(id: String) = withContext(Dispatchers.IO) {
        fieldPointDao.delete(id)
    }

    suspend fun exportPointToNote(id: String): String = withContext(Dispatchers.IO) {
        val point = fieldPointDao.getById(id)?.toDomain() ?: error("Точка не найдена")
        val result = noteRepository.upsertNote(
            id = null,
            title = "Гео: ${point.title}",
            content = point.toNoteMarkdown(),
            tagsInput = "field, map, гео",
            pinned = false,
            archived = false,
            source = "field_map",
            queueForSync = true,
        )
        result.noteId
    }

    private fun ensureLocationPermission() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        check(fine || coarse) { "Нет разрешения на геолокацию" }
    }

    private fun ensureLocationEnabled() {
        val enabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
        check(enabled) { "Геолокация выключена в Android" }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(
        priority: Int,
        timeoutMs: Long,
        provider: String,
        fallbackUsed: Boolean,
    ): FieldLocationCandidate? = runCatching {
        val tokenSource = CancellationTokenSource()
        val location = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { continuation ->
                fusedLocationClient.getCurrentLocation(priority, tokenSource.token)
                    .addOnSuccessListener { value ->
                        if (continuation.isActive) continuation.resume(value)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
                continuation.invokeOnCancellation { tokenSource.cancel() }
            }
        }
        tokenSource.cancel()
        location?.let {
            FieldLocationCandidate(
                location = it,
                provider = provider,
                fallbackUsed = fallbackUsed,
            )
        }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun requestLastKnownLocation(): FieldLocationCandidate? = runCatching {
        suspendCancellableCoroutine<Location?> { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }?.let {
            FieldLocationCandidate(
                location = it,
                provider = "last_known",
                fallbackUsed = true,
            )
        }
    }.getOrNull()

    private fun SollTask.coordinateSearchText(): String =
        listOf(
            title,
            description,
            sourceRef,
            projectName.orEmpty(),
            tags.joinToString(" "),
        ).joinToString("\n")

    private fun SollTask.toFieldNote(): String = buildString {
        appendLine(description.ifBlank { "Без описания" })
        appendLine()
        appendLine("Источник: $sourceRef")
        appendLine("Статус задачи: $status")
        if (!projectName.isNullOrBlank()) appendLine("Проект: $projectName")
        if (tags.isNotEmpty()) appendLine("Теги: ${tags.joinToString()}")
    }.trim()

    private fun FieldPoint.toNoteMarkdown(): String = buildString {
        appendLine("# $title")
        if (note.isNotBlank()) {
            appendLine()
            appendLine(note)
        }
        appendLine()
        appendLine("- Координаты: ${coordinate.formatted()}")
        accuracyMeters?.let { appendLine("- Точность: ${it.toInt()} м") }
        appendLine("- Статус: ${status.label}")
        appendLine("- Источник: ${source.label}")
        taskId?.let { appendLine("- Задача: $it") }
        appendLine()
        appendLine("geo:${coordinate.latitude},${coordinate.longitude}")
        appendLine("https://maps.google.com/?q=${coordinate.latitude},${coordinate.longitude}")
    }.trim()

    private fun timeLabel(timeMillis: Long): String =
        SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru")).format(Date(timeMillis))

    private data class FieldLocationCandidate(
        val location: Location,
        val provider: String,
        val fallbackUsed: Boolean,
    ) {
        val accuracyRank: Float =
            if (location.hasAccuracy() && location.accuracy > 0f) location.accuracy else Float.MAX_VALUE
        val ageMs: Long =
            (System.currentTimeMillis() - (location.time.takeIf { it > 0L } ?: 0L)).coerceAtLeast(0L)

        fun toSnapshot(): FieldLocationSnapshot =
            FieldLocationSnapshot(
                coordinate = GeoCoordinate(
                    latitude = location.latitude,
                    longitude = location.longitude,
                ),
                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
                capturedAt = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
                provider = provider,
                fallbackUsed = fallbackUsed,
            )
    }

    private companion object {
        const val PRIMARY_LOCATION_TIMEOUT_MS = 5_000L
        const val FALLBACK_LOCATION_TIMEOUT_MS = 3_000L
    }
}
