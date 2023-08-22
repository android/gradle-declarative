plugins {
	alias(libs.plugins.kotlin.jvm)
}

tasks.test {
	this.environment("CUSTOM_REPO", System.getenv("CUSTOM_REPO") + File.pathSeparatorChar + "${project.rootDir}/out/repo")
	this.environment("TEST_TMPDIR", project.buildDir)
	this.environment("AGP_WORKSPACE_LOCATION", providers.gradleProperty("com.android.workspace.location").get())
	this.environment("PLUGIN_VERSION", Constants.PLUGIN_VERSION)
}

dependencies {
	testImplementation(gradleApi())
	testImplementation(libs.tomlj)
	testImplementation(libs.junit)
	testImplementation("com.android.build.integration-test:framework")
}
