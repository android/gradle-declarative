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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.TestSourceFile
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.google.common.io.Files
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.toPath

class DeclarativeTestProjectBuilder {

    fun getTestProjectsDir(): Path {
        val thisClass = this.javaClass.classLoader.getResource(
            "${this.javaClass.name.replace(".", "/")}.class")
            ?: throw RuntimeException("Cannot locate test directories")
        // now go up until I find the project root folder
        var path : Path? = thisClass.toURI().toPath()
        while(path != null && path.name != "declarative-gradle") {
            path = path.parent
        }
        return path?.resolve("tests/src/test-projects")
            ?: throw RuntimeException("project not located in declarative-gradle")
    }

    private var testProject: DeclarativeTest? = null

    private fun fromTestApp(testProject: DeclarativeTest): DeclarativeTestProjectBuilder {
        this.testProject = testProject
        return this
    }

    fun create(): DeclarativeTestProject {
        return DeclarativeTestProject(
            name = GradleTestProject.DEFAULT_TEST_PROJECT_NAME,
            this.testProject!!,
        )
    }

    companion object {
        fun createTestProject(location: File): DeclarativeTestProjectBuilder {
            val testProject = DeclarativeTest(location.absolutePath)
            try {
                for (filePath in TestFileUtils.listFiles(location.toPath())) {
                    testProject.addFile(
                        TestSourceFile(
                            filePath!!, Files.toByteArray(File(location, filePath))
                        )
                    )
                }
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
            return DeclarativeTestProjectBuilder().fromTestApp(testProject)
        }
    }

}