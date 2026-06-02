package com.github.timeeidolon.logiccodeplugin.report

import java.nio.file.Path

data class GenerateResult(
    val projectRoot: Path,
    val outputDir: Path,
    val inventoryPath: Path? = null,
    val sourceContextPath: Path? = null,
    val promptPath: Path? = null,
    val reportPath: Path? = null,
    val playbookPath: Path? = null,
    val entrypointGuidePath: Path? = null,
    val modelOutputPath: Path? = null
)
