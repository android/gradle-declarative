package com.android.declarative.internal.fixtures

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceRegistry
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.VariantExtension
import com.android.build.api.variant.VariantExtensionConfig
import com.android.build.api.variant.VariantSelector
import org.gradle.api.Action

@Suppress("UnstableApiUsage")
class FakeAndroidComponentsExtension<
        CommonExtensionT: CommonExtension<*, *, *, *, *>,
        VariantBuilderT: VariantBuilder,
        VariantT: Variant>(
    private val selector: VariantSelector,
    private val variantBuilder: VariantBuilderT,
    private val variant: VariantT,
): AndroidComponentsExtension<CommonExtensionT, VariantBuilderT, VariantT> {

    override val managedDeviceRegistry: ManagedDeviceRegistry
        get() = TODO("Not yet implemented")
    override val pluginVersion: AndroidPluginVersion
        get() = TODO("Not yet implemented")
    override val sdkComponents: SdkComponents
        get() = TODO("Not yet implemented")

    override fun finalizeDsl(callback: Action<CommonExtensionT>) {
        TODO("Not yet implemented")
    }

    override fun finalizeDsl(callback: (CommonExtensionT) -> Unit) {
        TODO("Not yet implemented")
    }

    override fun registerSourceType(name: String) {
        TODO("Not yet implemented")
    }

    override fun selector(): VariantSelector = selector


    override fun registerExtension(
        dslExtension: DslExtension,
        configurator: (variantExtensionConfig: VariantExtensionConfig<VariantT>) -> VariantExtension
    ) {
        TODO("Not yet implemented")
    }

    override fun onVariants(selector: VariantSelector, callback: Action<VariantT>) {
        callback.execute(variant)
    }

    override fun onVariants(selector: VariantSelector, callback: (VariantT) -> Unit) {
        callback(variant)
    }

    @Deprecated("Replaced by finalizeDsl", replaceWith = ReplaceWith("finalizeDsl(callback)"))
    override fun finalizeDSl(callback: Action<CommonExtensionT>) {
        TODO("Not yet implemented")
    }

    override fun beforeVariants(selector: VariantSelector, callback: Action<VariantBuilderT>) {
        callback.execute(variantBuilder)

    }

    override fun beforeVariants(selector: VariantSelector, callback: (VariantBuilderT) -> Unit) {
        callback(variantBuilder)
    }
}