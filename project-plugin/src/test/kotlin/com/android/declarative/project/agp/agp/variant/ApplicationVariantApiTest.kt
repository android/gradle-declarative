package com.android.declarative.project.agp.agp.variant

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.ApplicationVariantBuilder
import com.android.declarative.project.agp.agp.AgpDslTest
import com.android.declarative.project.agp.fixtures.FakeAndroidComponentsExtension
import com.android.declarative.project.agp.fixtures.FakeSelector
import com.android.declarative.project.variantApi.AndroidComponentsParser
import com.google.common.truth.Truth
import org.gradle.api.provider.Property
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.tomlj.Toml
import java.lang.RuntimeException

class ApplicationVariantApiTest: AgpDslTest() {

    @Mock
    lateinit var variantBuilder: ApplicationVariantBuilder

    @Mock
    lateinit var variant: ApplicationVariant

    private val selector = FakeSelector()

    private val extension: AndroidComponentsExtension<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant> by lazy {
        FakeAndroidComponentsExtension(
            selector,
            mapOf("debug" to variantBuilder),
            mapOf("debug" to variant),
        )
    }

    @Test(expected = RuntimeException::class)
    fun testIncorrectVariantName() {

        val selector = FakeSelector()
        val extension: AndroidComponentsExtension<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant> by lazy {
            FakeAndroidComponentsExtension(
                selector,
                mapOf("debug" to variantBuilder),
                mapOf("debug" to variant),
            )
        }

        val toml = Toml.parse(
            """
            [androidComponents.beforeVariants.fake]
            enable = false
        """.trimIndent()
        )
        AndroidComponentsParser(project).parse(
            toml.getTable("androidComponents")!!,
            ApplicationAndroidComponentsExtension::class,
            extension
        )
    }

    @Test(expected = RuntimeException::class)
    fun testNoVariantName() {

        val extension: AndroidComponentsExtension<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant> by lazy {
            FakeAndroidComponentsExtension(
                selector,
                mapOf("debug" to variantBuilder),
                mapOf("debug" to variant),
            )
        }

        val toml = Toml.parse(
            """
            [androidComponents.beforeVariants]
            enable = false
        """.trimIndent()
        )
        AndroidComponentsParser(project).parse(
            toml.getTable("androidComponents")!!,
            ApplicationAndroidComponentsExtension::class,
            extension
        )
    }

    @Test(expected = RuntimeException::class)
    fun testNoVariantNameWithMultipleValues() {

        val extension: AndroidComponentsExtension<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant> by lazy {
            FakeAndroidComponentsExtension(
                selector,
                mapOf("debug" to variantBuilder),
                mapOf("debug" to variant),
            )
        }

        val toml = Toml.parse(
            """
            [androidComponents.beforeVariants]
            enable = false
            minSdk = 22
        """.trimIndent()
        )
        AndroidComponentsParser(project).parse(
            toml.getTable("androidComponents")!!,
            ApplicationAndroidComponentsExtension::class,
            extension
        )
    }

    @Test
    fun testBeforeVariantsWithAllVariants() {

        val debugVariantBuilder = Mockito.mock(ApplicationVariantBuilder::class.java)
        val debugVariant = Mockito.mock(ApplicationVariant::class.java)
        val releaseVariantBuilder = Mockito.mock(ApplicationVariantBuilder::class.java)
        val releaseVariant = Mockito.mock(ApplicationVariant::class.java)
        val extension: AndroidComponentsExtension<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant> by lazy {
            FakeAndroidComponentsExtension(
                selector,
                mapOf("debug" to debugVariantBuilder, "release" to releaseVariantBuilder),
                mapOf("debug" to debugVariant, "release" to releaseVariant),
            )
        }

        val toml = Toml.parse(
            """
            [androidComponents.beforeVariants.all]
            enable = false
        """.trimIndent()
        )
        AndroidComponentsParser(project).parse(
            toml.getTable("androidComponents")!!,
            ApplicationAndroidComponentsExtension::class,
            extension
        )

        Mockito.verify(debugVariantBuilder).enable = false
        Mockito.verify(releaseVariantBuilder).enable = false
    }

