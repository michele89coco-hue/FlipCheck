package com.flipcheck.nativebeta;

import androidx.core.os.EnvironmentCompat;
import com.flipcheck.nativebeta.Models;
import com.flipcheck.nativebeta.OpenAiClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

final class GeometryConsensusEngine {
    private static final int ACCEPT = 78;

    private GeometryConsensusEngine() {
    }

    static void run(Models.Identification id, Models.LocalScan local, List<String> images, String details, OpenAiClient client, Models.Usage usage) throws Exception {
        if (id == null || images == null || images.isEmpty() || client == null
                || !UniversalConsistencyGate.visionBudgetAvailable(usage)
                || hasStrongLocalIdentifier(id, local)) {
            return;
        }
        String prompt = "FLIPCHECK v0.74 FOREGROUND GEOMETRY CONSENSUS. NIENTE WEB. NON identificare marca, famiglia o modello. Sei un secondo osservatore il cui compito e' tentare di SMENTIRE la prima descrizione geometrica. Segmenta mentalmente il singolo oggetto fisico in primo piano e ignora riflessi di persone/stanze, mobili, pavimento, pareti, confezioni e oggetti vicini. Conta e descrivi solo componenti realmente attaccati all'oggetto. Per maniglie/barre/pannelli/porte/display/controlli usa l'orientamento rispetto agli assi dell'immagine: horizontal=sinistra-destra, vertical=alto-basso. Una forma riflessa sul vetro NON e' una maniglia o componente. Se non puoi verificare un elemento, omettilo. Scegli una categoria funzionale concreta solo se almeno due componenti fisici definitori la sostengono; altrimenti mantieni una categoria generica. Confronta esplicitamente CURRENT_SPATIAL e CURRENT_FINGERPRINT con la foto. current_geometry_valid=false se contengono una caratteristica strutturale importante non realmente presente o con orientamento errato. Rispondi SOLO JSON: {\"current_geometry_valid\":true,\"correction_confidence\":0,\"category\":\"\",\"category_key\":\"\",\"category_confidence\":0,\"defining_components\":[],\"spatial_signature\":[],\"visual_fingerprint\":\"\",\"candidate_hints\":[],\"reason\":\"\"}. CURRENT_CATEGORY=" + safe(id.category) + " | CURRENT_CATEGORY_KEY=" + safe(id.categoryKey) + " | CURRENT_SPATIAL=" + id.spatialSignature + " | CURRENT_FACTS=" + id.visualFacts + " | CURRENT_FINGERPRINT=" + safe(id.visualFingerprint) + " | CURRENT_HINTS=" + id.visionCandidates + " | LOCAL_OCR_UNTRUSTED_FOR_GEOMETRY=" + clip(local == null ? "" : local.joinedText(), 1000) + " | USER_HINT_UNTRUSTED=" + safe(details);
        OpenAiClient.Response r = client.vision(images, prompt);
        if (usage != null) {
            usage.add(r.usage);
        }
        apply(id, r.payload);
    }

    static void apply(Models.Identification id, JSONObject p) {
        if (id == null || p == null) {
            return;
        }
        int correction = clamp(p.optInt("correction_confidence", 0));
        int catConf = clamp(p.optInt("category_confidence", 0));
        if (correction < ACCEPT) {
            addOnce(id.observedEvidence, "geometry_consensus_v074=insufficient confidence=" + correction);
            return;
        }
        boolean valid = p.optBoolean("current_geometry_valid", true);
        List<String> spatial = strings(p.optJSONArray("spatial_signature"));
        String fingerprint = safe(p.optString("visual_fingerprint", ""));
        String category = safe(p.optString("category", ""));
        String categoryKey = safe(p.optString("category_key", "")).toLowerCase(Locale.ROOT);
        if (!valid && !spatial.isEmpty()) {
            id.spatialSignature.clear();
            id.spatialSignature.addAll(spatial);
            if (!fingerprint.isEmpty()) {
                id.visualFingerprint = fingerprint;
            }
        } else if (valid) {
            if (!fingerprint.isEmpty()) {
                id.visualFingerprint = fingerprint;
            }
            if (!spatial.isEmpty()) {
                id.spatialSignature.clear();
                id.spatialSignature.addAll(spatial);
            }
        }
        if (catConf >= 82 && !category.isEmpty() && (generic(id.category, id.categoryKey) || catConf > id.categoryConfidence + 5)) {
            id.category = category;
            if (!categoryKey.isEmpty()) {
                id.categoryKey = categoryKey;
            }
            id.categoryConfidence = catConf;
        }
        List<String> hints = strings(p.optJSONArray("candidate_hints"));
        if (!hints.isEmpty()) {
            id.visionCandidates.clear();
            id.visionCandidates.addAll(hints);
        }
        List<String> components = strings(p.optJSONArray("defining_components"));
        for (String c : components) {
            addOnce(id.visualFacts, "geometry_component=" + c);
            spatial = spatial;
        }
        CategoryFactPolicy.apply(id);
        addOnce(id.observedEvidence, "geometry_consensus_v074=true valid_first=" + valid + " correction_confidence=" + correction + " category_confidence=" + catConf);
        String reason = safe(p.optString("reason", ""));
        if (!reason.isEmpty()) {
            addOnce(id.observedEvidence, "geometry_consensus_reason=" + reason);
        }
    }

    private static boolean hasStrongLocalIdentifier(Models.Identification id, Models.LocalScan local) {
        if (local == null) {
            return false;
        }
        for (Models.Identifier x : local.identifiers) {
            if (x != null && EvidenceReliabilityGate.isHardIdentifier(local, x)) {
                return true;
            }
        }
        return false;
    }

    private static boolean generic(String category, String key) {
        String k = safe(key).toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        String c = safe(category).toLowerCase(Locale.ROOT);
        return k.isEmpty() || k.equals("other") || k.equals("appliance") || k.equals("electronics") || k.equals("consumer_object") || k.equals("household_appliance") || c.isEmpty() || c.equals("object") || c.equals("device") || c.equals("appliance") || c.contains("household appliance") || c.contains("consumer object") || c.contains(EnvironmentCompat.MEDIA_UNKNOWN);
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) {
            return out;
        }
        for (int i = 0; i < a.length() && i < 12; i++) {
            String x = safe(a.optString(i, ""));
            if (!x.isEmpty()) {
                addOnce(out, x);
            }
        }
        return out;
    }

    private static void addOnce(List<String> xs, String x) {
        if (xs == null || x == null || x.trim().isEmpty() || xs.contains(x.trim())) {
            return;
        }
        xs.add(x.trim());
    }

    private static String clip(String s, int n) {
        String s2 = safe(s);
        return s2.length() <= n ? s2 : s2.substring(0, n);
    }

    private static int clamp(int x) {
        return Math.max(0, Math.min(100, x));
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
