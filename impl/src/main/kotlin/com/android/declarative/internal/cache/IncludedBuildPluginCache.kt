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

package com.android.declarative.internal.cache

import org.gradle.api.Project
import java.io.File
import java.util.Properties

class IncludedBuildPluginInfo(
    val classesDir: File,
    val resourcesDir: File,
)

class IncludedBuildPluginCache(
    private val project: Project,
    private val includedBuildDir: File,
) {
    private val markerFileLookup = "**/META-INF/gradle-plugins/*.properties"

    private val markerFiles: Set<File> =
        project.fileTree(includedBuildDir) {
            it.include(markerFileLookup)
        }.files


    fun getPluginClassPath(): Set<File> =
        mutableSetOf<File>().also { allFolders ->
            markerFiles.forEach { markerFile ->
                getBinaryFolders(markerFile).let {
                    allFolders.add(it.first)
                    allFolders.add(it.second)
                }
            }
        }


    fun resolvePluginById(id:String): IncludedBuildPluginInfo? {
        markerFiles.firstOrNull {
            it.name == "$id.properties"
        }?.let { markerFile ->
            loadPluginMarkerFile(markerFile)?.let { implementationClassName ->

                val classFile = project.fileTree(includedBuildDir) {
                    it.include("**/$implementationClassName.class")
                }

                val resourcesDir = markerFile.parentFile.parentFile.parentFile

                val classesDir = classFile.singleFile.absolutePath.substring(0,
                    classFile.singleFile.absolutePath.length -
                            (implementationClassName.length + ".class".length + File.separator.length)
                )

                return IncludedBuildPluginInfo(File(classesDir), resourcesDir)
            }
        }
        return null
    }

    private fun getBinaryFolders(markerFile: File): Pair<File, File> {
        loadPluginMarkerFile(markerFile).let { implementationClassName ->

            val classFile = project.fileTree(includedBuildDir) {
                it.include("**/$implementationClassName.class")
            }

            val resourcesDir = markerFile.parentFile.parentFile.parentFile

            val classesDir = classFile.singleFile.absolutePath.substring(
                0,
                classFile.singleFile.absolutePath.length -
                        (implementationClassName.length + ".class".length + File.separator.length)
            )
            return File(classesDir) to resourcesDir
        }

    }
    private fun loadPluginMarkerFile(markerFile: File): String =
        Properties().also {
            it.load(markerFile.reader())
        }.getProperty("implementation-class")
}
