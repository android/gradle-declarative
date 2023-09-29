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

import com.android.declarative.internal.cache.VersionCatalogs
import com.android.declarative.internal.model.DependencyInfo
import com.android.declarative.internal.model.DependencyInfo.Alias
import com.android.declarative.internal.model.DependencyInfo.ExtensionFunction
import com.android.declarative.internal.model.DependencyInfo.Files
import com.android.declarative.internal.model.DependencyInfo.Maven
import com.android.declarative.internal.model.DependencyInfo.Notation
import com.android.declarative.internal.model.DependencyInfo.Platform
import com.android.declarative.internal.model.DependencyType
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler

/**
 * A processor class that process [List] of [DependencyInfo] for a module and adds such
 * dependency to the provided [dependencyHandler] using Gradle APIs.
 */
@Suppress("UnstableApiUsage")
class DependencyProcessor(
    private val projectResolver: (id: String) -> Project,
    private val project: Project,
    private val issueLogger: IssueLogger,
) {

    private val dependencyHandler = project.dependencies
    private val dependencyFactory = project.dependencyFactory
    private val versionCatalogs = VersionCatalogs(issueLogger)

    fun process(dependencies: List<DependencyInfo>) {
        dependencies.forEach { dependency ->
            when(dependency) {
                is Notation -> {
                    if (dependency.type == DependencyType.PROJECT) {
                        addProjectDependency(
                            dependency.configuration,
                            dependency.notation
                        )
                    } else if (dependency.type == DependencyType.NOTATION) {
                        addNotationDependency(
                            dependency
                        )
                    }
                }
                is Files -> {
                    addFilesDependency(dependency)
                }
                is Maven -> {
                    addLibraryDependency(dependency)
                }
                is ExtensionFunction -> {
                    addExtensionDependency(dependency)
                }
                is Alias -> {
                    addVersionCatalogDependency(dependency)
                }
                is Platform -> {
                    addPlatformDependency(dependency)
                }
            }
        }
    }

    private fun addVersionCatalogDependency(dependency: Alias) {

        val versionCatalogIdentifier = dependency.alias.substringBefore('.')
        val libraryName = dependency.alias.substringAfter('.')
        val versionCatalog = versionCatalogs.getVersionCatalog(project, versionCatalogIdentifier)
        val lib = versionCatalog.findLibrary(libraryName)
        lib.ifPresentOrElse({
            println("Adding Version catalog ${dependency.alias} to ${dependency.configuration}")
            dependencyHandler.add(
                dependency.configuration,
                it
            )
        }) {
            issueLogger.logger.warning(
                "$libraryName library not found in version catalog $versionCatalogIdentifier"
            )
        }
    }

    private fun addPlatformDependency(dependency: Platform) {
        println("Adding platform ${dependency.name} to ${dependency.configuration}")
        if (dependency.name.contains(".")) {
            // TODO: Check that the assumption of a notation with . is a version catalog alias.
            val versionCatalogIdentifier = dependency.name.substringBefore('.')
            val platformName = dependency.name.substringAfter('.')
            val versionCatalog = versionCatalogs.getVersionCatalog(project, versionCatalogIdentifier)
            val lib = versionCatalog.findLibrary(platformName)
            if (lib.isPresent) {
                println("Adding platform ${lib.get().get()} to ${dependency.configuration}")

                dependencyHandler.platform(lib.get().get().toString())
            }
//            lib.ifPresentOrElse(dependencyHandler::platform) {
//                issueLogger.raiseError("Cannot find ${dependency.name} in version catalog")
//            }
        } else {
            dependencyHandler.platform(
                dependency.name
            )
        }
    }

    private fun addFilesDependency(dependency: Files) {
        val fileCollection = project.files()
        dependency.files.forEach(fileCollection::from)
        println("adding files dependency ${dependency.files} to ${dependency.configuration}")
        dependencyHandler.add(
            dependency.configuration,
            dependencyFactory.create(
                fileCollection
            )
        )
    }

    private fun addLibraryDependency(dependency: Maven) {
        println("Adding maven ${dependency.name} to ${dependency.configuration}")
        dependencyHandler.add(
            dependency.configuration,
            dependencyFactory.create(
                dependency.group,
                dependency.name,
                dependency.version
            )
        )
    }

    private fun addProjectDependency(configurationName: String, notation: String) {
        val dependencyTarget = projectResolver(notation)
        //println("Adding project $dependencyTarget to $configurationName")
        dependencyHandler.add(configurationName, dependencyFactory.create(dependencyTarget))
    }

    private fun addNotationDependency(dependencyInfo: Notation) {
        val dependency = when(dependencyInfo.notation) {
            "localGroovy" -> dependencyFactory.localGroovy()
            "gradleApi" -> dependencyFactory.gradleApi()
            "gradleTestKit" -> dependencyFactory.gradleTestKit()
            else -> dependencyFactory.create(dependencyInfo.notation)
        }
        println("Adding $dependency to ${dependencyInfo.configuration}")
        dependencyHandler.add(dependencyInfo.configuration, dependency)
    }


    private fun addExtensionDependency(dependency: DependencyInfo.ExtensionFunction) {
        if (dependency.extension == "kotlin") {
            if (dependency.parameters.size == 0 || dependency.parameters.size > 2) {
                issueLogger.raiseError("Unsupported number of parameters for kotlin() extension, needed one" +
                        " or two parameters but got ${dependency.parameters.size}")
            }
            val kotlinDependencyExtensions = DeclarativePlugin::class.java.classLoader.loadClass(
                "org.gradle.kotlin.dsl.KotlinDependencyExtensionsKt"
            )

            val method = kotlinDependencyExtensions.getMethod("kotlin", DependencyHandler::class.java, String::class.java, String::class.java)
            val gradleDependency = dependencyFactory.create(
                method.invoke(
                    null, // extension function in kotlin are static methods in Java.
                    dependencyHandler,
                    dependency.parameters["module"],
                    dependency.parameters["version"],
                ) as String
            )
            println("Adding ${gradleDependency.name} to ${dependency.configuration}")
            dependencyHandler.add(dependency.configuration, gradleDependency)
        }
    }
}