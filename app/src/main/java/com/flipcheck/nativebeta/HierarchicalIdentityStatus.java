package com.flipcheck.nativebeta;

/** Public decision vocabulary; each level is evaluated independently. */
enum HierarchicalIdentityStatus {
    CATEGORY_IDENTIFIED,
    FAMILY_IDENTIFIED,
    MAIN_IDENTITY_PROBABLE,
    MAIN_IDENTITY_CONFIRMED,
    VARIANT_PROBABLE,
    VARIANT_CONFIRMED,
    MARKET_UNAVAILABLE,
    CONFLICTED,
    INSUFFICIENT_EVIDENCE
}
