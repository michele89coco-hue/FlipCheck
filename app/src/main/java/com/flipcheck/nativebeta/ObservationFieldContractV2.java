package com.flipcheck.nativebeta;

/** Shared transport vocabulary. Unclassified observations keep their raw text and role. */
final class ObservationFieldContractV2 {
    private ObservationFieldContractV2() {}
    static final String[] FIELDS = {
        "gradingCompany", "gradingGrade", "gradingCondition", "gradingSubgrades", "gradingCertification",
        "slabSetName", "slabCardNumber", "slabYear", "slabLanguage", "slabEdition", "slabFinish",
        "printedLabel", "visualSymbol", "physicalFeature", "controlLabel",
        "brand", "manufacturer", "game", "productLine", "setName", "subSeries",
        "productType", "productReleaseYear", "copyrightYear", "statisticsSeason",
        "cardName", "athlete", "physicalCardNumber", "collectorNumber", "printedTotal",
        "physicalSerial", "jerseyNumber", "graphicNumber", "edition", "firstEditionMark",
        "finish", "language", "evolutionStage", "cardRole", "rarity", "hp", "attacks",
        "artist", "team", "sport", "playerMeasurements", "configuration", "commercialFormat", "sku", "barcode",
        "productCode", "model", "compatibleDevice", "compatibleBrand", "controlLayout",
        "shortcutButtons", "navigationLayout", "numericKeypad", "voiceControl",
        "layoutSignature", "material", "color", "dimensions", "condition"
    };
}
