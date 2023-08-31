package com.android.declarative.internal.agp

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import java.io.File

abstract class AgpDslTest {
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

}