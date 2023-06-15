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
/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.declarative.internal

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Cache object for a dsl type. This will cache all properties, mutable properties
 * and methods that can b e called on this type.
 */
class DslTypeResult<T: Any>(
    val dslType: KClass<out T>,
    val properties: Map<String, KProperty1<in T, Any>>,
    val mutableProperties: Map<String, KMutableProperty1<in T, Any>>,
    val members: Map<String, Collection<KCallable<*>>>,
) {

    companion object {

        fun <T: Any> parseType(type: KClass<out T>): DslTypeResult<T> {
            val properties = mutableMapOf<String, KProperty1<in T, Any>>()
            val mutableProperties = mutableMapOf<String, KMutableProperty1<in T, Any>>()
            parseMembers(type, properties, mutableProperties)
            val members = mutableMapOf<String, MutableList<KCallable<*>>>()
            type.members.forEach { callable ->
                val values = members.getOrPut(callable.name) { mutableListOf() }
                values.add(callable)
            }
            return DslTypeResult(
                type,
                properties.toMap(),
                mutableProperties.toMap(),
                members.toMap(),
            )

        }

        private fun <T: Any> parseMembers(
            type: KClass<out T>,
            properties: MutableMap<String, KProperty1<in T, Any>>,
            mutableProperties: MutableMap<String, KMutableProperty1<in T, Any>>
        ) {
            type.memberProperties.forEach { property ->
                if (property is KMutableProperty1) {
                    mutableProperties[property.name] = property as KMutableProperty1<T, Any>
                } else {
                    properties[property.name] = property as KProperty1<T, Any>
                }
            }
        }
    }

    override fun toString(): String {
        val stringBuilder = StringBuilder(super.toString()).append("\n")
        stringBuilder.append("type = $dslType\n")
        properties.keys.forEach { key -> stringBuilder.append("P : ").append(key).append("\n") }
        mutableProperties.forEach { key -> stringBuilder.append("M : ").append(key).append("\n")}
        return stringBuilder.toString()
    }
}
