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

import com.android.declarative.internal.model.DependencyInfo
import com.android.declarative.internal.model.DependencyType
import com.android.declarative.internal.model.FilesDependencyInfo
import com.android.declarative.internal.model.MavenDependencyInfo
import com.android.declarative.internal.model.NotationDependencyInfo
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.ConfigurableFileCollection

/**
 * A processor class that process [List] of [DependencyInfo] for a module and adds such
 * dependency to the provided [dependencyHandler] using Gradle APIs.
 */
@Suppress("UnstableApiUsage")
class DependencyProcessor(
    private val projectResolver: (id: String) -> Project,
    private val fileCollectionFactory: () -> ConfigurableFileCollection,
    private val dependencyFactory: DependencyFactory,
    private val dependencyHandler: DependencyHandler,
    private val issueLogger: IssueLogger,
) {
    fun process(dependencies: List<DependencyInfo>) {
        dependencies.forEach { dependency ->
            when(dependency) {
                is NotationDependencyInfo -> {
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
                is FilesDependencyInfo -> {
                    addFilesDependency(dependency)
                }
                is MavenDependencyInfo -> {
                    addLibraryDependency(dependency)
                }
            }
        }
    }

    private fun addFilesDependency(dependency: FilesDependencyInfo) {
        val fileCollection = fileCollectionFactory()
        dependency.files.forEach(fileCollection::from)
        println("adding files dependency ${dependency.files} to ${dependency.configuration}")
        dependencyHandler.add(
            dependency.configuration,
            dependencyFactory.create(
                fileCollection
            )
        )
    }

    private fun addLibraryDependency(dependency: MavenDependencyInfo) {
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

    private fun addNotationDependency(dependencyInfo: NotationDependencyInfo) {
        val dependency = when(dependencyInfo.notation) {
            "localGroovy" -> dependencyFactory.localGroovy()
            "gradleApi" -> dependencyFactory.gradleApi()
            "gradleTestKit" -> dependencyFactory.gradleTestKit()
            else -> dependencyFactory.create(dependencyInfo.notation)
        }
        println("Adding $dependency to ${dependencyInfo.configuration}")
        dependencyHandler.add(dependencyInfo.configuration, dependency)
    }
}