package com.github.timeeidolon.logiccodeplugin.inventory

import com.intellij.psi.PsiMethod

/**
 * Shared rules for gutter entry detection, report collector, and prompts.
 */
object EntrypointSignatures {

    val SCHEDULE_ANNOTATIONS = setOf("Scheduled")

    val JOB_ANNOTATIONS = setOf(
        "XxlJob"
    )

    /** Message consumers / listeners commonly seen on methods. */
    val MESSAGE_ANNOTATIONS = setOf(
        "KafkaListener",
        "RabbitListener",
        "JmsListener",
        "PulsarListener"
    )

    /** Spring application events. */
    val EVENT_ANNOTATIONS = setOf(
        "EventListener",
        "TransactionalEventListener"
    )

    val GRPC_METHOD_ANNOTATIONS = setOf(
        "GrpcMethod",
        "RpcMethod",
    )

    val JAX_RS_HTTP_VERBS = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

    /** True if PSI method declares at least one “entry-ish” annotation. */
    fun isPsiEntrypointCandidate(method: PsiMethod): Boolean {
        val tags = psiAnnotationTags(method).toSet()

        if (tags.any { it in AnnotationVisitor.MAPPING_ANNOTATIONS }) return true
        if (tags.any { it in JOB_ANNOTATIONS }) return true
        if (tags.any { it in SCHEDULE_ANNOTATIONS }) return true
        if (tags.any { it in MESSAGE_ANNOTATIONS }) return true
        if (tags.any { it in EVENT_ANNOTATIONS }) return true
        if (tags.any { it in GRPC_METHOD_ANNOTATIONS }) return true

        // JAX-RS (Jakarta) method-level verbs (often on resource classes).
        val hasJaxRsVerb = JAX_RS_HTTP_VERBS.any { tags.contains(it) }
        val hasPath = tags.contains("Path")
        if (hasJaxRsVerb && hasPath && !tags.any { it in AnnotationVisitor.MAPPING_ANNOTATIONS }) {
            return true
        }

        return false
    }

    /** Short names extracted from PSI annotations (`Xxx` from `pkg.Xxx`). */
    fun psiAnnotationTags(method: PsiMethod): List<String> =
        method.modifierList.annotations.mapNotNull { it.qualifiedName?.substringAfterLast(".") }
}
