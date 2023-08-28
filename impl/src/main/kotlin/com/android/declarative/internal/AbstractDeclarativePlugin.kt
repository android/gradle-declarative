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

package com.android.declarative.internal

import com.android.declarative.internal.parsers.DeclarativeFileParser
import org.tomlj.TomlParseResult
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * There are 2 declarative plugins, a [Settings] plugin and a [Project] plugin.
 */
abstract class AbstractDeclarativePlugin {

    abstract val buildFileName: String

    internal fun parseDeclarativeFile(
        location: String,
        declarativeFileContent: String,
        issueLogger: IssueLogger,
    ): TomlParseResult =
        DeclarativeFileParser(issueLogger).parseDeclarativeFile(
            location,
            declarativeFileContent,
        )

    internal fun parseDeclarativeInFolder(folder: File, issueLogger: IssueLogger): TomlParseResult =
        parseDeclarativeFile(Paths.get(folder.absolutePath, buildFileName), issueLogger)

    protected fun parseDeclarativeFile(buildFile: Path, issueLogger: IssueLogger): TomlParseResult =
        DeclarativeFileParser(issueLogger).parseDeclarativeFile(buildFile)
}