    @Test
    fun testOnVariantsWithAllVariants() {

        val debugVariantBuilder = Mockito.mock(ApplicationVariantBuilder::class.java)
        val debugVariant = Mockito.mock(ApplicationVariant::class.java)
        @Suppress("UNCHECKED_CAST")
        val debugApplicationIdProperty = Mockito.mock(Property::class.java) as Property<String>
        Mockito.`when`(debugVariant.applicationId).thenReturn(debugApplicationIdProperty)

        val releaseVariantBuilder = Mockito.mock(ApplicationVariantBuilder::class.java)
        val releaseVariant = Mockito.mock(ApplicationVariant::class.java)
        @Suppress("UNCHECKED_CAST")
        val releaseApplicationIdProperty = Mockito.mock(Property::class.java) as Property<String>
        Mockito.`when`(releaseVariant.applicationId).thenReturn(releaseApplicationIdProperty)

        val extension: AndroidComponentsExtension<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant> by lazy {
            FakeAndroidComponentsExtension(
                selector,
                mapOf("debug" to debugVariantBuilder, "release" to releaseVariantBuilder),
                mapOf("debug" to debugVariant, "release" to releaseVariant),
            )
        }

        val toml = Toml.parse(
            """
            [androidComponents.onVariants.all]
            applicationId = "some.app"
        """.trimIndent()
        )
        AndroidComponentsParser(project).parse(
            toml.getTable("androidComponents")!!,
            ApplicationAndroidComponentsExtension::class,
            extension
        )

        Mockito.verify(debugVariant).applicationId
        Mockito.verify(debugApplicationIdProperty).set("some.app")
        Mockito.verify(releaseVariant).applicationId
        Mockito.verify(releaseApplicationIdProperty).set("some.app")
    }

    @Test
    fun testWithMultipleVariants() {

        val debugOneVariantBuilder = Mockito.mock(ApplicationVariantBuilder::class.java)
        val debugOneVariant = Mockito.mock(ApplicationVariant::class.java)
        @Suppress("UNCHECKED_CAST")
        val debugOneApplicationIdProperty = Mockito.mock(Property::class.java) as Property<String>
        Mockito.`when`(debugOneVariant.applicationId).thenReturn(debugOneApplicationIdProperty)

        val debugTwoVariantBuilder = Mockito.mock(ApplicationVariantBuilder::class.java)
        val debugTwoVariant = Mockito.mock(ApplicationVariant::class.java)
        @Suppress("UNCHECKED_CAST")
        val debugTwoApplicationIdProperty = Mockito.mock(Property::class.java) as Property<String>
        Mockito.`when`(debugTwoVariant.applicationId).thenReturn(debugTwoApplicationIdProperty)

        val releaseVariantBuilder = Mockito.mock(ApplicationVariantBuilder::class.java)
        val releaseVariant = Mockito.mock(ApplicationVariant::class.java)
        @Suppress("UNCHECKED_CAST")
        val releaseApplicationIdProperty = Mockito.mock(Property::class.java) as Property<String>

        val extension: AndroidComponentsExtension<ApplicationExtension, ApplicationVariantBuilder, ApplicationVariant> by lazy {
            FakeAndroidComponentsExtension(
                selector,
                mapOf(
                    "debugOne" to debugOneVariantBuilder,
                    "debugTwo" to debugTwoVariantBuilder,
                    "release" to releaseVariantBuilder
                ),
                mapOf(
                    "debugOne" to debugOneVariant,
                    "debugTwo" to debugTwoVariant,
                    "release" to releaseVariant),
            )
        }

        val toml = Toml.parse(
            """
            [androidComponents.beforeVariants.debugOne]
            enable = true
            [androidComponents.beforeVariants.debugTwo]
            enable = true
            [androidComponents.onVariants.debugOne]
            applicationId = "some.app"
            [androidComponents.onVariants.debugTwo]
            applicationId = "some.app"
        """.trimIndent()
        )
        AndroidComponentsParser(project).parse(
            toml.getTable("androidComponents")!!,
            ApplicationAndroidComponentsExtension::class,
            extension
        )

        Mockito.verify(debugOneVariantBuilder).enable = true
        Mockito.verify(debugTwoVariantBuilder).enable = true
        Mockito.verifyNoInteractions(releaseVariantBuilder)

        Mockito.verify(debugOneVariant).applicationId
        Mockito.verify(debugOneApplicationIdProperty).set("some.app")
        Mockito.verify(debugTwoVariant).applicationId
        Mockito.verify(debugTwoApplicationIdProperty).set("some.app")
        Mockito.verifyNoInteractions(releaseVariant)
        Mockito.verifyNoInteractions(releaseApplicationIdProperty)
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
            ApplicationAndroidComponentsExtension::class,
            extension
        )
        Truth.assertThat(selector.matches("debug")).isTrue()
        Mockito.verify(variantBuilder).enable = false
    }

    @Test
    fun testVariantApplicationId() {

        @Suppress("UNCHECKED_CAST")
        val applicationIdProperty = Mockito.mock(Property::class.java) as Property<String>
        Mockito.`when`(variant.applicationId).thenReturn(applicationIdProperty)

        val toml = Toml.parse(
            """
            [androidComponents.onVariants.debug]
            applicationId = "some.app"
        """.trimIndent()
        )
        AndroidComponentsParser(project).parse(
            toml.getTable("androidComponents")!!,
            ApplicationAndroidComponentsExtension::class,
            extension
        )
        Truth.assertThat(selector.matches("debug")).isTrue()
        Mockito.verify(variant).applicationId
        Mockito.verify(applicationIdProperty).set("some.app")
    }
}