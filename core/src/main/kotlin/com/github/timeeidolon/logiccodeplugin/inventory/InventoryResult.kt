package com.github.timeeidolon.logiccodeplugin.inventory

data class InventoryResult(
    val projectName: String,
    val projectPath: String,
    val modules: List<ModuleInfo>,
    val signals: Map<String, List<AnnotationSignal>>,
    val resourceFiles: List<String>
)

data class ModuleInfo(
    val name: String,
    val groupId: String?,
    val artifactId: String?,
    val packaging: String?,
    val javaVersion: String?,
    val subModules: List<String>,
    val sourceRoots: List<String>
)

data class AnnotationSignal(
    val filePath: String,
    val className: String,
    val detail: String = ""
)
