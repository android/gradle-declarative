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

import com.android.declarative.internal.model.ProjectDependenciesDAG
import com.android.declarative.internal.parsers.DeclarativeFileParser
import com.android.declarative.internal.parsers.DependenciesResolver
import com.android.declarative.internal.toml.checkElementsPresence
import com.android.declarative.internal.toml.forEach
import com.android.declarative.internal.toml.forEachTable
import com.android.declarative.internal.toml.safeGetString
import kotlinx.coroutines.runBlocking
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.internal.time.Time
import org.tomlj.TomlParseResult
import java.io.File
import java.net.URL
import java.util.logging.Logger
import javax.inject.Inject
import org.gradle.api.model.ObjectFactory

/**
 * Plugin implementation that handles [Settings] population from a settings.gradle.toml file
 *
 * It also offers other services like automatically registering the declarative plugin
 * to each module in this project.
 */
class SettingsDeclarativePlugin @Inject constructor(
    val objects: ObjectFactory
): AbstractDeclarativePlugin(), Plugin<Settings> {
    override val buildFileName: String
        get() = "settings.gradle.toml"

    private val logger =
        IssueLogger(lenient = false, JdkLoggerWrapper(
            Logger.getLogger(SettingsDeclarativePlugin::class.java.name)
        ))

    override fun apply(settings: Settings) {

        val declarativeProvider = DeclarativeFileValueSource.enlist(
            objects,
            settings,
            buildFileName
        )
        val declarativeFileContent = if (declarativeProvider.isPresent) {
            declarativeProvider.get()
        } else null

        if (declarativeFileContent.isNullOrEmpty()) return

        val settingsDeclarations: TomlParseResult = Time.currentTimeMillis().run {

            val parseResult: TomlParseResult = Time.currentTimeMillis().run {
                super.parseDeclarativeFile(
                    "${settings.settingsDir}${File.separatorChar}$buildFileName",
                    declarativeFileContent,
                    logger
                )
            }
            println("$buildFileName parsing finished in ${Time.currentTimeMillis() - this} ms")
            parseResult
        }

        settings.pluginManagement { pluginManagement ->
            // declare our plugin version to the plugins management
            pluginManagement.plugins.id("com.android.experiments.declarative").version(Constants.PLUGIN_VERSION)

            // declare all declared plugin version to the plugins management.
            settingsDeclarations.getArray("plugins")?.also {
                it.forEachTable { table ->
                    // if the user does not specify the `id`, it means they are not interested in providing plugins
                    // versions to the plugin management and probably solely relies on classpath configuration.
                    if (table.contains("id")) {
                        table.checkElementsPresence("plugins", "id", "version")
                        println("Applying plugin ${table.getString("id")} version ${table.getString("version")}")
                        pluginManagement.plugins
                            .id(table.safeGetString("id"))
                            .version(table.safeGetString("version"))
                            .apply(false)
                    }
                }
            }
        }

        settings.gradle.beforeProject { project ->

            addRepositoriesToSubProject(settingsDeclarations, settings, project)

            if (project.path == ":") {
                configureRootProject(settingsDeclarations, project)
            } else {
                configureSubProject(project)
            }
        }
        declareSubProjectsToGradle(settingsDeclarations, settings)
    }

    private fun getListOfFocusedProjects(
        settings: Settings,
        parsedDecl: TomlParseResult,
    ): List<String> {
        val focusProvider = DeclarativeFileValueSource.enlist(
            objects,
            settings,
            "focus.toml"
        )

        if (focusProvider.isPresent) {
            val fileContents = focusProvider.get()
                ?: return listOf()

            File(settings.settingsDir, "focus.toml").takeIf(File::exists)?.let { focusFile ->
                val listOfProjects = mutableListOf<String>()
                DeclarativeFileParser().parseDeclarativeFile(
                    "${settings.settingsDir}${File.separatorChar}focus.toml",
                    fileContents
                )
                    .forEach("focus", listOfProjects::add)
                return listOfProjects.toList()
            }
            return if (parsedDecl.contains("focus")) {
                parsedDecl.safeGetString("focus").split(",")
            } else {
                val listOfProjects = mutableListOf<String>()
                val regex = Regex("(:[^:]*):(.*)")
                settings.gradle.startParameter.taskNames.forEach {
                    if (regex.matches(it)) {
                        regex.matchEntire(it)?.groups?.get(1)?.value?.let { projectName ->
                            println("Focusing on $projectName from task name $it")
                            listOfProjects.add(projectName)
                        }
                    }
                }
                listOfProjects.toList()
            }
        } else {
            return listOf()
        }
    }

    /**
     * Adds all declared repositories from [settingsDeclarations] and adds them to the [project].
     *
     * @param settingsDeclarations the parsed declaration for the project.
     * @param settings the Gradle's [Settings] for this project
     * @param project the sub module's [Project]
     */
    private fun addRepositoriesToSubProject(settingsDeclarations: TomlParseResult, settings: Settings, project: Project) {
        settingsDeclarations.getTable("pluginsManagement.repositories")?.entrySet()?.forEach {
            if (it.key == "inheritSettings") {
                settings.pluginManagement.repositories.forEach {
                    println("Adding ${it.name} to project's buildscript")
                    project.buildscript.repositories.add(it)
                }
            } else {
                val repoInfo =
                    settingsDeclarations.getTable("pluginsManagement.repositories")?.getTable(it.key)
                        ?: throw RuntimeException("No 'url' and 'type' specified for repository `${it.key}`")
                val repoType = repoInfo.getString("type")
                if (repoType != "maven") {
                    throw RuntimeException("Only maven repository are supported at this point, unsupported repository type : $repoType")
                }

                println("Adding $repoType repository named ${it.key} to project's buildscript")
                project.buildscript.repositories.maven { repo ->
                    repo.name = it.key
                    repo.url = URL(repoInfo.getString("url")).toURI()
                }
            }
        }
    }

    /**
     * Configures a sub project.
     *
     * @param project the Gradle's [Project] to configure
     */
    private fun configureSubProject(project: Project) {

        val declarativeFile = DeclarativeFileValueSource.enlist(
            project.providers,
            project.layout.projectDirectory.file("build.gradle.toml"),
        )

        // only registers the declarative plugin if there is a build.gradle.toml file present in the project dir.
        if (declarativeFile.isPresent) {
            project.apply(mapOf("plugin" to "com.android.experiments.declarative"))
        } else {
            println("${project.path} ignored, build files are present")
        }
    }

    /**
     * Configures the root project
     *
     * @param settingsDeclarations Declarative parsed result.
     * @param project Root project's [Project]
     */
    private fun configureRootProject(settingsDeclarations: TomlParseResult, project: Project) {
                // add all plugins to the classpath of the root project, so it is available to subprojects.
                // sadly, so far, I have not found a way to do the equivalent of
                //    plugins {
                //        id 'com.android.application' apply false
                //    }
                // as the project's PluginManager does not allow me to alter the project's buildscript classpath.
                // therefore I must force user's to provide the module information and add it to the
                // classpath configuration.
                // note : I have tried to use project.pluginManager.apply(...) but it does not add the plugin to the
                // buildscript classpath, it just applies it (assuming it is already in the classpath).
                println("Configuring root project")
                project.buildscript.dependencies.also { dependencyHandler ->
            settingsDeclarations.getArray("plugins")?.forEachTable { table ->
                table.checkElementsPresence("plugins", "module", "version")
                val notation = "${table.safeGetString("module")}:${table.safeGetString("version")}"
                println("Adding $notation to classpath")
                dependencyHandler.add(
                    ScriptHandler.CLASSPATH_CONFIGURATION,
                    notation)
            }
        }
    }

    /**
     * Declare all sub projects to Gradle.
     *
     * First, read all projects, construct a DAG of all projects relationships and decided based on user's choice
     * which project should be declared to Gradle.
     *
     * @param settingsDeclarations root project settings.gradle.toml file parsed content.
     * @param settings [Settings] use to include sub project.
     */
    private fun declareSubProjectsToGradle(settingsDeclarations: TomlParseResult, settings: Settings) {
        val dependenciesResolver = DependenciesResolver(logger)

        var includedProjectsNumber = 0
        val alreadyAddedProjects = mutableSetOf<String>()

        val transitiveDependenciesStartTime = System.currentTimeMillis()

        val listOfFocusedProjects = getListOfFocusedProjects(settings, settingsDeclarations)
        if (listOfFocusedProjects.isNotEmpty()) {
            // create the DAG of project dependencies, it will be easier to manipulate.
            val dependenciesDAG = ProjectDependenciesDAG.create(
                runBlocking {

                    dependenciesResolver.readAllProjectsDependencies(
                        { relativePath, fileName ->

                            val settingsDir = objects.directoryProperty().also {
                                it.set(settings.settingsDir)
                            }
                            val buildDir = objects.directoryProperty().also {
                                it.set(settingsDir.dir(relativePath.substring(1)))
                            }

                            val declarativeFileContent = DeclarativeFileValueSource.enlist(
                                settings.providers,
                                buildDir.file(fileName),
                            )

                            if (declarativeFileContent.isPresent) {
                                declarativeFileContent.get()
                            } else null
                        },
                        settingsDeclarations,
                    )
                })
            listOfFocusedProjects.forEach { focusedProjectPath ->
                //println("Focusing on $focusedProjectPath -> ${mapOfDependencies[focusedProjectPath]}")
                dependenciesDAG.getNode(focusedProjectPath)?.let { node ->
                    node.getTransitiveDependencies().forEach {
                        if (!alreadyAddedProjects.contains(it.path)) {
                            settings.include(it.path)
                            includedProjectsNumber++
                            alreadyAddedProjects.add(it.path)
                        }
                    }
                }
            }
        } else {
            // add included sub projects, which can be expressed as a Table
            // or as array of strings.
            settingsDeclarations.forEach("include") { projectName ->
                //println("Including `$projectName` project")
                settings.include(projectName)
                includedProjectsNumber++
            }
        }
        println("Calculated transitive dependencies in ${System.currentTimeMillis() - transitiveDependenciesStartTime}")
        var totalProjectsNumber = 0
        settingsDeclarations.forEach("include") {
            totalProjectsNumber++
        }
        println("Included $includedProjectsNumber projects, discarded ${totalProjectsNumber - includedProjectsNumber}")
    }
}