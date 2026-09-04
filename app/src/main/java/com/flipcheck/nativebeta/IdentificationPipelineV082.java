package com.flipcheck.nativebeta;

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
        LocalEvidenceBootstrap.apply(id,local);
        if (images == null || images.isEmpty()) {
            stopForPhoto(id, "Aggiungi una foto chiara dell'oggetto intero",
                    "Non è disponibile una vista da analizzare.", "Nessuna immagine disponibile.");
            return id;
        }
        if (!UniversalConsistencyGate.visionBudgetAvailable(usage)
                || !UniversalConsistencyGate.discoveryBudgetAvailable(usage)) {
            id.visionResponseStatus="BUDGET_EXHAUSTED";
            finishTechnicalFailure(id,"Budget della scansione già esaurito: nessuna nuova foto richiesta.");
            return id;
        }

        OpenAiClient.Response firstVision;
        try {
            firstVision = client.observe(new ArrayList<>(images), buildMultimodalPrompt(local, details));
        } catch (Exception failure) {
            firstVision=null;id.visionResponseStatus=technicalStatus(failure);
            id.visionFinishReason=safe(failure.getMessage());id.pipelineFailureDomain="PRIMARY_VISION";
        }
        if (firstVision != null) {
            IdentificationEngine.collectStage(id, usage, firstVision,
                    "production-vision-1-observation-v119-no-web");
            id.visionResponseStatus=firstVision.technicalStatus;
            id.visionFinishReason=empty(firstVision.incompleteReason)?firstVision.technicalStatus:firstVision.incompleteReason;
        }
        if(technicalFailure(firstVision)){
            // Keep every complete fact recovered from the truncated response before retrying.
            if(firstVision!=null&&firstVision.payload!=null&&firstVision.payload.length()>0)
                ingestPartialTechnicalPayload(id,firstVision.payload,local);
            if(technicalRetryAllowed(usage)){
                id.technicalRetryCount++;
                try{
                    OpenAiClient.Response retry=client.observeTechnicalRecovery(new ArrayList<>(images),
                            buildMultimodalPrompt(local,details)+"\nReturn the shortest valid JSON possible; omit no required key.");
                    if(retry!=null)IdentificationEngine.collectStage(id,usage,retry,
                            "technical-vision-retry-compact-same-images");
                    firstVision=retry;id.visionResponseStatus=retry==null?"NETWORK_ERROR":retry.technicalStatus;
                    id.visionFinishReason=retry==null?"null_retry_response":
                            (empty(retry.incompleteReason)?retry.technicalStatus:retry.incompleteReason);
                }catch(Exception retryFailure){firstVision=null;id.visionResponseStatus=technicalStatus(retryFailure);
                    id.visionFinishReason=safe(retryFailure.getMessage());}
            }
            if(technicalFailure(firstVision)){
                finishTechnicalFailure(id,"Vision non ha completato una risposta valida dopo il recupero tecnico. Le prove OCR locali sono state conservate.");
                IdentificationEngine.finalizeOutput(id,null);return id;
            }
        }

        JSONObject observation = firstVision.payload;
        if("CONTENT_INSUFFICIENT".equals(firstVision.technicalStatus)){
            stopForPhoto(id, profileAwarePhotoRequest(id),
                    "Le immagini non mostrano dati leggibili sufficienti per il profilo rilevato.",
                    "CONTENT_INSUFFICIENT: richiesta fotografica basata sul contenuto, non su un errore tecnico.");
            return id;
        }
        if (!observationUsable(firstVision, observation)) {
            stopForPhoto(id, profileAwarePhotoRequest(id),
                    "L'oggetto non è stato osservato con sufficiente affidabilità; eventuali risultati web non vengono utilizzati.",
                    "FAIL-CLOSED v0.82: osservazione multimodale non valida.");
            return id;
        }

        boolean primaryClosed=parsePrimaryObservationAndAttemptClosure(id, observation, local);
        if (requiresMandatoryCardSecondVision(id,usage)) {
            id.additionalVisionReason="tcg_edition_missing_after_primary; focused_crop_retry_within_budget";
            try {
                OpenAiClient.Response focused=client.verifyTcgPhysicalEdition(TcgEditionCropper.prepare(images),
                        "PRIMARY_PHYSICAL_FIELDS="+clip(id.canonicalPhysicalFields,1000)
                                +"\nPRIMARY_PHOTO_FIELDS="+clip(id.canonicalPhotoFields,1200)
                                +"\nDo not trust candidate or Web edition values.");
                if(focused!=null)IdentificationEngine.collectStage(id,usage,focused,
                        "tcg-focused-edition-vision-v130");
                id.discriminativeVisionCount++;
                TcgPhysicalEditionPolicy.mergeFocusedResult(id,focused);
                primaryClosed=PhotographicIdentityClosure.apply(id,"after_tcg_focused_edition_retry")||id.identityConfirmed;
                ConsistencyInvariantChecker.enforce(id,"after_tcg_focused_edition_retry");
            } catch(Exception focusedFailure) {
                id.additionalVisionReason="tcg_focused_edition_retry_failed:"+technicalStatus(focusedFailure);
                TcgPhysicalEditionPolicy.normalize(id,"focused_retry_failed_evidence_preserved");
            }
        }
        if(!primaryClosed&&PhysicalIdentityRecovery.eligible(id,usage)){
            id.additionalVisionReason="unresolved_discriminative_electronics_field; focused_recovery_within_budget";
            try{
                OpenAiClient.Response recovery=client.recoverPhysicalIdentity(new ArrayList<>(images),PhysicalIdentityRecovery.prompt(id));
                if(recovery!=null)IdentificationEngine.collectStage(id,usage,recovery,"focused-physical-recovery-v131");
                id.discriminativeVisionCount++;
                mergePhysicalRecovery(id,recovery,local);
                primaryClosed=PhotographicIdentityClosure.apply(id,"after_focused_physical_recovery")||id.identityConfirmed;
                ConsistencyInvariantChecker.enforce(id,"after_focused_physical_recovery");
            }catch(Exception recoveryFailure){id.additionalVisionReason="focused_physical_recovery_failed:"+technicalStatus(recoveryFailure);}
        }
        if (primaryClosed||id.identityConfirmed) {
            enrichConfirmedIdentity(id, client, usage,images);
            IdentificationEngine.finalizeOutput(id, null);
            return id;
        }

        // A second Vision is allowed only for a localized discriminative field and remains bounded by budget.
        if (PhotographicIdentityClosure.mayRequestAnotherPhoto(id)) {
            id.additionalVisionReason="no_automatic_discriminative_call; requested_field="
                    +safe(id.discriminativeField);
        }
        Models.Identifier primary = selectPrimary(local, id);
        if (primary != null) {
            id.primaryIdentifier = clean(primary.value);
            id.identifierVariants.addAll(LocalVisionEngine.normalizeIdentifierVariants(primary.value));
        }
        HardConstraintEngine.apply(id, primary);

        if (ProductionClosureCheckpoint.attempt(id, "before_web_enrichment")) {
            enrichConfirmedIdentity(id, client, usage,images);
            IdentificationEngine.finalizeOutput(id, primary);
            return id;
        }

        if (!hasResolutionEvidence(id, primary)) {
            finishUnconfirmed(id,"La foto non contiene abbastanza segnali fisici per una tupla univoca.",
                    "insufficient_photographic_tuple");
            IdentificationEngine.finalizeOutput(id, primary);
            return id;
        }

        OpenAiClient.Response combined = client.webStage("resolve",
                buildResolvePrompt(id, primary, details));
        if (combined != null) {
            IdentificationEngine.collectStage(id, usage, combined,
                    "production-web-resolution-v119-after-vision");
        }
        if (combined == null || !combined.complete || !empty(combined.parseError)
                || combined.usage == null || combined.usage.webCalls != 1
                || combined.payload == null || combined.payload.length() == 0) {
            id.webStatus="FAILED";id.marketStatus="NOT_AVAILABLE";
            finishUnconfirmed(id,"La ricerca Web non è disponibile; la decisione resta basata soltanto sulle foto.",
                    "web_unavailable_nonblocking");
            IdentificationEngine.finalizeOutput(id, primary);
            return id;
        }

        id.searchQuery=PhotographicIdentityClosure.webSeed(id);
        NonDestructiveWebEnrichment.apply(id,combined);
        if(!id.catalogVerified)finishUnconfirmed(id,"Nessun candidato Web compatibile con l’intera impronta fotografica.",
                "catalog_candidate_not_compatible");
        FinalIdentityDecisionEngine.freeze(id,"after_web_resolution");
        IdentificationEngine.finalizeOutput(id, primary);
        return id;
    }

    /** Exact parser/checkpoint path used by production and replay regression tests. */
    static boolean parsePrimaryObservationAndAttemptClosure(Models.Identification id,
                                                             JSONObject observation,
                                                             Models.LocalScan local) {
        parseObservation(id, observation);
        OverlayScopePolicy.normalize(id);
        ObservationSanitizer.apply(id);
        collectSoftOcr(id,local);
        id.criticalCardDetailNeedsSecondVision=false;
        TcgPhysicalEditionPolicy.normalize(id,"after_primary_observation");
        boolean closed=PhotographicIdentityClosure.apply(id,"production_after_multimodal_parse");
        ProductionClosureCheckpoint.record(id,"production_after_multimodal_parse",closed);
        ConsistencyInvariantChecker.enforce(id,"after_primary_multimodal_parse");
        return closed||id.identityConfirmed;
    }

    static boolean requiresMandatoryCardSecondVision(Models.Identification id,
                                                      Models.Usage usage) {
        return TcgPhysicalEditionPolicy.needsFocusedPass(id,usage);
    }

    private static void mergePhysicalRecovery(Models.Identification id,
                                              OpenAiClient.Response recovery,
                                              Models.LocalScan local) {
        if (!PhysicalIdentityRecovery.apply(id, recovery)) return;
        OverlayScopePolicy.normalize(id);
        ObservationSanitizer.apply(id);
        PhotographicFactNormalizer.normalize(id,"after_focused_vision_merge");
    }

    private static void enrichConfirmedIdentity(Models.Identification id,
                                                OpenAiClient client,
                                                Models.Usage usage,
                                                List<String> images) {
        if(id==null||client==null||usage==null)return;
        id.exactResolutionReason=ExactCatalogResolver.reason(id);
        ProfileQueryBuilder.exactQueries(id);
        if(!ExactCatalogResolver.required(id)){id.disproofStatus=id.catalogVerified?"PASSED":"NOT_EXECUTED";FinalIdentityDecisionEngine.freeze(id,"exact_catalog_already_resolved");return;}
        if(usage.webCalls>=1){id.webStatus="SKIPPED_BUDGET";id.disproofStatus="NOT_EXECUTED";ConfirmedIdentityEnrichment.unavailable(id);return;}
        try{
            id.exactWebResolutionAttempts++;
            OpenAiClient.Response enrichment=client.enrichConfirmedIdentity(
                    ConfirmedIdentityEnrichment.prompt(id));
            if(enrichment!=null)IdentificationEngine.collectStage(id,usage,enrichment,
                    "post-photographic-closure-enrichment-v124");
            ConfirmedIdentityEnrichment.apply(id,enrichment);
            if(!id.catalogVerified&&usage.webCalls<2&&usage.costUsd+0.008d<=0.025d
                    &&("AMBIGUOUS".equals(id.catalogCompatibilityStatus)||"NO_STRUCTURED_CANDIDATE".equals(id.catalogCompatibilityStatus))){
                id.secondWebResolutionReason="first_batch_"+id.catalogCompatibilityStatus.toLowerCase(Locale.ROOT)+"; projected_total_cost_within_cap";
                id.exactWebResolutionAttempts++;
                OpenAiClient.Response second=client.enrichConfirmedIdentity(ConfirmedIdentityEnrichment.prompt(id)
                        +"\nSECOND_AND_FINAL_RESOLUTION: use unresolved hierarchy="+id.catalogHierarchy
                        +" conflicts="+id.catalogConflicts+". Return alternative authoritative/checklist candidates; do not repeat rejected URLs.");
                if(second!=null)IdentificationEngine.collectStage(id,usage,second,"exact-catalog-resolution-second-and-final");
                ConfirmedIdentityEnrichment.apply(id,second);
            }
            // Resolve a post-Web conflict only from the evidence already normalized.
            // A further Vision call seeded by the disputed tuple would be circular.
            PostEnrichmentConsistencyChecker.apply(id);
            CanonicalIdentityComposer.refreshConfirmedCard(id);
            ConsistencyInvariantChecker.enforce(id,"post_catalog_conflict_check");
        }catch(Exception ignored){ConfirmedIdentityEnrichment.unavailable(id);}
        FinalIdentityDecisionEngine.freeze(id,"after_enrichment_route");
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
        return response.payload.has("content_sufficient")
                ? response.payload.optBoolean("content_sufficient",false)
                : response.payload.optBoolean("observation_valid", false);
    }

    static boolean observationUsable(OpenAiClient.Response response, JSONObject observation) {
        return response != null && response.complete && empty(response.parseError)
                && observation != null && (observation.has("content_sufficient")
                ? observation.optBoolean("content_sufficient",false)
                : observation.optBoolean("observation_valid", false));
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
        if(p!=null&&p.has("facts")){
            parseCompactObservation(id,p);
            return;
        }
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
            id.photoIdentityExternalWatermark = photoIdentity.optBoolean(
                    "external_watermark", false);
            id.photoIdentityIdentityObscured = photoIdentity.optBoolean(
                    "identity_obscured", false);
            id.photoIdentityAmbiguous=photoIdentity.optBoolean("identity_ambiguous",false);
            id.photoAlternativeCount=photoIdentity.optInt("materially_distinct_alternatives",0);
            id.discriminativeField=clean(photoIdentity.optString("missing_discriminative_field",""));
            id.discriminativeFieldVisible=photoIdentity.optBoolean("discriminative_field_visible",false);
            id.photoIdentityConfidence = clamp(photoIdentity.optInt("confidence", 0));
            id.photoIdentityFields.addAll(strings(photoIdentity.optJSONArray("fields"), 24));
            id.photoIdentityComplete = photoIdentity.optBoolean("complete", false)
                    && id.photoIdentityPhysicalBinding
                    && !UniversalIdentityClosure.externalWatermarkObscuresIdentity(id)
                    && !id.photoIdentityName.isEmpty();
            EvidenceLedger.ingestPhotoObservation(id,photoIdentity);
            JSONArray physicalCandidates=photoIdentity.optJSONArray("candidates");
            if(physicalCandidates!=null)for(int i=0;i<physicalCandidates.length();i++){
                JSONObject raw=physicalCandidates.optJSONObject(i);if(raw==null)continue;
                Models.CandidateScore c=CandidateCanonicalizer.fromJson(raw);
                c.candidateFacts.add("origin=photo_candidate");id.candidates.add(c);
            }
        }
        CategoryPresentationPolicy.apply(id);

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
                    addOnce(id.externalLabels, text);
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
            id.brandEvidence = "unknown";
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

    private static void parseCompactObservation(Models.Identification id,JSONObject p){
        id.categoryKey=clean(p.optString("category","other_collectible")).toLowerCase(Locale.ROOT)
                .replace('-','_').replace(' ','_');
        id.category=CategoryPresentationPolicy.humanCategory(id.categoryKey);
        id.categoryConfidence=compactCategoryConfidence(p.optJSONArray("facts"),p.optBoolean("content_sufficient",false));
        id.categoryStatus=p.optBoolean("content_sufficient",false)?"CONFIRMED":"UNRESOLVED";
        id.photoViews.addAll(strings(p.optJSONArray("views"),6));
        EvidenceLedger.ingestCompactPhotoObservation(id,p.optJSONArray("facts"));
        String viewText=id.photoViews.toString().toLowerCase(Locale.ROOT);
        if(p.optBoolean("content_sufficient",false)&&(viewText.contains("front")||viewText.contains("fronte")))
            EvidenceLedger.addPhotoFact(id,"front_complete","true","direct_photo_observation",90,0,"front","full_object_bounds","complete_identity_bearing_view");
        id.photoIdentityName=clean(p.optString("identity_hint",""));
        id.photoIdentityKind="compact_evidence_ledger";
        id.photoIdentityPhysicalBinding=!id.evidenceLedger.isEmpty();
        JSONArray missing=p.optJSONArray("missing_discriminators");
        List<String> missingValues=strings(missing,8);
        id.discriminativeField=missingValues.isEmpty()?"":missingValues.get(0);
        id.discriminativeFieldVisible=false;
        JSONArray candidates=p.optJSONArray("candidates");
        if(candidates!=null)for(int i=0;i<candidates.length();i++){
            JSONObject raw=candidates.optJSONObject(i);if(raw==null)continue;
            Models.CandidateScore c=CandidateCanonicalizer.fromJson(raw);
            c.candidateFacts.add("origin=photo_candidate");id.candidates.add(c);
        }
        id.photoAlternativeCount=id.candidates.size();
        id.photoIdentityAmbiguous=id.photoAlternativeCount>1&&!id.discriminativeField.isEmpty();
        id.photoIdentityConfidence=id.candidates.size()==1?id.candidates.get(0).totalScore:
                (id.photoIdentityName.isEmpty()?0:85);
        id.photoIdentityComplete=p.optBoolean("content_sufficient",false)
                &&missingValues.isEmpty()&&!id.photoIdentityName.isEmpty()
                &&id.photoAlternativeCount<=1&&id.photoIdentityPhysicalBinding;
        id.title=id.photoIdentityName.isEmpty()?id.category:id.photoIdentityName;
        CategoryPresentationPolicy.apply(id);
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
            if (ProductionClosureCheckpoint.attempt(id, "before_no_candidate_fallback")) {
                ConfirmedIdentityEnrichment.apply(id,response);
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
            id.familyConfidence = Math.max(id.familyConfidence,
                    Math.min(top.totalScore,Math.max(id.familyConfidence,id.mainIdentityConfidence)));
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
        if (ProductionClosureCheckpoint.attempt(id, "after_candidate_selection")) {
            ConfirmedIdentityEnrichment.apply(id,response);
            return;
        }
        id.marketReady = false;
        id.disproofPassed = false;
        id.modelProof = "none";
        id.modelConfidence = id.model.isEmpty() ? 0 : Math.min(84, Math.max(35, top.totalScore));
        id.verificationSummary = clean(p.optString("evidence", ""));
        if (id.verificationSummary.isEmpty()) {
            id.verificationSummary = "Leader " + top.displayName() + " · " + top.totalScore
                    + "/100 · margine " + margin + ".";
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
        ProductionClosureCheckpoint.attempt(id, "before_probable_exposure");
        CollectibleCardIdentityPolicy.exposeBestSpecificProbable(id, top);
        if(ProductionClosureCheckpoint.attempt(id, "after_probable_exposure"))
            ConfirmedIdentityEnrichment.apply(id,response);
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
        return "Analyze the foreground physical object across all supplied images in one multimodal observation. Treat a sealed wrapper or box as the object surface when it is the product. Ignore gallery UI, timestamps, listing text, people, reflections and unrelated objects. "
                + "EVIDENCE LEDGER CONTRACT: every identity fact must be emitted once in facts with key, literal value, confidence, image, side, exact location and semantic role. The application records these as PRIMARY_VISION, not as OCR or direct human observation. Do not copy user hints, OCR from outside the object, or web knowledge into them. "
                + "Choose the category profile first: sports card, TCG, sealed trading-card product, consumer electronics, television/audio-video/appliance remote control, or other object. A profile is complete when the available photographed discriminators identify one physical object and no material photographed contradiction exists. Year, reverse, barcode, serial and catalog number are useful but never universally mandatory. A single sharp identity-bearing front may be sufficient, especially for TCG and sealed products. "
                + "Set identity_ambiguous=true only when at least two materially different physical identities fit the photographs. Then report their count, exactly one missing_discriminative_field, and whether that field is already visible. Never manufacture ambiguity from missing catalog or market data. Set complete=true whenever the photographed tuple is unique even if the Web is unavailable. "
                + "CANDIDATE CANONICALIZATION INPUT: return candidates as structured identity axes, not alternative prose titles. Descriptions, language labels, catalog aliases and finish wording for the same object are one candidate. materially_distinct_variant=true only when a different physical object remains after comparing card number, edition, printing, parallel marker or sealed format. "
                + "Numbers require semantic classification and physical localization. physical_card_number is allowed only for an explicit card/collector/checklist number printed on the card surface, with semantic_role=card_number or collector_number. physical_serial is only a physically localized limited print run x/y. Product codes and barcodes use their own roles. Statistics, ratings, HP/PV, years, dates, activation codes, graphic numbers, UI and watermarks are not card numbers. "
                + "For sports cards collect manufacturer/publisher, set or product line, insert/edition when visible, athlete/subject, team when visible, localized card number when present, and physical parallel/serial when present. A generic reflective, holo, foil or chrome appearance is finish only: it never proves a rare parallel. Emit physical_parallel only with a localized printed name, serial, or a separately localized distinctive marker. "
                + "For TCG collect game/publisher, set and set code/symbol when readable, card name, language, HP/PV as a statistic, moves, artist, rarity, layout/frame, illustration, finish, edition/printing and collector number when visible. Scan the full front and supplied detail crops for edition marks. Emit first_edition_mark=PRESENT only when the physical 1st Edition logo/text is visible; also emit edition=FIRST_EDITION. Keep edition, shadow_status, finish/holo status, rarity and collector number as independent facts. Emit evolution stage only as evolution_stage: it is descriptive and never a set/product line. Finish is a physical variant and never part of the card name. A complete composite front can be unique without a readable collector number; a common reverse is non-identifying. "
                + "For sealed products collect manufacturer, product line, season when visible, sport/category, product_type, format and printed configuration. Emit people pictured on the package only as featured_subject or featured_subjects, never as the product subject/model. Do not require a loose-card number from a sealed product. "
                + "For electronics and remote controls collect product_type, a located brand/logo mark, control/button layout, printed labels, shortcut buttons, navigation topology, numeric keypad, voice control and any literal model/part code. A brand inferred only from shape is a candidate hypothesis, never an observed manufacturer. Preserve remote_feature and printed_label as distinctive evidence. "
                + "overlay_or_watermark is only a warning. external_watermark=true only for an external mark, and identity_obscured=true only if it covers a discriminator. Generic overlay=true alone never invalidates physical evidence. Preserve complementary facts from different sides when the images show the same object. "
                + "Candidate hints are hypotheses only. Never invent a manufacturer, model, variant, number, serial, printing or finish. Keep unclear values unresolved rather than filling them from prior knowledge. "
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
        String seed=PhotographicIdentityClosure.webSeed(id);
        return "Resolve the photographed physical product using one web_search call total. QUERY_PROFILE="+id.queryProfile+". MARKET_ITEM_STATE="+ProfileQueryBuilder.expectedMarketState(id)+". RARE_VARIANT_PHYSICAL_PROOF="+id.rareVariantPhysicalProof+". PHOTO_TUPLE_QUERY_SEED="+seed+". STAGED_CATALOG_PLAN="+ProfileQueryBuilder.stagedPlan(id)+". Discovery must omit contested identifiers; verification and disproof of alternatives happen in this same batch. Build every query from this normalized photographed tuple; do not discard physically read manufacturer, product line, subject, format or code. Return up to six source-only candidates with structured identity axes; descriptions, aliases and already-observed language are not separate candidates. Return every comparable separately with sale status, RAW/GRADED/SEALED state, condition, exact grade, date, source URL, identity_match, variant_specific, variant_key and exclusion reason; never pre-mix buckets. "
                + "Use manufacturer-owned sources first when VISIBLE_MANUFACTURER is present. Do not use TRANSIENT_OR_CONTROL_TEXT in any query. "
                + "Identity closure and price availability are independent. The complete source reference must preserve suffixes after / or -. A family prefix is not a complete model. "
                + "A checklist/catalog number must be returned only as source_confirmed_catalog_number and never as a physical number. Return source_confirmed_variant only as source-only catalog metadata. Never query or price a rare parallel when RARE_VARIANT_PHYSICAL_PROOF=false. For sealed products never query pictured people, raw, graded or card number. "
                + "Set source_url to a URL actually retrieved in this call. candidate_facts may contain source-backed dynamic attributes, but model_code only when the source prints the complete reference. "
                + "VISIBLE_MANUFACTURER=" + (BrandBlindPolicy.trustedObservedBrand(id) ? id.brand : "none")
                + " | CATEGORY=" + id.category + " (" + id.categoryConfidence + "%)"
                + " | PRIMARY_LABELED_IDENTIFIER=" + (primary == null ? "none" : primary.label + "=" + primary.value)
                + " | SEARCHABLE_VISIBLE_LABELS=" + labels
                + " | SOFT_OCR_LITERALS_NOT_BRAND_LOCKS=" + id.softOcrLabels
                + " | TRANSIENT_OR_CONTROL_TEXT_EXCLUDED=" + combine(id.transientLabels, id.controlLabels)
                + " | CANONICAL_CLOSURE_INPUT=" + clip(id.closureInputSnapshot, 1400)
                + " | CANONICAL_PHYSICAL_FIELDS=" + clip(id.canonicalPhysicalFields, 900)
                + " | RAW_VISION_ALIASES_EXCLUDED_FROM_QUERY=true";
    }

    private static boolean hasResolutionEvidence(Models.Identification id, Models.Identifier primary) {
        IdentityProfileEngine.PhotoTuple tuple=IdentityProfileEngine.prepare(id);
        IdentityProfileEngine.Profile profile=IdentityProfileEngine.profile(id,tuple);
        boolean canonicalCore=(profile==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT
                &&(!tuple.brand.isEmpty()||!tuple.family.isEmpty()))
                ||((profile==IdentityProfileEngine.Profile.SPORTS_CARD||profile==IdentityProfileEngine.Profile.TCG)
                &&(!tuple.subject.isEmpty()||!tuple.family.isEmpty()||!tuple.brand.isEmpty()))
                ||(!tuple.modelCode.isEmpty()||!tuple.design.isEmpty());
        if(canonicalCore)return true;
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
                        && !containsCanonical(id.externalLabels, x)
                        && containsCanonical(id.visibleLabels, x)
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

    private static void ingestPartialTechnicalPayload(Models.Identification id,JSONObject payload,
                                                      Models.LocalScan local){
        if(id==null||payload==null)return;
        if(payload.has("facts"))parseCompactObservation(id,payload);
        else parseObservation(id,payload);
        collectSoftOcr(id,local);
        PhotographicFactNormalizer.normalize(id,"partial_technical_response");
        id.pipelineFailureDomain="PRIMARY_VISION_TECHNICAL";
    }

    private static boolean technicalFailure(OpenAiClient.Response r){
        if(r==null)return true;
        if("CONTENT_INSUFFICIENT".equals(r.technicalStatus))return false;
        return !r.complete||!empty(r.parseError)||r.payload==null||r.payload.length()==0;
    }

    private static boolean technicalRetryAllowed(Models.Usage usage){
        return usage==null||usage.costUsd<0.025d;
    }

    private static String technicalStatus(Exception failure){String x=safe(failure==null?"":failure.getMessage()).toLowerCase(Locale.ROOT);
        if(x.contains("timeout")||x.contains("timed out"))return "TIMEOUT";
        if(x.contains("json"))return "INVALID_JSON";return "NETWORK_ERROR";}

    private static int compactCategoryConfidence(JSONArray facts,boolean sufficient){if(!sufficient)return 40;int sum=0,count=0,roles=0;
        if(facts!=null)for(int i=0;i<facts.length();i++){JSONObject f=facts.optJSONObject(i);if(f==null)continue;int q=clamp(f.optInt("confidence",0));
            if(q>0){sum+=q;count++;}String role=clean(f.optString("role","")).toLowerCase(Locale.ROOT);if(role.contains("product")||role.contains("brand")||role.contains("subject")||role.contains("sport")||role.contains("game"))roles++;}
        int quality=count==0?55:sum/count;return clamp(45+(quality*3/10)+Math.min(25,roles*4));}

    private static void finishTechnicalFailure(Models.Identification id,String reason){
        CategoryPresentationPolicy.apply(id);PhotographicFactNormalizer.normalize(id,"technical_failure_preserved_evidence");
        id.pipelineFailureDomain="VISION_TECHNICAL";id.identityStatus="UNRESOLVED";
        id.categoryStatus=id.categoryKey.equals("other")?"UNRESOLVED":"PROBABLE";
        id.coreIdentityStatus="TECHNICAL_RETRY_FAILED";id.exactIdentityStatus="NOT_EVALUATED";
        id.variantStatus="NOT_EVALUATED";id.marketStatus="NOT_AVAILABLE";id.webStatus="SKIPPED_TECHNICAL_FAILURE";
        id.marketConfidence=0;id.marketDecisionStatus=HierarchicalIdentityStatus.MARKET_UNAVAILABLE.name();
        id.marketReady=false;id.priceAvailable=false;id.nextPhotoRequest="";id.nextPhotoReason="";
        id.requestedPhotoReason="";id.requestedPhotoProfile="";id.decision="TECHNICAL_ERROR";
        id.blockingReason="vision_technical_failure";id.decisionReason=reason;
        id.finalDecisionReason="technical_failure_not_photo_failure; evidence_preserved=true";
        ConsistencyInvariantChecker.enforce(id,"technical_failure");
    }

    private static String profileAwarePhotoRequest(Models.Identification id){
        String targeted=PhotographicIdentityClosure.targetedPhotoRequest(id);if(!targeted.isEmpty())return targeted;
        String p=safe(id==null?"":id.categoryKey).toLowerCase(Locale.ROOT);
        if(p.contains("tcg"))return "Fotografa nuovamente il fronte completo, diritto e nitido, includendo nome, HP/PV, mosse e angolo del collector number.";
        if(p.contains("sport")&&p.contains("card"))return "Fotografa fronte e retro completi e nitidi, includendo la zona del numero carta.";
        if(p.contains("sealed"))return "Fotografa il fronte completo e il lato con linea prodotto, stagione, configurazione e formato.";
        return "Fotografa l’oggetto intero e la targhetta con MODEL, P/N o codice prodotto, se presente.";
    }

    private static void stopForPhoto(Models.Identification id, String request, String reason, String decision) {
        if (ProductionClosureCheckpoint.attempt(id, "before_stop_for_photo")) return;
        if(id.evidenceLedger.isEmpty()&&id.photoViews.isEmpty()){
            id.identityStatus="UNRESOLVED";id.blockingReason="no_photographic_evidence";
            id.missingDiscriminativeFields="complete_object_photo";id.marketReady=false;
            id.nextPhotoRequest=clean(request);id.nextPhotoReason=clean(reason);id.requestedPhotoReason=clean(reason);
            id.requestedPhotoProfile=requestedProfile(id);
            id.decision="NEED_ANOTHER_PHOTO";id.decisionReason=clean(decision);return;
        }
        finishUnconfirmed(id,reason,decision);
    }

    private static void finishUnconfirmed(Models.Identification id,String reason,String decisionReason){
        if(ProductionClosureCheckpoint.attempt(id,"before_unconfirmed_result"))return;
        CategoryPresentationPolicy.apply(id);
        id.marketReady = false;
        id.disproofPassed = false;
        boolean eligible=PhotographicIdentityClosure.mayRequestAnotherPhoto(id);
        String targeted=eligible?DiscriminativeVisionPolicy.request(id):"";
        boolean request=!targeted.isEmpty();
        id.nextPhotoRequest=targeted;
        id.nextPhotoReason = clean(reason);
        id.requestedPhotoReason=request?DiscriminativeVisionPolicy.reason(id):"";
        id.requestedPhotoProfile=request?requestedProfile(id):"";
        id.identityStatus=request?"AMBIGUOUS":"UNRESOLVED";
        id.decision=request?"NEED_ANOTHER_PHOTO":"PROBABLE";
        id.decisionReason=clean(decisionReason);
        if(id.blockingReason.isEmpty())id.blockingReason=request?"materially_distinct_photo_candidates":"insufficient_photographic_tuple";
        id.priceAvailable=false;id.priceConfidence=0;
        id.priceSummary="mercato non disponibile/non affidabile";
        if(id.marketStatus.equals("NOT_AVAILABLE"))id.comparablesSummary="comparabili non disponibili";
        if (id.title.isEmpty()) {
            id.title = id.category.isEmpty() ? "Oggetto" : id.category;
        }
        if (id.verificationSummary.isEmpty()) {
            id.verificationSummary = clean(reason);
        }
        ConsistencyInvariantChecker.enforce(id,"finish_unconfirmed");
    }

    private static String fallbackPhoto(Models.Identification id) {
        if(TcgFrontIdentityPolicy.isTcg(id))return TcgFrontIdentityPolicy.nextPhotoRequest(id);
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

    private static String requestedProfile(Models.Identification id){String k=safe(id==null?"":id.categoryKey).toLowerCase(Locale.ROOT);
        if(k.contains("tcg"))return "tcg";if(k.contains("sport")&&k.contains("card"))return "sports_card";
        if(k.contains("sealed"))return "sealed_trading_card_product";return "other_collectible";}

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
