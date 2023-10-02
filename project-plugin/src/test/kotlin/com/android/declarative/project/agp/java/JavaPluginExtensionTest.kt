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

package com.android.declarative.project.agp.java

import com.android.declarative.project.GenericDeclarativeParser
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import org.tomlj.Toml
import java.io.File

class JavaPluginExtensionTest {
    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @get:Rule
    val temporaryFolder= TemporaryFolder()

    lateinit var project: Project

    @Before
    fun setup() {
        File(temporaryFolder.root, "gradle.properties").writeText(
            """
                org.gradle.logging.level=debug
            """.trimIndent()
        )
        project = ProjectBuilder.builder()
            .withProjectDir(temporaryFolder.root)
            .build()
    }

    @Test
    fun testJavaVersionUsingProperty() {
        val extension = Mockito.mock(JavaPluginExtension::class.java)

        val toml = Toml.parse(
            """
            [java.sourceCompatibility]
            valueOf = "VERSION_17"

            [java.targetCompatibility]
            valueOf = "VERSION_18"
        """.trimIndent()
        )
        GenericDeclarativeParser(project).parse(
            toml.getTable("java")!!,
            JavaPluginExtension::class,
            extension
        )
        Mockito.verify(extension).sourceCompatibility = JavaVersion.VERSION_17
        Mockito.verify(extension).targetCompatibility = JavaVersion.VERSION_18
    }

    @Test
    fun testJavaVersionUsingMethod() {
        val extension = Mockito.mock(JavaPluginExtension::class.java)

        val toml = Toml.parse(
            """
            [java.setSourceCompatibility]
            valueOf = "VERSION_17"

            [java.setTargetCompatibility]
            valueOf = "VERSION_18"
        """.trimIndent()
        )
        GenericDeclarativeParser(project).parse(
            toml.getTable("java")!!,
            JavaPluginExtension::class,
            extension
        )
        Mockito.verify(extension).sourceCompatibility = JavaVersion.VERSION_17
        Mockito.verify(extension).targetCompatibility = JavaVersion.VERSION_18
    }

    @Test
    fun testJavaVersionUsingPropertyWithAny() {
        val extension = Mockito.mock(JavaPluginExtension::class.java)

        val toml = Toml.parse(
            """
            [java] 
            sourceCompatibility = "VERSION_17

            [java]
            targetCompatibility = "VERSION_18"
        """.trimIndent()
        )
        GenericDeclarativeParser(project).parse(
            toml.getTable("java")!!,
            JavaPluginExtension::class,
            extension
        )
        Mockito.verify(extension).setSourceCompatibility("VERSION_17")
        Mockito.verify(extension).setTargetCompatibility("VERSION_18")
    }

    @Test
    fun testJavaVersionUsingMethodWithAny() {
        val extension = Mockito.mock(JavaPluginExtension::class.java)

        val toml = Toml.parse(
            """
            [java] 
            setSourceCompatibility = "VERSION_17

            [java]
            setTargetCompatibility = "VERSION_18"
        """.trimIndent()
        )
        GenericDeclarativeParser(project).parse(
            toml.getTable("java")!!,
            JavaPluginExtension::class,
            extension
        )
        Mockito.verify(extension).setSourceCompatibility("VERSION_17")
        Mockito.verify(extension).setTargetCompatibility("VERSION_18")
    }
}