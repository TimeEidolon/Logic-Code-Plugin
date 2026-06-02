package com.github.timeeidolon.logiccodeplugin.llm

import com.github.timeeidolon.logiccodeplugin.settings.ReportPluginSettings
import com.github.timeeidolon.logiccodeplugin.cleanup.GeneratedFilesManifestService
import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.io.path.deleteIfExists

@Service(Service.Level.PROJECT)
class MethodLogicCacheService {
    /**
     * Stable identity for a Java method so we can track "stale" across edits.
     * (Old cache used method textRange + file stamp, which changes on edits and can't be targeted.)
     */
    data class MethodId(
        val classFqn: String,
        val methodName: String,
        val parameterTypeTexts: List<String>
    ) {
        fun displayName(): String =
            "$classFqn.$methodName(${parameterTypeTexts.joinToString(", ")})"
    }

    data class Entry(
        val summary: String,
        val mermaid: String? = null,
        val createdAtMs: Long = System.currentTimeMillis(),
        val filePath: String,
        val fileModificationStamp: Long,
        val sourceHash: Int,
        val status: Status = Status.CLEAN,
        val staleReason: String? = null,
        val staleAtMs: Long? = null
    )

    enum class Status { CLEAN, STALE }

    private val cache = ConcurrentHashMap<MethodId, Entry>()
    private val gson = Gson()
    
    /**
     * In-memory callee → direct callers edges (filled when user clicks gutter on a method).
     * Uses [MethodId] for stable identity ([registerDependency] semantics: caller invokes callee).
     */
    private val calleeToDirectCallers = ConcurrentHashMap<MethodId, MutableSet<MethodId>>()
    private val staleMethods = ConcurrentHashMap<MethodId, StaleInfo>()

    data class StaleInfo(
        val reason: String,
        val markedAtMs: Long = System.currentTimeMillis()
    )

    fun get(project: Project, id: MethodId): Entry? {
        cache[id]?.let { return it }

        val loaded = loadPersistedEntry(project, id) ?: return null
        if (loaded.status == Status.STALE) {
            staleMethods.putIfAbsent(
                id,
                StaleInfo(reason = loaded.staleReason ?: "persisted stale", markedAtMs = loaded.staleAtMs ?: System.currentTimeMillis())
            )
        }
        cache[id] = loaded
        return loaded
    }

    fun put(project: Project, id: MethodId, entry: Entry) {
        cache[id] = entry
        staleMethods.remove(id)
        persist(project, id, entry)
    }

    fun invalidate(project: Project, id: MethodId) {
        cache.remove(id)
        staleMethods.remove(id)
        runCatching { cacheFile(project, id).deleteIfExists() }
    }

    fun markStale(project: Project, id: MethodId, reason: String) {
        staleMethods[id] = StaleInfo(reason = reason)
        val existing = cache[id] ?: loadPersistedEntry(project, id)
        if (existing != null) {
            val updated = existing.copy(
                status = Status.STALE,
                staleReason = reason,
                staleAtMs = System.currentTimeMillis()
            )
            cache[id] = updated
            persist(project, id, updated)
        }
    }

    fun listStale(): List<Pair<MethodId, StaleInfo>> =
        staleMethods.entries
            .map { it.key to it.value }
            .sortedBy { (id, _) -> id.displayName() }

    fun clearStaleMarks(project: Project) {
        staleMethods.clear()
        val dir = cacheDir(project)
        if (!dir.exists()) return
        runCatching {
            dir.listDirectoryEntries("*.json").filter { it.isRegularFile() }.forEach { path ->
                val json = runCatching { path.readText(Charsets.UTF_8) }.getOrNull() ?: return@forEach
                val wrapper = runCatching { gson.fromJson(json, Persisted::class.java) }.getOrNull() ?: return@forEach
                val entry = wrapper.entry.toEntry(wrapper.id) ?: return@forEach
                if (entry.status != Status.STALE) return@forEach
                val reset = entry.copy(status = Status.CLEAN, staleReason = null, staleAtMs = null)
                cache[wrapper.id] = reset
                persist(project, wrapper.id, reset)
            }
        }
    }
    
    fun registerDependency(caller: MethodId, callee: MethodId) {
        calleeToDirectCallers.computeIfAbsent(callee) { ConcurrentHashMap.newKeySet() }.add(caller)
    }

