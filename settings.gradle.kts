dependencyResolutionManagement {
//    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

rootProject.name = "com.android.experiments.declarative"

include("api")
include("impl")
include("tests")

// The remaining part of this script is to deal with importing dependencies from studio-main workspace.
// First, we need to piggyback on the prebuilts and the built android plugins in order to target testing
// with the latest version of the AGP plugins.
// Second, the tests are using integration-test framework as a dependency to use all the facilities
// related to test project building and APK files exploration.
// Therefore, one must define the "com.android.workspace.location" property to point to the location of the
// studio-main workspace.
val workspaceLocationProperty = providers.gradleProperty("com.android.workspace.location")
if (!workspaceLocationProperty.isPresent) {
    throw java.lang.IllegalArgumentException(
        "com.android.workspace.location Gradle property must point to your checked out studio-main workspace")
}
val workspaceLocation = workspaceLocationProperty.get()

// Provide the maven repositories to find all binaries and built dependencies.
if (System.getenv("CUSTOM_REPO") != null) {
    System.getenv("CUSTOM_REPO").split(File.pathSeparatorChar).forEach { repo ->
        println("Using maven repository : $repo")
        dependencyResolutionManagement.repositories {
            maven { url = java.net.URI("file://$repo") }
        }
    }
} else {
    // no environment variable, revert to hardcoded versions from the workspace location
    dependencyResolutionManagement.repositories {
        maven { url = java.net.URI("file://$workspaceLocation/out/repo") }
        maven { url = java.net.URI("file://$workspaceLocation/prebuilts/tools/common/m2/repository") }
    }
}

// any tests module automatically on published plugins
settings.gradle.beforeProject {
    if (path.contains("tests")) {
        this.afterEvaluate {
            this.getTasksByName("test", false).forEach {
                it.dependsOn(":api:publish", ":impl:publish")
            }
        }
    }
}

// Include the studio-main build to retrieve the integration-test framework dependency that 'tests' project is using.
includeBuild("$workspaceLocation/tools") {
    dependencySubstitution {
        substitute(module("com.android.build.integration-test:framework")).using(project(":base:build-system:integration-test:framework"))
        substitute(module("com.android.tools.build.declarative:model")).using(project(":base:declarative-gradle:model"))
    }
}
