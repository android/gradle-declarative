/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.declarative.internal

import com.android.declarative.internal.parsers.DependencyParser
import com.android.declarative.internal.parsers.PluginParser
import com.android.declarative.internal.tasks.DeclarativeBuildSerializerTask
import com.android.declarative.internal.variantApi.AndroidComponentsParser
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.tomlj.TomlParseResult
import org.gradle.internal.time.Time
import org.tomlj.TomlTable
import java.io.File
import javax.inject.Inject

class DeclarativePlugin @Inject constructor(

): AbstractDeclarativePlugin(), Plugin<Project> {

    override val buildFileName: String
        get() = "build.gradle.toml"

    override fun apply(project: Project) {
        val issueLogger = IssueLogger(lenient = false, logger = LoggerWrapper(project.logger))
        val cache = DslTypesCache()
        parseDeclarativeBuildFile(project, issueLogger, project.layout.projectDirectory.file(buildFileName), cache)


        project.afterEvaluate {
            createTasks(it)
        }
    }

    private fun parseDeclarativeBuildFile(project: Project, issueLogger: IssueLogger, buildFile: RegularFile, cache: DslTypesCache) {
        val declarativeProvider = DeclarativeFileValueSource.enlist(
            project.providers,
            buildFile
        )
        val declarativeFileContent = if (declarativeProvider.isPresent) {
            declarativeProvider.get()
        } else null

        if (declarativeFileContent.isNullOrEmpty()) return

        val parsedDecl: TomlParseResult = Time.currentTimeMillis().run {
            val parseResult = super.parseDeclarativeFile(
                "${project.projectDir}${File.separatorChar}$buildFileName",
                declarativeFileContent,
                issueLogger)
            // println("$buildFileName parsing finished in ${Time.currentTimeMillis() - this} ms")
            parseResult
        }

        // plugins must be applied first so extensions are correctly registered.
        parsedDecl.getArray("plugins")?.also { plugins ->
            PluginParser().parse(plugins).forEach { pluginInfo ->
                println("In project, applying ${pluginInfo.id}")
                project.pluginManager.apply(pluginInfo.id)
            }
        }

        parsedDecl.getArray("includeBuildFiles")?.let {
            val buildFileCount = it.size()
            for (index in 0 until buildFileCount) {
                val includedBuildFile = project.layout.projectDirectory.file(it.getString(index))
                parseDeclarativeBuildFile(project, issueLogger,  includedBuildFile, cache)
            }
        }

        parsedDecl.keySet().forEach { topLevelDeclaration ->
            when(topLevelDeclaration) {
                "includeBuildFiles" -> {
                    // skip includes processing again.
                }
                "plugins" -> {
                    // already applied above.
                }
                "dependencies" -> {
                    // handled below, so all DSL driven configurations are created before dependencies are added.
                }
                "androidComponents" -> {
                    // androidComponents is handled separately with a dedicated parser since it is very
                    // heavy on callbacks which cannot be generically parsed.

                    parseTomlTable(
                        AndroidComponentsParser(project, cache, issueLogger),
                        topLevelDeclaration,
                        parsedDecl,
                        project,
                        issueLogger
                    )
                }
                else -> {
                    parseTomlTable(
                        GenericDeclarativeParser(project, cache, issueLogger),
                        topLevelDeclaration,
                        parsedDecl,
                        project,
                        issueLogger
                    )
                }
            }
        }

        parsedDecl.getTable("dependencies")?.also {
            @Suppress("UnstableApiUsage")
            DependencyProcessor(
                { projectPath: String -> project.rootProject.project(projectPath)},
                project::files,
                project.dependencyFactory,
                project.dependencies,
                issueLogger,
            ).process(DependencyParser(issueLogger).parseToml(it))
        }
    }

    private fun createTasks(project: Project) {
        project.extensions.findByName("android")?.let { android ->
            project.tasks.register(
                "serializeBuildDeclarations",
                DeclarativeBuildSerializerTask::class.java,
            ) {
                println("Configuring serializeBuildDeclaration")
                it.extension.set(android)
            }
        }
    }

    /**
     * Parse a [TomlTable] using a dedicated [DeclarativeFileParser].
     *
     * @param parser the Toml parser for this Toml declaration
     * @param topLevelDeclaration name of the top level extension block, like 'android' or 'androidComponents`
     * @param parsedToml the [TomlTable] to parse
     * @param project the project being configured
     * @param issueLogger to log issues and traces
     */
    private fun parseTomlTable(
        parser: DeclarativeFileParser,
        topLevelDeclaration: String,
        parsedToml: TomlTable,
        project: Project,
        issueLogger: IssueLogger
    ) {
        if (!parsedToml.isTable(topLevelDeclaration)) {
            throw Error("Invalid declaration, $topLevelDeclaration must be a TOML table")
        }
        // find the extension registered under the name
        val publicExtensionType = project.extensions.extensionsSchema.firstOrNull() {
            it.name == topLevelDeclaration
        }?.publicType
            ?: throw Error("Cannot find top level key $topLevelDeclaration in ")

        issueLogger.logger.LOG { "Extension type is $publicExtensionType" }
        project.extensions.findByName(topLevelDeclaration)?.also { extension ->
            parser.parse(
                parsedToml.getTable(topLevelDeclaration)
                    ?: throw Error("Internal error : please file a bug providing your TOML file"),
                publicExtensionType.concreteClass.kotlin,
                publicExtensionType.concreteClass.cast(extension)
            )
        } ?: throw Error("Cannot find extension $topLevelDeclaration, " +
                "has the plugin registering the extension been applied ?")
    }
}
