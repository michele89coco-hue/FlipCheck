package com.flipcheck.nativebeta;

import androidx.core.os.EnvironmentCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Bounded evidence-first pipeline introduced after the v0.76 device tests.
 * One scan performs at most one structured Vision observation and one unified
 * web discovery/verification pass. No retry may turn noisy OCR into a query.
 */
final class IdentificationPipelineV077 {
    private IdentificationPipelineV077() {
    }

    static boolean enabled() {
        return true;
    }

    static Models.Identification identify(Models.LocalScan local, List<String> images, String details,
                                          OpenAiClient client, Models.Usage usage) throws Exception {
        Models.Identification id = new Models.Identification();
        id.localScan = local;
        if (images == null || images.isEmpty()) {
            stopForPhoto(id, "Aggiungi una foto chiara dell'oggetto intero",
                    "Non è disponibile una vista da analizzare.", "Nessuna immagine disponibile.");
            return id;
        }
        if (!UniversalConsistencyGate.visionBudgetAvailable(usage)) {
            stopForPhoto(id, fallbackPhoto(id),
                    "Il budget di una sola analisi Vision per scansione è già esaurito.",
                    "BUDGET v0.77: nessuna Vision aggiuntiva automatica.");
            return id;
        }

        OpenAiClient.Response observed = client.observe(new ArrayList<>(images),
                buildObservationPrompt(local, details));
        if (usage != null) {
            usage.add(observed.usage);
        }
        if (!observationUsable(observed)) {
            stopForPhoto(id, "Aggiungi una foto più ravvicinata e nitida dell'oggetto, includendo logo ed eventuale etichetta",
                    "La sola osservazione Vision ammessa non ha restituito una struttura completa; nessuna ricerca è stata avviata da OCR grezzo.",
                    "FAIL-CLOSED v0.77: osservazione strutturata incompleta"
                            + reasonSuffix(observed == null ? "" : observed.incompleteReason,
                            observed == null ? "" : observed.parseError));
            return id;
        }

        parseObservation(id, observed.payload);
        collectSoftOcr(id, local);
        ObservationSanitizer.apply(id);
        CategoryFactPolicy.apply(id);
        BrandBlindPolicy.sanitizeBrandEvidence(id, local);
        Models.Identifier primary = selectPrimary(local, id);
        if (primary != null) {
            id.primaryIdentifier = clean(primary.value);
            id.identifierVariants.addAll(LocalVisionEngine.normalizeIdentifierVariants(primary.value));
        }
        HardConstraintEngine.apply(id, primary);

        if (!hasResolutionEvidence(id, primary)) {
            stopForPhoto(id, fallbackPhoto(id),
                    "La foto non contiene ancora abbastanza segnali strutturati per una ricerca affidabile; l'OCR non etichettato è stato escluso.",
                    "FAIL-CLOSED v0.77: nessuna query costruita da testo OCR non verificato.");
            IdentificationEngine.finalizeOutput(id, primary);
            return id;
        }
        if (!UniversalConsistencyGate.discoveryBudgetAvailable(usage)) {
            stopForPhoto(id, fallbackPhoto(id),
                    "Il limite di una Web Search per identificazione è già esaurito.",
                    "BUDGET v0.77: nessuna ricerca aggiuntiva automatica.");
            IdentificationEngine.finalizeOutput(id, primary);
            return id;
        }

        id.searchQuery = searchSeed(id, primary);
        OpenAiClient.Response resolved = client.webStage("resolve", buildResolvePrompt(id, primary, details));
        IdentificationEngine.collectStage(id, usage, resolved, "one-pass-resolve-v077");
        if (resolved == null || !resolved.complete || !empty(resolved.parseError)
                || resolved.payload == null || resolved.payload.length() == 0) {
            stopForPhoto(id, fallbackPhoto(id),
                    "L'unica ricerca ammessa non ha restituito un risultato strutturato completo; non viene eseguito alcun retry.",
                    "FAIL-CLOSED v0.77: Web Search incompleta"
                            + reasonSuffix(resolved == null ? "" : resolved.incompleteReason,
                            resolved == null ? "" : resolved.parseError));
            IdentificationEngine.finalizeOutput(id, primary);
            return id;
        }

        applyResolution(id, resolved, primary);
        IdentificationEngine.finalizeOutput(id, primary);
        return id;
    }

