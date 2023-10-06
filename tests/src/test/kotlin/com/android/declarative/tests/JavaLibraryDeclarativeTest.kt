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

class JavaLibraryDeclarativeTest {

    @Rule
    @JvmField
    val project: GradleTestProject = GradleTestProject.builder()
        .withExtraPluginClasspath("com.android.experiments.declarative:project-api:${System.getenv("PLUGIN_VERSION")}")
        .fromTestApp(
            MultiModuleTestProject(
                mapOf<String, GradleProject>(
                    ":app" to HelloWorldApp.noBuildFile("com.example.app"),
                    ":javaLib" to javaLib
                )
            )
        ).create()

    companion object {
        val javaLib = object : GradleProject() {
            override fun containsFullBuildScript(): Boolean {
                return false
            }
        }

        fun initJavaLib(project: GradleTestProject, includeDeclarativePlugin: Boolean = true) {
            val lib = project.getSubproject(":javaLib")
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
                    apply plugin: 'com.android.experiments.declarative.project'
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    fun testLoadingFromDeclarativeForm() {
        val app: GradleTestProject = project.getSubproject(":app")
        File(app.projectDir, "build.gradle.toml").writeText("""
            [[plugins]]
            id = "com.android.application"

            [android]
            compileSdk = 33
            namespace = "com.example.app"

            [android.defaultConfig]
            minSdk = 21
            [android.aaptOptions]
            noCompress = ["2"]

            [dependencies]
            implementation = [
                { project = ":javaLib" }, 
            ]
        """.trimIndent())
        app.buildFile.writeText(
            """
                apply plugin: 'com.android.experiments.declarative.project'
            """.trimIndent()
        )

        initJavaLib(project)

        project.executor().run("assembleDebug")
    }
}
