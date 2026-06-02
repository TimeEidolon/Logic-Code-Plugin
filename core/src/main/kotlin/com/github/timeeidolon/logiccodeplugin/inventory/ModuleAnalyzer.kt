package com.github.timeeidolon.logiccodeplugin.inventory

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ModuleAnalyzer(private val project: Project) {

    fun analyze(): List<ModuleInfo> {
        val modules = ModuleManager.getInstance(project).modules
        val pomModules = parseRootPomModules(project.basePath)
        return modules.map { module -> buildModuleInfo(module, pomModules) }
    }

    private fun buildModuleInfo(module: Module, pomModules: Map<String, PomInfo>): ModuleInfo {
        val rootManager = ModuleRootManager.getInstance(module)
        val sourceRoots = rootManager.sourceRoots.map { it.path }
        val pomInfo = pomModules[module.name] ?: pomModules.values.firstOrNull()

        return ModuleInfo(
            name = module.name,
            groupId = pomInfo?.groupId,
            artifactId = pomInfo?.artifactId,
            packaging = pomInfo?.packaging,
            javaVersion = pomInfo?.javaVersion,
            subModules = pomInfo?.modules ?: emptyList(),
            sourceRoots = sourceRoots
        )
    }

    private fun parseRootPomModules(basePath: String?): Map<String, PomInfo> {
        if (basePath == null) return emptyMap()
        val pomFile = File(basePath, "pom.xml")
        if (!pomFile.exists()) return emptyMap()
        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFile)
            val result = mutableMapOf<String, PomInfo>()
            val rootInfo = parsePomXml(pomFile)
            result[""] = rootInfo
            rootInfo.modules.forEach { moduleName ->
                val modulePom = File(basePath, "$moduleName/pom.xml")
                if (modulePom.exists()) result[moduleName] = parsePomXml(modulePom)
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private data class PomInfo(
        val groupId: String?,
        val artifactId: String?,
        val packaging: String?,
        val javaVersion: String?,
        val modules: List<String>
    )

    private fun parsePomXml(file: File): PomInfo {
        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            doc.documentElement.normalize()
            fun findText(tag: String): String? {
                val nodes = doc.documentElement.getElementsByTagName(tag)
                return if (nodes.length > 0) nodes.item(0).textContent?.trim() else null
            }
            val modules = mutableListOf<String>()
            val modulesNode = doc.documentElement.getElementsByTagName("modules")
            if (modulesNode.length > 0) {
                val moduleNodes = modulesNode.item(0).childNodes
                for (i in 0 until moduleNodes.length) {
                    val node = moduleNodes.item(i)
                    if (node.nodeName == "module") {
                        node.textContent?.trim()?.let { modules.add(it) }
                    }
                }
            }
            PomInfo(
                groupId = findText("groupId"),
                artifactId = findText("artifactId"),
                packaging = findText("packaging"),
                javaVersion = findText("java.version") ?: findText("maven.compiler.source"),
                modules = modules
            )
        } catch (e: Exception) {
            PomInfo(null, null, null, null, emptyList())
        }
    }
}
