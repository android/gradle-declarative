package com.android.declarative.internal.agp

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.build.api.variant.VariantSelector
import com.android.declarative.internal.fixtures.FakeAndroidComponentsExtension
import com.android.declarative.internal.variantApi.AndroidComponentsParser
import org.gradle.api.provider.Property
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.tomlj.Toml

class ApplicationVariantApiTest: AgpDslTest() {

    @Mock
    lateinit var variantBuilder: ApplicationVariantBuilder

    @Mock
    lateinit var variant: ApplicationVariant

    @Mock
    lateinit var selector: VariantSelector

    private val extension: AndroidComponentsExtension<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant> by lazy {
        FakeAndroidComponentsExtension(
            selector,
            variantBuilder,
            variant
        )
    }

    @Test
    fun testVariantBuilderEnable() {

        Mockito.`when`(selector.withName("debug")).thenReturn(selector)

        val toml = Toml.parse(
            """
            [androidComponents."beforeVariants.debug"]
            enable = false
        """.trimIndent()
        )
        AndroidComponentsParser(project).parse(
            toml.getTable("androidComponents")!!,
            ApplicationAndroidComponentsExtension::class,
            extension
        )
        Mockito.verify(selector).withName("debug")
        Mockito.verify(variantBuilder).enable = false
    }

    @Test
    fun testVariantApplicationId() {

        Mockito.`when`(selector.withName("debug")).thenReturn(selector)
        @Suppress("UNCHECKED_CAST")
        val applicationIdProperty = Mockito.mock(Property::class.java) as Property<String>
        Mockito.`when`(variant.applicationId).thenReturn(applicationIdProperty)

        val toml = Toml.parse(
            """
            [androidComponents."onVariants.debug"]
            applicationId = "some.app"
        """.trimIndent()
        )
        AndroidComponentsParser(project).parse(
            toml.getTable("androidComponents")!!,
            ApplicationAndroidComponentsExtension::class,
            extension
        )
        Mockito.verify(selector).withName("debug")
        Mockito.verify(variant).applicationId
        Mockito.verify(applicationIdProperty).set("some.app")
    }
}