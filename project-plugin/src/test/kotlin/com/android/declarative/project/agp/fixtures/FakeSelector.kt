package com.android.declarative.project.agp.fixtures

import com.android.build.api.variant.VariantSelector
import java.util.regex.Pattern

class FakeSelector: VariantSelector {

    var selectorName: String? = null
    override fun all(): VariantSelector {
        selectorName = "all"
        return this
    }

    override fun withBuildType(buildType: String): VariantSelector {
        TODO("Not yet implemented")
    }

    override fun withFlavor(flavorToDimension: Pair<String, String>): VariantSelector {
        TODO("Not yet implemented")
    }

    override fun withFlavor(dimension: String, flavorName: String): VariantSelector {
        TODO("Not yet implemented")
    }

    override fun withName(pattern: Pattern): VariantSelector {
        TODO("Not yet implemented")
    }

    override fun withName(name: String): VariantSelector {
        selectorName = name
        return this
    }

    fun matches(value: String): Boolean {
        return selectorName == value
    }
}