package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

final class BrandEntityPolicy {
    private static final int RESOLVE_THRESHOLD = 78;

    private BrandEntityPolicy() {
    }

    static boolean isResolved(Models.Identification id) {
        return (id == null || !BrandAnchorPolicy.isLocked(id) || id.brandEntityConfidence < RESOLVE_THRESHOLD || empty(id.brandEntity) || empty(id.brandOfficialDomain)) ? false : true;
    }

    static String resolutionPrompt(Models.Identification id, String details) {
        String brand = BrandAnchorPolicy.anchor(id);
        return "FLIPCHECK v0.73 BRAND ENTITY RESOLUTION. Il marchio \"" + esc(brand) + "\" e' FISICAMENTE VISIBILE sul prodotto e quindi resta un vincolo duro. NON cercare ancora un modello specifico: devi prima determinare quale entita' produttore/manufacturer usa questo marchio su prodotti fisicamente compatibili con l'oggetto osservato. Il semplice fatto che un negozio, concessionario, distributore, sito, dominio, luogo o azienda contenga la stessa parola nel proprio nome NON significa che sia il produttore. Per resolved=true servono TUTTE queste condizioni: (1) una fonte ufficiale o chiaramente manufacturer-owned collega il marchio al produttore; (2) quel produttore realizza una classe di prodotto compatibile con struttura e categoria osservate; (3) identifichi un dominio/catalogo ufficiale del produttore; (4) il marchio e' nel ruolo di marca DEL PRODOTTO, non nel ruolo di venditore del prodotto di terzi. Usa al massimo due query nella singola ricerca web. Se ci sono piu' aziende omonime, confrontale e scegli solo quella coerente con l'oggetto; se non puoi separarle con affidabilita', resolved=false. Rispondi SOLO JSON: {\"resolved\":false,\"visible_brand\":\"\",\"manufacturer_entity\":\"\",\"official_domain\":\"\",\"official_url\":\"\",\"manufacturer_role_confirmed\":false,\"product_class_match\":false,\"compatible_product_classes\":[],\"confidence\":0,\"evidence\":\"\"}.\nVISIBLE_BRAND=" + brand + "\nCATEGORY=" + safe(id == null ? "" : id.category) + "\nCATEGORY_KEY=" + safe(id == null ? "" : id.categoryKey) + "\nSTRUCTURE=" + (id == null ? "[]" : id.spatialSignature) + "\nVISUAL_FACTS=" + (id == null ? "[]" : id.visualFacts) + "\nVISIBLE_LABELS=" + (id != null ? id.visibleLabels : "[]") + "\nFINGERPRINT=" + safe(id != null ? id.visualFingerprint : "") + "\nUSER_DETAILS=" + safe(details);
    }

    static void applyResolution(Models.Identification id, JSONObject p) {
        if (id == null || p == null || !BrandAnchorPolicy.isLocked(id)) {
            return;
        }
        boolean resolved = p.optBoolean("resolved", false);
        boolean manufacturerRole = p.optBoolean("manufacturer_role_confirmed", false);
        boolean classMatch = p.optBoolean("product_class_match", false);
        int confidence = clamp(p.optInt("confidence", 0));
        String returnedBrand = safe(p.optString("visible_brand", ""));
        String entity = safe(p.optString("manufacturer_entity", ""));
        String domain = normalizeDomain(p.optString("official_domain", ""));
        String url = safe(p.optString("official_url", ""));
        if ((!returnedBrand.isEmpty() && !BrandAnchorPolicy.sameBrand(id.brand, returnedBrand)) || !resolved || !manufacturerRole || !classMatch || confidence < RESOLVE_THRESHOLD || entity.isEmpty() || domain.isEmpty() || looksNonManufacturerPlatform(domain)) {
            return;
        }
        id.brandEntity = entity;
        id.brandOfficialDomain = domain;
        id.brandOfficialUrl = url;
        id.brandEntityConfidence = confidence;
        id.brandProductClasses.clear();
        JSONArray a = p.optJSONArray("compatible_product_classes");
        if (a != null) {
            for (int i = 0; i < a.length() && i < 8; i++) {
                String x = safe(a.optString(i, ""));
                if (!x.isEmpty() && !containsIgnoreCase(id.brandProductClasses, x)) {
                    id.brandProductClasses.add(x);
                }
            }
        }
        String ev = safe(p.optString("evidence", ""));
        String verified = "Entita' marca verificata: " + entity + " · dominio ufficiale: " + domain + " · confidenza " + confidence + "%";
        if (!ev.isEmpty()) {
            verified = verified + " · " + ev;
        }
        if (!containsIgnoreCase(id.verifiedEvidence, verified)) {
            id.verifiedEvidence.add(verified);
        }
    }

