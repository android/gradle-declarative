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

package com.android.declarative.project.agp

import com.android.declarative.common.AbstractDeclarativePlugin
import com.android.declarative.internal.IssueLogger
import com.android.declarative.internal.model.DependencyInfo.*
import com.android.declarative.internal.model.DependencyType
import com.android.declarative.project.DependencyProcessor
import com.android.utils.ILogger
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Provider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import java.util.*

@Suppress("UnstableApiUsage")
class DependencyProcessorTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.LENIENT)

    @Mock
    lateinit var dependencyHandler: DependencyHandler

    @Mock
    lateinit var dependencyFactory: DependencyFactory

    @Mock
    lateinit var fileCollection: ConfigurableFileCollection

    @Mock
    lateinit var project: Project

    @Before
    fun setup() {
        Mockito.`when`(project.rootProject).thenReturn(project)
        Mockito.`when`(project.files()).thenReturn(fileCollection)
        Mockito.`when`(project.dependencies).thenReturn(dependencyHandler)
        Mockito.`when`(project.dependencyFactory).thenReturn(dependencyFactory)
    }

    @Test
    fun testProjectDependency() {
        val parser = createDependenciesParser()
        val dependency = createSubProjectAndWireDependency(":lib1")
        val dependencies = listOf(
            Notation(
                DependencyType.PROJECT,
                "implementation",
                ":lib1"
            )
        )
        parser.process(dependencies)

        Mockito.verify(dependencyHandler).add("implementation", dependency)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testMultipleShortFormProjectDependency() {
        val parser = createDependenciesParser()
        val dependency1 = createSubProjectAndWireDependency(":lib1")
        val dependency2 = createSubProjectAndWireDependency(":lib2")
        val dependencies = listOf(
            Notation(
                DependencyType.PROJECT,
                "implementation",
                ":lib1"
            ),
            Notation(
                DependencyType.PROJECT,
                "testImplementation",
                ":lib2"
            )
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler).add("implementation", dependency1)
        Mockito.verify(dependencyHandler).add("testImplementation", dependency2)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testSimpleExternalDependency() {
        val parser = createDependenciesParser()
        val dependency = createExternalDependency("org.mockito:mockito-core:4.8.0")
        val dependencies = listOf(
            Notation(
                DependencyType.NOTATION,
                configuration = "implementation",
                notation = "org.mockito:mockito-core:4.8.0",
            )
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler).add("implementation", dependency)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testPartialExternalDependency() {
        val parser = createDependenciesParser()
        val dependency = createExternalDependency("org.mockito", "mockito-core", "4.8.0")
        val dependencies = listOf(
            Maven(
                configuration = "implementation",
                group = "org.mockito",
                name = "mockito-core",
                version = "4.8.0",
            )
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler).add("implementation", dependency)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testPartialExternalDependencyInDottedNames() {
        val parser = createDependenciesParser()
        val dependency1 = createExternalDependency("org.mockito", "mockito-core", "4.8.0")
        val dependency2 = createExternalDependency("org.junit", "junit", "5.7.0")
        val dependencies = listOf(
            Maven(
                configuration = "testImplementation",
                group = "org.mockito",
                name = "mockito-core",
                version = "4.8.0",
            ),
            Maven(
                configuration = "testImplementation",
                group = "org.junit",
                name = "junit",
                version = "5.7.0",
            )
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler).add("testImplementation", dependency1)
        Mockito.verify(dependencyHandler).add("testImplementation", dependency2)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testVersionCatalogDependency() {
        val parser = createDependenciesParser()
        val extensions = Mockito.mock(ExtensionContainer::class.java)
        Mockito.`when`(project.extensions).thenReturn(extensions)
        val versionCatalogs = Mockito.mock(VersionCatalogsExtension::class.java)
        Mockito.`when`(extensions.findByType(VersionCatalogsExtension::class.java)).thenReturn(versionCatalogs)
        val libs = Mockito.mock(VersionCatalog::class.java)
        Mockito.`when`(versionCatalogs.find("libs")).thenReturn(Optional.of(libs))
        val provider = Mockito.mock(Provider::class.java) as Provider<MinimalExternalModuleDependency>
        val providerOptional = Optional.of(provider)
        Mockito.`when`(libs.findLibrary("junit")).thenReturn(providerOptional)

        val dependencies = listOf(
            Alias(
                "implementation",
                "libs.junit"
            )
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler).add("implementation", provider)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testFilesNotationDependency() {
        val parser = DependencyProcessor(
            Mockito.mock(AbstractDeclarativePlugin::class.java),
            { id -> project.rootProject.project(id)},
            project,
            IssueLogger(lenient = true, logger = Mockito.mock(ILogger::class.java))
        )
        val dependency = Mockito.mock(FileCollectionDependency::class.java)
        Mockito.doReturn(dependency).`when`(dependencyFactory).create(fileCollection)

        val dependencies = listOf(
            Files(
                "implementation",
                listOf("local.jar")
            ),
            Files(
                "implementation",
                listOf("some.jar", "something.else", "final.one")
            )
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler, times(2)).add("implementation", dependency)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    @Test
    fun testMultipleProjectDependency() {
        val dependency1 = createSubProjectAndWireDependency(":lib1")
        val dependency2 = createSubProjectAndWireDependency(":lib2")
        val dependency3 = createSubProjectAndWireDependency(":lib3")

        val parser = createDependenciesParser()

        val dependencies = listOf(
            Notation(
                type = DependencyType.PROJECT,
                configuration = "implementation",
                notation = ":lib1"
            ),
            Notation(
                type = DependencyType.PROJECT,
                configuration = "implementation",
                notation = ":lib2"
            ),
            Notation(
                type = DependencyType.PROJECT,
                configuration = "implementation",
                notation = ":lib3"
            ),
        )
        parser.process(dependencies)
        Mockito.verify(dependencyHandler).add("implementation", dependency1)
        Mockito.verify(dependencyHandler).add("implementation", dependency2)
        Mockito.verify(dependencyHandler).add("implementation", dependency3)
        Mockito.verifyNoMoreInteractions(dependencyHandler)
    }

    private fun createDependenciesParser() =
        DependencyProcessor(
            Mockito.mock(AbstractDeclarativePlugin::class.java),
            { id -> project.rootProject.project(id)},
            project,
            IssueLogger(lenient = true, logger = Mockito.mock(ILogger::class.java))
    )
    private fun createSubProject(projectPath: String): Project =
        Mockito.mock(Project::class.java).also {
            Mockito.`when`(project.project(projectPath)).thenReturn(it)
        }

    private fun createSubProjectAndWireDependency(projectPath: String): ProjectDependency =
        Mockito.mock(ProjectDependency::class.java).also {
            Mockito.`when`(dependencyFactory.create(createSubProject(projectPath))).thenReturn(it)
        }

    private fun createExternalDependency(group: String?, name: String, version: String?) =
        Mockito.mock(ExternalModuleDependency::class.java).also {
            Mockito.`when`(dependencyFactory.create(group, name, version)).thenReturn(
                it
            )
        }

    private fun createExternalDependency(notation: String) =
        Mockito.mock(ExternalModuleDependency::class.java).also {
            Mockito.`when`(dependencyFactory.create(notation)).thenReturn(
                it
            )
        }
}
