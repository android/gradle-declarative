package com.android.declarative.internal.configurators

import com.android.declarative.internal.IssueLogger
import com.android.declarative.internal.model.PreDefinedRepositoryInfo
import com.android.declarative.internal.model.RepositoryInfo
import com.android.declarative.internal.model.RepositoryType
import com.android.utils.ILogger
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

class RepositoriesConfiguratorTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock
    lateinit var repositoryHandler: RepositoryHandler

    @Mock
    lateinit var logger: ILogger

    private val issueLogger by lazy { IssueLogger(false, logger) }

    @Test
    fun testGoogle() {
        RepositoriesConfigurator(issueLogger).apply(
            "test",
            repositoryHandler,
            listOf(PreDefinedRepositoryInfo("google"))
        )

        Mockito.verify(repositoryHandler).google()
        Mockito.verifyNoMoreInteractions(repositoryHandler)
    }

    @Test
    fun testMavenCentral() {
        RepositoriesConfigurator(issueLogger).apply(
            "test",
            repositoryHandler,
            listOf(PreDefinedRepositoryInfo("mavenCentral"))
        )

        Mockito.verify(repositoryHandler).mavenCentral()
        Mockito.verifyNoMoreInteractions(repositoryHandler)
    }

    @Test
    fun testMavenLocal() {
        RepositoriesConfigurator(issueLogger).apply(
            "test",
            repositoryHandler,
            listOf(PreDefinedRepositoryInfo("mavenLocal"))
        )

        Mockito.verify(repositoryHandler).mavenLocal()
        Mockito.verifyNoMoreInteractions(repositoryHandler)
    }

    @Test
    fun testGradlePluginPortal() {
        RepositoriesConfigurator(issueLogger).apply(
            "test",
            repositoryHandler,
            listOf(PreDefinedRepositoryInfo("gradlePluginPortal"))
        )

        Mockito.verify(repositoryHandler).gradlePluginPortal()
        Mockito.verifyNoMoreInteractions(repositoryHandler)
    }

    @Test(expected = RuntimeException::class)
    fun testUnknownNamed() {
        RepositoriesConfigurator(issueLogger).apply(
            "test",
            repositoryHandler,
            listOf(PreDefinedRepositoryInfo("unknown"))
        )

        Mockito.verifyNoInteractions(repositoryHandler)
    }

    @Test(expected = RuntimeException::class)
    fun testUnknownRepositoryInfo() {
        val repositoryInfo = object: RepositoryInfo {
            override val type: RepositoryType = RepositoryType.PRE_DEFINED
        }
        RepositoriesConfigurator(issueLogger).apply(
            "test",
            repositoryHandler,
            listOf(repositoryInfo)
        )

        Mockito.verifyNoInteractions(repositoryHandler)
    }
}