    fun getDirectCallerIds(callee: MethodId): Set<MethodId> =
        calleeToDirectCallers[callee].orEmpty().toSet()

    /**
     * All methods that recursively appear as callers of [rootCallee], following edges built via [registerDependency].
     */
    fun getTransitiveCallerIds(rootCallee: MethodId): Set<MethodId> {
        val callers = mutableSetOf<MethodId>()
        val queue = ArrayDeque<MethodId>()
        queue.add(rootCallee)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (caller in calleeToDirectCallers[current].orEmpty()) {
                if (callers.add(caller)) queue.add(caller)
            }
        }
        callers.remove(rootCallee)
        return callers
    }

    fun clearCallerGraph() {
        calleeToDirectCallers.clear()
    }

    companion object {
        fun getInstance(project: Project): MethodLogicCacheService = project.getService(MethodLogicCacheService::class.java)
    }

    private data class Persisted(
        val id: MethodId,
        val entry: PersistedEntry
    )

    private data class PersistedEntry(
        val summary: String?,
        val mermaid: String?,
        val createdAtMs: Long,
        val filePath: String?,
        val fileModificationStamp: Long?,
        val sourceHash: Int?,
        val status: String?,
        val staleReason: String?,
        val staleAtMs: Long?
    ) {
        fun toEntry(id: MethodId): Entry? {
            val s = summary?.takeIf { it.isNotBlank() } ?: return null
            val st = runCatching { Status.valueOf(status ?: Status.CLEAN.name) }.getOrElse { Status.CLEAN }
            return Entry(
                summary = s,
                mermaid = mermaid?.takeIf { m -> m.isNotBlank() },
                createdAtMs = createdAtMs,
                filePath = filePath ?: "",
                fileModificationStamp = fileModificationStamp ?: 0L,
                sourceHash = sourceHash ?: 0,
                status = st,
                staleReason = staleReason,
                staleAtMs = staleAtMs
            )
        }
    }

    private fun loadPersistedEntry(project: Project, id: MethodId): Entry? {
        val file = cacheFile(project, id)
        if (!file.exists()) return null
        return runCatching {
            val json = file.readText(Charsets.UTF_8)
            val wrapper = gson.fromJson(json, Persisted::class.java)
            if (wrapper.id == id) wrapper.entry.toEntry(id) else null
        }.getOrNull()
    }

    private fun persist(project: Project, id: MethodId, entry: Entry) {
        val dir = cacheDir(project)
        dir.createDirectories()

        val target = cacheFile(project, id)
        val tmp = target.parent.resolve("${target.fileName}.tmp")
        val json = gson.toJson(
            Persisted(
                id = id,
                entry = PersistedEntry(
                    summary = entry.summary,
                    mermaid = entry.mermaid,
                    createdAtMs = entry.createdAtMs,
                    filePath = entry.filePath,
                    fileModificationStamp = entry.fileModificationStamp,
                    sourceHash = entry.sourceHash,
                    status = entry.status.name,
                    staleReason = entry.staleReason,
                    staleAtMs = entry.staleAtMs
                )
            )
        )
        runCatching {
            tmp.writeText(json, Charsets.UTF_8)
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            // Best-effort: fallback to non-atomic move
            runCatching {
                tmp.writeText(json, Charsets.UTF_8)
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        // Track for safe cleanup on user request.
        GeneratedFilesManifestService.getInstance(project).recordGeneratedFile(project, target)
    }

    private fun cacheDir(project: Project): Path {
        val settings = ReportPluginSettings.getInstance().state
        val projectRoot = project.basePath?.let { Path(it) } ?: Path(".")
        val outputDir = if (!settings.outputDir.isNullOrBlank()) projectRoot.resolve(settings.outputDir) else projectRoot.resolve("docs")
        return outputDir.resolve("cache").resolve("method-logic")
    }

    private fun cacheFile(project: Project, id: MethodId): Path {
        val name = sha256Hex("${id.classFqn}|${id.methodName}|${id.parameterTypeTexts.joinToString("|")}")
        return cacheDir(project).resolve("$name.json")
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append("%02x".format(b))
        return sb.toString()
    }
}

