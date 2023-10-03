/*
* Copyright (C) 2012 The Android Open Source Project
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
package com.android.declarative.common

import com.android.utils.ILogger
import java.io.Serializable
import java.util.function.Supplier
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging


/**
 * Implementation of Android's [ILogger] over Gradle's [Logger].
 *
 * Note that this maps info to the default user-visible lifecycle.
 */
class LoggerWrapper(private val logger: Logger) : ILogger {
  override fun error(throwable: Throwable?, s: String?, vararg objects: Any) {
    var s = s
    if (!logger.isEnabled(ILOGGER_ERROR)) {
      return
    }
    if (s == null) {
      s = "[no message defined]"
    } else if (objects != null && objects.size > 0) {
      s = String.format(s, *objects)
    }
    if (throwable == null) {
      logger.log(ILOGGER_ERROR, s)
    } else {
      logger.log(
        ILOGGER_ERROR,
        s,
        throwable
      )
    }
  }

  override fun warning(s: String, vararg objects: Any) {
    log(ILOGGER_WARNING, s, objects)
  }

  override fun quiet(s: String, vararg objects: Any) {
    log(ILOGGER_QUIET, s, objects)
  }

  override fun lifecycle(s: String, vararg objects: Any) {
    log(ILOGGER_LIFECYCLE, s, objects)
  }

  override fun info(s: String, vararg objects: Any) {
    log(ILOGGER_INFO, s, objects)
  }

  override fun verbose(s: String, vararg objects: Any) {
    log(ILOGGER_VERBOSE, s, objects)
  }

  private fun log(logLevel: LogLevel, s: String, vararg objects: Any) {
    if (!logger.isEnabled(logLevel)) {
      return
    }
    if (objects == null || objects.size == 0) {
      logger.log(logLevel, s)
    } else {
      logger.log(logLevel, String.format(s, *objects))
    }
  }

  private class LoggerSupplier private constructor(private val clazz: Class<*>) :
    Supplier<com.android.utils.ILogger?>, Serializable {
    private var logger: com.android.utils.ILogger? = null

    @Synchronized
    override fun get(): com.android.utils.ILogger? {
      if (logger == null) {
        logger = LoggerWrapper(
          Logging.getLogger(
            clazz
          )
        )
      }
      return logger
    }
  }

  companion object {
    // Mapping from ILogger method call to gradle log level.
    private val ILOGGER_ERROR = LogLevel.ERROR
    private val ILOGGER_WARNING = LogLevel.WARN
    private val ILOGGER_QUIET = LogLevel.QUIET
    private val ILOGGER_LIFECYCLE = LogLevel.LIFECYCLE
    private val ILOGGER_INFO = LogLevel.INFO
    private val ILOGGER_VERBOSE = LogLevel.INFO
    fun getLogger(klass: Class<*>): LoggerWrapper {
      return LoggerWrapper(Logging.getLogger(klass))
    }
  }
}