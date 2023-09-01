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

package com.android.declarative.internal.agp

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ProductFlavor
import com.android.declarative.internal.AbstractDeclarativePlugin
import com.android.declarative.internal.GenericDeclarativeParser
import com.android.declarative.internal.IssueLogger
import com.android.declarative.internal.JdkLoggerWrapper
import com.google.common.truth.Truth
import org.gradle.api.NamedDomainObjectContainer
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.tomlj.Toml
import java.io.File
import java.lang.RuntimeException
import java.util.logging.Logger

class BuildTypeTest: AgpDslTest() {

    @Mock
    lateinit var extension: ApplicationExtension

    @Mock
    lateinit var debug: ApplicationBuildType

    @Mock
    lateinit var release: ApplicationBuildType

    @Mock
    lateinit var staging: ApplicationBuildType

    @Test
    fun testInvalidDeclaration() {
        File(temporaryFolder.root, "build.gradle.toml").writeText(
            """
            [android.buildTypes]
            debug.minifyEnabled = true
            [android.buildTypes.debug.minifyEnabled]
        """.trimIndent()
        )
        val plugin = object : AbstractDeclarativePlugin() {
            override val buildFileName: String
                get() = "build.gradle.toml"
        }

        val exception: RuntimeException = assertThrows(RuntimeException::class.java) {
            plugin.parseDeclarativeInFolder(
                temporaryFolder.root, IssueLogger(
                    lenient = false,
                    JdkLoggerWrapper(Logger.getLogger(BuildTypeTest::class.java.name))
                )
            )
        }
        Truth.assertThat(exception.message).contains(
            "3:1 android.buildTypes.debug.minifyEnabled previously defined at line 2, column 1"
        )
    }

    @Test
    fun testBuildTypes() {
        val buildTypeContainer = Mockito.mock(NamedDomainObjectContainer::class.java)
                as NamedDomainObjectContainer<out ApplicationBuildType>

        Mockito.`when`(extension.buildTypes).thenReturn(buildTypeContainer)
        Mockito.`when`(buildTypeContainer.maybeCreate("debug")).thenReturn(debug)
        val proguardFiles = mutableListOf<File>()
        Mockito.`when`(debug.proguardFiles).thenReturn(proguardFiles)
        Mockito.`when`(buildTypeContainer.maybeCreate("release")).thenReturn(release)
        Mockito.`when`(buildTypeContainer.maybeCreate("staging")).thenReturn(staging)
        Mockito.`when`(buildTypeContainer.getByName("debug")).thenReturn(debug)
        val placeHolders = mutableMapOf<String, Any>()
        Mockito.`when`(staging.manifestPlaceholders).thenReturn(placeHolders)
        Mockito.`when`(extension.getDefaultProguardFile("proguard-android.txt")).thenReturn(
            File(temporaryFolder.newFolder(), "proguard-android.txt")
        )

        val toml = Toml.parse(
            """
            [android.buildTypes.debug]
            minifyEnabled = true
            proguardFiles = [ "proguard-rules.pro" ]
           
            [android.buildTypes.debug._dispatch_]
            target = "proguardFiles"
            method = "getDefaultProguardFile"
            params = [ "proguard-android.txt" ]

            [android.buildTypes.release]
            applicationIdSuffix = ".debug"
            debuggable = true

            [android.buildTypes.staging]
            initWith = "debug"
            manifestPlaceholders = { hostName = "internal.example.com" }
            applicationIdSuffix = ".debugStaging"
        """.trimIndent()
        )
        GenericDeclarativeParser(project).parse(
            toml.getTable("android")!!,
            ApplicationExtension::class,
            extension
        )
        Mockito.verify(extension).buildTypes
        Truth.assertThat(placeHolders).containsEntry("hostName", "internal.example.com")
        Truth.assertThat(proguardFiles.map { it.name }).containsExactly(
            "proguard-android.txt", "proguard-rules.pro"
        )

        Mockito.verify(buildTypeContainer).maybeCreate("debug")
        Mockito.verify(buildTypeContainer).maybeCreate("release")
        Mockito.verify(buildTypeContainer).maybeCreate("staging")
        Mockito.verify(debug).isMinifyEnabled = true
        Mockito.verify(release).applicationIdSuffix = ".debug"
        Mockito.verify(release).isDebuggable = true
        Mockito.verify(staging).initWith(debug)
        Mockito.verify(staging).applicationIdSuffix = ".debugStaging"
    }

    @Test
    fun testMatchingFallbacks() {
        val buildTypeContainer = Mockito.mock(NamedDomainObjectContainer::class.java)
                as NamedDomainObjectContainer<out ApplicationBuildType>

        Mockito.`when`(extension.buildTypes).thenReturn(buildTypeContainer)
        Mockito.`when`(buildTypeContainer.maybeCreate("debug")).thenReturn(debug)
        val matchingFallback = mutableListOf<String>()
        Mockito.`when`(staging.matchingFallbacks).thenReturn(matchingFallback)
        Mockito.`when`(buildTypeContainer.maybeCreate("release")).thenReturn(release)
        Mockito.`when`(buildTypeContainer.maybeCreate("staging")).thenReturn(staging)

        val toml = Toml.parse(
            """
            [android.buildTypes.debug]
            [android.buildTypes.release]
            [android.buildTypes.staging]
            matchingFallbacks = [ "debug", "qa", "release" ]
        """.trimIndent()
        )
        GenericDeclarativeParser(project).parse(
            toml.getTable("android")!!,
            ApplicationExtension::class,
            extension
        )
        Mockito.verify(extension).buildTypes
        Truth.assertThat(matchingFallback).containsExactly("debug", "qa", "release")

        Mockito.verify(buildTypeContainer).maybeCreate("debug")
        Mockito.verify(buildTypeContainer).maybeCreate("release")
        Mockito.verify(buildTypeContainer).maybeCreate("staging")
        Mockito.verify(staging).matchingFallbacks
    }
}