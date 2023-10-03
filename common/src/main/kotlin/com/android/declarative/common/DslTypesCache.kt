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


package com.android.declarative.common

import kotlin.reflect.KClass

/**
 * Maintains all the [DslTypeResult] for already visited DSL types.
 */
class DslTypesCache() {

    val cache = mutableListOf<DslTypeResult<*>>()

    fun <T: Any> getCache(
        dslType: KClass<out T>
    ): DslTypeResult<T> {
        val cached: DslTypeResult<T>? = cache.firstOrNull { it.dslType == dslType } as DslTypeResult<T>?
        return cached ?: DslTypeResult.parseType(dslType).also {
                cache.add(it)
        }
    }
}
