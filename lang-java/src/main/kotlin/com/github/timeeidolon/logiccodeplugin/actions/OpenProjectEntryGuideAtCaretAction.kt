package com.github.timeeidolon.logiccodeplugin.actions

import com.github.timeeidolon.logiccodeplugin.graph.EntrypointGuideGraphExtractor
import com.github.timeeidolon.logiccodeplugin.inventory.EntrypointSignatures
import com.github.timeeidolon.logiccodeplugin.nav.ReportGuideNavigator
import kotlin.io.path.exists
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiMethod

/**
 * Opens [PROJECT_ENTRYPOINT_GUIDE.md] at the Markdown section anchored on the caret method (`SimpleClass.methodName`).
 */
class OpenProjectEntryGuideAtCaretAction : AnAction(
    "Open Entry Guide Anchor",
    "Jump to PROJECT_ENTRYPOINT_GUIDE.md near this entrypoint method.",
    null
) {

    override fun update(e: AnActionEvent) {
        val project = e.project
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)
        val pathOk = project != null &&
            EntrypointGuideGraphExtractor(project).guideMarkdownPath()?.exists() == true
        if (project == null || psiFile == null || editor == null || !pathOk) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val offset = editor.caretModel.primaryCaret.offset
        val elt = psiFile.findElementAt(offset)
        val method = PsiTreeUtil.getParentOfType(elt, PsiMethod::class.java, false)

        val ok = method != null && EntrypointSignatures.isPsiEntrypointCandidate(method)
        e.presentation.isEnabledAndVisible = ok
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val offset = editor.caretModel.primaryCaret.offset
        val elt = psiFile.findElementAt(offset) ?: return
        val method = PsiTreeUtil.getParentOfType(elt, PsiMethod::class.java, false) ?: return
        val clazz = method.containingClass ?: return

        val simple = clazz.name ?: return

        val ok = ReportGuideNavigator.navigateToGuideSection(project, simple, method.name)
        if (!ok) {
            Messages.showWarningDialog(
                project,
                "Could not locate PROJECT_ENTRYPOINT_GUIDE.md. Generate report first.",
                "Logic Code"
            )
        }
    }
}
