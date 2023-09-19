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

class SettingsDeclaredPluginTest {

    @Rule
    @JvmField
    val project: GradleTestProject = GradleTestProject.builder()
        .withExtraPluginClasspath("com.android.experiments.declarative:api:${System.getenv("PLUGIN_VERSION")}")
        .fromTestApp(
            MultiModuleTestProject(
                mapOf<String, GradleProject>(
                    ":app" to HelloWorldApp.noBuildFile("com.example.app"),
                    ":javaLib" to JavaLibraryDeclarativeTest.javaLib
                )
            )
        ).create()

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

            [dependencies]
            javalib = { configuration = "implementation", project = ":javaLib" }
        """.trimIndent())

        JavaLibraryDeclarativeTest.initJavaLib(project, false)

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
""".trimIndent()
            )
        }.toString())

        File(project.projectDir, "settings.gradle.toml").writeText("""
            [[plugins]]
            id = "com.android.application"
            module = "com.android.tools.build:gradle" 
            version = "7.4.0"

            [include]
            app = ":app"
            javaLib = ":javaLib"

        """.trimIndent())

        project.executor().run("tasks")
    }
}