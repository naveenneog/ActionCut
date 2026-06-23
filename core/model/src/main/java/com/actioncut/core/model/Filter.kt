package com.actioncut.core.model

import kotlinx.serialization.Serializable

/**
 * A LUT-based color filter (CapCut-style "filters" tab). A filter references a 3D LUT
 * asset (e.g. a `.cube` file packaged in assets) plus an intensity factor.
 *
 * @property id Stable filter id (also the asset key).
 * @property name Display name.
 * @property category Grouping shown in the UI.
 * @property lutAssetPath Path to the LUT asset, or null for built-in/no-op.
 * @property intensity 0f..1f blend strength.
 */
@Serializable
data class Filter(
    val id: String,
    val name: String,
    val category: FilterCategory,
    val lutAssetPath: String? = null,
    val intensity: Float = 1f,
)

@Serializable
enum class FilterCategory {
    NONE,
    PORTRAIT,
    FOOD,
    LANDSCAPE,
    VINTAGE,
    MONO,
    CINEMATIC,
}

/**
 * Built-in catalogue of sample filters so the UI has real data to render without assets.
 * Replace [Filter.lutAssetPath] with packaged `.cube` files to enable real LUT rendering.
 */
object Filters {
    val None = Filter("none", "Original", FilterCategory.NONE)

    val catalogue: List<Filter> = listOf(
        None,
        Filter("portrait_soft", "Soft", FilterCategory.PORTRAIT, "luts/portrait_soft.cube"),
        Filter("portrait_glow", "Glow", FilterCategory.PORTRAIT, "luts/portrait_glow.cube"),
        Filter("food_fresh", "Fresh", FilterCategory.FOOD, "luts/food_fresh.cube"),
        Filter("landscape_vivid", "Vivid", FilterCategory.LANDSCAPE, "luts/landscape_vivid.cube"),
        Filter("vintage_70s", "70s", FilterCategory.VINTAGE, "luts/vintage_70s.cube"),
        Filter("vintage_fade", "Fade", FilterCategory.VINTAGE, "luts/vintage_fade.cube"),
        Filter("mono_noir", "Noir", FilterCategory.MONO, "luts/mono_noir.cube"),
        Filter("cinematic_teal", "Teal & Orange", FilterCategory.CINEMATIC, "luts/cinematic_teal.cube"),
        Filter("cinematic_moody", "Moody", FilterCategory.CINEMATIC, "luts/cinematic_moody.cube"),
    )
}
