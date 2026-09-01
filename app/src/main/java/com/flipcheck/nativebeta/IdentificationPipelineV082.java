package com.flipcheck.nativebeta;

import androidx.core.os.EnvironmentCompat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Multimodal bounded-evidence pipeline introduced after the v0.77 device tests.
 * One scan uses one Responses request containing the original image(s) and at
 * most one hosted web_search call. The photo therefore survives retrieval and
 * can directly disprove generic text/layout matches.
 */
final class IdentificationPipelineV082 {
    private IdentificationPipelineV082() {
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
        if (!UniversalConsistencyGate.visionBudgetAvailable(usage)
                || !UniversalConsistencyGate.discoveryBudgetAvailable(usage)) {
            stopForPhoto(id, fallbackPhoto(id),
                    "Il budget di una sola richiesta multimodale e una sola Web Search per scansione è già esaurito.",
                    "BUDGET v0.82: nessun secondo tentativo automatico.");
            return id;
        }

        OpenAiClient.Response combined = client.resolveMultimodal(new ArrayList<>(images),
                buildMultimodalPrompt(local, details));
        if (combined != null) {
            IdentificationEngine.collectStage(id, usage, combined, "multimodal-resolve-v082");
        }
        if (combined == null || !combined.complete || !empty(combined.parseError)
                || combined.payload == null || combined.payload.length() == 0) {
            stopForPhoto(id, "Aggiungi una foto più ravvicinata e nitida dell'oggetto, includendo logo ed eventuale etichetta",
                    "La richiesta multimodale non ha restituito una struttura completa; nessun retry viene eseguito.",
                    "FAIL-CLOSED v0.82: risposta multimodale incompleta"
                            + reasonSuffix(combined == null ? "" : combined.incompleteReason,
                            combined == null ? "" : combined.parseError));
            return id;
        }

        JSONObject root = combined.payload;
        JSONObject observation = root.optJSONObject("observation");
        JSONObject resolution = root.optJSONObject("resolution");
        if (!observationUsable(combined, observation)) {
            stopForPhoto(id, "Aggiungi una foto più ravvicinata e nitida dell'oggetto, includendo logo ed eventuale etichetta",
                    "L'oggetto non è stato osservato con sufficiente affidabilità; eventuali risultati web non vengono utilizzati.",
                    "FAIL-CLOSED v0.82: osservazione multimodale non valida.");
            return id;
        }

        parseObservation(id, observation);
        OverlayScopePolicy.normalize(id);
        ObservationSanitizer.apply(id);
        CategoryFactPolicy.apply(id);
        PhysicalIdentityConsolidator.apply(id);
        BrandBlindPolicy.sanitizeBrandEvidence(id, local);
        collectSoftOcr(id, local);
        PhotoIdentityPolicy.consolidateObservation(id, local);
        SealedProductIdentityPolicy.consolidateObservation(id, local);
        CollectibleCardIdentityPolicy.sanitizeObservation(id, local);
        if (PhysicalIdentityRecovery.eligible(id, usage)) {
            OpenAiClient.Response recovery = client.recoverPhysicalIdentity(
                    new ArrayList<>(images), PhysicalIdentityRecovery.prompt(id));
            if (recovery != null) {
                IdentificationEngine.collectStage(id, usage, recovery,
                        "physical-identity-recovery-v101-no-web");
                if (PhysicalIdentityRecovery.apply(id, recovery)) {
                    CategoryFactPolicy.apply(id);
                    PhysicalIdentityConsolidator.apply(id);
                    BrandBlindPolicy.sanitizeBrandEvidence(id, local);
                    PhotoIdentityPolicy.consolidateObservation(id, local);
                    SealedProductIdentityPolicy.consolidateObservation(id, local);
                    CollectibleCardIdentityPolicy.sanitizeObservation(id, local);
                }
            }
        }
        Models.Identifier primary = selectPrimary(local, id);
        if (primary != null) {
            id.primaryIdentifier = clean(primary.value);
            id.identifierVariants.addAll(LocalVisionEngine.normalizeIdentifierVariants(primary.value));
        }
        HardConstraintEngine.apply(id, primary);

        // Card closure must run before generic fail-closed exits.  Previously
        // those exits stopped the mandatory second verification before it was
        // even issued, leaving a complete Curry/Boniface tuple at brand only.
        if (CardPhotoTupleClosure.requiresMandatoryVerification(id, usage)) {
            OpenAiClient.Response cardVerification = client.recoverExactCardCatalog(
                    new ArrayList<>(images), ExactCardCatalogRecovery.prompt(id), false);
            if (cardVerification != null) {
                IdentificationEngine.collectStage(id, usage, cardVerification,
                        "mandatory-card-verification-v112-no-web");
                ExactCardCatalogRecovery.apply(id, cardVerification);
            }
            // The second review may add catalog proof, but it cannot suppress
            // an already complete commercial tuple read from the photographs.
            if (CardPhotoTupleClosure.apply(id)) {
                IdentificationEngine.finalizeOutput(id, primary);
                return id;
            }
        }

        if (!hasResolutionEvidence(id, primary)) {
            stopForPhoto(id, fallbackPhoto(id),
                    "La foto non contiene abbastanza segnali strutturati per sostenere una risoluzione affidabile; l'OCR non etichettato è stato escluso.",
                    "FAIL-CLOSED v0.82: evidenza visiva insufficiente.");
            IdentificationEngine.finalizeOutput(id, primary);
            return id;
        }

        if (combined.usage == null || combined.usage.webCalls != 1 || resolution == null) {
            stopForPhoto(id, fallbackPhoto(id),
                    "La richiesta non ha completato l'unica Web Search prevista; nessun candidato viene promosso.",
                    "FAIL-CLOSED v0.82: Web Search assente o incompleta.");
            IdentificationEngine.finalizeOutput(id, primary);
            return id;
        }

        id.searchQuery = searchSeed(id, primary);
        if (primary == null && !BrandBlindPolicy.trustedObservedBrand(id)
                && !firstQueryIsBrandNeutral(id, combined.queries)) {
            stopForPhoto(id, fallbackPhoto(id),
                    "In assenza di marca o codice visibile, la ricerca non è partita da una query neutrale: i candidati vengono scartati per evitare ancoraggio a un marchio ipotizzato.",
                    "FAIL-CLOSED v0.82: query iniziale non brand-neutral.");
            IdentificationEngine.finalizeOutput(id, primary);
            return id;
        }

        combined.payload = resolution;
        applyResolution(id, combined, primary);
        combined.payload = root;
        if (ExactCardCatalogRecovery.eligible(id, usage)) {
            OpenAiClient.Response cardCatalog = client.recoverExactCardCatalog(
                    ExactCardCatalogRecovery.attachImages(id)
                            ? firstImageOnly(images) : new ArrayList<>(),
                    ExactCardCatalogRecovery.prompt(id),
                    ExactCardCatalogRecovery.useSecondWeb(usage));
            if (cardCatalog != null) {
                IdentificationEngine.collectStage(id, usage, cardCatalog,
                        "universal-exact-identity-recovery-v110");
                ExactCardCatalogRecovery.apply(id, cardCatalog);
            }
        }
        if (SportsCardParallelRecovery.eligible(id, usage)) {
            OpenAiClient.Response exactParallel = client.recoverSportsCardParallel(
                    new ArrayList<>(images), SportsCardParallelRecovery.prompt(id));
            if (exactParallel != null) {
                IdentificationEngine.collectStage(id, usage, exactParallel,
                        "sports-card-parallel-recovery-v104-one-web");
                SportsCardParallelRecovery.apply(id, exactParallel);
            }
        }
        if (BorderlineIdentityAdjudicator.eligible(id, usage)) {
            OpenAiClient.Response adjudication = client.adjudicateBorderline(
                    new ArrayList<>(images), BorderlineIdentityAdjudicator.prompt(id));
            if (adjudication != null) {
                IdentificationEngine.collectStage(id, usage, adjudication,
                        "borderline-adjudication-v099-no-web");
                BorderlineIdentityAdjudicator.apply(id, adjudication);
            }
        }
        if (VisualBrandFamilyRecovery.eligible(id, usage)) {
            OpenAiClient.Response visualBrand = client.recoverVisualBrandFamily(
                    new ArrayList<>(images), VisualBrandFamilyRecovery.prompt(id));
            boolean visualBrandApplied = false;
            if (visualBrand != null) {
                IdentificationEngine.collectStage(id, usage, visualBrand,
                        "visual-brand-family-recovery-v102-no-web");
                visualBrandApplied = VisualBrandFamilyRecovery.apply(id, visualBrand);
            }
            if (!visualBrandApplied) {
                VisualBrandFamilyRecovery.applyInconclusiveGuard(id);
            }
        }
        // Card identity is a physical tuple. A missing/poorly indexed catalog
        // page may reduce corroboration confidence, but must not collapse a
        // complete front/back tuple to only "Panini card" or a set name.
        CardPhotoTupleClosure.apply(id);
        RemoteCandidateGuard.apply(id);
        IdentificationEngine.finalizeOutput(id, primary);
        return id;
    }

