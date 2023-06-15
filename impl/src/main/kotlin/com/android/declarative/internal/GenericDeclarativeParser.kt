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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import java.io.File
import kotlin.RuntimeException
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.valueParameters
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.jvmErasure

/**
 * Simplistic version of a facility to map toml file content to reflection invocations on extension
 * objects. Does not handle all cases, just a proof of concept at this point
 */
class GenericDeclarativeParser(
    private val project: Project,
    private val issueLogger: IssueLogger =
        IssueLogger(false, LoggerWrapper(project.logger)),
) {
    companion object {

        private val tableExtractors = mapOf<KType, (table: TomlTable, key: String) -> Any?>(
            Int::class.createType(listOf(), false) to { table, key -> table.getLong(key)?.toInt() },
            Long::class.createType(listOf(), false) to { table, key -> table.getLong(key) },
            String::class.createType(listOf(), false) to { table, key -> table.getString(key) },
            Boolean::class.createType(listOf(), false) to { table, key -> table.getBoolean(key) },
            Double::class.createType(listOf(), false) to { table, key -> table.getDouble(key) },
            Any::class.createType(listOf(), false) to { table, key -> table.get(key) }
        )
    }

    private val logger = LoggerWrapper(project.logger)

    private interface Context {
        fun <T : Any> resolve(value: Any, expectedType: Class<T>): T?

        fun findMember(memberName: String): KCallable<*>?

        fun callMember(
            memberName: String,
            parameters: TomlArray,
            genericDeclarativeParser: GenericDeclarativeParser
        ): Any?
    }

    private class ExtensionContext<T : Any>(
        private val extension: T,
        private val dslTypeResult: DslTypeResult<T>,
    ) : Context {
        override fun <T : Any> resolve(value: Any, expectedType: Class<T>): T? {
            if (value is TomlTable) {
                return value.keySet().single().let { key ->
                    expectedType.getMethod(key, String::class.java)?.let {
                        //println("Invoking $it")
                        it.invoke(null, value.get(key)) as T
                    }
                }
            }
            return null
        }

        override fun findMember(memberName: String): KCallable<*>? =
            dslTypeResult.members[memberName]?.firstOrNull()

        override fun callMember(
            memberName: String,
            parameters: TomlArray,
            genericDeclarativeParser: GenericDeclarativeParser
        ) =
            findMember(memberName)?.let { kCallable ->
                val parameterValues: List<Any> = kCallable.valueParameters
                    .mapIndexed { index, kParameter ->
                        genericDeclarativeParser.extractFromArray(parameters, index, kParameter.type)
                    }
                return@let kCallable.call(extension, *parameterValues.toTypedArray())
            }
    }

    private class NamedDomainObjectContainerContext(
        val container: NamedDomainObjectContainer<*>,
        val domainObjectType: KType,
    ) : Context {
        override fun <U : Any> resolve(value: Any, expectedType: Class<U>): U? {
            if (expectedType.isAssignableFrom(domainObjectType.jvmErasure.java)) {
                return container.getByName(value.toString()) as U
            }
            return null
        }

        override fun findMember(memberName: String): KFunction<Any>? = null
        override fun callMember(
            memberName: String,
            parameters: TomlArray,
            genericDeclarativeParser: GenericDeclarativeParser
        ): Any {
            throw RuntimeException("Cannot invoke method on NamedDomainObjectContainer")
        }

    }


    private val cache = DslTypesCache()
    private val contexts = ArrayDeque<Context>()

    fun <T : Any> parse(table: TomlTable, type: KClass<out T>, extension: T) {

        val dslTypeResult: DslTypeResult<Any> = cache.getCache(type)

        contexts.addLast(ExtensionContext(extension, dslTypeResult))

        table.keySet().forEach { tableKey ->
            if (tableKey == "_dispatch_") {
                issueLogger.expect(
                    table.isTable("_dispatch_"),
                    { "_dispatch_ dottedKey is expected to be a TomlTable, found ${table.get("_dispatch_")}" },
                    table.getTable("_dispatch_"),
                    { "Null table for _dispatch_ dotted key." },
                ) { dispatchTable ->
                    handleDispatchRequest(dispatchTable, dslTypeResult, extension)
                }
                return@forEach
            }

            val mutableProperty: KMutableProperty1<in Any, Any>? = dslTypeResult.mutableProperties[tableKey]
                ?: dslTypeResult.mutableProperties["is${tableKey.capitalized()}"]
            if (mutableProperty != null) {
                logger.LOG { "F: $tableKey = ${table.get(tableKey)} at $mutableProperty" }
                val propertyValue: Any = getPropertyValue(table, tableKey, mutableProperty)
                mutableProperty.set(extension, propertyValue)
            } else {
                val property = dslTypeResult.properties[tableKey]
                if (property != null) {
                    val subExtensionObject = property.get(extension)
                    if (subExtensionObject is NamedDomainObjectContainer<*>) {
                        val valueFromToml = table.getTable(tableKey)
                        logger.LOG {
                            "O: $tableKey = $valueFromToml at $property, instance = $subExtensionObject"
                        }
                        logger.LOG { "Got ${property.name} value $subExtensionObject with type ${subExtensionObject.javaClass}" }
                        val domainObjectType = property.returnType.arguments[0].type
                            ?: throw RuntimeException("${property.name}  with type ${subExtensionObject.javaClass} does not have a parameter type to the NamedDomainObjectContainer")
                        valueFromToml?.let { namesForDomainObjects ->
                            logger.LOG { "maybeCreating ${namesForDomainObjects.keySet().size} instances in $tableKey" }
                            namesForDomainObjects.keySet().forEach { nameForDomainObject ->
                                logger.LOG { "maybeCreating $nameForDomainObject of type $domainObjectType" }
                                val containedExtension = subExtensionObject.maybeCreate(nameForDomainObject)
                                contexts.addLast(
                                    NamedDomainObjectContainerContext(
                                        subExtensionObject,
                                        domainObjectType
                                    )
                                )
                                parse(
                                    namesForDomainObjects.getTable(nameForDomainObject)!!,
                                    property.returnType.arguments[0].type!!,
                                    containedExtension
                                )
                                contexts.removeLast()
                            }
                        }
                    } else {
                        logger.LOG { "Parsing for ${property.returnType.jvmErasure}" }
                        when (val value = table.get(tableKey)) {
                            is TomlTable -> processTomlTable(value, property.returnType, subExtensionObject)
                            is TomlArray -> processTomlArray(value, property.returnType, subExtensionObject)
                            else -> logger.LOG { "I don't handle this value $value" }
                        }
                    }
                } else {
                    // last ditch, look for member.
                    val callables = dslTypeResult.members[tableKey]
                        ?: dslTypeResult.members["set${tableKey.capitalized()}"]
                    if (callables != null ) {
                        logger.LOG { "Found ${callables.size} potential candidates for $tableKey" }
                        invokeMethod(callables, extension, table, tableKey)
                    } else {
                        issueLogger.raiseError(
                            "Cannot find $tableKey in ${dslTypeResult.dslType}, available members : \n $dslTypeResult"
                        )
                    }

                }
            }
        }
        contexts.removeLast()
    }

    // HANDLE REFERENCES TO OTHER NAMED OBJECTS
    private fun parse(table: TomlTable, type: KType, extension: Any) {
        parse(table, type.jvmErasure, extension)
    }

    private fun processTomlTable(table: TomlTable, type: KType, extension: Any) {
        if (type.jvmErasure.isSubclassOf(MutableMap::class)) {
            // if the receiver is a Map, we need to treat this differently.
            table.entrySet().forEach { mapEntry ->
                logger.LOG { "Adding ${mapEntry.key} -> ${mapEntry.value} to $extension" }
                (extension as MutableMap<String, Any>)[mapEntry.key] =
                    convertFrom(mapEntry.value, type.arguments[1].type?.jvmErasure?.java ?: String::class.java)
            }
        } else {
            parse(table, type.jvmErasure, extension)
        }
    }

    private fun processTomlArray(table: TomlArray, type: KType, extension: Any) {
        if (type.jvmErasure.isSubclassOf(MutableCollection::class)) {
            for (i in 0 until table.size()) {
                // collection only have one argument, the content type.
                val contentType: KTypeProjection = type.arguments[0]
                extractFromArray(table, i, contentType).let {
                    logger.LOG { "Adding $it to $extension " }
                    (extension as MutableCollection<Any>).add(it)
                }
            }
        } else {
            println("I don't know how to handle : ${type.jvmErasure}")
        }
    }

    fun extractFromArray(table: TomlArray, index: Int, type: KTypeProjection): Any =
        type.type?.let {
            extractFromArray(table, index, it)
        } ?: extractFromArray(table, index, String::class.java)

    private fun extractFromArray(table: TomlArray, index: Int, type: KType): Any =
        extractFromArray(table, index, type.jvmErasure.java)

    private fun <T : Any> extractFromArray(table: TomlArray, index: Int, clazz: Class<T>): T =
        clazz.cast(
            when (clazz) {
                String::class.java -> table.getString(index)
                Boolean::class.java -> table.getBoolean(index)
                Float::class.java -> table.getDouble(index).toFloat()
                Double::class.java -> table.getDouble(index)
                File::class.java -> project.file(table.getString(index))
                else -> throw IllegalArgumentException("Error: extractFromArray does not handle $clazz")
            }
        )

    private fun getPropertyValue(table: TomlTable, key: String, type: KType): Any {
        val tableExtractor = tableExtractors[type]
            ?: throw RuntimeException("Cannot handle unwrapping $type")

        return tableExtractor.invoke(table, key)
            ?: throw RuntimeException("No value provided for key $key")
    }

    private fun getPropertyValue(table: TomlTable, key: String, property: KMutableProperty1<out Any?, Any>): Any =
        getPropertyValue(table, key, property.returnType.withNullability(false))

    private fun <T : Any> convertFrom(value: Any, clazz: Class<T>): T =
        clazz.cast(
            when (clazz) {
                String::class.java -> value.toString()
                Boolean::class.java -> value.toString().toBoolean()
                Int::class.java -> value.toString().toInt()
                File::class.java -> project.file(value)
                Object::class.java -> value
                else -> throw IllegalArgumentException("Cannot convert from ${value.javaClass} to $clazz")
            }
        )

    private fun handleDispatchRequest(tomlTable: TomlTable, extensionTypeCache: DslTypeResult<Any>, extension: Any) {
        val target = tomlTable.getString("target")
        val method = tomlTable.getString("method")
        val params = tomlTable.getArray("params")

        extensionTypeCache.properties[target]?.let { kProperty ->
            logger.LOG { "Dispatch using $kProperty to get ${kProperty.get(extension)}" }
            val targetExtension = kProperty.get(extension)
            contexts.reversed().firstNotNullOfOrNull { context ->
                context.findMember(method!!)?.let {
                    context.callMember(method, params!!, this)
                }
            }?.let { newValue ->
                if (targetExtension is MutableCollection<*>) {
                    (targetExtension as MutableCollection<Any>).add(newValue)
                }
            }
        }
    }

    private fun invokeMethod(callables: Collection<KCallable<*>>, extension: Any, table: TomlTable, tableKey: String) {
        val declarativeForm = table.get(tableKey)
        if (declarativeForm == null) {
            logger.LOG { "Error, empty value for key $tableKey"}
            return
        }
        for (callable in callables) {
            if (callable.valueParameters.size != 1) {
                logger.LOG { "$callable has been eliminated because it has ${callable.parameters.size} parameter(s)"}
                continue
            }
            val parameterType = callable.parameters[1].type.withNullability(false)
            if (declarativeForm is TomlTable) {
                // if we have a table, that means we are looking at an object that needs to be constructed.
                // So far, I am only supporting static methods with one parameter, but eventually, we need to look
                // at constructors, etc...
                if (declarativeForm.size() != 1) {
                    logger.LOG { "Table at $tableKey has ${declarativeForm.size()} elements which is unsupported" }
                    return
                }
                val staticMethodName = declarativeForm.keySet().single()
                try {
                    parameterType.jvmErasure.java.getMethod(
                        staticMethodName,
                        String::class.java
                    ).let {
                        logger.LOG { "$parameterType extracted using $it" }
                        val parameterValue = it.invoke(
                            null,
                            declarativeForm.getString(
                                staticMethodName
                            )
                        )
                        logger.LOG { "Invoking $callable with $parameterValue" }
                        callable.call(extension, parameterValue)
                        return
                    }
                } catch (e : NoSuchMethodException) {
                    logger.LOG { "$callable has been eliminated, a static method named $staticMethodName does not exist" }
                }
            } else {
                val resolvedValue = if (tableExtractors[parameterType] != null) {
                    getPropertyValue(table, tableKey, parameterType)
                } else {
                    contexts.reversed()
                        .firstNotNullOfOrNull { it.resolve(declarativeForm, parameterType.jvmErasure.java) }
                }
                if (resolvedValue != null) {
                    logger.LOG { "Invoking $callable with $resolvedValue" }
                    callable.call(extension, resolvedValue)
                    return
                } else {
                    logger.LOG { "$callable has been eliminated, we cannot handle $parameterType extraction" }
                }
            }
        }
    }
}