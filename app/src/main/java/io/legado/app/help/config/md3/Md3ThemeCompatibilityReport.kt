package io.legado.app.help.config.md3

internal data class Md3ThemeCompatibilityReport(
    val format: Md3ThemePackageFormat,
    val themeName: String,
    val knownFieldCount: Int,
    val presentKnownFieldCount: Int,
    val missingKnownFields: Set<String>,
    val unknownConfigFields: Set<String>,
    val fieldCountsByArea: Map<Md3ThemeFieldArea, Int>,
    val fieldCountsByDisposition: Map<Md3ThemeFieldDisposition, Int>,
    val declaredAssetSlots: Set<String>,
    val unknownAssetSlots: Set<String>,
    val coverAlbumCount: Int,
    val coverImageCount: Int,
    val warnings: List<String>,
)

internal fun Md3ThemePackageInspection.compatibilityReport(
    normalizedFieldNames: Set<String> = presentConfigFields,
): Md3ThemeCompatibilityReport {
    val presentKnownFields = normalizedFieldNames intersect Md3ThemeCoverageRegistry.knownFieldNames
    val presentSpecs = presentKnownFields.mapNotNull(Md3ThemeCoverageRegistry.byName::get)
    val albums = manifest?.coverAlbums.orEmpty().ifEmpty {
        if (format == Md3ThemePackageFormat.LEGACY_APPLICATION_THEME_V1) {
            listOfNotNull(legacyRoot?.let { root ->
                val lightCount = root.getAsJsonObject("dayCover")
                    ?.getAsJsonArray("images")?.size() ?: 0
                val darkCount = root.getAsJsonObject("nightCover")
                    ?.getAsJsonArray("images")?.size() ?: 0
                if (lightCount + darkCount == 0) null else Md3ThemePackageCoverAlbum(
                    ref = "legacy.default",
                    lightImages = List(lightCount) { Md3ThemePackageCoverImage() },
                    darkImages = List(darkCount) { Md3ThemePackageCoverImage() },
                )
            })
        } else {
            emptyList()
        }
    }
    return Md3ThemeCompatibilityReport(
        format = format,
        themeName = name,
        knownFieldCount = Md3ThemeCoverageRegistry.EXPECTED_FIELD_COUNT,
        presentKnownFieldCount = presentKnownFields.size,
        missingKnownFields = Md3ThemeCoverageRegistry.knownFieldNames - presentKnownFields,
        unknownConfigFields = unknownConfigFields,
        fieldCountsByArea = presentSpecs.groupingBy(Md3ThemeFieldSpec::area).eachCount(),
        fieldCountsByDisposition = presentSpecs
            .groupingBy(Md3ThemeFieldSpec::disposition)
            .eachCount(),
        declaredAssetSlots = assetBindings.keys,
        unknownAssetSlots = assetBindings.keys - Md3ThemeAssetSlots.all,
        coverAlbumCount = albums.size,
        coverImageCount = albums.sumOf { it.lightImages.size + it.darkImages.size },
        warnings = warnings,
    )
}
