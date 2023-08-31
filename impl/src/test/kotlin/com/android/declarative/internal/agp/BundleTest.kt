package com.android.declarative.internal.agp

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.Bundle
import com.android.build.api.dsl.BundleAbi
import com.android.build.api.dsl.BundleCodeTransparency
import com.android.build.api.dsl.BundleDensity
import com.android.build.api.dsl.BundleDeviceTier
import com.android.build.api.dsl.BundleLanguage
import com.android.build.api.dsl.BundleTexture
import com.android.build.api.dsl.SigningConfig
import com.android.declarative.internal.GenericDeclarativeParser
import org.junit.Test

import org.mockito.Mock
import org.mockito.Mockito
import org.tomlj.Toml
import java.io.File


@Suppress("UnstableApiUsage")
class BundleTest: AgpDslTest() {

    @Mock
    lateinit var extension: ApplicationExtension

    @Mock
    lateinit var bundle: Bundle

    @Test
    fun testBundleAbi() {
        Mockito.`when`(extension.bundle).thenReturn(bundle)
        val abi = Mockito.mock(BundleAbi::class.java)
        Mockito.`when`(bundle.abi).thenReturn(abi)

        val toml = Toml.parse(
            """
            [android.bundle.abi]
            enableSplit = true
        """.trimIndent()
        )
        GenericDeclarativeParser(project).parse(
            toml.getTable("android")!!,
            ApplicationExtension::class,
            extension
        )
        Mockito.verify(extension).bundle
        Mockito.verify(bundle).abi
        Mockito.verify(abi).enableSplit = true
    }

    @Test
    fun testBundleCodeTransparency() {
        Mockito.`when`(extension.bundle).thenReturn(bundle)
        val bundleCodeTransparency = Mockito.mock(BundleCodeTransparency::class.java)
        Mockito.`when`(bundle.codeTransparency).thenReturn(bundleCodeTransparency)
        val signingConfig = Mockito.mock(SigningConfig::class.java)
        Mockito.`when`(bundleCodeTransparency.signing).thenReturn(signingConfig)

        val toml = Toml.parse(
            """
            [android.bundle.codeTransparency.signing]
            keyAlias = "alias"
            keyPassword = "pwd"
            storeFile = "path/to/file"
            storePassword = "pwd2"
            storeType = "type"
        """.trimIndent()
        )
        GenericDeclarativeParser(project).parse(
            toml.getTable("android")!!,
            ApplicationExtension::class,
            extension
        )
        Mockito.verify(extension).bundle
        Mockito.verify(bundle).codeTransparency
        Mockito.verify(bundleCodeTransparency).signing
        Mockito.verify(signingConfig).keyAlias = "alias"
        Mockito.verify(signingConfig).keyPassword = "pwd"
        Mockito.verify(signingConfig).storeFile = File("path/to/file")
        Mockito.verify(signingConfig).storePassword = "pwd2"
        Mockito.verify(signingConfig).storeType = "type"
    }

    @Test
    fun testBundleDensity() {
        Mockito.`when`(extension.bundle).thenReturn(bundle)
        val density = Mockito.mock(BundleDensity::class.java)
        Mockito.`when`(bundle.density).thenReturn(density)

        val toml = Toml.parse(
            """
            [android.bundle.density]
            enableSplit = true
        """.trimIndent()
        )
        GenericDeclarativeParser(project).parse(
            toml.getTable("android")!!,
            ApplicationExtension::class,
            extension
        )
        Mockito.verify(extension).bundle
        Mockito.verify(bundle).density
        Mockito.verify(density).enableSplit = true
    }

    @Test
    fun testBundleDeviceTier() {
        Mockito.`when`(extension.bundle).thenReturn(bundle)
        val deviceTier = Mockito.mock(BundleDeviceTier::class.java)
        Mockito.`when`(bundle.deviceTier).thenReturn(deviceTier)

        val toml = Toml.parse(
            """
            [android.bundle.deviceTier]
            defaultTier = "default"
            enableSplit = true
        """.trimIndent()
        )
        GenericDeclarativeParser(project).parse(
            toml.getTable("android")!!,
            ApplicationExtension::class,
            extension
        )
        Mockito.verify(extension).bundle
        Mockito.verify(bundle).deviceTier
        Mockito.verify(deviceTier).defaultTier = "default"
        Mockito.verify(deviceTier).enableSplit = true
    }

    @Test
    fun testBundleLanguage() {
        Mockito.`when`(extension.bundle).thenReturn(bundle)
        val bundleLanguage = Mockito.mock(BundleLanguage::class.java)
        Mockito.`when`(bundle.language).thenReturn(bundleLanguage)

        val toml = Toml.parse(
            """
            [android.bundle.language]
            enableSplit = true
        """.trimIndent()
        )
        GenericDeclarativeParser(project).parse(
            toml.getTable("android")!!,
            ApplicationExtension::class,
            extension
        )
        Mockito.verify(extension).bundle
        Mockito.verify(bundle).language
        Mockito.verify(bundleLanguage).enableSplit = true
    }

    @Test
    fun testBundleTexture() {
        Mockito.`when`(extension.bundle).thenReturn(bundle)
        val bundleTexture = Mockito.mock(BundleTexture::class.java)
        Mockito.`when`(bundle.texture).thenReturn(bundleTexture)

        val toml = Toml.parse(
            """
            [android.bundle.texture]
            defaultFormat = "format"
            enableSplit = true
        """.trimIndent()
        )
        GenericDeclarativeParser(project).parse(
            toml.getTable("android")!!,
            ApplicationExtension::class,
            extension
        )
        Mockito.verify(extension).bundle
        Mockito.verify(bundle).texture
        Mockito.verify(bundleTexture).defaultFormat = "format"
        Mockito.verify(bundleTexture).enableSplit = true
    }
}