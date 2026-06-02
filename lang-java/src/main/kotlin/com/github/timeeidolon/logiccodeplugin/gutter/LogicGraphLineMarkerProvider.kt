package com.github.timeeidolon.logiccodeplugin.gutter

import com.github.timeeidolon.logiccodeplugin.cache.MethodPsiResolver
import com.github.timeeidolon.logiccodeplugin.graph.EntrypointGuideGraphExtractor
import com.github.timeeidolon.logiccodeplugin.graph.MermaidGraphParser
import com.github.timeeidolon.logiccodeplugin.graph.MermaidGraphSlicer
import com.github.timeeidolon.logiccodeplugin.inventory.EntrypointSignatures
import com.github.timeeidolon.logiccodeplugin.llm.MethodLogicCacheService
import com.github.timeeidolon.logiccodeplugin.nav.ReportGuideNavigator
import com.github.timeeidolon.logiccodeplugin.ui.ReportToolWindowRegistry
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import java.awt.event.MouseEvent
import javax.swing.Icon

class LogicGraphLineMarkerProvider : LineMarkerProviderDescriptor() {

    override fun getName(): String = "Logic graph preview"

    override fun getIcon(): Icon = AllIcons.Toolwindows.ToolWindowAskAI

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Place icon on the method identifier
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false) ?: return null
        if (method.nameIdentifier != element) return null

        val project = element.project
        val containingClass = method.containingClass ?: return null
        val className = containingClass.name ?: return null
        val methodName = method.name
        val isEntrypoint = isEntrypointMethod(method)

        val tooltip = buildString {
            if (isEntrypoint) {
                append("Show full logic graph for entrypoint $className.$methodName")
            } else {
                append("Show logic sub-graph for $className.$methodName")
            }
            append("\n")
            append("⌘/Ctrl+click gutter: jump to PROJECT_ENTRYPOINT_GUIDE.md anchor")
        }

        val handler: (MouseEvent, PsiElement) -> Unit = fc@{ ev, _ ->
            if (ev.isMetaDown || ev.isControlDown) {
                ReportGuideNavigator.navigateToGuideSection(project, className, methodName)
                return@fc
            }
            showGraph(project, method, className, methodName, isEntrypoint)
        }

        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { tooltip },
            handler,
            GutterIconRenderer.Alignment.RIGHT
        ) { "Logic graph" }
    }

    private fun isEntrypointMethod(method: PsiMethod): Boolean =
        EntrypointSignatures.isPsiEntrypointCandidate(method)

    private fun showGraph(project: Project, method: PsiMethod, className: String, methodName: String, isEntrypoint: Boolean) {
        val extractor = EntrypointGuideGraphExtractor(project)
        val block = extractor.findMermaidBlockFor(className, methodName)
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("LogicCodeReport")
        toolWindow?.activate(null)
        val ui = ReportToolWindowRegistry.get(project)

        if (block == null || ui == null) {
            // ToolWindow not ready or no mermaid found: fallback to status message.
            ReportToolWindowRegistry.setStatusIfAvailable(
                project,
                "No Mermaid block found for $className.$methodName. Generate report first (PROJECT_ENTRYPOINT_GUIDE.md)."
            )
            return
        }

        val nodeId = block.matchedNodeId
        val mermaidBody = if (isEntrypoint || nodeId == null) {
            block.fullText
        } else {
            val graph = MermaidGraphParser.parse(block.fullText)
            val sliced = MermaidGraphSlicer.sliceDownstream(graph, nodeId)
            MermaidGraphSlicer.render(sliced)
        }

        if (isEntrypoint) {
            ui.showEntrypointFromGuide(
                "Entrypoint: $className.$methodName",
                className,
                methodName,
                mermaidBody
            )
            
            registerMethodDependencies(method)
            return
        }

        val file = method.containingFile?.virtualFile
        val filePath = file?.path ?: "(unknown)"
        val fileStamp = file?.modificationStamp ?: 0L
        val methodSource = method.text ?: ""
        val methodId = MethodPsiResolver.methodIdOf(method)
        if (methodId == null) {
            ReportToolWindowRegistry.setStatusIfAvailable(project, "Cannot build MethodId for $className.$methodName")
            return
        }
        ui.showNonEntrypointLogic("Method Logic: $className.$methodName", methodId, filePath, fileStamp, methodSource)
        
        registerMethodDependencies(method)
    }
    
    private fun registerMethodDependencies(method: PsiMethod) {
        val project = method.project
        val cacheService = MethodLogicCacheService.getInstance(project)
        val callerId = MethodPsiResolver.methodIdOf(method) ?: return
        val calledMethods = findCalledMethods(method)
        for (calledMethod in calledMethods) {
            val calleeId = MethodPsiResolver.methodIdOf(calledMethod) ?: continue
            cacheService.registerDependency(callerId, calleeId)
        }
    }
    
    private fun findCalledMethods(method: PsiMethod): List<PsiMethod> {
        val calledMethods = mutableListOf<PsiMethod>()
        method.accept(object : com.intellij.psi.PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is com.intellij.psi.PsiMethodCallExpression) {
                    val resolved = element.resolveMethod()
                    if (resolved != null && resolved != method) {
                        calledMethods.add(resolved)
                    }
                }
                super.visitElement(element)
            }
        })
        
        return calledMethods
    }
}

