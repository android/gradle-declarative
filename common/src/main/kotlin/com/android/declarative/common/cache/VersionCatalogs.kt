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

package com.android.declarative.common.cache

import com.android.declarative.internal.IssueLogger
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Cache for version catalogs instances.
 */
class VersionCatalogs(
    val issueLogger: IssueLogger,
) {
    val cache = mutableMapOf<String, VersionCatalog>()

    fun getVersionCatalog(project: Project, versionCatalogName: String) =
        synchronized(cache) {
            cache.getOrPut(versionCatalogName) {
                val versionCatalogs = project.extensions.findByType(VersionCatalogsExtension::class.java)
                if (versionCatalogs == null) {
                    throw RuntimeException("Version catalog support not enabled")
                }
                val versionCatalog = versionCatalogs.find(versionCatalogName)
                if (!versionCatalog.isPresent) {
                    throw RuntimeException("Version catalog $versionCatalogName not found !")
                }
                versionCatalog.get()
            }
        }
}