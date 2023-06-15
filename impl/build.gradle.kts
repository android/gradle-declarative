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
		create("comAndroidInternalDeclarative") {
			id = "com.android.internal.declarative"
			version = Constants.PLUGIN_VERSION
			implementationClass = "com.android.declarative.internal.DeclarativePlugin"
		}
		create("comAndroidDeclarativeSettings") {
			id = "com.android.internal.settings-declarative"
			version = Constants.PLUGIN_VERSION
			implementationClass = "com.android.declarative.internal.SettingsDeclarativePlugin"
		}
	}
}

dependencies {
	implementation(gradleApi())
	implementation(libs.tomlj)
	implementation(libs.coroutines)
	implementation(libs.toolsCommon)
	implementation(libs.declarativeModel)

	testImplementation(libs.junit)
	testImplementation(libs.mockito)
	testImplementation(libs.agpApi)
	testImplementation(libs.truth)
}
