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

package com.android.declarative.tests

import com.android.build.gradle.integration.common.fixture.GradleProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.*

class LargeDeclarativeTest {

    val nbOfApplications = 10
    val nbOfAndroidLibs = 50
    val nbOfJavaLibs = 100

    val nbOfAndroidLibsDependenciesPerApp: Int = nbOfApplications / 3
    val nbOfJavaLibsDependenciesPerApp: Int = nbOfAndroidLibs / 10
    val nbOfJavaLibsDependenciesPerAndroidLib: Int = nbOfJavaLibs / 40

    private val random = Random(1)

    @Rule
    @JvmField
    val project: GradleTestProject = GradleTestProject.builder()
        .withHeap("4G")
        .withExtraPluginClasspath("com.android.experiments.declarative:api:${System.getenv("PLUGIN_VERSION")}")
        .fromTestApp(
            MultiModuleTestProject(
                mutableMapOf<String, GradleProject>().also { projects ->
                    repeat(nbOfApplications) { appIndex ->
                        projects[":app$appIndex"] = HelloWorldApp.noBuildFile("com.example.app$appIndex")
                    }
                    repeat(nbOfAndroidLibs) {androidLibIndex ->
                        projects[":lib$androidLibIndex"] = HelloWorldApp.noBuildFile("com.example.lib$androidLibIndex")
                    }
                    repeat(nbOfJavaLibs) { javaLibIndex ->
                        projects[":javaLib$javaLibIndex"] = object : GradleProject() {
                            override fun containsFullBuildScript(): Boolean {
                                return false
                            }
                        }
                    }
                }.toMap()
            )
        ).create()

    @Test
    fun testLoadingFromDeclarativeForm() {
        repeat(nbOfApplications) { appIndex ->
            val app: GradleTestProject = project.getSubproject(":app$appIndex")
            File(app.projectDir, "build.gradle.toml").writeText(
                StringBuilder().also { builder ->
                    builder.append(
                        """
                        [[plugins]]
                        id = "com.android.application"

                        [android]
                        compileSdk = 33
                        namespace = "com.example.app$appIndex"

                        [android.defaultConfig]
                        minSdk = 21

                        """.trimIndent()
                    )
                    val alreadyAllocated = mutableListOf<Int>()
                    repeat(nbOfAndroidLibsDependenciesPerApp) { _ ->
                        var nextIndex = random.nextInt(nbOfAndroidLibs)
                        while (alreadyAllocated.contains(nextIndex)) {
                            nextIndex = random.nextInt(nbOfAndroidLibs)
                        }
                        alreadyAllocated.add(nextIndex)
                        builder.append("[dependencies.implementation.lib$nextIndex]\n")
                    }
                    alreadyAllocated.clear()
                    repeat(nbOfJavaLibsDependenciesPerApp) { _ ->
                        var nextIndex = random.nextInt(nbOfJavaLibs)
                        while (alreadyAllocated.contains(nextIndex)) {
                            nextIndex = random.nextInt(nbOfJavaLibs)
                        }
                        alreadyAllocated.add(nextIndex)
                        builder.append("[dependencies.implementation.javaLib$nextIndex]\n")
                    }
                }.toString()
            )
            app.buildFile.delete()
        }
        repeat(nbOfAndroidLibs) { androidLibIndex ->
            val androidLib: GradleTestProject = project.getSubproject(":lib$androidLibIndex")
            File(androidLib.projectDir, "build.gradle.toml").writeText(
                StringBuilder().also {builder ->
                    builder.append(
                        """
                        [[plugins]]
                        id = "com.android.library"

                        [android]
                        compileSdk = 33
                        namespace = "com.example.lib$androidLibIndex"

                        [android.defaultConfig]
                        minSdk = 21

                        """.trimIndent())
                    val alreadyAllocated = mutableListOf<Int>()
                    repeat(nbOfJavaLibsDependenciesPerAndroidLib) { _ ->
                        var nextIndex = random.nextInt(nbOfJavaLibs)
                        while (alreadyAllocated.contains(nextIndex)) {
                            nextIndex = random.nextInt(nbOfJavaLibs)
                        }
                        alreadyAllocated.add(nextIndex)
                        builder.append("[dependencies.implementation.javaLib$nextIndex]\n")
                    }
                }.toString()
            )
            androidLib.buildFile.delete()
        }
        repeat(nbOfJavaLibs) { javaLibIndex ->
            initJavaLib(
                javaLibIndex = javaLibIndex,
                project = project,
                includeDeclarativePlugin = false
            )
        }
        project.settingsFile.writeText(StringBuilder().also {
            it.append(
                """
                pluginManagement {
                    repositories {
                """.trimIndent()
            )

            System.getenv("CUSTOM_REPO").split(File.pathSeparatorChar).forEach { repository ->
                it.append("        maven { url '$repository'}\n")
            }
            it.append(
                """
                }
            }
            plugins {
                id 'com.android.experiments.declarative.settings' version '${System.getenv("PLUGIN_VERSION")}'
            }
            dependencyResolutionManagement {
                RepositoriesMode.PREFER_SETTINGS
                repositories {
            """.trimIndent())
            System.getenv("CUSTOM_REPO").split(File.pathSeparatorChar).forEach { repository ->
                it.append("        maven { url '$repository'}\n")
            }
            it.append(
            """
                }
            }
            """.trimIndent())
        }.toString())

        File(project.projectDir, "settings.gradle.toml").writeText(
            StringBuilder().also { builder ->
                builder.append("""
                [[plugins]]
                id = "com.android.application"
                module = "com.android.tools.build:gradle" 
                version = "8.0.0"

                [[plugins]]
                id = "com.android.library"
                module = "com.android.tools.build:gradle" 
                version = "8.0.0"

                [includes]

                """.trimIndent())

                repeat(nbOfApplications) {
                    builder.append("app$it = \":app$it\"\n")
                }
                repeat(nbOfAndroidLibs) {
                    builder.append("lib$it = \":lib$it\"\n")
                }
                repeat(nbOfJavaLibs) {
                    builder.append("javaLib$it = \":javaLib$it\"\n")
                }
            }.toString()
        )

      File(project.projectDir, "gradle.properties").writeText(
        StringBuilder().also { builder ->
          builder.append("""org.gradle.jvmargs=-Xmx6096m""")
        }.toString()
      )

        project.executor().run("assembleDebug")
    }

    private fun initJavaLib(
        javaLibIndex: Int,
        project: GradleTestProject,
        includeDeclarativePlugin: Boolean = true) {
        val lib = project.getSubproject(":javaLib$javaLibIndex")
        File(lib.projectDir, "build.gradle.toml").writeText("""
            [[plugins]]
            id = "java"

            [java.sourceCompatibility] 
            valueOf = "VERSION_17"

            [java.targetCompatibility]
            valueOf = "VERSION_17"

            """.trimIndent())
        if (includeDeclarativePlugin) {
            lib.buildFile.writeText(
                """
                    apply plugin: 'com.android.experiments.declarative'
                    """.trimIndent()
            )
        }
    }
}