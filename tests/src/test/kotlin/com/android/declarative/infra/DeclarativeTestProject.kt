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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.fixture.GradleTestRule
import com.android.build.gradle.integration.common.fixture.gradle_project.ProjectLocation
import com.android.build.gradle.integration.common.fixture.gradle_project.initializeProjectLocation
import com.android.build.gradle.integration.common.truth.forEachLine
import com.android.testutils.TestUtils
import com.google.common.truth.Truth
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class DeclarativeTestProject constructor(
    val name : String = GradleTestProject.DEFAULT_TEST_PROJECT_NAME,
    private val testProject: DeclarativeTest,
    private val gradleDistributionDirectory: File =
        TestUtils.resolveWorkspacePath("tools/external/gradle").toFile(),
    private var mutableProjectLocation: ProjectLocation? = null,
    override val androidSdkDir: File? = null,
    override val androidNdkSxSRootSymlink: File? = null,
    override val additionalMavenRepoDir: Path? = null,
    override val heapSize: GradleTestProjectBuilder.MemoryRequirement = GradleTestProjectBuilder.MemoryRequirement.useDefault(),
    override val withConfigurationCaching: BaseGradleExecutor.ConfigurationCaching = BaseGradleExecutor.ConfigurationCaching.PROJECT_ISOLATION,
) : GradleTestRule
{
    /** Returns the latest build result.  */
    private var _buildResult: GradleBuildResult? = null
    val buildResult: GradleBuildResult
        get() = _buildResult ?: throw RuntimeException("No result available. Run Gradle first.")

    private val openConnections: MutableList<ProjectConnection> = mutableListOf()

    private val projectConnection: ProjectConnection by lazy {

        val connector = GradleConnector.newConnector()
        (connector as DefaultGradleConnector)
            .daemonMaxIdleTime(
                GradleTestProject.GRADLE_DEAMON_IDLE_TIME_IN_SECONDS,
                TimeUnit.SECONDS
            )

    connector
        .useGradleUserHomeDir(location.testLocation.gradleUserHome.toFile())
        .forProjectDirectory(location.projectDir)


        val distributionName = String.format(
            "gradle-%s-bin.zip",
            GradleTestProject.GRADLE_TEST_VERSION
        )
        val distributionZip = File(gradleDistributionDirectory, distributionName)
        Truth.assertThat(distributionZip.isFile).isTrue()

        connector.useDistribution(distributionZip.toURI())

        connector.connect().also { connection ->
            openConnections.add(connection)
        }
    }

    override val location: ProjectLocation
        get() = mutableProjectLocation ?: error("Project location has not been initialized yet")

    override fun getProfileDirectory(): Path? = null

    override fun setLastBuildResult(lastBuildResult: GradleBuildResult) {
        this._buildResult = lastBuildResult
    }

    val projectDir: File
        get() = location.projectDir

    override fun apply(base: Statement, description: Description): Statement {
        return object: Statement() {
            override fun evaluate() {
                if (mutableProjectLocation == null) {
                    mutableProjectLocation = initializeProjectLocation(
                        description.testClass,
                        description.methodName,
                        name
                    )
                }
                populateTestDirectory()
                var testFailed = false
                try {
                    base.evaluate()
                } catch (t: Throwable) {
                    testFailed = true
                    throw t
                } finally {
                    openConnections.forEach(ProjectConnection::close)

                    if (testFailed) {
                        _buildResult?.let {
                            System.err
                                .println("==============================================")
                            System.err
                                .println("= Test $description failed. Last build:")
                            System.err
                                .println("==============================================")
                            System.err
                                .println("=================== Stderr ===================")
                            // All output produced during build execution is written to the standard
                            // output file handle since Gradle 4.7. This should be empty.
                            it.stderr.forEachLine { System.err.println(it) }
                            System.err
                                .println("=================== Stdout ===================")
                            it.stdout.forEachLine { System.err.println(it) }
                            System.err
                                .println("==============================================")
                            System.err
                                .println("=============== End last build ===============")
                            System.err
                                .println("==============================================")
                        }
                    }
                }

            }

        }
    }

    fun executor(): GradleTaskExecutor {
        return GradleTaskExecutor(this, projectConnection)
    }

    private fun getRepoDirectories(): List<Path> {
        val builder = mutableListOf<Path>()
        builder.addAll(GradleTestProject.localRepositories)
        testProject.additionalMavenRepositories.forEach(builder::add)
        return builder.toList()
    }

    private fun generateRepoScript(): String =
        StringBuilder().also {builder ->
            builder.append("repositories {\n")
            getRepoDirectories().forEach { builder.append(GradleTestProject.mavenSnippet(it)) }
            builder.append("}\n")
        }.toString()

    fun populateTestDirectory() {
        val projectDir = projectDir
        projectDir.deleteRecursively()
        projectDir.mkdirs()
        testProject.write(
            projectDir,
            null,
            generateRepoScript()
        )
    }
}