package com.flipcheck.nativebeta;

import java.text.Normalizer;
import java.util.Locale;
import org.json.JSONObject;

/** Physical edition/printing/finish axes. Web data may corroborate, never overwrite them. */
final class TcgPhysicalEditionPolicy {
    static final String FIRST_EDITION = "FIRST_EDITION";
    private TcgPhysicalEditionPolicy() {}

    static void normalize(Models.Identification id, String stage) {
        if (id == null) return;
        NormalizedPhotoIdentity n = PhotographicFactNormalizer.require(id);
        NormalizedPhotoIdentity.Fact mark = strongestMark(n);
        NormalizedPhotoIdentity.Fact edition = n.bestFact(CanonicalFieldKey.EDITION);
        boolean first = confirmsFirst(mark) || confirmsFirst(edition);
        if (first) {
            transition(id, "edition", id.edition, FIRST_EDITION, "physical_first_edition_mark", source(mark, edition), confidence(mark, edition), stage);
            id.edition = FIRST_EDITION;
            id.firstEditionMark = "PRESENT";
            id.firstEditionSource = source(mark, edition);
            id.exactEditionStatus = "CONFIRMED";
        } else {
            id.edition = n.edition();
            id.firstEditionMark = hasReliableAbsence(n) ? "ABSENT" : "NOT_OBSERVED";
            id.firstEditionSource = source(mark, edition);
            id.exactEditionStatus = id.edition.isEmpty() ? "TO_VERIFY" : "CONFIRMED";
        }
        String shadow = n.best(CanonicalFieldKey.SHADOW_STATUS);
        id.shadowStatus = shadow.isEmpty() ? "TO_VERIFY" : normalizeShadow(shadow);
        String holo = first(n.best(CanonicalFieldKey.HOLO_STATUS), n.best(CanonicalFieldKey.REVERSE_HOLO_STATUS), n.best(CanonicalFieldKey.FINISH));
        id.holoStatus = holo.isEmpty() ? "TO_VERIFY" : normalizeHolo(holo);
        if (id.finish.isEmpty()) id.finish = normalizedFinish(holo);
        id.finishStatus = id.finish.isEmpty() ? "TO_VERIFY" : "CONFIRMED";
    }

    static boolean needsFocusedPass(Models.Identification id, Models.Usage usage) {
        if (id == null || !TcgFrontIdentityPolicy.isTcg(id) || !TcgFrontIdentityPolicy.frontComplete(id)) return false;
        normalize(id, "focused_pass_gate");
        if ("PRESENT".equals(id.firstEditionMark) || "CONFIRMED".equals(id.exactEditionStatus)) return false;
        return usage == null || (usage.visionCalls < 2 && usage.costUsd + 0.0045d <= 0.025d);
    }

    static boolean mergeFocusedResult(Models.Identification id, OpenAiClient.Response response) {
        if (id == null || response == null || !response.complete || response.payload == null || !response.parseError.isEmpty()) return false;
        JSONObject p = response.payload;
        if (!p.optBoolean("same_card", false) || !p.optBoolean("front_sufficient", false)) return false;
        int confidence = p.optInt("confidence", 0);
        String location = clean(p.optString("location", "edition candidate region"));
        String crop = clean(p.optString("crop_region", location));
        boolean present = p.optBoolean("first_edition_present", false);
        String observed = clean(p.optString("observed_text", ""));
        if (present && confidence >= 72) {
            EvidenceLedger.addPhotoFact(id, "first_edition_mark", "PRESENT", "logo_geometry_focused_vision", confidence,
                    p.optInt("image_index", 0), "front", crop, "edition_mark");
            EvidenceLedger.addPhotoFact(id, "edition", FIRST_EDITION, "logo_geometry_focused_vision", confidence,
                    p.optInt("image_index", 0), "front", location, "edition");
            if (!observed.isEmpty()) EvidenceLedger.addPhotoFact(id, "visual_symbol", observed, "focused_visual_transcription", confidence,
                    p.optInt("image_index", 0), "front", location, "edition_symbol");
        } else if (!present && confidence >= 85) {
            EvidenceLedger.addPhotoFact(id, "first_edition_mark", "ABSENT", "focused_visual_absence", confidence,
                    p.optInt("image_index", 0), "front", crop, "edition_mark");
        }
        addOptional(id, "shadow_status", p.optString("shadow_status", ""), confidence, location, "printing_layout");
        addOptional(id, "finish", p.optString("finish", ""), confidence, location, "finish");
        PhotographicFactNormalizer.normalize(id, "tcg_focused_edition_merge");
        normalize(id, "tcg_focused_edition_merge");
        return present && "PRESENT".equals(id.firstEditionMark);
    }

