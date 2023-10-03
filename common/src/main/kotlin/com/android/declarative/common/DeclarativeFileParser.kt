package com.android.declarative.common

import org.tomlj.TomlTable
import kotlin.reflect.KClass

interface DeclarativeFileParser {

    /**
     * parse a [TomlTable] into an extension object of type [T]
     */
    fun <T : Any> parse(table: TomlTable, type: KClass<out T>, extension: T)
}