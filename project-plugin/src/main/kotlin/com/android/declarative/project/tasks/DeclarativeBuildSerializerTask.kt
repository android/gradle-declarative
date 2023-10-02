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

package com.android.declarative.project.tasks

import com.android.declarative.common.DslTypesCache
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

/**
 * Unfinished task to experiment with serializing an extension to a build.gradle.toml file.
 */
abstract class DeclarativeBuildSerializerTask: DefaultTask() {

    @get:Input
    abstract val extension: Property<Any>

    @TaskAction
    fun taskAction() {
        val typesCache = DslTypesCache()
        println("I am running ${extension.get()}!")
        val dslTypeCache = typesCache.getCache(extension.get()::class)
        println(dslTypeCache)

        dslTypeCache.properties.forEach { key, property ->
            println("K: ${key}")
            println("T: -------")
            if (property.returnType.jvmErasure.isSubclassOf(Collection::class)) {
                println("Collection of ${property.returnType.arguments[0]}")
                println("Parsing ${property.returnType.arguments[0].type}")
                println("Parsing ${property.returnType.arguments[0].type?.javaType?.typeName}")
                val className = property.returnType.arguments[0].type.toString()
                if (!className.startsWith("kotlin")) {
                    val argumentClass = this.javaClass.classLoader.loadClass(className).kotlin
                    println("${typesCache.getCache(argumentClass)}")
                }
            } else {
                val subTypeCache = DslTypesCache().getCache(property.returnType.jvmErasure)

                println(subTypeCache)
            }
        }

    }

}
