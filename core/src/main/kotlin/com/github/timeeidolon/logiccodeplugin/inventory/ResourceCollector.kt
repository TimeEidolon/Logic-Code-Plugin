package com.github.timeeidolon.logiccodeplugin.inventory

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile

class ResourceCollector(private val project: Project) {

    companion object {
        val RESOURCE_SUFFIXES = setOf("yml", "yaml", "properties", "xml", "sql", "md")
        val SKIP_DIRS = setOf(".git", ".idea", ".mvn", "target", "build", "out", "node_modules", ".gradle")
    }

    fun collect(): List<String> {
        val resourceFiles = mutableListOf<String>()
        val modules = ModuleManager.getInstance(project).modules

        for (module in modules) {
            val rootManager = ModuleRootManager.getInstance(module)
            for (root in rootManager.contentRoots) {
                collectFromDirectory(root, resourceFiles)
            }
        }

        return resourceFiles.sorted()
    }

    private fun collectFromDirectory(dir: VirtualFile, result: MutableList<String>) {
        for (child in dir.children) {
            if (child.isDirectory && !SKIP_DIRS.contains(child.name)) {
                collectFromDirectory(child, result)
            } else if (!child.isDirectory) {
                val ext = child.extension ?: continue
                if (ext in RESOURCE_SUFFIXES) {
                    val relativePath = try {
                        child.path.removePrefix(project.basePath + "/")
                    } catch (e: Exception) {
                        child.path
                    }
                    // Prioritize application config files and xml/sql/md
                    if (child.name.startsWith("application") || ext in setOf("xml", "sql", "md")) {
                        result.add(relativePath)
                    }
                }
            }
        }
    }
}
