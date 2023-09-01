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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.ProductFlavor
import com.android.declarative.internal.GenericDeclarativeParser
import com.google.common.truth.Truth
import org.gradle.api.NamedDomainObjectContainer
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.tomlj.Toml

class ProductFlavorTest: AgpDslTest() {

    @Mock(strictness = Mock.Strictness.STRICT_STUBS)
    lateinit var extension: ApplicationExtension

    @Mock(strictness = Mock.Strictness.STRICT_STUBS)
    lateinit var demoFlavor: ApplicationProductFlavor

    @Mock(strictness = Mock.Strictness.STRICT_STUBS)
    lateinit var fullFlavor: ApplicationProductFlavor

    @Test
    fun testSetDimension() {
        val flavorDimensions = mutableListOf<String>()
        Mockito.`when`(extension.flavorDimensions).thenReturn(flavorDimensions)
        val toml = Toml.parse(
            """
            [android]
            flavorDimensions = [ "version" ]
        """.trimIndent()
        )
        GenericDeclarativeParser(project = project).parse(
            toml.getTable("android")!!,
            ApplicationExtension::class,
            extension
        )
        Mockito.verify(extension, times(1)).flavorDimensions
        Truth.assertThat(flavorDimensions).containsExactly("version")
    }

    @Test
    fun testProductFlavors() {
        val flavorDimensions = mutableListOf<String>()
        val flavorContainer = Mockito.mock(NamedDomainObjectContainer::class.java)
                as NamedDomainObjectContainer<out ApplicationProductFlavor>
        Mockito.`when`(extension.flavorDimensions).thenReturn(flavorDimensions)
        Mockito.`when`(extension.productFlavors).thenReturn(flavorContainer)
        Mockito.`when`(flavorContainer.maybeCreate("demo")).thenReturn(demoFlavor)
        Mockito.`when`(flavorContainer.maybeCreate("full")).thenReturn(fullFlavor)
        val toml = Toml.parse(
            """
            [android]
            flavorDimensions = [ "version" ]
            
            [android.productFlavors.demo]
            dimension="version"
            applicationIdSuffix=".demo"
            versionNameSuffix="-demo"
            
            [android.productFlavors.full]
            dimension="version"
            applicationIdSuffix=".full"
            versionNameSuffix="-full"
        """.trimIndent()
        )
        GenericDeclarativeParser(project = project).parse(
            toml.getTable("android")!!,
            ApplicationExtension::class,
            extension
        )
        Mockito.verify(extension, times(1)).flavorDimensions
        Mockito.verify(extension, times(1)).productFlavors
        Mockito.verify(flavorContainer).maybeCreate("demo")
        Mockito.verify(flavorContainer).maybeCreate("full")
        Mockito.verify(demoFlavor).dimension = "version"
        Mockito.verify(demoFlavor).applicationIdSuffix = ".demo"
        Mockito.verify(demoFlavor).versionNameSuffix = "-demo"
        Mockito.verify(fullFlavor).dimension = "version"
        Mockito.verify(fullFlavor).applicationIdSuffix = ".full"
        Mockito.verify(fullFlavor).versionNameSuffix = "-full"
        Mockito.verifyNoMoreInteractions(extension, flavorContainer, demoFlavor, fullFlavor)
        Truth.assertThat(flavorDimensions).containsExactly("version")
    }
}