package com.android.declarative.tests.samples

import com.android.declarative.infra.DeclarativeTestProject
import com.android.declarative.infra.DeclarativeTestProjectBuilder
import com.android.testutils.truth.PathSubject.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

class NestedBuildScriptSingleLibraryApplication {
    @Rule
    @JvmField
    val project: DeclarativeTestProject = DeclarativeTestProjectBuilder.createTestProject(
        File(DeclarativeTestProjectBuilder().getTestProjectsDir().toFile(), "nestedBuildScriptSingleLibApplication")
    ).create()

    @Test
    fun buildAll() {
        project.executor().run("assembleDebug")
        val appProject = project.getSubproject("app")
        // test that demo.toml build file is included and applied
        val demoManifest = appProject.getMergedManifestFile("demo", "debug")
        assertThat(appProject.getOutputMetadataJson("demo", "debug"))
            .contains(""""applicationId": "com.example.app.demo"""")

        // test that minSdkVersion defined in demo.toml build file is overridden
        assertThat(demoManifest).contains("""android:minSdkVersion="21"""")

        // test that paid.toml build file is included and applied
        val paidManifest = appProject.getMergedManifestFile("paid", "debug")
        assertThat(appProject.getOutputMetadataJson("paid", "debug"))
            .contains(""""applicationId": "com.example.app.paid"""")
        // test that minSdkVersion defined in paid.toml build file is overridden
        assertThat(paidManifest).contains("""android:minSdkVersion="21"""")


        // test that libProject is built successfully
        val libProject = project.getSubproject("lib")
        assertThat(libProject.getMergedManifestFile("debug"))
            .contains("""android:minSdkVersion="21"""")

    }
}