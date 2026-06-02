package com.github.timeeidolon.logiccodeplugin.ui

import org.webjars.WebJarAssetLocator
import java.nio.charset.StandardCharsets

/** Embedded Mermaid `mermaid.min.js` from classpath (WebJar). */
object MermaidWebJar {
    private val locator = runCatching { WebJarAssetLocator() }.getOrNull()

    val mermaidMinJs: String by lazy {
        val path = runCatching {
            locator?.getFullPath("mermaid.min.js")
                ?: locator?.getFullPath("mermaid", "dist/mermaid.min.js")
        }.getOrNull() ?: return@lazy ""

        val stream =
            MermaidWebJar::class.java.classLoader.getResourceAsStream(path) ?: return@lazy ""
        stream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }
}
