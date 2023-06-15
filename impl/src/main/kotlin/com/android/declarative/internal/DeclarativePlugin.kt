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

import com.android.declarative.internal.IssueLogger
import com.android.declarative.internal.toml.forEachTable
import com.android.declarative.internal.parsers.DependencyParser
import com.android.declarative.internal.parsers.PluginParser
import com.android.declarative.internal.tasks.DeclarativeBuildSerializerTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.tomlj.TomlParseResult
import org.gradle.internal.time.Time
import javax.inject.Inject

class DeclarativePlugin @Inject constructor(

): AbstractDeclarativePlugin(), Plugin<Project> {

    override val buildFileName: String
        get() = "build.gradle.toml"

    override fun apply(project: Project) {
        val issueLogger = IssueLogger(lenient = false, logger = LoggerWrapper(project.logger))

        val parsedDecl: TomlParseResult = Time.currentTimeMillis().run {
            val parseResult = super.parseDeclarativeInFolder(project.projectDir, issueLogger)
            // println("$buildFileName parsing finished in ${Time.currentTimeMillis() - this} ms")
            parseResult
        }

        // plugins must be applied first so extensions are correctly registered.
        parsedDecl.getArray("plugins")?.also { plugins ->
            PluginParser().parse(plugins).forEach { pluginInfo ->
                project.apply(mapOf("plugin" to pluginInfo.id))
            }
        }

        parsedDecl.keySet().forEach { topLevelDeclaration ->
            when(topLevelDeclaration) {
                "plugins" -> {
                    // already applied above.
                }
                "dependencies" -> {
                    // handled below, so all DSL driven configurations are created before dependencies are added.
                }
                else -> {
                    if (!parsedDecl.isTable(topLevelDeclaration)) {
                        throw Error("Invalid declaration, $topLevelDeclaration must be a TOML table")
                    }
                    // find the extension registered under the name
                    val publicExtensionType = project.extensions.extensionsSchema.first {
                        it.name == topLevelDeclaration
                    }.publicType

                    issueLogger.logger.LOG { "Extension type is $publicExtensionType" }
                    project.extensions.findByName(topLevelDeclaration)?.also { extension ->
                        GenericDeclarativeParser(project, issueLogger).parse(
                            parsedDecl.getTable(topLevelDeclaration)
                                ?: throw Error("Internal error : please file a bug providing your TOML file"),
                            publicExtensionType.concreteClass.kotlin,
                            extension
                        )
                    } ?: throw Error("Cannot find extension $topLevelDeclaration, " +
                            "has the plugin registering the extension been applied ?")
                }
            }
        }

        parsedDecl.getTable("dependencies")?.also {
            DependencyProcessor(
                { projectPath: String -> project.rootProject.project(projectPath)},
                project::files,
                project.dependencyFactory,
                project.dependencies,
                issueLogger,
            ).process(DependencyParser(issueLogger).parseToml(it))
        }

        project.afterEvaluate {
            createTasks(it)
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
}
