package com.github.timeeidolon.logiccodeplugin.inventory

/**
 * Static entrypoint markers for regex-based scanners (no PSI dependency).
 *
 * NOTE: Java PSI based detection uses a separate implementation in the Java language module.
 */
object EntrypointAnnotationNames {
    val JOB_ANNOTATIONS = setOf("XxlJob")
    val SCHEDULE_ANNOTATIONS = setOf("Scheduled")
    val MESSAGE_ANNOTATIONS = setOf("KafkaListener", "RabbitListener", "JmsListener", "PulsarListener")
    val EVENT_ANNOTATIONS = setOf("EventListener", "TransactionalEventListener")
    val GRPC_METHOD_ANNOTATIONS = setOf("GrpcMethod", "RpcMethod")
    val JAX_RS_HTTP_VERBS = setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")
}

