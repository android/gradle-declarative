plugins {
	id("declarative-plugin-module-conventions")
	alias(libs.plugins.kotlin.jvm)
	//alias(libs.plugins.plugin.publish)
}

gradlePlugin {
	plugins {
		create("comAndroidInternalDeclarative") {
			id = "com.android.internal.declarative.project"
			version = Constants.PLUGIN_VERSION
			implementationClass = "com.android.declarative.project.DeclarativePlugin"
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
