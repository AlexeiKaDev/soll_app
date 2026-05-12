package com.soll.data.repository

import com.soll.data.local.dao.ScanDao
import com.soll.data.local.entity.ScanItemEntity
import com.soll.data.local.entity.ScanSessionEntity
import com.soll.domain.scanner.EanBarcode
import com.soll.domain.scanner.ScannerDuplicatePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ScanAddResult(
    val item: ScanItemEntity,
    val duplicate: Boolean,
    val stored: Boolean,
)

@Singleton
class ScannerRepository @Inject constructor(
    private val scanDao: ScanDao,
) {
    fun observeSessions(): Flow<List<ScanSessionEntity>> =
        scanDao.observeSessions()

    fun observeItems(sessionId: String): Flow<List<ScanItemEntity>> =
        scanDao.observeItems(sessionId)

    suspend fun ensureSession(): ScanSessionEntity = withContext(Dispatchers.IO) {
        scanDao.getLatestSession() ?: ScanSessionEntity(title = "Сканы").also {
            scanDao.insertSession(it)
        }
    }

    suspend fun addScan(
        rawValue: String,
        detectedFormat: String? = null,
        duplicatePolicy: ScannerDuplicatePolicy = ScannerDuplicatePolicy.COUNT_REPEATS,
    ): ScanAddResult = withContext(Dispatchers.IO) {
        val cleanRaw = rawValue.trim()
        require(cleanRaw.isNotBlank()) { "Введите код" }
        val session = ensureSession()
        val normalized = EanBarcode.normalize(cleanRaw).ifBlank { cleanRaw }
        val format = detectedFormat?.takeIf { it.isNotBlank() } ?: EanBarcode.detectFormat(cleanRaw)
        val now = System.currentTimeMillis()
        val duplicate = scanDao.getDuplicate(
            sessionId = session.id,
            format = format,
            normalizedValue = normalized,
        )
        val result = if (duplicate == null) {
            val item = ScanItemEntity(
                sessionId = session.id,
                rawValue = cleanRaw,
                normalizedValue = normalized,
                format = format,
                firstScannedAt = now,
                lastScannedAt = now,
            ).also { scanDao.insertItem(it) }
            scanDao.touchSession(session.id, now)
            ScanAddResult(item = item, duplicate = false, stored = true)
        } else if (duplicatePolicy == ScannerDuplicatePolicy.IGNORE_EXISTING) {
            ScanAddResult(item = duplicate, duplicate = true, stored = false)
        } else {
            val item = duplicate.copy(
                rawValue = cleanRaw,
                count = duplicate.count + 1,
                lastScannedAt = now,
            ).also { scanDao.updateItem(it) }
            scanDao.touchSession(session.id, now)
            ScanAddResult(item = item, duplicate = true, stored = true)
        }
        result
    }

    suspend fun getItems(ids: List<String>): List<ScanItemEntity> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val byId = scanDao.getItemsByIds(ids).associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }

    suspend fun markExported(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) scanDao.markExported(ids)
    }
}
