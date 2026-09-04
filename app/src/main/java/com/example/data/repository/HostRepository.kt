package com.example.data.repository

import com.example.data.dao.ClipboardDao
import com.example.data.dao.CommandSnippetDao
import com.example.data.dao.HostDao
import com.example.data.dao.RfcDao
import com.example.data.entity.ClipboardItemEntity
import com.example.data.entity.CommandSnippetEntity
import com.example.data.entity.HostEntity
import com.example.data.entity.RfcItemEntity
import kotlinx.coroutines.flow.Flow

class HostRepository(
    private val hostDao: HostDao,
    private val rfcDao: RfcDao,
    private val snippetDao: CommandSnippetDao,
    private val clipboardDao: ClipboardDao
) {
    // Hosts
    val allHosts: Flow<List<HostEntity>> = hostDao.getAllHosts()

    fun getHostById(id: Int): Flow<HostEntity?> = hostDao.getHostById(id)

    suspend fun insertHost(host: HostEntity): Long = hostDao.insertHost(host)

    suspend fun updateHost(host: HostEntity) = hostDao.updateHost(host)

    suspend fun deleteHost(host: HostEntity) = hostDao.deleteHost(host)

    suspend fun updateProvisioned(hostId: Int, provisioned: Boolean) =
        hostDao.updateProvisionedStatus(hostId, provisioned)

    suspend fun updateTelemetry(hostId: Int, cpu: Int, ram: Int, disk: Int, temp: Int) =
        hostDao.updateHostTelemetry(hostId, cpu, ram, disk, temp)

    // RFCs
    val allRfcs: Flow<List<RfcItemEntity>> = rfcDao.getAllRfcs()

    fun getRfcsForHost(hostId: Int): Flow<List<RfcItemEntity>> = rfcDao.getRfcsForHost(hostId)

    suspend fun insertRfc(rfc: RfcItemEntity): Long = rfcDao.insertRfc(rfc)

    suspend fun updateRfc(rfc: RfcItemEntity) = rfcDao.updateRfc(rfc)

    suspend fun deleteRfc(rfc: RfcItemEntity) = rfcDao.deleteRfc(rfc)

    suspend fun updateRfcStatus(rfcId: Int, status: String, log: String, executedAt: Long?) =
        rfcDao.updateRfcStatus(rfcId, status, log, executedAt)

    // Snippets
    val allSnippets: Flow<List<CommandSnippetEntity>> = snippetDao.getAllSnippets()

    suspend fun insertSnippet(snippet: CommandSnippetEntity): Long = snippetDao.insertSnippet(snippet)

    suspend fun updateSnippet(snippet: CommandSnippetEntity) = snippetDao.updateSnippet(snippet)

    suspend fun deleteSnippet(snippet: CommandSnippetEntity) = snippetDao.deleteSnippet(snippet)

    // Clipboard
    fun getClipboardForHost(hostId: Int): Flow<List<ClipboardItemEntity>> =
        clipboardDao.getClipboardForHost(hostId)

    suspend fun insertClipboard(item: ClipboardItemEntity): Long = clipboardDao.insertClipboard(item)

    suspend fun deleteClipboard(item: ClipboardItemEntity) = clipboardDao.deleteClipboard(item)

    suspend fun clearClipboard(hostId: Int) = clipboardDao.clearClipboardForHost(hostId)
}
