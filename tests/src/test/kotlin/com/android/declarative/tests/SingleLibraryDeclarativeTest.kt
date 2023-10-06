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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import org.junit.Rule
import org.junit.Test
import java.io.File

class SingleLibraryDeclarativeTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .withExtraPluginClasspath("com.android.experiments.declarative:project-api:${System.getenv("PLUGIN_VERSION")}")
        .fromTestApp(HelloWorldLibraryApp.create()).create()

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

            [android.defaultConfig.ndk]
            ldLibs = ["315"]

            # nullable property
            [android.defaultConfig.signingConfig]
            storePassword = "333"
            storeType = "334"

            [android.buildTypes.debug]
            proguardFiles = ["/Users/amishar/git/gradle-declarative/tests/258/259/260"]

            [android.buildTypes.debug.shaders]
            glslcArgs = ["261"]

            # map type field
            [android.buildTypes.debug.shaders.scopedGlslcArgs]
            "262" = "263"

            # property with no backing field and accessor
            [android.dataBinding]
            addDefaultAdapters = true

            [android.buildTypes.debug.signingConfig]
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = false
            enableV4Signing = true

            [android.buildTypes.debug.externalNativeBuild.ndkBuild]
            abiFilters = ["240"]

            [android.buildTypes.debug.externalNativeBuild.experimentalProperties]
            "238" = "239"

            [dependencies]
            implementation = [
                { project = ":lib" } 
            ]
        """.trimIndent())
        app.buildFile.writeText(
            """
                apply plugin: 'com.android.experiments.declarative.project'
            """.trimIndent()
        )

        project.executor().run("assembleDebug")
    }
}