    static String promptBlock(Models.Identification id) {
        if (!BrandAnchorPolicy.isLocked(id)) {
            return "BRAND_ENTITY_LOCK=inactive";
        }
        if (!isResolved(id)) {
            return "BRAND_ENTITY_LOCK=pending | VISIBLE_BRAND_ROLE=product_manufacturer_only | RULE=same-word retailers/dealers/sites/locations are NOT brand evidence | CANDIDATE_REQUIREMENT=for every candidate state manufacturer_role=true|false, manufacturer_entity and manufacturer_domain; different manufacturer entities are separate namespaces and must not reinforce each other";
        }
        return "BRAND_ENTITY_LOCK=resolved | MANUFACTURER_ENTITY=\"" + esc(id.brandEntity) + "\" | OFFICIAL_DOMAIN=" + id.brandOfficialDomain + " | PRODUCT_CLASSES=" + id.brandProductClasses + " | RULE=first discovery queries must use the manufacturer ecosystem; candidate_facts must state manufacturer_role=true and brand_entity_domain=" + id.brandOfficialDomain;
    }

    static boolean candidateCompatible(Models.Identification id, Models.CandidateScore c) {
        if (!isResolved(id)) {
            return true;
        }
        if (!BrandAnchorPolicy.candidateCompatible(id, c)) {
            return false;
        }
        String role = fact(c, "manufacturer_role");
        if (!"true".equalsIgnoreCase(role)) {
            return false;
        }
        String domain = normalizeDomain(firstNonEmpty(fact(c, "brand_entity_domain"), fact(c, "official_domain"), fact(c, "manufacturer_domain"), fact(c, "source_domain")));
        if (!domain.isEmpty() && sameDomain(id.brandOfficialDomain, domain)) {
            return true;
        }
        String entity = firstNonEmpty(fact(c, "manufacturer_entity"), fact(c, "brand_entity"));
        return !entity.isEmpty() && sameEntity(id.brandEntity, entity);
    }

    static void filterSources(Models.Identification id) {
        if (!isResolved(id) || id.sources.isEmpty()) {
            return;
        }
        List<Models.Source> keep = new ArrayList<>();
        String model = canon(id.model);
        String brand = canon(id.brand);
        for (Models.Source s : id.sources) {
            if (s != null) {
                String d = normalizeDomain(s.domain());
                if (sameDomain(id.brandOfficialDomain, d)) {
                    keep.add(s);
                } else if (!model.isEmpty()) {
                    String hay = canon(s.title + " " + s.snippet + " " + s.url);
                    if (!brand.isEmpty() && hay.contains(brand) && hay.contains(model)) {
                        keep.add(s);
                    }
                }
            }
        }
        id.sources.clear();
        id.sources.addAll(keep);
    }

    static String normalizeDomain(String raw) {
        String x = safe(raw).toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        int slash = x.indexOf(47);
        if (slash >= 0) {
            x = x.substring(0, slash);
        }
        int colon = x.indexOf(58);
        if (colon >= 0) {
            x = x.substring(0, colon);
        }
        return x.replaceAll("[^a-z0-9.-]", "");
    }

    private static boolean sameDomain(String a, String b) {
        String x = normalizeDomain(a);
        String y = normalizeDomain(b);
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        return x.equals(y) || x.endsWith(new StringBuilder().append(".").append(y).toString()) || y.endsWith(new StringBuilder().append(".").append(x).toString());
    }

    private static boolean sameEntity(String a, String b) {
        String x = canonEntity(a);
        String y = canonEntity(b);
        return !x.isEmpty() && x.equals(y);
    }

    private static String canonEntity(String s) {
        String x = safe(s).toLowerCase(Locale.ROOT).replace('&', ' ').replaceAll("[^a-z0-9]+", " ").replaceAll("\\b(incorporated|inc|llc|ltd|limited|corporation|corp|company|gmbh|srl|spa|plc|sa|group|holding|holdings)\\b", " ").replaceAll("\\s+", " ").trim();
        return x.replace(" ", "");
    }

    private static boolean looksNonManufacturerPlatform(String d) {
        String x = normalizeDomain(d);
        return x.contains("amazon.") || x.contains("ebay.") || x.contains("aliexpress.") || x.contains("walmart.") || x.contains("etsy.") || x.contains("facebook.") || x.contains("instagram.") || x.contains("pinterest.") || x.contains("reddit.") || x.contains("youtube.") || x.contains("manualslib.") || x.contains("manualzz.");
    }

    private static String fact(Models.CandidateScore c, String key) {
        if (c == null) {
            return "";
        }
        String p = key.toLowerCase(Locale.ROOT) + "=";
        for (String raw : c.candidateFacts) {
            String s = safe(raw);
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) {
                return s.substring(s.indexOf(61) + 1).trim();
            }
        }
        return "";
    }

    private static String firstNonEmpty(String... xs) {
        if (xs == null) {
            return "";
        }
        for (String x : xs) {
            if (!safe(x).isEmpty()) {
                return safe(x);
            }
        }
        return "";
    }

    private static boolean containsIgnoreCase(List<String> xs, String value) {
        if (xs == null) {
            return false;
        }
        for (String x : xs) {
            if (safe(x).equalsIgnoreCase(safe(value))) {
                return true;
            }
        }
        return false;
    }

    private static String canon(String s) {
        return safe(s).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static int clamp(int x) {
        return Math.max(0, Math.min(100, x));
    }

    private static boolean empty(String s) {
        return safe(s).isEmpty();
    }

    private static String esc(String s) {
        return safe(s).replace("\"", "");
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
