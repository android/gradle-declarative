package com.android.declarative.common

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

class GradleIssuesWorkarounds {
    companion object {

        /**
         * This is a hack until https://github.com/gradle/gradle/issues/22468 is fixed.
         * we basically steal the versionCatalogs extension from the root project and store them
         * in the sub project's extensions. In turn the Project's DeclarativePlugin will remove those
         * so it can be reinserted by Gradle when it is ready to do so.
         */
        fun installVersionCatalogSupport(project: Project) {

            project.rootProject.extensions.findByName("libs")?.let {
                project.extensions.add("libs", it)
            }
            project.rootProject.extensions.findByType(VersionCatalogsExtension::class.java)?.let {
                project.extensions.add("versionCatalogs", it)
            }
        }

        /**
         * this is a hack until https://github.com/gradle/gradle/issues/22468 is fixed.
         *
         * Counterpart to the [installVersionCatalogSupport] method, where all extensions added are removed. This is
         * necessary as Gradle will eventually try to register those extensions and would fail if they are already
         * present in the extensions contains.
         */
        fun removeVersionCatalogSupport(project: Project) {

            val mapOfExtensions = project.extensions.javaClass.getDeclaredField("extensionsStorage").let {
                it.isAccessible = true
                it.get(project.extensions)
            }.let { extensions ->
                extensions.javaClass.getDeclaredField("extensions").let {
                    it.isAccessible = true
                    it.get(extensions)
                }
            } as MutableMap<*, *>
            mapOfExtensions.remove("libs")
            mapOfExtensions.remove("versionCatalogs")
        }
    }
}