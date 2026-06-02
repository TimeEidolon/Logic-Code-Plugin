package com.github.timeeidolon.logiccodeplugin.db

data class TableUsage(
    val table: String,
    val op: Op,
    val evidence: String,
    val confidence: Confidence
) {
    enum class Op { READ, WRITE, READ_WRITE, UNKNOWN }
    enum class Confidence { HIGH, MEDIUM, LOW }
}

