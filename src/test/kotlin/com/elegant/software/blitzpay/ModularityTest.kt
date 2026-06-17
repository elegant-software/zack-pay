/*
 * Copyright 2022-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.elegant.software.blitzpay

import com.elegant.software.blitzpay.QuickpayApplication
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Verifying modulithic structure and creating documentation for [ApplicationModules].
 *
 * @author Oliver Drotbohm
 */

internal class ModularityTests {
    companion object {
        private val LOG = LoggerFactory.getLogger(ModularityTests::class.java)
    }

    var modules: ApplicationModules = ApplicationModules.of(QuickpayApplication::class.java)

    @Test
    fun verifiesArchitecture() {
        println(modules)

        modules.verify()
    }

    @Test
    fun createDocumentation() {
        Documenter(modules).writeDocumentation()
    }


    @Test
    @Throws(IOException::class)
    fun writeSummary() {
        val docsPathName = "target/spring-modulith-docs"
        val summaryFileName = "all-docs.adoc"

        val docsPath = Paths.get(docsPathName)
        val fileMap: MutableMap<String?, StringBuilder?> = TreeMap<String?, StringBuilder?>()

        try {
            Files.list(docsPath).use { files ->
                files
                    .filter { path: Path? -> Files.isRegularFile(path) }
                    .filter { path: Path? -> path!!.getFileName().toString() != summaryFileName }
                    .forEach { filePath: Path? ->
                        val fileName = filePath!!.getFileName().toString()
                        val fileHandle = fileName.substring(0, fileName.lastIndexOf('.'))
                        val relativePath = docsPath.relativize(filePath).toString().replace("\\", "/")

                        // Determine the include directive based on file extension
                        val includeDirective = if (fileName.endsWith(".puml")) "plantuml::" else "include::"
                        fileMap.computeIfAbsent(fileHandle) { k: String? -> StringBuilder() }!!
                            .append(includeDirective).append(relativePath).append("[]\n")
                    }
            }
        } catch (e: IOException) {
            LOG.warn("Skip writing summary: {} {}", e.javaClass.getName(), e.message)
            return
        }

        if (fileMap.isEmpty()) {
            LOG.warn("Skip writing summary: Nothing to summarize in {} ", docsPathName)
            return
        }

        // Create summary file
        val indexFile = File(docsPathName + "/" + summaryFileName)
        FileWriter(indexFile).use { writer ->
            fileMap.forEach { (handle: String?, references: StringBuilder?) ->
                try {
                    writer.write("== " + handle + "\n")
                    writer.write(references.toString())
                    writer.write("\n")
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }
}