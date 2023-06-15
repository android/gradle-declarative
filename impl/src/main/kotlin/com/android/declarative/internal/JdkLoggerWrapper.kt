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

import com.android.utils.ILogger
import java.util.logging.Level
import java.util.logging.Logger

class JdkLoggerWrapper(
  val logger: Logger
): ILogger {
  override fun error(t: Throwable?, msgFormat: String?, vararg args: Any?) =
    logger.log(Level.SEVERE, t) {
      if (msgFormat == null) {
        "[no message defined]"
      } else
        String.format(msgFormat, *args)
      }


  override fun warning(msgFormat: String, vararg args: Any?) =
    logger.warning {
      String.format(msgFormat, args)
    }

  override fun info(msgFormat: String, vararg args: Any?) =
    logger.info {
      String.format(msgFormat, args)
    }


  override fun verbose(msgFormat: String, vararg args: Any?) =
    logger.fine {
      String.format(msgFormat, args)
    }
}