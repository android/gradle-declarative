package com.android.declarative.internal.agp

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.LibraryVariant
import com.android.build.api.variant.LibraryVariantBuilder
import com.android.build.api.variant.VariantSelector
import com.android.declarative.internal.fixtures.FakeAndroidComponentsExtension
import com.android.declarative.internal.fixtures.FakeSelector
import com.android.declarative.internal.variantApi.AndroidComponentsParser
import com.google.common.truth.Truth
import org.gradle.api.provider.Property
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.tomlj.Toml

class LibraryVariantApiTest : AgpDslTest() {

    @Mock
    lateinit var variantBuilder: LibraryVariantBuilder

    @Mock
    lateinit var variant: LibraryVariant

    private val selector = FakeSelector()

    private val extension: AndroidComponentsExtension<LibraryExtension, LibraryVariantBuilder, LibraryVariant> by lazy {
        FakeAndroidComponentsExtension(
            selector,
            mapOf("debug" to variantBuilder),
            mapOf("debug" to variant),
        )
    }

    @Test
    fun testVariantBuilderEnable() {

        val toml = Toml.parse(
            """
            [androidComponents.beforeVariants.debug]
            enable = false
        """.trimIndent()
        )
        AndroidComponentsParser(project).parse(
            toml.getTable("androidComponents")!!,
            LibraryAndroidComponentsExtension::class,
            extension
        )
        Truth.assertThat(selector.matches("debug")).isTrue()
        Mockito.verify(variantBuilder).enable = false
    }

    @Test
    fun testVariantEnable() {

        @Suppress("UNCHECKED_CAST")
        val pseudoLocalesEnabledProperty = Mockito.mock(Property::class.java) as Property<Boolean>
        Mockito.`when`(variant.pseudoLocalesEnabled).thenReturn(pseudoLocalesEnabledProperty)

        val toml = Toml.parse(
            """
            [androidComponents.onVariants.debug]
            pseudoLocalesEnabled = true
        """.trimIndent()
        )
        AndroidComponentsParser(project).parse(
            toml.getTable("androidComponents")!!,
            LibraryAndroidComponentsExtension::class,
            extension
        )
        Truth.assertThat(selector.matches("debug")).isTrue()
        Mockito.verify(variant).pseudoLocalesEnabled
        Mockito.verify(pseudoLocalesEnabledProperty).set(true)
    }
}