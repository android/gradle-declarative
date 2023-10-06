import org.gradle.kotlin.dsl.extra

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

plugins {
	alias(libs.plugins.kotlin.jvm)
}

val agpWorkspace = extra.get("agpWorkspace")

tasks.test {
	this.environment("CUSTOM_REPO", System.getenv("CUSTOM_REPO") + File.pathSeparatorChar + "${project.rootDir}/out/repo")
	this.environment("TEST_TMPDIR", project.buildDir)
	this.environment("TEST_ROOTDIR", project.rootProject.projectDir)
	this.environment("AGP_WORKSPACE_LOCATION", agpWorkspace!!)
	this.environment("PLUGIN_VERSION", Constants.PLUGIN_VERSION)
}

dependencies {
	testImplementation(gradleApi())
	testImplementation(libs.tomlj)
	testImplementation(libs.junit)
	testImplementation(libs.truth)
	testImplementation(libs.testutils)
	testImplementation(libs.testFramework)
	testImplementation(libs.toolsCommon)
	testImplementation(libs.agpApi)
}
