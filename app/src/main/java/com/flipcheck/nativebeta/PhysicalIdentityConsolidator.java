package com.flipcheck.nativebeta;

import java.util.Locale;

/**
 * Repairs category/container disagreements using only structured physical
 * observations from the supplied photos.  It never imports web-only facts.
 */
final class PhysicalIdentityConsolidator {
    private PhysicalIdentityConsolidator() {
    }

    static void apply(Models.Identification id) {
        if (id == null) return;
        boolean looseCard = strongLooseCardEvidence(id) && !completeSealedTuple(id);
        if (looseCard) {
            boolean pokemon = contains(id, "pokemon") || contains(id, "pokémon")
                    || hasFact(id, "first_edition_stamp")
                    || hasFact(id, "illustration_frame_drop_shadow")
                    || hasFact(id, "copyright_layout");
            id.categoryKey = pokemon ? "pokemon_tcg_card" : "sports_collectible_card";
            id.category = pokemon ? "Pokémon trading card" : "sports collectible card";
        } else if (completeSealedTuple(id)) {
            id.categoryKey = "sealed_products";
            if (safe(id.category).isEmpty() || !canon(id.category).contains("SEALED")) {
                id.category = "sealed trading card product";
            }
        }

        if (!looseCard) return;
        for (String raw : id.visualFacts) {
            String key = fieldKey(raw);
            if (cardIdentityKey(key)) addOnce(id.photoIdentityFields, raw);
        }
        if (!safe(id.brand).isEmpty()) {
            addOnce(id.photoIdentityFields, "manufacturer=" + id.brand);
        }
        if (id.photoIdentityFields.size() >= 3 && !id.photoIdentityOverlayOrWatermark) {
            id.photoIdentityPhysicalBinding = true;
            id.photoIdentityKind = "composite_markings";
            id.photoIdentityConfidence = Math.max(id.photoIdentityConfidence, 92);
        }
    }

    private static boolean strongLooseCardEvidence(Models.Identification id) {
        boolean pokemonLayout = hasFact(id, "first_edition_stamp")
                || hasFact(id, "illustration_frame_drop_shadow")
                || hasFact(id, "nintendo_copyright_99")
                || hasFact(id, "copyright_layout")
                || hasFact(id, "physical_printing");
        boolean sportsTuple = hasFact(id, "player") && hasFact(id, "team")
                && hasFact(id, "card_number")
                && (hasFact(id, "parallel") || hasFact(id, "rookie_card"));
        String all = canon(safe(id.category) + " " + safe(id.categoryKey) + " "
                + safe(id.photoIdentityName) + " " + id.visibleLabels + " " + id.visualFacts);
        boolean cardLanguage = all.contains("TRADING CARD") || all.contains("COLLECTIBLE CARD")
                || all.contains("BASIC POKEMON") || all.contains("PANINI")
                || all.contains("PRIZM") || all.contains("CARD NUMBER");
        return pokemonLayout || sportsTuple || (cardLanguage && hasFact(id, "card_number"));
    }

    private static boolean completeSealedTuple(Models.Identification id) {
        boolean manufacturer = hasIdentityField(id, "manufacturer", "brand", "publisher");
        boolean season = hasIdentityField(id, "season", "season/year", "season_year", "year");
        boolean line = hasIdentityField(id, "product_line", "product line", "collection");
        boolean box = hasIdentityField(id, "format", "box_format", "product_format",
                "configuration", "pack_configuration", "box_configuration");
        return manufacturer && season && line && box;
    }

    private static boolean cardIdentityKey(String key) {
        return key.equals("manufacturer") || key.equals("brand") || key.equals("publisher")
                || key.equals("set") || key.equals("collection") || key.equals("subject")
                || key.equals("player") || key.equals("team") || key.equals("season")
                || key.equals("card_number") || key.equals("collector_number")
                || key.equals("parallel") || key.equals("rookie_card") || key.equals("rookie")
                || key.equals("holo") || key.equals("holofoil") || key.equals("holo_or_foil")
                || key.equals("finish") || key.equals("first_edition_stamp")
                || key.equals("first_edition_stamp_area_clear")
                || key.equals("first_edition_stamp_position")
                || key.equals("illustration_frame_drop_shadow")
                || key.equals("nintendo_copyright_99") || key.equals("copyright_layout")
                || key.equals("physical_printing");
    }

    private static boolean hasFact(Models.Identification id, String wanted) {
        for (String raw : id.visualFacts) if (fieldKey(raw).equals(wanted)) return true;
        for (String raw : id.photoIdentityFields) if (fieldKey(raw).equals(wanted)) return true;
        return false;
    }

    private static boolean hasIdentityField(Models.Identification id, String... wanted) {
        for (String raw : id.photoIdentityFields) {
            String key = fieldKey(raw);
            for (String value : wanted) {
                if (key.equals(value.toLowerCase(Locale.ROOT).replace(' ', '_'))) return true;
            }
        }
        return false;
    }

    private static String fieldKey(String raw) {
        String x = safe(raw);
        int split = x.indexOf('=');
        if (split < 1) split = x.indexOf(':');
        return split < 1 ? "" : x.substring(0, split).trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static boolean contains(Models.Identification id, String token) {
        String all = (safe(id.category) + " " + safe(id.categoryKey) + " "
                + safe(id.photoIdentityName) + " " + id.visibleLabels + " "
                + id.visualFacts + " " + id.photoIdentityFields).toLowerCase(Locale.ROOT);
        return all.contains(token.toLowerCase(Locale.ROOT));
    }

    private static void addOnce(java.util.List<String> list, String value) {
        String c = canon(value);
        if (c.isEmpty()) return;
        for (String old : list) if (canon(old).equals(c)) return;
        list.add(value);
    }

    private static String canon(String value) {
        return safe(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
