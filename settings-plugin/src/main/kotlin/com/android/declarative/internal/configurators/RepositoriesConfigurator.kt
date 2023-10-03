package com.android.declarative.internal.configurators

import com.android.declarative.internal.IssueLogger
import com.android.declarative.internal.model.PreDefinedRepositoryInfo
import com.android.declarative.internal.model.RepositoryInfo
import org.gradle.api.artifacts.dsl.RepositoryHandler

/**
 * Configures a [RepositoryHandler] with repositories.
 */
class RepositoriesConfigurator(
    private val issueLogger: IssueLogger,
) {

    /**
     * Add the [List] of [RepositoryInfo]s to the provided [RepositoryHandler]
     *
     * @param context a user friendly string to provide context to error/warning messages.
     * @param repositoryHandler the [RepositoryHandler] to add repositories to.
     * @param repositoryModels [List] of repository model to add.
     */
    fun apply(
        context: String,
        repositoryHandler: RepositoryHandler,
        repositoryModels: List<RepositoryInfo>
    ) {
        repositoryModels.forEach {
            when(it) {
                is PreDefinedRepositoryInfo -> {
                    when(it.name) {
                        "google" -> {
                            issueLogger.logger.info("Adding google repository to $context")
                            repositoryHandler.google()
                        }
                        "mavenCentral" -> {
                            issueLogger.logger.info("Adding mavenCentral repository to $context")
                            repositoryHandler.mavenCentral()
                        }
                        "mavenLocal" -> {
                            issueLogger.logger.info("Adding mavenCentral repository to $context")
                            repositoryHandler.mavenLocal()
                        }
                        "gradlePluginPortal" -> {
                            issueLogger.logger.info("Adding gradlePluginPortal repository to $context")
                            repositoryHandler.gradlePluginPortal()
                        }
                        else -> {
                            throw RuntimeException("Unknown repository named : ${it.name}")
                        }
                    }
                }
                else -> throw RuntimeException("Unhandled repository type: ${it.type}, ${it.javaClass}")
            }
        }
    }
}