    static boolean webEditionConflicts(Models.Identification id, String candidateEdition) {
        if (id == null || !"PRESENT".equals(id.firstEditionMark)) return false;
        String c = canon(candidateEdition);
        return c.contains("UNLIMITED") || (!c.isEmpty() && !c.contains("FIRST EDITION") && !c.contains("1ST EDITION"));
    }

    private static void addOptional(Models.Identification id, String key, String value, int confidence, String location, String role) {
        String v = clean(value); if (v.isEmpty() || v.equalsIgnoreCase("unknown") || confidence < 65) return;
        EvidenceLedger.addPhotoFact(id, key, v, "focused_visual_observation", confidence, 0, "front", location, role);
    }
    private static NormalizedPhotoIdentity.Fact strongestMark(NormalizedPhotoIdentity n) { return n.bestFact(CanonicalFieldKey.FIRST_EDITION_MARK); }
    private static boolean confirmsFirst(NormalizedPhotoIdentity.Fact f) { return f != null && f.confidence >= 70 && isFirst(f.value); }
    private static boolean isFirst(String value) { String c=canon(value); return c.equals("PRESENT")||c.contains("1ST EDITION")||c.contains("FIRST EDITION")||c.contains("EDITION 1")||c.contains("1 EDIZIONE"); }
    private static boolean hasReliableAbsence(NormalizedPhotoIdentity n) { NormalizedPhotoIdentity.Fact f=strongestMark(n); return f!=null&&f.confidence>=85&&canon(f.value).equals("ABSENT"); }
    private static String normalizeShadow(String x) { String c=canon(x); return c.contains("SHADOWLESS")||c.equals("ABSENT")?"SHADOWLESS":c.contains("SHADOW")?"SHADOWED":"TO_VERIFY"; }
    private static String normalizeHolo(String x) { String c=canon(x); return c.contains("REVERSE")?"REVERSE_HOLO":c.contains("HOLO")?"HOLO":c.contains("NON HOLO")||c.equals("NONE")?"NON_HOLO":"TO_VERIFY"; }
    private static String normalizedFinish(String x) { String h=normalizeHolo(x); return "TO_VERIFY".equals(h)?"":h; }
    private static String source(NormalizedPhotoIdentity.Fact... fs) { for (NormalizedPhotoIdentity.Fact f:fs) if(f!=null) return f.origin+":"+f.evidenceType+"@"+f.location; return ""; }
    private static int confidence(NormalizedPhotoIdentity.Fact... fs) { int q=0; for(NormalizedPhotoIdentity.Fact f:fs) if(f!=null) q=Math.max(q,f.confidence); return q; }
    private static String first(String... xs){for(String x:xs)if(!clean(x).isEmpty())return clean(x);return "";}
    private static void transition(Models.Identification id,String field,String before,String after,String reason,String source,int confidence,String stage){String row="field="+field+"; previousValue="+clean(before)+"; newValue="+clean(after)+"; reason="+reason+"; source="+source+"; confidence="+confidence+"; pipelineStage="+stage;if(!id.fieldTransitions.contains(row))id.fieldTransitions.add(row);}
    private static String canon(String x){return Normalizer.normalize(clean(x),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