    private static List<String> firstImageOnly(List<String> images) {
        List<String> out = new ArrayList<>();
        if (images != null && !images.isEmpty()) out.add(images.get(0));
        return out;
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
            // Text OCR may belong to an instruction card, removable overlay, component or
            // nearby object. It becomes a product identifier only after the multimodal
            // observer binds the same literal to the foreground product. A decoded barcode
            // remains an independent machine-readable channel.
            boolean accepted = barcode
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

    static boolean observationUsable(OpenAiClient.Response response, JSONObject observation) {
        return response != null && response.complete && empty(response.parseError)
                && observation != null && observation.optBoolean("observation_valid", false);
    }

    static boolean firstQueryIsBrandNeutral(List<String> queries) {
        return firstQueryIsBrandNeutral(null, queries);
    }

    static boolean firstQueryIsBrandNeutral(Models.Identification id, List<String> queries) {
        if (queries == null) {
            return false;
        }
        for (String raw : queries) {
            String q = clean(raw).toLowerCase(Locale.ROOT);
            if (!q.isEmpty()) {
                return !q.contains("site:")
                        && !BrandBlindPolicy.queryContainsUnobservedBrand(id, q);
            }
        }
        return false;
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
        JSONObject photoIdentity = p.optJSONObject("photo_identity");
        if (photoIdentity != null) {
            id.photoIdentityName = clean(photoIdentity.optString("canonical_name", ""));
            id.photoIdentityCode = clean(photoIdentity.optString("identity_code", ""));
            id.photoIdentityKind = clean(photoIdentity.optString("evidence_kind", "none"))
                    .toLowerCase(Locale.ROOT);
            id.photoIdentityPhysicalBinding = photoIdentity.optBoolean("physical_binding", false);
            id.photoIdentityOverlayOrWatermark = photoIdentity.optBoolean(
                    "overlay_or_watermark", false);
            id.photoIdentityConfidence = clamp(photoIdentity.optInt("confidence", 0));
            id.photoIdentityFields.addAll(strings(photoIdentity.optJSONArray("fields"), 12));
            id.photoIdentityComplete = photoIdentity.optBoolean("complete", false)
                    && id.photoIdentityPhysicalBinding
                    && !id.photoIdentityOverlayOrWatermark
                    && !id.photoIdentityName.isEmpty();
        }

        JSONArray labels = p.optJSONArray("visible_labels");
        if (labels != null) {
            for (int i = 0; i < labels.length() && i < 20; i++) {
                JSONObject item = labels.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String text = clean(item.optString("text", ""));
                String type = clean(item.optString("type", "unknown")).toLowerCase(Locale.ROOT);
                String entityRole = clean(item.optString("entity_role", "uncertain"))
                        .toLowerCase(Locale.ROOT);
                boolean foreground = "foreground_product".equals(entityRole);
                boolean identityBinding = item.optBoolean("identity_binding", false);
                if (text.isEmpty()) {
                    continue;
                }
                addOnce(id.visibleLabels, text);
                if (!foreground) {
                    continue;
                }
                if ("transient_display".equals(type) || SearchEvidenceFilter.isTransientDisplay(text)) {
                    addOnce(id.transientLabels, text);
                } else if ("control".equals(type) || SearchEvidenceFilter.isControlLabel(text)) {
                    addOnce(id.controlLabels, text);
                } else if ("brand_logo".equals(type) || "manufacturer_text".equals(type)) {
                    addOnce(id.brandLabels, text);
                    addOnce(id.searchableLabels, text);
                } else if ("identifier".equals(type) && identityBinding) {
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
            id.brandEvidence = evidence.toLowerCase(Locale.ROOT)
                    .contains("visible_logo_cross_photo")
                    ? "visible_logo_cross_photo"
                    : evidence.toLowerCase(Locale.ROOT).contains("logo")
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
                String name = normalizedCandidateName(clean(c.optString("brand", "")),
                        clean(c.optString("family", "")),
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
            addOnce(id.visionCandidates, "Hypothesis only: "
                    + normalizedCandidateName(returnedBrand, family, model));
        }
        if (id.title.isEmpty() || genericTitle(id.title)) {
            id.title = id.category.isEmpty() ? "Oggetto" : id.category;
        }
    }

    private static void applyResolution(Models.Identification id, OpenAiClient.Response response,
                                        Models.Identifier primary) {
        CollectibleCardIdentityPolicy.sanitizeObservation(id, id.localScan);
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
                String rawFamily = clean(x.optString("family", ""));
                String rawModel = clean(x.optString("model", ""));
                boolean completeReference = x.optBoolean("exact_reference_complete", false);
                c.family = normalizedFamily(rawFamily, c.brand);
                c.model = modelAtSupportedLevel(rawModel, completeReference);
                c.probableReferenceConfidence = clamp(x.optInt(
                        "probable_reference_confidence", 0));
                c.probableReference = probableReferenceAtSupportedLevel(
                        clean(x.optString("probable_reference", "")),
                        c.probableReferenceConfidence);
                c.probableReference = dedupeLeadingBrand(c.probableReference, c.brand);
                if (c.family.isEmpty() && !completeReference) {
                    c.family = normalizedFamily(rawModel, c.brand);
                }
                if (c.brand.isEmpty() && c.family.isEmpty() && c.model.isEmpty()
                        && c.probableReference.isEmpty()) {
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
                Models.Source matchedSource = matchedSource(response.sources, sourceUrl, c, id);
                boolean sourceGrounded = matchedSource != null;
                if (sourceGrounded) {
                    sourceUrl = matchedSource.url;
                }
                boolean visualReference = x.optBoolean("visual_reference_checked", false);
                int visualConfidence = clamp(x.optInt("visual_match_confidence", 0));
                boolean majorGeometryConflict = x.optBoolean("major_geometry_conflict", false);
                int sourceConfidence = clamp(x.optInt("source_identity_confidence", 0));
                boolean disproof = x.optBoolean("disproof_passed", false);
                String resolvedCode = completeReference ? c.model : c.probableReference;
                int codeBindingConfidence = sourceGrounded && completeReference
                        && sourceConfidence >= 85 && disproof && !majorGeometryConflict
                        ? PhotoIdentityPolicy.resolvedCodeBindingConfidence(
                        id, primary, resolvedCode, c.family) : 0;
                if (codeBindingConfidence >= 90) {
                    PhotoIdentityPolicy.promoteResolvedCodeIdentity(
                            id, c, resolvedCode, codeBindingConfidence);
                }
                boolean exactSupported = completeReference && !c.model.isEmpty()
                        && sourceGrounded && (codeBindingConfidence >= 90
                        || (x.optBoolean("exact_identity_supported", false)
                        && (candidateContainsIdentifier(c, primary)
                        || (visualReference && visualConfidence >= 85
                        && !majorGeometryConflict))));
                List<String> matchedPhotoIdentity = strings(
                        x.optJSONArray("matched_photo_identity_fields"), 12);
                int matchedPhotoIdentityCount = matchedPhotoIdentityCount(
                        id.photoIdentityFields, matchedPhotoIdentity);
                List<String> matchedFeatures = strings(
                        x.optJSONArray("matched_distinctive_features"), 8);
                c.identifierScore = clamp(x.optInt("identifier_score", 0));
                c.textScore = clamp(x.optInt("text_score", 0));
                int rawLayoutScore = clamp(x.optInt("layout_score", 0));
                c.webScore = sourceGrounded ? clamp(x.optInt("web_score", 0))
                        : Math.min(45, clamp(x.optInt("web_score", 0)));
                c.evidence = clean(x.optString("evidence", ""));
                for (String rawFact : strings(x.optJSONArray("candidate_facts"), 14)) {
                    if (!reservedResolverFact(rawFact)) {
                        addOnce(c.candidateFacts, rawFact);
                    }
                }
                for (String contradiction : strings(x.optJSONArray("contradictions"), 10)) {
                    if (isEvidenceGap(contradiction)) {
                        addOnce(c.candidateFacts, "evidence_gap=" + clean(contradiction));
                    } else {
                        addOnce(c.contradictions, contradiction);
                    }
                }
                for (String feature : matchedFeatures) {
                    addOnce(c.candidateFacts, "photo_feature=" + feature);
                }
                for (String conflict : strings(x.optJSONArray("conflicting_distinctive_features"), 8)) {
                    addOnce(c.contradictions, "STRONG: " + conflict);
                }
                int sourceCompositeCount = PhotoIdentityPolicy
                        .sourceSupportedCompositeFieldCount(id, c, matchedSource);
                matchedPhotoIdentityCount = Math.max(matchedPhotoIdentityCount,
                        sourceCompositeCount);
                if (codeBindingConfidence >= 90) {
                    matchedPhotoIdentityCount = Math.max(matchedPhotoIdentityCount,
                            Math.min(3, id.photoIdentityFields.size()));
                }
                boolean deterministicCompositeSupport = sourceGrounded
                        && PhotoIdentityPolicy.observationStrong(id)
                        && PhotoIdentityPolicy.isComposite(id)
                        && sourceCompositeCount >= 5 && sourceConfidence >= 75
                        && disproof && c.textScore >= 82 && c.webScore >= 55
                        && !majorGeometryConflict
                        && !UniversalConsistencyGate.strongCandidateConflict(c);
                boolean returnedPhotoSupport = x.optBoolean(
                        "photo_identity_supported", false)
                        && matchedPhotoIdentityCount >= Math.min(3,
                        id.photoIdentityFields.size());
                boolean photoIdentitySupported = sourceGrounded
                        && PhotoIdentityPolicy.observationStrong(id)
                        && (codeBindingConfidence >= 90
                        || deterministicCompositeSupport || returnedPhotoSupport);
                boolean labelCooccurrenceSupport = sourceGrounded
                        && matchedFeatures.size() >= 3
                        && c.textScore >= 68 && c.webScore >= 60;
                c.layoutScore = rawLayoutScore;
                if (!visualReference) {
                    c.layoutScore = Math.min(labelCooccurrenceSupport ? 72 : 55,
                            c.layoutScore);
                } else if (visualConfidence < 50) {
                    c.layoutScore = Math.min(35, c.layoutScore);
                }
                addOnce(c.candidateFacts, "resolver_index=" + i);
                addOnce(c.candidateFacts, "source_url=" + sourceUrl);
                addOnce(c.candidateFacts, "source_grounded=" + sourceGrounded);
                addOnce(c.candidateFacts, "source_url_recovered="
                        + (sourceGrounded && !normalizeUrl(sourceUrl).equals(
                        normalizeUrl(clean(x.optString("source_url", ""))))));
                addOnce(c.candidateFacts, "source_exact_reference="
                        + (sourceGrounded && completeReference));
                addOnce(c.candidateFacts, "exact_reference_complete=" + completeReference);
                addOnce(c.candidateFacts, "exact_identity_supported=" + exactSupported);
                addOnce(c.candidateFacts, "source_identity_confidence=" + sourceConfidence);
                addOnce(c.candidateFacts, "same_entity_role=true");
                addOnce(c.candidateFacts, "relationship_only=false");
                addOnce(c.candidateFacts, "disproof_passed=" + disproof);
                addOnce(c.candidateFacts, "visual_reference_checked=" + visualReference);
                addOnce(c.candidateFacts, "visual_match_confidence=" + visualConfidence);
                addOnce(c.candidateFacts, "major_geometry_mismatch=" + majorGeometryConflict);
                addOnce(c.candidateFacts, "photo_identity_supported=" + photoIdentitySupported);
                addOnce(c.candidateFacts, "photo_identity_kind=" + id.photoIdentityKind);
                addOnce(c.candidateFacts, "photo_identity_matched_count="
                        + matchedPhotoIdentityCount);
                addOnce(c.candidateFacts, "photo_identity_source_match_count="
                        + sourceCompositeCount);
                addOnce(c.candidateFacts, "ocr_code_binding_confidence="
                        + codeBindingConfidence);
                for (String field : matchedPhotoIdentity) {
                    if (matchesAnyCanonical(id.photoIdentityFields, field)) {
                        addOnce(c.candidateFacts, "photo_identity_field=" + field);
                    }
                }
                if (!c.probableReference.isEmpty()) {
                    addOnce(c.candidateFacts, "probable_reference=" + c.probableReference);
                    addOnce(c.candidateFacts, "probable_reference_confidence="
                            + c.probableReferenceConfidence);
                }
                boolean familyIdentitySupported = sourceGrounded && !c.family.isEmpty()
                        && !majorGeometryConflict
                        && (photoIdentitySupported
                        || (visualReference && visualConfidence >= 75
                        && c.layoutScore >= 65 && c.textScore >= 65)
                        || (labelCooccurrenceSupport && c.layoutScore >= 60));
                boolean brandIdentitySupported = familyIdentitySupported && !c.brand.isEmpty();
                addOnce(c.candidateFacts, "family_identity_supported=" + familyIdentitySupported);
                addOnce(c.candidateFacts, "brand_identity_supported=" + brandIdentitySupported);
                ReferenceScopePolicy.enforceCandidateScope(id, c);
                exactSupported = factTrue(c, "exact_identity_supported");
                addOnce(c.candidateFacts, "identity_level="
                        + (!c.model.isEmpty() && completeReference ? "model"
                        : !c.family.isEmpty() ? "family" : "brand"));
                if (completeReference && sourceGrounded && !c.model.isEmpty()) {
                    addOnce(c.candidateFacts, "model_code=" + c.model);
                }
                CollectibleCardIdentityPolicy.applyCandidateGate(id, c);
                String scopeViolation = ReferenceScopePolicy.hardViolation(id, c);
                if (!scopeViolation.isEmpty()) {
                    c.hardRejected = true;
                    addOnce(c.hardViolations, scopeViolation);
                    addOnce(c.contradictions, "STRONG: " + scopeViolation);
                }
                if (c.hardRejected) {
                    id.rejectedCandidates.add(c);
                    continue;
                }
                if (majorGeometryConflict) {
                    c.hardRejected = true;
                    c.hardViolations.add("major photographed geometry conflict");
                    id.rejectedCandidates.add(c);
                    continue;
                }
                c.totalScore = candidateTotal(c, primary, id);
                c.totalScore = Math.max(c.totalScore,
                        CollectibleCardIdentityPolicy.supportedScore(id, c));
                if (!sourceGrounded) {
                    c.totalScore = Math.min(c.totalScore, 55);
                } else if (primary == null && !visualReference && !labelCooccurrenceSupport
                        && !photoIdentitySupported) {
                    c.totalScore = Math.min(c.totalScore, 65);
                } else if (primary == null && !visualReference && !photoIdentitySupported) {
                    c.totalScore = Math.min(c.totalScore, 68);
                } else if (primary == null && visualConfidence < 65) {
                    if (!photoIdentitySupported) {
                        c.totalScore = Math.min(c.totalScore, 62);
                    }
                } else if (!completeReference && !photoIdentitySupported) {
                    c.totalScore = Math.min(c.totalScore, familyIdentitySupported ? 78 : 68);
                } else if (!exactSupported && !photoIdentitySupported) {
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
        mergeEquivalentCandidates(id);
        IdentificationEngine.sortCandidates(id);
        Models.CandidateScore top = id.candidates.isEmpty() ? null : id.candidates.get(0);
        String resolvedBrand = clean(p.optString("resolved_brand", ""));
        if ((id.brand.isEmpty() || !BrandBlindPolicy.trustedObservedBrand(id))
                && !resolvedBrand.isEmpty() && supportedBrandCandidate(id, resolvedBrand)) {
            id.brand = resolvedBrand;
            id.brandEvidence = "verified_web";
            id.brandRoleConfidence = Math.max(id.brandRoleConfidence, 80);
        }
        if (top == null) {
            SealedProductIdentityPolicy.applyPhotoTupleFallback(id);
            if (SealedProductIdentityPolicy.canConfirmPhotoTupleWithoutCandidate(id)) {
                SealedProductIdentityPolicy.confirmPhotoTupleWithoutCandidate(id);
                return;
            }
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
                    "ONE-PASS v0.90: nessun candidato grounded superstite; l'eventuale identità composita stampata resta disponibile a livello famiglia.");
            return;
        }

        SealedProductIdentityPolicy.applyPhotoTupleFallback(id);
        if (SealedProductIdentityPolicy.canConfirmPhotoTupleWithoutCandidate(id)
                && !factTrue(top, "exact_identity_supported")) {
            SealedProductIdentityPolicy.confirmPhotoTupleWithoutCandidate(id);
            return;
        }

        if (!top.brand.isEmpty() && (id.brand.isEmpty()
                || !BrandBlindPolicy.trustedObservedBrand(id))
                && factTrue(top, "brand_identity_supported")) {
            id.brand = top.brand;
            id.brandEvidence = "verified_web";
        }
        id.family = top.family;
        id.model = top.model;
        CollectibleCardIdentityPolicy.prepareForCandidateConfirmation(id, top);
        if (factTrue(top, "family_identity_supported")) {
            id.familyConfidence = Math.max(id.familyConfidence, Math.min(88, top.totalScore));
        }
        boolean sourceGrounded = factTrue(top, "source_grounded");
        boolean completeReference = factTrue(top, "exact_reference_complete");
        boolean exactSupported = factTrue(top, "exact_identity_supported");
        boolean disproof = factTrue(top, "disproof_passed");
        int sourceConfidence = factInt(top, "source_identity_confidence");
        boolean visualReference = factTrue(top, "visual_reference_checked");
        int visualConfidence = factInt(top, "visual_match_confidence");
        boolean identityBearingMatch = candidateContainsIdentifier(top, primary)
                || (visualReference && visualConfidence >= 88);
        int margin = id.candidates.size() < 2 ? top.totalScore
                : Math.max(0, top.totalScore - id.candidates.get(1).totalScore);
        boolean directProof = directProof(clean(p.optString("model_proof", "none")));
        boolean confirmed = p.optBoolean("confirmed", false) && sourceGrounded && completeReference
                && exactSupported && !top.model.isEmpty()
                && !CollectibleCardIdentityPolicy.variantUnresolved(id, top)
                && sourceConfidence >= 85 && disproof && directProof
                && identityBearingMatch && top.totalScore >= 85
                && (margin >= 10 || top.hardMatchWeight >= 60)
                && ReferenceScopePolicy.allowsExactConfirmation(id, top);
        boolean photoConfirmed = PhotoIdentityPolicy.canConfirm(id, top, primary, margin)
                && ReferenceScopePolicy.allowsExactConfirmation(id, top);
        boolean cardConfirmed = CollectibleCardIdentityPolicy.canConfirm(id, top);
        if (cardConfirmed) {
            CollectibleCardIdentityPolicy.confirm(id, top);
            return;
        }
        if (SealedProductIdentityPolicy.canConfirmCommercialSku(id, top)) {
            SealedProductIdentityPolicy.confirmCommercialSku(id, top);
            return;
        }
        if (CommercialIdentityPolicy.canConfirmPhoneFamily(id, top)) {
            CommercialIdentityPolicy.confirmPhoneFamily(id, top);
            return;
        }
        if (photoConfirmed) {
            PhotoIdentityPolicy.confirm(id, top);
            return;
        }
        id.marketReady = confirmed;
        id.disproofPassed = confirmed;
        id.modelProof = confirmed ? clean(p.optString("model_proof", "none")) : "none";
        id.modelConfidence = id.model.isEmpty() ? 0 : confirmed
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
            id.decisionReason = "CONFIRMED v0.82: la richiesta multimodale conserva la foto durante Web Search e converge su riferimento completo, prova identitaria e disproof.";
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
        id.decisionReason = "NEED_ANOTHER_PHOTO v0.82: pipeline fermata dopo una richiesta multimodale con massimo 1 Web; nessun retry o ricerca prezzo.";
        CollectibleCardIdentityPolicy.exposeBestSpecificProbable(id, top);
    }

    static void applyResolutionForTest(Models.Identification id, OpenAiClient.Response response,
                                       Models.Identifier primary) {
        applyResolution(id, response, primary);
    }

    /** Equivalent catalog hits are one candidate, not competitors with margin zero. */
    private static void mergeEquivalentCandidates(Models.Identification id) {
        List<Models.CandidateScore> merged = new ArrayList<>();
        for (Models.CandidateScore incoming : id.candidates) {
            Models.CandidateScore same = null;
            String key = candidateIdentityKey(incoming);
            for (Models.CandidateScore existing : merged) {
                if (!key.isEmpty() && key.equals(candidateIdentityKey(existing))) {
                    same = existing;
                    break;
                }
            }
            if (same == null) {
                merged.add(incoming);
                continue;
            }
            same.identifierScore = Math.max(same.identifierScore, incoming.identifierScore);
            same.textScore = Math.max(same.textScore, incoming.textScore);
            same.layoutScore = Math.max(same.layoutScore, incoming.layoutScore);
            same.webScore = Math.max(same.webScore, incoming.webScore);
            same.totalScore = Math.max(same.totalScore, incoming.totalScore);
            same.hardMatchWeight = Math.max(same.hardMatchWeight, incoming.hardMatchWeight);
            same.probableReferenceConfidence = Math.max(same.probableReferenceConfidence,
                    incoming.probableReferenceConfidence);
            if (same.probableReference.isEmpty()) same.probableReference = incoming.probableReference;
            if (same.model.isEmpty()) same.model = incoming.model;
            if (same.evidence.isEmpty() || incoming.evidence.length() > same.evidence.length())
                same.evidence = incoming.evidence;
            mergeStrings(same.candidateFacts, incoming.candidateFacts);
            mergeStrings(same.contradictions, incoming.contradictions);
            mergeStrings(same.hardMatches, incoming.hardMatches);
            mergeStrings(same.hardViolations, incoming.hardViolations);
        }
        id.candidates.clear();
        id.candidates.addAll(merged);
    }

    private static String candidateIdentityKey(Models.CandidateScore c) {
        if (c == null) return "";
        String strongest = !clean(c.probableReference).isEmpty()
                ? c.probableReference : c.displayName();
        return canonicalWords(c.brand + " " + c.family + " " + strongest);
    }

    private static void mergeStrings(List<String> into, List<String> from) {
        for (String value : from) addOnce(into, value);
    }

    private static String canonicalWords(String value) {
        return Normalizer.normalize(clean(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String buildMultimodalPrompt(Models.LocalScan local, String details) {
        StringBuilder labeled = new StringBuilder();
        if (local != null) {
            for (Models.Identifier x : local.identifiers) {
                if (x != null && LocalVisionEngine.isStrongIdentifierLabel(x.label)
                        && (safe(x.origin).toLowerCase(Locale.ROOT).contains("_labeled")
                        || safe(x.origin).toLowerCase(Locale.ROOT).contains("barcode"))) {
                    if (labeled.length() > 0) {
                        labeled.append(" | ");
                    }
                    labeled.append(x.label).append('=').append(x.value);
                }
            }
        }
        List<String> localControlCandidates = localDistinctiveControls(local);
        List<String> localWordCandidates = localOcrWordCandidates(local);
        return "Observe and resolve the foreground object in every supplied view and examine it at 0/90/180/270-degree orientations before transcribing text. Ignore people, reflections, walls, furniture, unrelated packaging and nearby objects. If the foreground object is itself a factory-sealed retail/hobby product, its printed box or wrapper is the physical identity-bearing product surface and must not be ignored as packaging. "
                + "Fill observation.visible_labels only with text physically visible in the image; classify its type, entity_role and identity_binding. "
                + "Only text visibly paired with MODEL, P/N, PART, SKU, REF, TYPE, ITEM or a barcode may be an identifier, and only when it is on an identity-bearing plate or body marking of the foreground product. "
                + "A code printed on an instruction card, control legend, manual, removable overlay, packaging, component or nearby object is not an identifier of the foreground product. Unlabelled OCR-like codes are not identifiers. "
                + "MULTI-PHOTO BRAND CONTINUITY: inspect every supplied photo separately, then establish whether they show the same physical object. A manufacturer logo physically printed on the foreground object in any clear view remains the manufacturer anchor for all complementary views. Generic battery wording (for example alkaline/LR6/AA), compliance text, firmware strings and moulded compartment codes are never brands or remote model references and cannot override that anchor. When the same-object continuity is clear and a manufacturer logo is visually unambiguous, return brand_evidence=visible_logo_cross_photo and confidence at least 92. "
                + "Fill observation.photo_identity independently from web retrieval. Set complete=true from the physical photos whenever every discriminator needed to name the photographed item is clear, physically bound to it and mutually consistent; do not set it false merely because a web source has not yet been found. A camera watermark, app overlay, filename, caption or text added to the image must set overlay_or_watermark=true and can never close identity. A truncated code ending in ellipsis is incomplete. "
                + "For a physical identity label, barcode or device identity screen, return the complete model/reference in identity_code and list the bound fields. For a collectible or other multi-field printed item shown in complementary views, evidence_kind=composite_markings may be complete when manufacturer, set/collection, subject/design and the physical item/card number are legible; canonical_name must compose those literal fields without invention. "
                + "COLLECTIBLE CARD INTEGRITY: never use a biography date, birth date, game/season/championship year, narrative prose date or copyright year as the release/set year unless it is explicitly printed as part of the set/collection identity. Never interpret the first two components of a three-part date (for example 8/23/78) as a serial or print-run fraction. A physical card number such as FC7 is a hard discriminator and must reject an FC2 sibling even when the design is similar. An illustrator credit such as 'Illus. Mitsuhiro Arita' is an artist field, never a brand_logo, manufacturer_text, manufacturer, publisher or product candidate. On Pokemon cards, Pokémon is the commercial brand even when Nintendo/Creatures/GAMEFREAK and an illustrator credit appear in fine print. "
                + "CARD FRACTION SEMANTICS: first classify the card domain. On sports/non-TCG collectible cards, an isolated short numeric fraction x/y physically printed inside the card boundary is the specimen print run/serial even when no SERIAL word accompanies it; return serial=x/y and serial_binding=physical_card_surface, never card_number=x. On trading-card games such as Pokemon, a fraction such as 10/102 in the collector-number area is the card's checklist/collector number: return card_number=10/102 and never serial. A TCG print run is allowed only when a separate stamped fraction is explicitly identifiable as limited/numbered; return limited_serial_marking=x/y. Inspect all four corners at high visual attention, especially for tiny 1/1 sports-card markings. When a sports-card fraction is visually localized even though OCR missed it, add exactly physical_serial_marking=x/y to variant_facts; never add that structured fact from USER_HINT alone. A four-digit season range such as 2025/26 is a season, not a serial, and the first two components of a three-part date such as 8/23/78 are not a serial. Fractions in an app/gallery/listing overlay are invalid. A checklist print run such as /120 describes a catalog parallel and must never be copied into the photographed specimen identity unless that exact fraction is physically printed on the card. When the physical serial differs from the base checklist print run, keep the photographed serial and leave the exact commercial parallel name unresolved unless the source proves it. Treat an OCR glyph ambiguity as unresolved during observation; the deterministic client may reconcile one repeated B/8, O/0 or I/1 confusion only against a complete grounded source code. POKEMON FRONT IDENTITY: the common card back normally adds no identity information, so a clear front alone may close the card. The final supplied image may be a client-generated diagnostic crop of the first photo, not a new object or independent view; use it only to inspect tiny physical markings. For Base Set inspect the black '1' in a circle with the EDITION ribbon immediately below the lower-left corner of the illustration before deciding the printing, then compare the complete front directly with grounded reference images. A stamp is visual iconography and must be inspected even when OCR does not read its text. Report each cue separately in variant_facts using exactly first_edition_stamp=present|absent|unclear, first_edition_stamp_area_clear=true|false, first_edition_stamp_position=left_below_artwork|other|not_applicable|unclear, illustration_frame_drop_shadow=present|absent|unclear, nintendo_copyright_99=present|absent|unclear and copyright_layout=shadowless|unlimited|unclear. first_edition_stamp=absent is valid only when first_edition_stamp_area_clear=true; otherwise return unclear. nintendo_copyright_99 refers ONLY to the extra 99 inside the Nintendo year sequence (the sequence containing 1995, 96 and 98); the ordinary final Wizards copyright ©1999 is present across printings and is NEVER this cue. If the stamp area is cropped, blurred, covered or reflective, return unclear rather than absent. If the stamp is clearly absent, position must be not_applicable, never other. A stamp is valid only when visibly localized at left_below_artwork; unrelated text or a source title containing '1st Edition' is not physical evidence. EDITION MARK AND PRINT LAYOUT ARE INDEPENDENT AXES: after deciding whether the localized stamp is present, always continue to inspect the frame and Nintendo copyright sequence. A localized genuine stamp establishes 1st Edition but does not by itself establish Shadowless. Report physical_printing=1st Edition Shadowless only when the stamp and independent Shadowless layout cues both converge; report physical_printing=1st Edition Shadowed when the stamp is present and independent shadowed/Unlimited layout cues converge; otherwise report physical_printing=1st Edition without guessing the layout. For an English Base Set card without a 1st Edition stamp, nintendo_copyright_99=present establishes Shadowless and outranks an apparent frame shadow; nintendo_copyright_99=absent together with copyright_layout=unlimited and a genuine right/bottom frame shadow supports Unlimited. Judge the drop shadow only on the narrow right/bottom outer edge of the illustration frame, never from the sleeve, glare, dark holo artwork or yellow card border. Add physical_printing only after the relevant physical cues converge; otherwise state the unresolved axis explicitly. Include subject, holo/non-holo, collector number and the complete combined printing in the commercial identity. "
                + "SEALED RETAIL PRODUCT INTEGRITY: when the unopened box/wrapper is the foreground product, treat manufacturer, season/year, product line, sport/category, format and pack configuration physically printed on that surface as a bound composite identity tuple. Normalize hyphenated categories such as sealed basketball trading-card product as sealed products, never loose cards. Inspect small front-panel season, format and pack/autograph callouts before requesting another view. Classify the manufacturer word as brand_logo or manufacturer_text and also return manufacturer=<literal> inside photo_identity.fields. Do not require a separate MODEL/P/N code for a uniquely named sealed product. A complete front tuple may close a Hobby Box when its printed line, season, sport and configuration match one grounded commercial SKU; a barcode is optional. A complete tuple may use an observed manufacturer/product line in retrieval; this is physical evidence, not a guessed-brand anchor. "
                + "COMMERCIAL SMARTPHONE IDENTITY: pricing groups regional suffixes of the same hardware model together. When SAMSUNG and Galaxy S24 Ultra are physically visible and the four-camera S24 Ultra geometry matches a grounded source, resolve the commercial model as Galaxy S24 Ultra even if SM-S928 is truncated or its regional suffix is unreadable. Keep any guessed SM-S928 suffix out of the exact identity; the missing suffix is not a contradiction and must not force another photo. "
                + "Record geometry and topology in variant_facts, spatial_signature and visual_fingerprint before searching. Candidate hints are hypotheses only and must not steer the first query. "
                + "The first web query must always be domain-unrestricted. It must be brand-neutral when no manufacturer is physically verified. When a manufacturer or product line is physically printed and independently corroborated by local OCR, including on a foreground sealed retail box, the query may contain that observed literal; never substitute a guessed brand. Build it from category, rare geometry and two to four exact stable labels. "
                + "Inside the single web_search tool call, run FOUR distinct queries and up to SIX when a complete photo identity needs source/checklist corroboration or a strong family still lacks a probable reference: (1) neutral category plus rare exact label co-occurrence, (2) geometry/topology plus one rare label, (3) exact visible identifier or identity tuple, (4) manufacturer manual/checklist, and when useful (5) probable model/reference topology and (6) an exact physical-discriminator disproof query. This is still one hosted web_search call. "
                + "No query may contain a guessed manufacturer not physically observed or grounded by the neutral search. In particular, never inject a competing brand merely because it appeared in a Vision hypothesis. "
                + "When at least two locally recovered distinctive labels are visually confirmed, do not stop after one generic query. Prefer their exact quoted co-occurrence and never replace them with a guessed caption. "
                + "A stable control label may narrow retrieval only as quoted co-occurrence evidence; generic controls and changing displays must be excluded. "
                + "A source-backed family or product line is a valid partial candidate even when the exact reference is unavailable. In that case fill family, leave model empty and set exact_reference_complete=false. "
                + "SOURCE SCOPE: a manual covering several models proves only the shared family unless the photographed object carries a unique model binding or a model-specific diagram matches every discriminating physical count. For irrigation controllers compare the photographed station/zone/slider count with an explicitly stated count or diagram in the exact manual; any count mismatch is a hard contradiction. Never infer a station count from digits inside a model number (for example 57004). "
                + "If a complete source-printed model/reference is physically plausible but not proven exact, put it only in probable_reference with a calibrated confidence; never promote it to model. For accessories/remotes, search manuals and parts catalogs for these probable references instead of returning only a generic family. "
                + "When photo_identity.complete=true, compare every field with a grounded product page, catalog or checklist. Set photo_identity_supported=true when the candidate/source supports the base identity tuple and return the matching literals in matched_photo_identity_fields. A source need not print the whole tuple as one title, repeat an individual serial fraction, or contain a listing for the exact physical copy when the supplied views themselves carry those complementary identity fields. "
                + "Fill model only with a complete source-printed model/reference; never put prose such as 'reference not exposed', 'model unresolved' or any evidence disclaimer into model. Missing proof is an evidence gap, not a contradiction. "
                + "Resolution candidates must denote the photographed physical entity and survive direct comparison with the supplied photo. Exact-model confirmation still requires a complete reference. "
                + "LOCAL_OCR_IDENTIFIER_CANDIDATES_REQUIRE_VISUAL_ENTITY_BINDING=" + (labeled.length() == 0 ? "none" : labeled)
                + " | LOCAL_OCR_DISTINCTIVE_CONTROL_CANDIDATES_REQUIRE_VISUAL_CHECK=" + localControlCandidates
                + " | LOCAL_OCR_WORD_CANDIDATES_REQUIRE_VISUAL_CHECK=" + localWordCandidates
                + " | UNLABELLED_LOCAL_OCR=withheld_from_model_and_search"
                + " | USER_HINT_UNTRUSTED_SOFT_PRIOR=" + clip(details, 500);
    }

    static String multimodalPromptForTest(Models.LocalScan local, String details) {
        return buildMultimodalPrompt(local, details);
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
                + "brand_logo, manufacturer_text, identifier, control, transient_display, descriptor, unknown, and assign entity_role plus identity_binding. A time such as 14:21, a counter, a temperature, a percentage, "
                + "day indicators and other changing readouts are transient_display. Button captions such as CYCLE START are control. Only classify text as identifier when it is visibly paired "
                + "with MODEL, P/N, PART, SKU, REF, TYPE, ITEM or a barcode on an identity-bearing plate/body marking of the foreground product. Codes on instruction cards, control legends, manuals, overlays, packaging or components must use identity_binding=false. Unlabelled code-like OCR is not an identifier. Use variant_facts and spatial_signature for foreground geometry, "
                + "control topology, openings, handles, connectors, mounting and materials. candidate_hints/fast_candidates are non-binding hypotheses. Never put a guessed brand/model into visible_labels. "
                + "LOCAL_LABELED_OCR_CANDIDATES_REQUIRE_VISUAL_ENTITY_BINDING=" + (labeled.length() == 0 ? "none" : labeled)
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

    static List<String> localDistinctiveControls(Models.LocalScan local) {
        List<String> lines = new ArrayList<>();
        if (local != null) {
            for (String page : local.textByImage) {
                if (page == null) {
                    continue;
                }
                for (String raw : page.split("[\\r\\n]+")) {
                    String x = clean(raw);
                    if (!x.isEmpty()) {
                        lines.add(x);
                    }
                }
            }
        }
        return SearchEvidenceFilter.distinctiveControls(lines, 6);
    }

    static List<String> localOcrWordCandidates(Models.LocalScan local) {
        List<String> out = new ArrayList<>();
        if (local == null) {
            return out;
        }
        for (String page : local.textByImage) {
            if (page == null) {
                continue;
            }
            for (String raw : page.split("[\\r\\n]+")) {
                String x = clean(raw);
                if (x.length() < 4 || x.length() > 24 || x.contains(" ")
                        || !x.matches("[A-Za-z][A-Za-z&.'-]*")
                        || SearchEvidenceFilter.isControlLabel(x)
                        || SearchEvidenceFilter.isTransientDisplay(x)
                        || SearchEvidenceFilter.probableOcrNoise(x)) {
                    continue;
                }
                addOnce(out, x);
                if (out.size() >= 6) {
                    return out;
                }
            }
        }
        return out;
    }

    private static int candidateTotal(Models.CandidateScore c, Models.Identifier primary,
                                      Models.Identification id) {
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
        score = Math.max(score, PhotoIdentityPolicy.candidateScore(id, c));
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
        String category = id == null ? "" : (safe(id.categoryKey) + " "
                + safe(id.category)).toLowerCase(Locale.ROOT);
        if (category.contains("smartphone") || category.contains("mobile phone")
                || category.contains("cell phone")) {
            return "Apri Impostazioni > Info sul telefono e fotografa la schermata con il codice modello completo; in alternativa fotografa l'etichetta originale della scatola";
        }
        if (id != null && (!id.brandLabels.isEmpty() || !id.visibleLabels.isEmpty())) {
            return "Aggiungi un primo piano nitido dell'etichetta MODEL/P-N oppure una vista diversa che mostri i dettagli distintivi";
        }
        return "Aggiungi una seconda vista più ravvicinata includendo logo, etichetta e dettagli distintivi";
    }

    static String modelAtSupportedLevel(String rawModel, boolean completeReference) {
        String model = clean(rawModel);
        return completeReference && !isEvidenceGap(model) ? model : "";
    }

    static String probableReferenceAtSupportedLevel(String raw, int confidence) {
        String reference = clean(raw);
        if (confidence < 40 || reference.length() < 3 || isEvidenceGap(reference)
                || reference.contains("...") || reference.contains("…")) {
            return "";
        }
        return reference.matches(".*[A-Za-z].*") && reference.matches(".*[0-9].*")
                ? reference : "";
    }

    private static String dedupeLeadingBrand(String value, String brand) {
        String out = clean(value);
        String maker = clean(brand);
        if (maker.isEmpty()) {
            return out;
        }
        String doubled = maker + " " + maker + " ";
        while (out.toLowerCase(Locale.ROOT).startsWith(doubled.toLowerCase(Locale.ROOT))) {
            out = maker + " " + clean(out.substring(doubled.length()));
        }
        return out;
    }

    private static String normalizedCandidateName(String brand, String family,
                                                  String model) {
        Models.CandidateScore candidate = new Models.CandidateScore();
        candidate.brand = clean(brand);
        candidate.family = clean(family);
        candidate.model = clean(model);
        return candidate.displayName();
    }

    static boolean isEvidenceGap(String raw) {
        String x = clean(raw).toLowerCase(Locale.ROOT);
        if (x.isEmpty()) {
            return false;
        }
        return x.contains("cannot be established")
                || x.contains("cannot be confirmed")
                || x.contains("does not establish")
                || x.contains("does not prove")
                || x.contains("does not expose")
                || x.contains("not exposed")
                || x.contains("not proven")
                || x.contains("model unresolved")
                || x.contains("reference unresolved")
                || x.contains("suffix unresolved")
                || x.contains("no labelled model")
                || x.contains("no labeled model")
                || x.contains("no complete reference")
                || x.contains("lacks a complete reference")
                || x.contains("missing exact reference")
                || x.contains("exact model marking is not visible")
                || x.contains("model marking is not visible")
                || x.contains("source text was not sufficient to verify")
                || x.contains("source text is not sufficient to verify");
    }

    private static String normalizedFamily(String rawFamily, String brand) {
        String family = clean(rawFamily);
        if (family.isEmpty() || isEvidenceGap(family)) {
            return "";
        }
        String maker = clean(brand);
        if (!maker.isEmpty() && family.toLowerCase(Locale.ROOT)
                .startsWith(maker.toLowerCase(Locale.ROOT) + " ")) {
            family = clean(family.substring(maker.length()));
        }
        family = family.replaceAll("(?i)\\bChrome Updates Basketball\\b",
                "Chrome Update Basketball");
        return family;
    }

    private static boolean supportedBrandCandidate(Models.Identification id, String brand) {
        if (id == null || empty(brand)) {
            return false;
        }
        String wanted = canon(brand);
        for (Models.CandidateScore candidate : id.candidates) {
            if (candidate != null && !candidate.hardRejected
                    && wanted.equals(canon(candidate.brand))
                    && factTrue(candidate, "brand_identity_supported")
                    && !UniversalConsistencyGate.strongCandidateConflict(candidate)) {
                return true;
            }
        }
        return false;
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

    private static Models.Source matchedSource(List<Models.Source> sources, String returnedUrl,
                                               Models.CandidateScore candidate,
                                               Models.Identification id) {
        String wanted = normalizeUrl(returnedUrl);
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        for (Models.Source source : sources) {
            String actual = normalizeUrl(source == null ? "" : source.url);
            if (!wanted.isEmpty() && !actual.isEmpty()
                    && (wanted.equals(actual) || wanted.startsWith(actual)
                    || actual.startsWith(wanted))) {
                return source;
            }
        }
        Models.Source best = null;
        int bestScore = 0;
        int secondScore = 0;
        for (Models.Source source : sources) {
            if (source == null || empty(source.url)) {
                continue;
            }
            String hay = canon(source.title + " " + source.snippet + " " + source.url);
            String brand = canon(candidate == null ? "" : candidate.brand);
            int score = 0;
            score = Math.max(score, phraseSourceScore(hay,
                    candidate == null ? "" : candidate.model, 95));
            score = Math.max(score, phraseSourceScore(hay,
                    candidate == null ? "" : candidate.probableReference, 90));
            score = Math.max(score, phraseSourceScore(hay,
                    id == null ? "" : id.photoIdentityCode, 100));
            score = Math.max(score, phraseSourceScore(hay,
                    candidate == null ? "" : candidate.family, 75));
            score = Math.max(score, phraseSourceScore(hay,
                    id == null ? "" : id.photoIdentityName, 82));
            if (!brand.isEmpty() && hay.contains(brand)) {
                score += 12;
            }
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = source;
            } else if (score > secondScore) {
                secondScore = score;
            }
        }
        return bestScore >= 82 && (bestScore - secondScore >= 10 || secondScore == 0)
                ? best : null;
    }

    private static int phraseSourceScore(String canonicalSource, String phrase, int value) {
        String p = canon(phrase);
        if (p.length() < 6 || canonicalSource.isEmpty() || !canonicalSource.contains(p)) {
            return 0;
        }
        int specific = 0;
        for (String token : clean(phrase).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() >= 3 && !token.matches(
                    "product|remote|control|manual|series|device|object|smart|television|model")) {
                specific++;
            }
        }
        return specific >= 2 || p.matches(".*[A-Z].*[0-9].*") ? value : 0;
    }

    private static int matchedPhotoIdentityCount(List<String> observed, List<String> returned) {
        int count = 0;
        if (observed == null || returned == null) {
            return 0;
        }
        for (String x : returned) {
            if (matchesAnyCanonical(observed, x)) {
                count++;
            }
        }
        return Math.min(count, observed.size());
    }

    private static boolean matchesAnyCanonical(List<String> values, String candidate) {
        String wanted = canon(candidate);
        if (wanted.length() < 2 || values == null) {
            return false;
        }
        for (String raw : values) {
            String actual = canon(raw);
            if (!actual.isEmpty() && (actual.equals(wanted)
                    || actual.contains(wanted) || wanted.contains(actual))) {
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
                || "exact_identifier".equals(x) || "photo_complete_identity".equals(x);
    }

    private static boolean candidateContainsIdentifier(Models.CandidateScore candidate,
                                                       Models.Identifier primary) {
        if (candidate == null || primary == null) {
            return false;
        }
        String wanted = canon(primary.value);
        if (wanted.length() < 3) {
            return false;
        }
        String identity = canon(join(candidate.brand, candidate.family, candidate.model));
        return identity.contains(wanted) || wanted.contains(canon(candidate.model));
    }

    private static boolean reservedResolverFact(String raw) {
        String x = safe(raw).toLowerCase(Locale.ROOT);
        return x.startsWith("source_grounded=") || x.startsWith("source_exact_reference=")
                || x.startsWith("exact_reference_complete=")
                || x.startsWith("exact_identity_supported=")
                || x.startsWith("source_identity_confidence=")
                || x.startsWith("same_entity_role=") || x.startsWith("relationship_only=")
                || x.startsWith("disproof_passed=") || x.startsWith("model_code=")
                || x.startsWith("resolver_index=") || x.startsWith("source_url=")
                || x.startsWith("visual_reference_checked=")
                || x.startsWith("visual_match_confidence=")
                || x.startsWith("major_geometry_mismatch=")
                || x.startsWith("source_url_recovered=")
                || x.startsWith("probable_reference=")
                || x.startsWith("probable_reference_confidence=")
                || x.startsWith("photo_identity_supported=")
                || x.startsWith("photo_identity_kind=")
                || x.startsWith("photo_identity_matched_count=")
                || x.startsWith("photo_identity_source_match_count=")
                || x.startsWith("ocr_code_binding_confidence=")
                || x.startsWith("photo_identity_field=")
                || x.startsWith("family_identity_supported=")
                || x.startsWith("brand_identity_supported=")
                || x.startsWith("identity_level=");
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
