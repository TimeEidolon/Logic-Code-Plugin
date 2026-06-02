package com.github.timeeidolon.logiccodeplugin.report

/**
 * Ordered steps for "Generate Report (LLM)" so UI can render a checklist.
 *
 * Note: steps are coarse-grained; they map to [ReportGenerator] milestones.
 */
enum class ReportGenerationStep(val displayName: String) {
    PREPARE_OUTPUT_DIR("Preparing output directories"),
    SCAN_INVENTORY("Scanning project inventory"),
    COLLECT_ENTRYPOINTS("Collecting entrypoints (controllers/jobs/scheduled)"),
    COLLECT_SOURCE_CONTEXT("Collecting source context"),
    BUILD_PROMPT("Building report prompt"),
    WRITE_INTERMEDIATE("Writing intermediate files (prev)"),
    CALL_LLM("Calling LLM (streaming)"),
    WRITE_REPORTS("Writing final reports (report)"),
    DONE("Done"),
    ERROR("Error")
}

