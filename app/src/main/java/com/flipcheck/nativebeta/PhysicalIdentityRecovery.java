package com.flipcheck.nativebeta;

import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded second visual pass for rich photos whose first schema omitted identity fields. */
final class PhysicalIdentityRecovery {
    private static final double MAX_TOTAL_COST_USD = 0.0200d;
    private static final double RESERVED_VISUAL_PASS_USD = 0.0035d;

    private PhysicalIdentityRecovery() {
    }

    static boolean eligible(Models.Identification id, Models.Usage usage) {
        if (id == null || usage == null || usage.requests != 1 || usage.webCalls != 1
                || usage.costUsd + RESERVED_VISUAL_PASS_USD > MAX_TOTAL_COST_USD
                || id.photoIdentityOverlayOrWatermark || id.photoIdentityComplete) {
            return false;
        }
        if (CollectibleCardIdentityPolicy.isCard(id)) {
            if (CardPhotoTupleClosure.shouldReserveCatalogPass(id)) return false;
            return hasFront(id) && id.visibleLabels.size() >= 4
                    && (cardFactCount(id) >= 2 || pokemonCardLanguage(id));
        }
        if (SealedProductIdentityPolicy.isSealedRetailProduct(id)) {
            return id.visibleLabels.size() >= 3 && sealedFactCount(id) >= 3;
        }
        return false;
    }

    static String prompt(Models.Identification id) {
        return "FIRST_CATEGORY=" + safe(id.category)
                + "\nFIRST_LABELS=" + clip(id.visibleLabels.toString(), 1000)
                + "\nFIRST_PHYSICAL_FACTS=" + clip(id.visualFacts.toString(), 1200)
                + "\nFIRST_IDENTITY_FIELDS=" + clip(id.photoIdentityFields.toString(), 1000)
                + "\nPHOTO_VIEWS=" + clip(id.photoViews.toString(), 300);
    }

    static boolean apply(Models.Identification id, OpenAiClient.Response response) {
        if (id == null || response == null || !response.complete
                || !safe(response.parseError).isEmpty() || response.payload == null) return false;
        JSONObject p = response.payload;
        if (!p.optBoolean("applicable", false)
                || !p.optBoolean("same_foreground_object", false)
                || !p.optBoolean("physical_binding", false)
                || p.optBoolean("overlay_or_watermark", true)
                || !safe(p.optString("contradiction", "")).isEmpty()
                || p.optInt("confidence", 0) < 92) return false;
        JSONArray fields = p.optJSONArray("fields");
        if (fields == null || fields.length() < 3) return false;
        for (int i = 0; i < fields.length(); i++) addOnce(id.photoIdentityFields,
                safe(fields.optString(i, "")));
        JSONArray labels = p.optJSONArray("observed_labels");
        if (labels != null) for (int i = 0; i < labels.length(); i++)
            addOnce(id.visibleLabels, safe(labels.optString(i, "")));
        String name = safe(p.optString("canonical_name", ""));
        if (!name.isEmpty()) id.photoIdentityName = name;
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityOverlayOrWatermark = false;
        id.photoIdentityKind = "composite_markings";
        id.photoIdentityConfidence = Math.max(id.photoIdentityConfidence,
                p.optInt("confidence", 0));
        id.photoIdentityComplete = p.optBoolean("complete", false);
        String category = safe(p.optString("category_key", ""));
        if ("loose_card".equals(category)) {
            id.category = pokemonCardLanguage(id) ? "Pokémon trading card" : "sports collectible card";
            id.categoryKey = pokemonCardLanguage(id) ? "pokemon_tcg_card" : "sports_collectible_card";
        } else if ("sealed_box".equals(category)) {
            id.category = "sealed trading-card product";
            id.categoryKey = "sealed_products";
        }
        return true;
    }

    private static int cardFactCount(Models.Identification id) {
        int count = 0;
        for (String raw : id.visualFacts) {
            String key = key(raw);
            if (key.equals("subject") || key.equals("player") || key.equals("card_number")
                    || key.equals("collector_number") || key.equals("parallel")
                    || key.equals("rookie_card") || key.equals("holo")
                    || key.equals("physical_printing") || key.equals("first_edition_stamp")) count++;
        }
        return count;
    }

    private static int sealedFactCount(Models.Identification id) {
        int count = 0;
        for (String raw : id.visualFacts) {
            String key = key(raw);
            if (key.equals("season") || key.equals("sport") || key.equals("sealed_format")
                    || key.equals("format") || key.equals("autograph_callout")
                    || key.equals("configuration") || key.equals("featured_players")) count++;
        }
        return count;
    }

    private static boolean pokemonCardLanguage(Models.Identification id) {
        String x = (safe(id.category) + " " + id.visibleLabels + " " + id.visualFacts
                + " " + id.photoIdentityFields).toLowerCase(Locale.ROOT);
        return x.contains("pokemon") || x.contains("pokémon")
                || x.contains("basic pokémon") || x.contains("first_edition_stamp");
    }

    private static boolean hasFront(Models.Identification id) {
        if (id.photoViews.isEmpty()) return true;
        for (String raw : id.photoViews) {
            String x = safe(raw).toLowerCase(Locale.ROOT);
            if (x.contains("front") || x.contains("fronte")) return true;
        }
        return false;
    }

    private static String key(String raw) {
        String x = safe(raw);
        int split = x.indexOf('=');
        if (split < 1) split = x.indexOf(':');
        return split < 1 ? "" : x.substring(0, split).trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
    }

    private static void addOnce(java.util.List<String> out, String value) {
        if (value.isEmpty()) return;
        String c = canon(value);
        for (String old : out) if (canon(old).equals(c)) return;
        out.add(value);
    }

    private static String canon(String value) {
        return safe(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }

    private static String clip(String value, int max) {
        String x = safe(value);
        return x.length() <= max ? x : x.substring(0, max);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
