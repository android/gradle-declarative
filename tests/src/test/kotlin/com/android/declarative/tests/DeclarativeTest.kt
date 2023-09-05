package com.android.declarative.tests

import com.google.common.truth.Truth
import java.io.File
import java.nio.file.Files

/**
 * Adds a gradle wrapper declaration, using the one located under
 * TEST_ROOTDIR environment variable.
 */
fun addGradleWrapper(testRootFolder: File) {
    File(testRootFolder, "gradle").also { gradle ->
        gradle.maybeCreateFolder()
        File(gradle, "wrapper").also { wrapper ->
            wrapper.maybeCreateFolder()
            val origin = File(System.getenv("TEST_ROOTDIR"), "gradle/wrapper")
            File(origin, "gradle-wrapper.jar").maybeCopy(wrapper)
            File(origin, "gradle-wrapper.properties").maybeCopy(wrapper)
        }
    }

}

private fun File.maybeCreateFolder() {
    if (exists()) return
    Truth.assertThat(mkdirs()).isTrue()
}

private fun File.maybeCopy(destination: File) {
    if (exists()) return
    println("Copying $name to ${destination.absolutePath}")
    Files.copy(toPath(), destination.toPath())
}