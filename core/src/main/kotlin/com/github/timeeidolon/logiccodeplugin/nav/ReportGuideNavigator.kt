package com.github.timeeidolon.logiccodeplugin.nav

import com.github.timeeidolon.logiccodeplugin.graph.EntrypointGuideGraphExtractor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

object ReportGuideNavigator {

    fun navigateToGuideSection(project: Project, classSimpleName: String, methodName: String): Boolean {
        val extractor = EntrypointGuideGraphExtractor(project)
        val path = extractor.guideMarkdownPath() ?: return false
        if (!path.exists()) return false

        val lineZeroBased = extractor.guideSectionStartLine(classSimpleName, methodName)?.coerceAtLeast(0) ?: 0
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(path.absolutePathString())) ?: return false

        ApplicationManager.getApplication().invokeLater {
            val doc = FileDocumentManager.getInstance().getDocument(vf) ?: return@invokeLater
            val lc = doc.lineCount
            if (lc <= 0) return@invokeLater
            val line = lineZeroBased.coerceIn(0, (lc - 1).coerceAtLeast(0))
            val offset = doc.getLineStartOffset(line)
            OpenFileDescriptor(project, vf, offset).navigateInEditor(project, true)
        }

        return true
    }

    /** Best-effort: open guide file even when section anchor is unknown (line 1). */
    fun openGuideFile(project: Project): Boolean {
        val path = EntrypointGuideGraphExtractor(project).guideMarkdownPath() ?: return false
        if (!path.exists()) return false
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(path.absolutePathString())) ?: return false
        ApplicationManager.getApplication().invokeLater {
            OpenFileDescriptor(project, vf, 0).navigateInEditor(project, true)
        }
        return true
    }
}
