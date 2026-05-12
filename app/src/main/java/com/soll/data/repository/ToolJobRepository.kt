package com.soll.data.repository

import com.soll.data.local.dao.ToolJobDao
import com.soll.data.local.entity.ToolJobEntity
import com.soll.domain.tool.ToolJob
import com.soll.domain.tool.ToolJobStatus
import com.soll.domain.tool.ToolJobStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ToolJobRepository @Inject constructor(
    private val toolJobDao: ToolJobDao,
) : ToolJobStore {
    override fun getRecentJobs(limit: Int): Flow<List<ToolJob>> =
        toolJobDao.getRecentJobs(limit).map { jobs -> jobs.map { it.toDomain() } }

    override fun getJobsByStatus(status: ToolJobStatus): Flow<List<ToolJob>> =
        toolJobDao.getJobsByStatus(status.name).map { jobs -> jobs.map { it.toDomain() } }

    override suspend fun getJob(id: String): ToolJob? =
        toolJobDao.getJob(id)?.toDomain()

    override suspend fun countActiveJobs(): Int =
        toolJobDao.countActiveJobs()

    override suspend fun insert(job: ToolJob): ToolJob {
        toolJobDao.insert(ToolJobEntity.fromDomain(job))
        return job
    }

    override suspend fun update(job: ToolJob) {
        val current = toolJobDao.getJob(job.id)?.toDomain()
        if (current?.status == ToolJobStatus.CANCELLED && job.status != ToolJobStatus.CANCELLED) {
            return
        }
        toolJobDao.update(ToolJobEntity.fromDomain(job))
    }

    override suspend fun deleteFinishedJobs() {
        toolJobDao.deleteFinishedJobs()
    }
}