    static Models.Identifier selectPrimary(Models.LocalScan local, Models.Identification id) {
        if (local == null) {
            return null;
        }
        Models.Identifier best = null;
        int bestScore = -1;
        for (Models.Identifier candidate : local.identifiers) {
            if (candidate == null || !LocalVisionEngine.isStrongIdentifierLabel(candidate.label)
                    || !SearchEvidenceFilter.isIdentifierLike(candidate.value)) {
                continue;
            }
            String origin = safe(candidate.origin).toLowerCase(Locale.ROOT);
            boolean barcode = origin.contains("barcode") || "BARCODE".equalsIgnoreCase(candidate.label);
            boolean explicitlyLabeled = origin.contains("_labeled");
            boolean visionCorroborated = containsCanonical(id == null ? null : id.identifierLabels, candidate.value);
            int reliability = EvidenceReliabilityGate.identifierConfidence(local, candidate);
            boolean accepted = barcode || (explicitlyLabeled && reliability >= 70)
                    || (explicitlyLabeled && visionCorroborated && reliability >= 50);
            if (!accepted) {
                continue;
            }
            int score = reliability + (barcode ? 30 : 0) + (visionCorroborated ? 12 : 0);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    static boolean observationUsable(OpenAiClient.Response response) {
        if (response == null || !response.complete || !empty(response.parseError)
                || response.payload == null || response.payload.length() == 0) {
            return false;
        }
        return response.payload.optBoolean("observation_valid", false);
    }

    static void parseObservation(Models.Identification id, JSONObject p) {
        id.title = clean(p.optString("title", ""));
        id.category = clean(p.optString("category", ""));
        id.categoryKey = clean(p.optString("category_key", "other")).toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        id.categoryConfidence = clamp(p.optInt("category_confidence", 0));
        id.familyConfidence = clamp(p.optInt("family_confidence", 0));
        id.visionIdentityConfidence = Math.min(80, clamp(p.optInt("identity_confidence", 0)));
        id.visionIdentityReason = clean(p.optString("identity_reason", ""));
        id.brandRoleConfidence = clamp(p.optInt("brand_role_confidence", 0));
        id.brandRoleReason = clean(p.optString("brand_role_reason", ""));
        id.distinctiveTerms.addAll(strings(p.optJSONArray("distinctive_terms"), 10));
        id.visualFacts.addAll(strings(p.optJSONArray("variant_facts"), 16));
        id.spatialSignature.addAll(strings(p.optJSONArray("spatial_signature"), 14));
        id.photoViews.addAll(strings(p.optJSONArray("photo_views"), 5));
        id.visualFingerprint = clean(p.optString("visual_fingerprint", ""));

        JSONArray labels = p.optJSONArray("visible_labels");
        if (labels != null) {
            for (int i = 0; i < labels.length() && i < 20; i++) {
                JSONObject item = labels.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String text = clean(item.optString("text", ""));
                String type = clean(item.optString("type", "unknown")).toLowerCase(Locale.ROOT);
                if (text.isEmpty()) {
                    continue;
                }
                addOnce(id.visibleLabels, text);
                if ("transient_display".equals(type) || SearchEvidenceFilter.isTransientDisplay(text)) {
                    addOnce(id.transientLabels, text);
                } else if ("control".equals(type) || SearchEvidenceFilter.isControlLabel(text)) {
                    addOnce(id.controlLabels, text);
                } else if ("brand_logo".equals(type) || "manufacturer_text".equals(type)) {
                    addOnce(id.brandLabels, text);
                    addOnce(id.searchableLabels, text);
                } else if ("identifier".equals(type)) {
                    addOnce(id.identifierLabels, text);
                    if (SearchEvidenceFilter.isIdentifierLike(text)) {
                        addOnce(id.searchableLabels, text);
                    }
                } else if ("descriptor".equals(type) && SearchEvidenceFilter.isSearchableLiteral(text)) {
                    addOnce(id.descriptorLabels, text);
                    addOnce(id.searchableLabels, text);
                }
            }
        }
        List<String> safeDistinctive = SearchEvidenceFilter.uniqueSearchable(id.distinctiveTerms, 6);
        for (String x : safeDistinctive) {
            addOnce(id.searchableLabels, x);
        }

        String returnedBrand = clean(p.optString("brand", ""));
        if (!returnedBrand.isEmpty() && containsCanonical(id.brandLabels, returnedBrand)
                && id.brandRoleConfidence >= 85) {
            id.brand = returnedBrand;
            String evidence = clean(p.optString("brand_evidence", ""));
            id.brandEvidence = evidence.toLowerCase(Locale.ROOT).contains("logo")
                    ? "visible_logo" : "visible_brand_text";
        } else if (!id.brandLabels.isEmpty() && id.brandRoleConfidence >= 85) {
            id.brand = id.brandLabels.get(0);
            id.brandEvidence = "visible_brand_text";
        } else {
            id.brand = "";
            id.brandEvidence = EnvironmentCompat.MEDIA_UNKNOWN;
        }

        id.visionCandidates.addAll(strings(p.optJSONArray("candidate_hints"), 5));
        JSONArray fast = p.optJSONArray("fast_candidates");
        if (fast != null) {
            for (int i = 0; i < fast.length() && i < 4; i++) {
                JSONObject c = fast.optJSONObject(i);
                if (c == null) {
                    continue;
                }
                String name = join(clean(c.optString("brand", "")), clean(c.optString("family", "")),
                        clean(c.optString("model", "")));
                if (!name.isEmpty()) {
                    addOnce(id.visionCandidates, "Hypothesis only: " + name + " ("
                            + clamp(c.optInt("confidence", 0)) + "%)");
                }
            }
        }
        String family = clean(p.optString("family", ""));
        String model = clean(p.optString("model", ""));
        if (!family.isEmpty() || !model.isEmpty()) {
            addOnce(id.visionCandidates, "Hypothesis only: " + join(returnedBrand, family, model));
        }
        if (id.title.isEmpty() || genericTitle(id.title)) {
            id.title = id.category.isEmpty() ? "Oggetto" : id.category;
        }
    }

    private static void applyResolution(Models.Identification id, OpenAiClient.Response response,
                                        Models.Identifier primary) {
        JSONObject p = response.payload;
        String resolvedCategory = clean(p.optString("resolved_category", ""));
        if (!resolvedCategory.isEmpty() && (id.category.isEmpty() || id.categoryConfidence < 70)) {
            id.category = resolvedCategory;
            id.categoryConfidence = Math.max(id.categoryConfidence, 70);
        }
        JSONArray candidates = p.optJSONArray("candidates");
        if (candidates != null) {
            for (int i = 0; i < candidates.length() && i < 6; i++) {
                JSONObject x = candidates.optJSONObject(i);
                if (x == null) {
                    continue;
                }
                Models.CandidateScore c = new Models.CandidateScore();
                c.brand = clean(x.optString("brand", ""));
                c.family = clean(x.optString("family", ""));
                c.model = clean(x.optString("model", ""));
                if (c.model.isEmpty()) {
                    continue;
                }
                boolean sameEntity = x.optBoolean("same_entity_role", false);
                boolean relatedOnly = x.optBoolean("relationship_only", true);
                if (!sameEntity || relatedOnly) {
                    c.hardRejected = true;
                    c.hardViolations.add("same physical entity required");
                    id.rejectedCandidates.add(c);
                    continue;
                }
                String sourceUrl = clean(x.optString("source_url", ""));
                boolean sourceGrounded = sourcePresent(response.sources, sourceUrl);
                boolean completeReference = x.optBoolean("exact_reference_complete", false);
                boolean exactSupported = x.optBoolean("exact_identity_supported", false) && sourceGrounded;
                int sourceConfidence = clamp(x.optInt("source_identity_confidence", 0));
                boolean disproof = x.optBoolean("disproof_passed", false);
                c.identifierScore = clamp(x.optInt("identifier_score", 0));
                c.textScore = clamp(x.optInt("text_score", 0));
                c.layoutScore = clamp(x.optInt("layout_score", 0));
                c.webScore = sourceGrounded ? clamp(x.optInt("web_score", 0))
                        : Math.min(45, clamp(x.optInt("web_score", 0)));
                c.evidence = clean(x.optString("evidence", ""));
                for (String rawFact : strings(x.optJSONArray("candidate_facts"), 14)) {
                    if (!reservedResolverFact(rawFact)) {
                        addOnce(c.candidateFacts, rawFact);
                    }
                }
                c.contradictions.addAll(strings(x.optJSONArray("contradictions"), 10));
                addOnce(c.candidateFacts, "resolver_index=" + i);
                addOnce(c.candidateFacts, "source_url=" + sourceUrl);
                addOnce(c.candidateFacts, "source_grounded=" + sourceGrounded);
                addOnce(c.candidateFacts, "source_exact_reference=" + sourceGrounded);
                addOnce(c.candidateFacts, "exact_reference_complete=" + completeReference);
                addOnce(c.candidateFacts, "exact_identity_supported=" + exactSupported);
                addOnce(c.candidateFacts, "source_identity_confidence=" + sourceConfidence);
                addOnce(c.candidateFacts, "same_entity_role=true");
                addOnce(c.candidateFacts, "relationship_only=false");
                addOnce(c.candidateFacts, "disproof_passed=" + disproof);
                if (completeReference && sourceGrounded) {
                    addOnce(c.candidateFacts, "model_code=" + c.model);
                }
                c.totalScore = candidateTotal(c, primary);
                if (!sourceGrounded) {
                    c.totalScore = Math.min(c.totalScore, 55);
                } else if (!completeReference) {
                    c.totalScore = Math.min(c.totalScore, 72);
                } else if (!exactSupported) {
                    c.totalScore = Math.min(c.totalScore, 76);
                } else if (!disproof) {
                    c.totalScore = Math.min(c.totalScore, 84);
                }
                if (exactSupported && completeReference && sourceConfidence >= 85 && disproof) {
                    c.totalScore = Math.min(96, c.totalScore + 6);
                }
                UniversalConsistencyGate.calibrateCandidate(c);
                id.candidates.add(c);
            }
        }
        HardConstraintEngine.apply(id, primary);
        IdentificationEngine.sortCandidates(id);
        Models.CandidateScore top = id.candidates.isEmpty() ? null : id.candidates.get(0);
        String resolvedBrand = clean(p.optString("resolved_brand", ""));
        if (id.brand.isEmpty() && !resolvedBrand.isEmpty()) {
            id.brand = resolvedBrand;
            id.brandEvidence = "verified_web";
            id.brandRoleConfidence = Math.max(id.brandRoleConfidence, 80);
        }
        if (top == null) {
            id.marketReady = false;
            id.disproofPassed = false;
            id.model = "";
            id.modelConfidence = 0;
            id.verificationSummary = clean(p.optString("evidence", ""));
            stopForPhoto(id, nonEmpty(p.optString("next_photo_request", ""))
                            ? clean(p.optString("next_photo_request", "")) : fallbackPhoto(id),
                    nonEmpty(p.optString("next_photo_reason", ""))
                            ? clean(p.optString("next_photo_reason", ""))
                            : "La ricerca non ha prodotto un candidato source-backed per la stessa entità fisica.",
                    "ONE-PASS v0.77: nessun candidato grounded superstite.");
            return;
        }

        if (!top.brand.isEmpty() && id.brand.isEmpty()) {
            id.brand = top.brand;
        }
        id.family = top.family;
        id.model = top.model;
        id.familyConfidence = Math.max(id.familyConfidence, Math.min(88, top.totalScore));
        boolean sourceGrounded = factTrue(top, "source_grounded");
        boolean completeReference = factTrue(top, "exact_reference_complete");
        boolean exactSupported = factTrue(top, "exact_identity_supported");
        boolean disproof = factTrue(top, "disproof_passed");
        int sourceConfidence = factInt(top, "source_identity_confidence");
        int margin = id.candidates.size() < 2 ? top.totalScore
                : Math.max(0, top.totalScore - id.candidates.get(1).totalScore);
        boolean directProof = directProof(clean(p.optString("model_proof", "none")));
        boolean confirmed = p.optBoolean("confirmed", false) && sourceGrounded && completeReference
                && exactSupported && sourceConfidence >= 85 && disproof && directProof
                && top.totalScore >= 85 && (margin >= 10 || top.hardMatchWeight >= 60);
        id.marketReady = confirmed;
        id.disproofPassed = confirmed;
        id.modelProof = confirmed ? clean(p.optString("model_proof", "none")) : "none";
        id.modelConfidence = confirmed
                ? Math.min(95, Math.max(85, Math.min(sourceConfidence, top.totalScore + 5)))
                : Math.min(84, Math.max(35, top.totalScore));
        id.verificationSummary = clean(p.optString("evidence", ""));
        if (id.verificationSummary.isEmpty()) {
            id.verificationSummary = "Leader " + top.displayName() + " · " + top.totalScore
                    + "/100 · margine " + margin + ".";
        }
        if (confirmed) {
            id.nextPhotoRequest = "";
            id.nextPhotoReason = "";
            id.decisionReason = "CONFIRMED v0.77: una Vision strict e una sola Web Search convergono su riferimento completo, fonte esatta e disproof.";
            id.categoryConfidence = Math.max(id.categoryConfidence, 92);
            id.familyConfidence = Math.max(id.familyConfidence, 88);
            return;
        }
        id.marketReady = false;
        id.disproofPassed = false;
        id.nextPhotoRequest = clean(p.optString("next_photo_request", ""));
        id.nextPhotoReason = clean(p.optString("next_photo_reason", ""));
        if (id.nextPhotoRequest.isEmpty()) {
            id.nextPhotoRequest = fallbackPhoto(id);
        }
        if (id.nextPhotoReason.isEmpty()) {
            id.nextPhotoReason = "Il modello esatto non supera ancora riferimento completo, fonte diretta e separazione dal miglior concorrente.";
        }
        id.decisionReason = "NEED_ANOTHER_PHOTO v0.77: pipeline fermata dopo 1 Vision + 1 Web; nessun retry o ricerca prezzo.";
    }

    private static String buildObservationPrompt(Models.LocalScan local, String details) {
        StringBuilder labeled = new StringBuilder();
        if (local != null) {
            for (Models.Identifier x : local.identifiers) {
                if (x != null && LocalVisionEngine.isStrongIdentifierLabel(x.label)
                        && safe(x.origin).toLowerCase(Locale.ROOT).contains("_labeled")) {
                    if (labeled.length() > 0) {
                        labeled.append(" | ");
                    }
                    labeled.append(x.label).append('=').append(x.value);
                }
            }
        }
        return "Analyze the foreground object in every supplied view. First identify its boundary and ignore people/reflections, walls, furniture, packaging and nearby items. "
                + "Return a concrete functional category when supported by visible components. visible_labels must classify each clear foreground inscription as exactly one of: "
                + "brand_logo, manufacturer_text, identifier, control, transient_display, descriptor, unknown. A time such as 14:21, a counter, a temperature, a percentage, "
                + "day indicators and other changing readouts are transient_display. Button captions such as CYCLE START are control. Only classify text as identifier when it is visibly paired "
                + "with MODEL, P/N, PART, SKU, REF, TYPE, ITEM or a barcode. Unlabelled code-like OCR is not an identifier. Use variant_facts and spatial_signature for foreground geometry, "
                + "control topology, openings, handles, connectors, mounting and materials. candidate_hints/fast_candidates are non-binding hypotheses. Never put a guessed brand/model into visible_labels. "
                + "LOCAL_LABELED_OCR_CANDIDATES=" + (labeled.length() == 0 ? "none" : labeled)
                + " | LOCAL_OCR_NOISY_REFERENCE_ONLY=" + clip(local == null ? "" : local.joinedText(), 3600)
                + " | USER_HINT_UNTRUSTED=" + clip(details, 500);
    }

    private static String buildResolvePrompt(Models.Identification id, Models.Identifier primary, String details) {
        List<String> labels = SearchEvidenceFilter.uniqueSearchable(id.searchableLabels, 8);
        return "Resolve the photographed physical product using one web_search call total. Return up to six concrete same-entity candidates and rank them. "
                + "Use manufacturer-owned sources first when VISIBLE_MANUFACTURER is present. Do not use TRANSIENT_OR_CONTROL_TEXT in any query. "
                + "Do not search price/value. The complete source reference must preserve suffixes after / or -. A family prefix is not a complete model. "
                + "Set source_url to a URL actually retrieved in this call. candidate_facts may contain source-backed dynamic attributes, but model_code only when the source prints the complete reference. "
                + "VISIBLE_MANUFACTURER=" + (BrandBlindPolicy.trustedObservedBrand(id) ? id.brand : "none")
                + " | CATEGORY=" + id.category + " (" + id.categoryConfidence + "%)"
                + " | PRIMARY_LABELED_IDENTIFIER=" + (primary == null ? "none" : primary.label + "=" + primary.value)
                + " | SEARCHABLE_VISIBLE_LABELS=" + labels
                + " | SOFT_OCR_LITERALS_NOT_BRAND_LOCKS=" + id.softOcrLabels
                + " | TRANSIENT_OR_CONTROL_TEXT_EXCLUDED=" + combine(id.transientLabels, id.controlLabels)
                + " | STRUCTURE=" + clip(UniversalSearchPlan.structureSeed(id), 1400)
                + " | HYPOTHESES_NON_BINDING=" + clip(id.visionCandidates.toString(), 700)
                + " | HARD_CONSTRAINTS=" + id.hardConstraints
                + " | USER_HINT_SOFT_PRIOR=" + clip(details, 400);
    }

    private static boolean hasResolutionEvidence(Models.Identification id, Models.Identifier primary) {
        if (primary != null || !id.brandLabels.isEmpty()) {
            return true;
        }
        boolean concreteCategory = id.categoryConfidence >= 55 && !genericCategory(id.category, id.categoryKey);
        boolean structure = !id.spatialSignature.isEmpty() || !id.visualFacts.isEmpty()
                || safe(id.visualFingerprint).length() >= 18;
        return concreteCategory && structure;
    }

    private static int candidateTotal(Models.CandidateScore c, Models.Identifier primary) {
        int numerator;
        int denominator;
        if (primary != null) {
            numerator = c.identifierScore * 35 + c.textScore * 20 + c.layoutScore * 20 + c.webScore * 25;
            denominator = 100;
        } else {
            numerator = c.textScore * 30 + c.layoutScore * 35 + c.webScore * 35;
            denominator = 100;
        }
        int score = Math.round((float) numerator / denominator);
        int weak = 0;
        int strong = 0;
        for (String raw : c.contradictions) {
            String x = safe(raw).toUpperCase(Locale.ROOT);
            if (x.startsWith("STRONG:") || x.startsWith("FORTE:")) {
                strong++;
            } else if (!x.isEmpty()) {
                weak++;
            }
        }
        score -= Math.min(18, weak * 6);
        score -= Math.min(50, strong * 25);
        return clamp(score);
    }

    private static String searchSeed(Models.Identification id, Models.Identifier primary) {
        StringBuilder b = new StringBuilder();
        if (BrandBlindPolicy.trustedObservedBrand(id)) {
            b.append(id.brand);
        }
        if (primary != null) {
            append(b, primary.value);
        }
        append(b, id.category);
        for (String x : SearchEvidenceFilter.uniqueSearchable(id.searchableLabels, 4)) {
            append(b, x);
        }
        for (String x : id.softOcrLabels) {
            append(b, x);
        }
        return b.toString();
    }

    private static void collectSoftOcr(Models.Identification id, Models.LocalScan local) {
        if (id == null || local == null) {
            return;
        }
        for (String page : local.textByImage) {
            if (page == null) {
                continue;
            }
            for (String raw : page.split("[\\r\\n]+")) {
                String x = clean(raw);
                if (SearchEvidenceFilter.isSoftOcrLiteral(x)
                        && !containsCanonical(id.transientLabels, x)
                        && !containsCanonical(id.controlLabels, x)) {
                    addOnce(id.softOcrLabels, x);
                    if (id.softOcrLabels.size() >= 3) {
                        return;
                    }
                }
            }
        }
    }

    private static void stopForPhoto(Models.Identification id, String request, String reason, String decision) {
        id.marketReady = false;
        id.disproofPassed = false;
        id.nextPhotoRequest = clean(request);
        id.nextPhotoReason = clean(reason);
        id.decisionReason = clean(decision);
        if (id.title.isEmpty()) {
            id.title = id.category.isEmpty() ? "Oggetto" : id.category;
        }
        if (id.verificationSummary.isEmpty()) {
            id.verificationSummary = reason;
        }
    }

    private static String fallbackPhoto(Models.Identification id) {
        if (id != null && (!id.brandLabels.isEmpty() || !id.visibleLabels.isEmpty())) {
            return "Aggiungi un primo piano nitido dell'etichetta MODEL/P-N oppure una vista diversa che mostri i dettagli distintivi";
        }
        return "Aggiungi una seconda vista più ravvicinata includendo logo, etichetta e dettagli distintivi";
    }

    private static boolean sourcePresent(List<Models.Source> sources, String url) {
        String wanted = normalizeUrl(url);
        if (wanted.isEmpty() || sources == null) {
            return false;
        }
        for (Models.Source source : sources) {
            String actual = normalizeUrl(source == null ? "" : source.url);
            if (wanted.equals(actual) || (!wanted.isEmpty() && !actual.isEmpty()
                    && (wanted.startsWith(actual) || actual.startsWith(wanted)))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeUrl(String raw) {
        String x = safe(raw).toLowerCase(Locale.ROOT);
        int hash = x.indexOf('#');
        if (hash >= 0) {
            x = x.substring(0, hash);
        }
        int query = x.indexOf('?');
        if (query >= 0) {
            x = x.substring(0, query);
        }
        return x.replaceFirst("^https?://(?:www\\.)?", "").replaceAll("/+$", "");
    }

    private static boolean directProof(String x) {
        return "direct_product_page".equals(x) || "exact_manual".equals(x)
                || "exact_catalog".equals(x) || "exact_retailer".equals(x)
                || "exact_identifier".equals(x);
    }

    private static boolean reservedResolverFact(String raw) {
        String x = safe(raw).toLowerCase(Locale.ROOT);
        return x.startsWith("source_grounded=") || x.startsWith("source_exact_reference=")
                || x.startsWith("exact_reference_complete=")
                || x.startsWith("exact_identity_supported=")
                || x.startsWith("source_identity_confidence=")
                || x.startsWith("same_entity_role=") || x.startsWith("relationship_only=")
                || x.startsWith("disproof_passed=") || x.startsWith("model_code=")
                || x.startsWith("resolver_index=") || x.startsWith("source_url=");
    }

    private static boolean factTrue(Models.CandidateScore c, String key) {
        return "true".equalsIgnoreCase(fact(c, key));
    }

    private static int factInt(Models.CandidateScore c, String key) {
        try {
            return Integer.parseInt(fact(c, key));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String fact(Models.CandidateScore c, String key) {
        if (c == null) {
            return "";
        }
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        for (String raw : c.candidateFacts) {
            String x = safe(raw);
            if (x.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return x.substring(x.indexOf('=') + 1).trim();
            }
        }
        return "";
    }

    private static boolean containsCanonical(List<String> values, String wanted) {
        String target = canon(wanted);
        if (target.isEmpty() || values == null) {
            return false;
        }
        for (String x : values) {
            if (target.equals(canon(x))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> strings(JSONArray a, int limit) {
        List<String> out = new ArrayList<>();
        if (a == null) {
            return out;
        }
        for (int i = 0; i < a.length() && i < limit; i++) {
            String x = clean(a.optString(i, ""));
            if (!x.isEmpty()) {
                addOnce(out, x);
            }
        }
        return out;
    }

    private static String combine(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>();
        if (a != null) {
            out.addAll(a);
        }
        if (b != null) {
            out.addAll(b);
        }
        return out.toString();
    }

    private static boolean genericCategory(String category, String key) {
        String c = safe(category).toLowerCase(Locale.ROOT);
        String k = safe(key).toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return c.isEmpty() || c.equals("object") || c.equals("device") || c.equals("thing")
                || c.equals("oggetto") || k.isEmpty() || k.equals("other") || k.equals("object");
    }

    private static boolean genericTitle(String title) {
        String x = safe(title).toLowerCase(Locale.ROOT);
        return x.isEmpty() || x.equals("object") || x.equals("oggetto")
                || x.equals("unidentified object") || x.equals("oggetto non identificato");
    }

    private static String reasonSuffix(String a, String b) {
        String x = nonEmpty(a) ? a : b;
        return nonEmpty(x) ? " (" + x + ")" : "";
    }

    private static void append(StringBuilder b, String value) {
        String x = clean(value);
        if (x.isEmpty()) {
            return;
        }
        if (b.length() > 0) {
            b.append(' ');
        }
        b.append(x);
    }

    private static void addOnce(List<String> values, String value) {
        if (values == null || empty(value)) {
            return;
        }
        for (String old : values) {
            if (value.equalsIgnoreCase(old)) {
                return;
            }
        }
        values.add(value);
    }

    private static String join(String... values) {
        StringBuilder b = new StringBuilder();
        for (String value : values) {
            append(b, value);
        }
        return b.toString();
    }

    private static String clip(String s, int n) {
        String x = safe(s);
        return x.length() <= n ? x : x.substring(0, n);
    }

    private static String canon(String s) {
        return safe(s).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static int clamp(int x) {
        return Math.max(0, Math.min(100, x));
    }

    private static boolean nonEmpty(String s) {
        return !empty(s);
    }

    private static boolean empty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String clean(String s) {
        return safe(s).replaceAll("\\s+", " ");
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
