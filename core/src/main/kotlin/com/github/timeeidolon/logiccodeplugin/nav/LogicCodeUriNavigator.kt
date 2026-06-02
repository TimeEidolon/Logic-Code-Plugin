package com.github.timeeidolon.logiccodeplugin.nav

import com.github.timeeidolon.logiccodeplugin.bridge.JavaFeatureBridge
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Handles `logiccode:` links from markdown previews (JBHtmlPane / JCEF) and opens targets in the IDE.
 */
object LogicCodeUriNavigator {

    private val log = Logger.getInstance(LogicCodeUriNavigator::class.java)

    fun navigate(project: Project, rawUrl: String) {
        val url = rawUrl.trim()
        when {
            url.startsWith("logiccode://") -> navigateLogicCode(project, url)
            url.startsWith("http://") || url.startsWith("https://") ->
                ApplicationManager.getApplication().invokeLater { BrowserUtil.browse(url) }
        }
    }

    private fun navigateLogicCode(project: Project, url: String) {
        val uri = runCatching { URI(url) }.getOrElse {
            log.warn("Bad logiccode URI: $url", it)
            return
        }
        if (uri.scheme != "logiccode") return
        val host = uri.host ?: return
        when (host) {
            "java" -> navigateJava(project, uri)
            "ts" -> navigateTs(project, uri)
            else -> log.debug("Unknown logiccode host: $host")
        }
    }

    private fun navigateJava(project: Project, uri: URI) {
        val path = (uri.path ?: "").trimStart('/')
        if (path != "method") return
        val label = parseQuery(uri.rawQuery)["label"]?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) } ?: return
        if (label.isBlank()) return
        JavaFeatureBridge.tryNavigateFlowNode(project, label)
    }

    private fun navigateTs(project: Project, uri: URI) {
        val path = (uri.path ?: "").trimStart('/')
        if (path != "file") return
        val rel = parseQuery(uri.rawQuery)["path"]?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) } ?: return
        if (rel.isBlank()) return
        val base = project.basePath ?: return
        val normalized = rel.replace('\\', '/')
        val f = File(base, normalized)
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f) ?: return
        ApplicationManager.getApplication().invokeLater {
            OpenFileDescriptor(project, vf, 0).navigateInEditor(project, true)
        }
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (part in raw.split('&')) {
            val idx = part.indexOf('=')
            if (idx <= 0) continue
            val k = part.substring(0, idx)
            val v = part.substring(idx + 1)
            out[k] = v
        }
        return out
    }
}
