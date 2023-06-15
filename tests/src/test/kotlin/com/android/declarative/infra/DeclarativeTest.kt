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
package com.android.declarative.infra

import com.android.build.gradle.integration.common.fixture.GradleProject
import java.io.File
import java.nio.file.Path

class DeclarativeTest(
    path: String? = null
): GradleProject(path) {

    val additionalMavenRepositories: List<Path> = listOf()

    override fun write(projectDir: File, buildScriptContent: String?, projectRepoScript: String) {
        for (sourceFile in getAllSourceFiles()) {
            sourceFile.writeToDir(projectDir)
        }
        generateSettingsFile(projectDir, projectRepoScript)
    }

    override fun containsFullBuildScript(): Boolean {
        TODO("Not yet implemented")
    }

     private fun generateSettingsFile(projectDir: File, projectRepoScript: String) {
         File(projectDir, "settings.gradle").writeText(
             """
pluginManagement {
    $projectRepoScript
}

plugins {
    id 'com.android.experiments.declarative.settings' version '0.0.1'
}

dependencyResolutionManagement {
    RepositoriesMode.PREFER_SETTINGS
    $projectRepoScript
}
             """
         )
     }
}