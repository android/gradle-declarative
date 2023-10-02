plugins {
	id("declarative-plugin-module-conventions")
	alias(libs.plugins.kotlin.jvm)
	//alias(libs.plugins.plugin.publish)
}

val copyConstantsTask = tasks.register<Copy>("copyConstants") {
	from(layout.projectDirectory.file("../buildSrc/src/main/kotlin/Constants.kt"))
	into(layout.buildDirectory.dir("src/generated"))
}
kotlin.sourceSets["main"].kotlin {
	srcDir(copyConstantsTask)
}

gradlePlugin {
	plugins {
		create("comAndroidDeclarativeSettings") {
			id = "com.android.internal.settings-declarative"
			version = Constants.PLUGIN_VERSION
			implementationClass = "com.android.declarative.internal.SettingsDeclarativePlugin"
		}
	}
}

dependencies {
	implementation(project(":common"))
	implementation(gradleApi())
	implementation(libs.tomlj)
	implementation(libs.coroutines)
	implementation(libs.toolsCommon)
	implementation(libs.declarativeModel)
	implementation(libs.agpApi)

	testImplementation(libs.junit)
	testImplementation(libs.mockito)
	testImplementation(libs.truth)
}
