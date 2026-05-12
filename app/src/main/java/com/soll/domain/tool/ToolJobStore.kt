package com.soll.domain.tool

import kotlinx.coroutines.flow.Flow

interface ToolJobStore {
    fun getRecentJobs(limit: Int = 100): Flow<List<ToolJob>>
    fun getJobsByStatus(status: ToolJobStatus): Flow<List<ToolJob>>
    suspend fun getJob(id: String): ToolJob?
    suspend fun countActiveJobs(): Int
    suspend fun insert(job: ToolJob): ToolJob
    suspend fun update(job: ToolJob)
    suspend fun deleteFinishedJobs()
}
