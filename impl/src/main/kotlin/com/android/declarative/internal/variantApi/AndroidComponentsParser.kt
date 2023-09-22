package com.android.declarative.internal.variantApi

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.VariantSelector
import com.android.declarative.internal.DeclarativeFileParser
import com.android.declarative.internal.DslTypesCache
import com.android.declarative.internal.GenericDeclarativeParser
import com.android.declarative.internal.IssueLogger
import com.android.declarative.internal.LoggerWrapper
import org.gradle.api.Project
import org.tomlj.TomlTable
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure

/**
 * Specialized parser responsible for parsing the 'androidComponents' elements
 * in declarative file.
 */
class AndroidComponentsParser(
    private val project: Project,
    private val cache: DslTypesCache = DslTypesCache(),
    private val issueLogger: IssueLogger = IssueLogger(false, LoggerWrapper(project.logger)),
): DeclarativeFileParser {

    override fun <T: Any> parse(table: TomlTable, type: KClass<out T>, extension: T) {
        // the extension is a subclass of AndroidComponentsExtension, its type parameters will tell me
        // type types of the VariantBuilder and Variant object I am dealing with.
        val superType = type.supertypes.first { superType ->
            superType.jvmErasure.simpleName == "AndroidComponentsExtension"
        }

        // now the superType arguments are as follows
        // superType.arguments[0] is the DSL common extension type
        // superType.arguments[1] is the VariantBuilder type
        // superType.arguments[2] is the Variant type.
        val variantBuilderType = superType.arguments[1].type
            ?: throw RuntimeException("Cannot determine the VariantBuilder type for extension $type")

        val variantType = superType.arguments[2].type
            ?: throw RuntimeException("Cannot determine the Variant type for extension $type")

        // It is not necessary to cast to the AndroidComponentsExtension subtype as I am only
        // interested in invoking methods that are all defined in the AndroidComponentsExtension interface itself.
        val typedExtension = AndroidComponentsExtension::class.java.cast(extension)

        // order does not matter, it's just registering a callback at this point which will be called by AGP
        // in the right order.
        handleCallback(table, typedExtension, "beforeVariants", variantBuilderType)
        { tomlDeclaration, variantApiType, selector ->
            if (selector == null) {
                typedExtension.beforeVariants { variantBuilder ->
                    parse(tomlDeclaration, variantBuilder, variantApiType)
                }
            } else {
                typedExtension.beforeVariants(selector) { variantBuilder ->
                    parse(tomlDeclaration, variantBuilder, variantApiType)
                }
            }
        }
        handleCallback(table, typedExtension, "onVariants", variantType) {
                tomlDeclaration, variantApiType, selector ->
            if (selector == null) {
                typedExtension.onVariants { variant ->
                    parse(tomlDeclaration, variant, variantApiType)
                }
            } else {
                typedExtension.onVariants(selector) { variant ->
                    parse(tomlDeclaration, variant, variantApiType)
                }
            }
        }
    }

    /**
     * Handle a Variant API callback style pf declarations.
     *
     * @param table the toml declarations
     * @param typedExtension the type of the Variant API we are dealing with (Application, Library, etc...)
     * @param variantApiName callback name like 'onVariants' or 'beforeVariants'
     * @param variantApiType type of instance passed to the Variant API callback so for 'onVariants', it's
     * ApplicationVariant (if dealing with an application) while for 'beforeVariants`, it's ApplicationVariantBuilder.
     * @param action lambda function to register callbacks on [typedExtension] for each target variants.
     */
    private fun handleCallback(
        table: TomlTable,
        typedExtension: AndroidComponentsExtension<*, *, *>,
        variantApiName: String,
        variantApiType: KType,
        action: (tomlDeclaration: TomlTable, variantApiType: KType, selector: VariantSelector?) -> Unit
    ) {
        // let's look at setting that applies to all variants.
        table.getTable(variantApiName)?.let { variantNames ->
            variantNames.keySet().forEach { variantName ->
                if (!variantNames.isTable(variantName)) {
                    throw RuntimeException(
                        """
                            When invoking the beforeVariants/onVariants API, you must always provide a 
                            variant name, or `all` to target all variants. 
                            
                            For example, instead of 
                                [androidComponents.beforeVariants]
                                enable = false

                            To target all variants, you must do 
                                [androidComponents.beforeVariants.all]
                                enable = false
                            To target a variant by its VARIANT_NAME name, you must do 
                                [androidComponents.beforeVariants.VARIANT_NAME]
                                enable = false
                        """.trimIndent()
                    )
                }
                variantNames.getTable(variantName)?.let {
                    action(
                        it,
                        variantApiType,
                        if (variantName.equals("all")) {
                            typedExtension.selector().all()
                        } else {
                            typedExtension.selector().withName(variantName)
                        }
                    )
                }
            }
        }
    }

    private fun parse(tomlDeclaration: TomlTable, variantBuilder: VariantBuilder, variantBuilderType: KType) {
        GenericDeclarativeParser(project, cache, issueLogger)
            .parse(tomlDeclaration, variantBuilderType.jvmErasure, variantBuilder)
    }

    private fun parse(tomlDeclaration: TomlTable, variant: Variant, variantType: KType) {
        GenericDeclarativeParser(project, cache, issueLogger)
            .parse(tomlDeclaration, variantType.jvmErasure, variant)
    }
}