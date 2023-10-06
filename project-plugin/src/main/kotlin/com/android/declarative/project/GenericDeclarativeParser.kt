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

package com.android.declarative.project

import com.android.declarative.common.DslTypeResult
import com.android.declarative.common.DslTypesCache
import com.android.declarative.common.LoggerWrapper
import com.android.declarative.internal.IssueLogger
import com.android.declarative.internal.LOG
import com.google.common.collect.ListMultimap
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.configurationcache.extensions.capitalized
import org.tomlj.TomlArray
import org.tomlj.TomlTable
import java.io.File
import kotlin.RuntimeException
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.valueParameters
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure

/**
 * Simplistic version of a facility to map toml file content to reflection invocations on extension
 * objects. Does not handle all cases, just a proof of concept at this point
 */
class GenericDeclarativeParser(
    private val project: Project,
    private val cache: DslTypesCache = DslTypesCache(),
    private val issueLogger: IssueLogger =
        IssueLogger(false, LoggerWrapper(project.logger)),
) : com.android.declarative.common.DeclarativeFileParser {
    companion object {

        private val tableExtractors = mapOf<KType, (table: TomlTable, key: String) -> Any?>(
            Int::class.createType(listOf(), false) to { table, key -> table.getLong(key)?.toInt() },
            Long::class.createType(listOf(), false) to { table, key -> table.getLong(key) },
            String::class.createType(listOf(), false) to { table, key -> table.getString(key) },
            Boolean::class.createType(listOf(), false) to { table, key -> table.getBoolean(key) },
            Double::class.createType(listOf(), false) to { table, key -> table.getDouble(key) },
            File::class.createType(listOf(), false) to { table, key -> File(table.getString(key)!!)},
            JavaVersion::class.createType(listOf(), false) to { table, key -> JavaVersion.valueOf(table.getString(key)!!)},
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


    private val contexts = ArrayDeque<Context>()

    private fun <T : Any> processProperty(
        property: KProperty1<in T, Any>,
        table: TomlTable,
        tableKey: String,
        extension: T) {
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
        } else  if (subExtensionObject is Property<*>) {
            // Gradle properties only have one parameter type. We can retrieve it from the property
            // declaration on the extension type we are parsing for.
            if (property.returnType.arguments.size != 1) {
                throw RuntimeException("org.gradle.api.provider.Property " +
                        "are supposed to have only one parameter type.")
            }
            val propertyType = property.returnType.arguments[0]
            tableExtractors.get(propertyType.type)?.invoke(table, tableKey)?.let { valueFromToml ->
                logger.LOG {
                    "O: $tableKey = $valueFromToml for Property $property, instance = $subExtensionObject"
                }
                (subExtensionObject as Property<Any>).set(valueFromToml)
            }

        } else {
            logger.LOG { "Parsing for ${property.returnType.jvmErasure}" }
            when (val value = table.get(tableKey)) {
                is TomlTable -> processTomlTable(value, property.returnType, subExtensionObject)
                is TomlArray -> processTomlArray(value, property.returnType, subExtensionObject)
                else -> {
                    value?.let {
                        logger.LOG { "Adding $value to MutableCollection ${property.name}" }
                        (property.get(extension) as MutableCollection<Any>).add(value)
                    }
                }
            }
        }
    }

    override fun <T : Any> parse(table: TomlTable, type: KClass<out T>, extension: T) {

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
                // Try setting the property as accessible. "ldlibs" is inaccessible maybe because it is nullable and not initialized?
                mutableProperty.javaField?.trySetAccessible()
                if (mutableProperty.returnType.jvmErasure.isSubclassOf(MutableCollection::class)) {
                    when (val value = table.get(tableKey)) {
                        is TomlArray -> processTomlArray(value, mutableProperty.returnType, mutableProperty.get(extension))
                        else -> {
                            logger.LOG { " I don't handle this value $value" }
                            val propertyValue: Any = getPropertyValue(table, tableKey, mutableProperty)
                            (mutableProperty.get(extension) as MutableCollection<Any>).add(propertyValue)
                        }
                    }
                } else {
                    val propertyValue: Any? = getPropertyValueOrNull(table, tableKey, mutableProperty)
                    if (propertyValue != null) {
                        // If property value is simple type, just set the value on the extension object.
                        try {
                            // If the property has backing field, we can use property setter
                            mutableProperty.set(extension, propertyValue)
                        } catch (e: Error) {
                            // If kotlin reflect could not find an accessor for this property and if there is no backing javaField, we receive
                            // a KotlinReflectionError when trying to set the property.
                            // In such cases, we can bypass the kotlin reflect and set the value by directly invoking java method if it is available.
                            val callable = type.java.methods.find { it.name == "set${tableKey.capitalized()}" }
                            if (callable != null) {
                                callable.invoke(extension, propertyValue)
                            } else {
                                issueLogger.raiseError("Could not set mutable property $mutableProperty on $extension object with error $e")
                            }
                        }
                    } else {
                        // We reach here in two cases:
                        // 1. When the mutableProperty.returnType.withNullability(false) is generic type(V)
                        // 2. when the mutableProperty is nullable like ApkSigningConfig? property of DefaultConfig.
                        // In first case, we can treat the mutable property as property but in second case, the property.get(instance)
                        // returns null. We would need to create the appropriate object before we can continue processing.
                        // TODO: Handle nullable properties that are initialized as null on extension an object. Also figure
                        // out why this happens on some extension objects.
                        if (mutableProperty.get(extension) != null) {
                            processProperty(mutableProperty, table, tableKey, extension)
                        }
                    }
                }
            } else {
                val property = dslTypeResult.properties[tableKey]
                if (property != null) {
                    processProperty(property, table, tableKey, extension)
                } else {
                    // last ditch, look for member.
                    setValueThroughExtensionMemberLookup(dslTypeResult, type, extension, table, tableKey)
                }
            }
        }
        contexts.removeLast()
    }

    private fun <T : Any> setValueThroughExtensionMemberLookup(
        dslTypeResult: DslTypeResult<Any>,
        type: KClass<out T>,
        extension: T,
        table: TomlTable,
        tableKey: String) {
        if (mapTypes.any { it.isAssignableFrom(type.javaObjectType) }) {
            // TODO: figure out a better way to handle map types
            val callables = dslTypeResult.members["put"]
            if (callables != null) {
                invokePutMethod(callables, extension, table, tableKey)
            }else {
                issueLogger.raiseError(
                    "Cannot find method put in ${dslTypeResult.dslType}, available members : \n $dslTypeResult"
                )
            }
        } else {
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
                val mutableExtension = extension as MutableCollection<Any>?
                if (mutableExtension != null) {
                    extractFromArray(table, i, contentType).let {
                        logger.LOG { "Adding $it to $extension " }
                            mutableExtension.add(it)
                    }
                } else {
                    println("Extension is null. Ignoring : ${type.jvmErasure}")
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
                JavaVersion::class.java -> JavaVersion.valueOf(table.getString(index))
                else -> throw IllegalArgumentException("Error: extractFromArray does not handle $clazz")
            }
        )

    private fun getPropertyValue(table: TomlTable, key: String, type: KType): Any {
        val tableExtractor = tableExtractors[type]
            ?: throw RuntimeException("Cannot handle unwrapping $type")

        return tableExtractor.invoke(table, key)
            ?: throw RuntimeException("No value provided for key $key")
    }

    private fun getPropertyValueOrNull(table: TomlTable, key: String, type: KType): Any? {
        return tableExtractors[type]?.invoke(table, key)
    }

    private fun getPropertyValueOrNull(table: TomlTable, key: String, property: KMutableProperty1<out Any?, Any>): Any? =
        getPropertyValueOrNull(table, key, property.returnType.withNullability(false))

    private fun getPropertyValue(table: TomlTable, key: String, property: KMutableProperty1<out Any?, Any>): Any =
        getPropertyValue(table, key, property.returnType.withNullability(false))

    private fun <T : Any> convertFrom(value: Any, clazz: Class<T>): T =
        clazz.cast(
            when (clazz) {
                String::class.java -> value.toString()
                Boolean::class.java -> value.toString().toBoolean()
                Int::class.java -> value.toString().toInt()
                File::class.java -> project.file(value)
                JavaVersion::class.java -> JavaVersion.valueOf(value.toString())
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

    private fun invokePutMethod(callables: Collection<KCallable<*>>, extension: Any, table: TomlTable, tableKey: String) {
        val declarativeForm = table.get(tableKey)
        if (declarativeForm == null) {
            logger.LOG { "Error, empty value for key $tableKey"}
            return
        }
        for (callable in callables) {
            if (callable.valueParameters.size != 2) {
                logger.LOG { "$callable has been eliminated because it has ${callable.parameters.size} parameter(s)"}
                continue
            }
            val parameterType = callable.parameters[2].type.withNullability(false)
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
                        callable.call(extension, tableKey, parameterValue)
                        return
                    }
                } catch (e : NoSuchMethodException) {
                    logger.LOG { "$callable has been eliminated, a static method named $staticMethodName does not exist" }
                }
            } else {
                val resolvedValue = if (tableExtractors[parameterType] != null) {
                    getPropertyValue(table, tableKey, parameterType)
                } else if (parameterType.jvmErasure.java.isAssignableFrom(declarativeForm.javaClass)) {
                    declarativeForm
                } else {
                    contexts.reversed()
                        .firstNotNullOfOrNull { it.resolve(declarativeForm, parameterType.jvmErasure.java) }
                }
                if (resolvedValue != null) {
                    logger.LOG { "Invoking $callable with $resolvedValue" }
                    callable.call(extension, tableKey, resolvedValue)
                    return
                } else {
                    logger.LOG { "$callable has been eliminated, we cannot handle $parameterType extraction" }
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
                logger.LOG { "$callable has been eliminated because it has ${callable.valueParameters.size} parameter(s)"}
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

    private val mapTypes = setOf(
        Map::class.java,
        MapProperty::class.java,
        ListMultimap::class.java
    )
}