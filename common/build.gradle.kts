plugins {
	id("declarative-plugin-module-conventions")
	alias(libs.plugins.kotlin.jvm)
	//alias(libs.plugins.plugin.publish)
}

dependencies {
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
