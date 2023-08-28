package com.android.declarative.internal

import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * Implementation of [ValueSource] for declarative files that are accessed/read during configuration
 * time.
 */
abstract class DeclarativeFileValueSource : ValueSource<String?, DeclarativeFileValueSource.Params> {

    companion object {

        /**
         * Enlists a new file to be monitored for configuration cache invalidation.
         * @param providers [ProviderFactory] to create instances of [DeclarativeFileValueSource]
         * @param file the [File] to monitor.
         */
        fun enlist(
            providers: ProviderFactory,
            file: RegularFile,
        ): Provider<String?> =
            providers.of(DeclarativeFileValueSource::class.java) {
                it.parameters.configFile.set(file)
            }

        /**
         * Enlists a new file to be monitored for configuration cache invalidation.
         * @param providers [ProviderFactory] to create instances of [DeclarativeFileValueSource]
         * @param file the [Provider] of [RegularFile] to monitor.
         */
        fun enlist(
            providers: ProviderFactory,
            file: Provider<RegularFile>,
        ): Provider<String?> =
            providers.of(DeclarativeFileValueSource::class.java) {
                it.parameters.configFile.set(file)
            }

        /**
         * Enlists a new file to be monitored for configuration cache invalidation.
         * @param objects [ObjectFactory] to create instances of [DirectoryProprty]
         * @param settings [Settings] instance
         * @param fileName file name for a file present or not in [Settings.getSettingsDir]
         */
        fun enlist(
            objects: ObjectFactory,
            settings: Settings,
            fileName: String,
        ): Provider<String?> =
            enlist(settings.providers,
                objects.directoryProperty().also {
                    it.set(settings.settingsDir)
                }.file(fileName)
            )
    }

    interface Params : ValueSourceParameters {
        val configFile: RegularFileProperty
    }

    /**
     * Return the file content or null if the file does not exists.
     */
    override fun obtain(): String? {
        return parameters.configFile.asFile.get().takeIf { it.exists() }?.bufferedReader()?.readText()
    }
}