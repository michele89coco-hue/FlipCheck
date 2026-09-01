package com.flipcheck.nativebeta;

import androidx.core.os.EnvironmentCompat;
import com.flipcheck.nativebeta.ClarificationPlanner;
import com.flipcheck.nativebeta.GoogleReverseImageEngine;
import com.flipcheck.nativebeta.Models;
import com.flipcheck.nativebeta.OpenAiClient;
import com.flipcheck.nativebeta.PhotoProtocol;
import com.flipcheck.nativebeta.VisualRetrievalEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import org.json.JSONArray;
import org.json.JSONObject;

final class IdentificationEngine {
    private IdentificationEngine() {
    }

    /* JADX WARN: Multi-variable type inference failed */
    static Models.Identification identify(Models.LocalScan localScan, List<String> list, String str, OpenAiClient openAiClient, Models.Usage usage) throws Exception {
        if (IdentificationPipelineV082.enabled()) {
            return IdentificationPipelineV082.identify(localScan, list, str, openAiClient, usage);
        }
        GoogleReverseImageEngine.Outcome outcomeRunPure;
        String str2;
        String str3;
        OpenAiClient openAiClient2;
        int i;
        Models.CandidateScore candidateScore;
        boolean z;
        String str4;
        boolean z2;
        boolean z3;
        Iterator<Models.CandidateScore> it;
        if (GoogleReverseImageEngine.hasKey() && list != null && !list.isEmpty()) {
            outcomeRunPure = GoogleReverseImageEngine.runPure(list, openAiClient, usage);
        } else {
            outcomeRunPure = null;
        }
        List<String> listCropForVision = cropForVision(list);
        OpenAiClient.Response responseVision = openAiClient.vision(listCropForVision, buildVisionPrompt(localScan, str));
        usage.add(responseVision.usage);
        JSONObject jSONObject = responseVision.payload;
        Models.Identification identification = new Models.Identification();
        identification.localScan = localScan;
        identification.title = clean(jSONObject.optString("title", "Oggetto non identificato"));
        str2 = "";
        identification.category = clean(jSONObject.optString("category", ""));
        identification.categoryKey = clean(jSONObject.optString("category_key", "other")).toLowerCase(Locale.ROOT);
        identification.brand = clean(jSONObject.optString("brand", ""));
        identification.brandEvidence = normalizeBrandEvidence(jSONObject.optString("brand_evidence", EnvironmentCompat.MEDIA_UNKNOWN));
        identification.brandRoleConfidence = clamp(jSONObject.optInt("brand_role_confidence", 0));
        identification.brandRoleReason = clean(jSONObject.optString("brand_role_reason", ""));
        identification.family = clean(jSONObject.optString("family", ""));
        identification.model = clean(jSONObject.optString("model", ""));
        identification.categoryConfidence = clamp(jSONObject.optInt("category_confidence", 0));
        identification.familyConfidence = clamp(jSONObject.optInt("family_confidence", 0));
        identification.distinctiveTerms.addAll(strings(jSONObject.optJSONArray("distinctive_terms")));
        identification.visualFacts.addAll(strings(jSONObject.optJSONArray("variant_facts")));
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("visible_labels");
        identification.visibleLabels.addAll(visibleLabelStrings(jSONArrayOptJSONArray));
        applyVisibleBrandAnchor(identification, jSONArrayOptJSONArray);
        identification.spatialSignature.addAll(strings(jSONObject.optJSONArray("spatial_signature")));
        identification.visionCandidates.addAll(strings(jSONObject.optJSONArray("candidate_hints")));
        identification.photoViews.addAll(strings(jSONObject.optJSONArray("photo_views")));
        identification.visualFingerprint = clean(jSONObject.optString("visual_fingerprint", ""));
        identification.visionIdentityConfidence = clamp(jSONObject.optInt("identity_confidence", 0));
        identification.visionIdentityReason = clean(jSONObject.optString("identity_reason", ""));
        BrandBlindPolicy.sanitizeBrandEvidence(identification, localScan);
        parseVisionCandidates(jSONObject.optJSONArray("fast_candidates"), identification);
        CategoryFactPolicy.apply(identification);
        GeometryConsensusEngine.run(identification, localScan, list, str, openAiClient, usage);
        ObservationSanitizer.apply(identification);
        if (outcomeRunPure != null && outcomeRunPure.attempted) {
            identification.decisionReason = outcomeRunPure.summary == null ? "" : outcomeRunPure.summary;
            for (Models.CandidateScore candidateScore2 : outcomeRunPure.candidates) {
                if (candidateScore2 != null) {
                    identification.candidates.add(candidateScore2);
                }
            }
            if (outcomeRunPure.best != null && ImageMatchPolicy.publicCandidateAllowed(outcomeRunPure.best)) {
                identification.visionIdentityReason = outcomeRunPure.summary;
                identification.visionIdentityConfidence = Math.max(identification.visionIdentityConfidence, ImageMatchPolicy.calibratedConfidence(outcomeRunPure.best));
            }
        }
        BrandBlindPolicy.sanitizeBrandEvidence(identification, localScan);
        if (!BrandBlindPolicy.trustedObservedBrand(identification)) {
            identification.brand = "";
        }
        identification.family = "";
        identification.model = "";
        identification.modelConfidence = 0;
        if (BrandAnchorPolicy.isLocked(identification)) {
            OpenAiClient.Response responseWebStage = openAiClient.webStage("brand_entity", BrandEntityPolicy.resolutionPrompt(identification, str));
            collectStage(identification, usage, responseWebStage, "brand-entity-v074-pre-retrieval");
            BrandEntityPolicy.applyResolution(identification, responseWebStage.payload);
            if (!BrandEntityPolicy.isResolved(identification)) {
                identification.observedEvidence.add("brand_entity_v074=unresolved; same-word domains remain untrusted");
            }
        }
        VisualRetrievalEngine.Outcome outcomeRun = VisualRetrievalEngine.run(identification, listCropForVision, str, openAiClient, usage);
        identification.decisionReason = (identification.decisionReason == null || identification.decisionReason.isEmpty()) ? outcomeRun.summary : identification.decisionReason + " " + outcomeRun.summary;
        Iterator<Models.CandidateScore> it2 = outcomeRun.preliminaryCandidates.iterator();
        while (it2.hasNext()) {
            Models.CandidateScore next = it2.next();
            if (next != null && next.model != null) {
                if (!next.model.trim().isEmpty()) {
                    boolean z4 = false;
                    Iterator<Models.CandidateScore> it3 = it2;
                    Iterator<Models.CandidateScore> it4 = identification.candidates.iterator();
                    while (true) {
                        if (!it4.hasNext()) {
                            break;
                        }
                        Models.CandidateScore next2 = it4.next();
                        if (next2 != null) {
                            it = it4;
                            if (canon(next2.displayName()).equals(canon(next.displayName()))) {
                                z4 = true;
                                break;
                            }
                        } else {
                            it = it4;
                        }
                        it4 = it;
                    }
                    if (!z4) {
                        identification.candidates.add(next);
                    }
                    it2 = it3;
                }
            }
        }
        if (outcomeRun.usable && outcomeRun.candidate != null) {
            Models.CandidateScore candidateScore3 = outcomeRun.candidate;
            boolean z5 = false;
            Iterator<Models.CandidateScore> it5 = identification.candidates.iterator();
            while (true) {
                if (!it5.hasNext()) {
                    break;
                }
                Models.CandidateScore next3 = it5.next();
                if (next3 != null) {
                    z3 = z5;
                    if (canon(next3.displayName()).equals(canon(candidateScore3.displayName()))) {
                        next3.totalScore = Math.max(next3.totalScore, candidateScore3.totalScore);
                        next3.candidateFacts.addAll(candidateScore3.candidateFacts);
                        z5 = true;
                        break;
                    }
                } else {
                    z3 = z5;
                }
                z5 = z3;
            }
            if (!z5) {
                identification.candidates.add(candidateScore3);
            }
        }
        ArrayList arrayList = new ArrayList();
        if (localScan != null) {
            arrayList.addAll(localScan.identifiers);
        }
        Models.Identifier identifierChoosePrimary = choosePrimary(arrayList);
        if (identifierChoosePrimary != null) {
            identification.primaryIdentifier = identifierChoosePrimary.value;
            identification.identifierVariants.addAll(LocalVisionEngine.normalizeIdentifierVariants(identifierChoosePrimary.value));
        }
        PhotoProtocol.Assessment assessmentAssess = PhotoProtocol.assess(identification.categoryKey, identification.category, identification.photoViews, localScan, list == null ? 0 : list.size());
        identification.categoryKey = assessmentAssess.categoryKey;
        identification.photoProtocolReady = assessmentAssess.ready;
        identification.requiredShots.addAll(assessmentAssess.required);
        identification.missingShots.addAll(assessmentAssess.missing);
        stabilizeCategoryConfidence(identification);
        if (!assessmentAssess.ready && VisionEnsemble.shouldRun(identification, assessmentAssess, usage)) {
            str3 = str;
            openAiClient2 = openAiClient;
            VisionEnsemble.enrich(identification, localScan, list, str3, openAiClient2, usage);
            BrandBlindPolicy.sanitizeBrandEvidence(identification, localScan);
            CategoryFactPolicy.apply(identification);
            ObservationSanitizer.apply(identification);
        } else {
            str3 = str;
            openAiClient2 = openAiClient;
        }
        if (!assessmentAssess.ready) {
            HardConstraintEngine.apply(identification, identifierChoosePrimary);
            sortCandidates(identification);
            Models.CandidateScore candidateScoreBestPublicCandidate = VisionEnsemble.bestPublicCandidate(identification);
            if (candidateScoreBestPublicCandidate != null) {
                if (!candidateScoreBestPublicCandidate.brand.isEmpty()) {
                    identification.brand = candidateScoreBestPublicCandidate.brand;
                }
                if (!candidateScoreBestPublicCandidate.family.isEmpty()) {
                    identification.family = candidateScoreBestPublicCandidate.family;
                }
                if (VisionEnsemble.isStrongExactCandidate(candidateScoreBestPublicCandidate, identification)) {
                    identification.model = candidateScoreBestPublicCandidate.model;
                    identification.modelConfidence = Math.min(88, Math.max(55, candidateScoreBestPublicCandidate.totalScore));
                } else {
                    identification.model = "";
                    identification.modelConfidence = 0;
                    identification.familyConfidence = Math.max(identification.familyConfidence, Math.min(78, candidateScoreBestPublicCandidate.totalScore));
                }
                if (!identification.visionIdentityReason.isEmpty()) {
                    identification.verificationSummary = identification.visionIdentityReason;
                }
                z2 = false;
            } else {
                identification.model = "";
                z2 = false;
                identification.modelConfidence = 0;
            }
            identification.marketReady = z2;
            identification.disproofPassed = z2;
            identification.nextPhotoRequest = assessmentAssess.nextRequest;
            identification.nextPhotoReason = assessmentAssess.reason;
            StringBuilder sb = new StringBuilder();
            if (identification.decisionReason != null && !identification.decisionReason.isEmpty()) {
                str2 = identification.decisionReason + " ";
            }
            identification.decisionReason = sb.append(str2).append("Protocollo fotografico incompleto: il risultato resta preliminare.").toString();
            if (identification.verificationSummary == null || identification.verificationSummary.isEmpty()) {
                identification.verificationSummary = "Il tipo di oggetto e' riconosciuto; marca/famiglia/modello vengono mostrati solo quando le analisi convergono. " + assessmentAssess.reason;
            }
            finalizeOutput(identification, identifierChoosePrimary);
            return identification;
        }
        List<String> listRareTerms = rareTerms(identification.distinctiveTerms, localScan == null ? "" : localScan.joinedText());
        identification.searchQuery = buildBrandAgnosticQuery(identification, identifierChoosePrimary, listRareTerms);
        HardConstraintEngine.apply(identification, identifierChoosePrimary);
        boolean zIsLocked = BrandAnchorPolicy.isLocked(identification);
        if (isDirectModelIdentifier(identifierChoosePrimary)) {
            Models.CandidateScore candidateScore4 = new Models.CandidateScore();
            candidateScore4.brand = trustedBrand(identification) ? identification.brand : "";
            candidateScore4.family = identification.family;
            candidateScore4.model = identifierChoosePrimary.value;
            candidateScore4.identifierScore = 100;
            candidateScore4.textScore = identification.visibleLabels.isEmpty() ? 0 : 70;
            candidateScore4.layoutScore = identification.spatialSignature.isEmpty() ? 0 : 70;
            candidateScore4.webScore = 0;
            candidateScore4.candidateFacts.add("model_code=" + identifierChoosePrimary.value);
            candidateScore4.totalScore = computeCandidateTotal(candidateScore4, identification, identifierChoosePrimary);
            identification.candidates.add(candidateScore4);
            HardConstraintEngine.apply(identification, identifierChoosePrimary);
            sortCandidates(identification);
            OpenAiClient.Response responseWebStage2 = openAiClient2.webStage("verify", buildVerifyPrompt(identification, candidateScore4, str3, identifierChoosePrimary));
            int i2 = (zIsLocked ? 1 : 0) + 1;
            collectStage(identification, usage, responseWebStage2, "verify-direct");
            applyVerification(identification, responseWebStage2.payload, candidateScore4, identifierChoosePrimary);
            if (identification.marketReady) {
                finalizeOutput(identification, identifierChoosePrimary);
                return identification;
            }
            identification.candidates.clear();
            i = i2;
        } else {
            i = zIsLocked ? 1 : 0;
        }
        HardConstraintEngine.apply(identification, identifierChoosePrimary);
        sortCandidates(identification);
        Models.CandidateScore candidateScore5 = topCandidate(identification);
        if (ClarificationPlanner.plan(identification) != null && candidateScore5 != null) {
            if (candidateScore5.totalScore >= 60) {
                identification.brand = candidateScore5.brand.isEmpty() ? identification.brand : candidateScore5.brand;
                identification.family = candidateScore5.family.isEmpty() ? identification.family : candidateScore5.family;
                identification.model = candidateScore5.model;
                identification.modelConfidence = Math.min(84, Math.max(55, candidateScore5.totalScore));
                identification.marketReady = false;
                identification.disproofPassed = false;
                identification.nextPhotoRequest = "";
                identification.nextPhotoReason = "";
                identification.decisionReason = "FAST IDENTIFY ha prodotto candidati distinguibili con una domanda ad alto valore informativo: nessuna discovery web eseguita.";
                identification.verificationSummary = identification.visionIdentityReason.isEmpty() ? "Riconoscimento iniziale Vision: serve una conferma rapida prima della verifica." : identification.visionIdentityReason;
                finalizeOutput(identification, identifierChoosePrimary);
                return identification;
            }
        }
        if (candidateScore5 != null && candidateScore5.totalScore >= 72 && i < 3 && UniversalConsistencyGate.discoveryBudgetAvailable(usage)) {
            OpenAiClient.Response responseWebStage3 = openAiClient2.webStage("verify", buildVerifyPrompt(identification, candidateScore5, str3, identifierChoosePrimary));
            i++;
            collectStage(identification, usage, responseWebStage3, "fast-verify");
            applyVerification(identification, responseWebStage3.payload, candidateScore5, identifierChoosePrimary);
            if (identification.marketReady) {
                finalizeOutput(identification, identifierChoosePrimary);
                return identification;
            }
            identification.nextPhotoRequest = "";
            identification.nextPhotoReason = "";
        }
        OpenAiClient.Response responseWebStage4 = openAiClient2.webStage("discovery", buildDiscoveryPrompt(identification, listRareTerms, str3, identifierChoosePrimary));
        int i3 = i + 1;
        collectStage(identification, usage, responseWebStage4, "discovery");
        parseTournamentCandidates(responseWebStage4.payload, identification, identifierChoosePrimary, false);
        applyNextPhoto(responseWebStage4.payload, identification);
        HardConstraintEngine.apply(identification, identifierChoosePrimary);
        sortCandidates(identification);
        Models.CandidateScore candidateScore6 = topCandidate(identification);
        boolean zIsClearWinner = isClearWinner(identification, 82, 12);
        ClarificationPlanner.Plan plan = ClarificationPlanner.plan(identification);
        if (!zIsClearWinner && plan != null && candidateScore6 != null) {
            if (candidateScore6.totalScore >= 55) {
                identification.brand = candidateScore6.brand.isEmpty() ? identification.brand : candidateScore6.brand;
                identification.family = candidateScore6.family.isEmpty() ? identification.family : candidateScore6.family;
                identification.model = candidateScore6.model;
                identification.modelConfidence = Math.min(82, Math.max(50, candidateScore6.totalScore));
                identification.marketReady = false;
                identification.disproofPassed = false;
                identification.nextPhotoRequest = "";
                identification.nextPhotoReason = "";
                identification.verificationSummary = tournamentSummary(identification, "Una conferma rapida distingue i candidati meglio di un altra ricerca generica.");
                identification.decisionReason = "SMART CLARIFY dopo discovery: fermata la pipeline prima del compare.";
                finalizeOutput(identification, identifierChoosePrimary);
                return identification;
            }
        }
        if (!zIsClearWinner && i3 < 3 && identification.candidates.size() >= 2 && UniversalConsistencyGate.discoveryBudgetAvailable(usage)) {
            OpenAiClient.Response responseWebStage5 = openAiClient2.webStage("compare", buildComparePrompt(identification, str3, identifierChoosePrimary));
            i3++;
            collectStage(identification, usage, responseWebStage5, "compare");
            parseTournamentCandidates(responseWebStage5.payload, identification, identifierChoosePrimary, true);
            applyNextPhoto(responseWebStage5.payload, identification);
            HardConstraintEngine.apply(identification, identifierChoosePrimary);
            sortCandidates(identification);
            candidateScore = topCandidate(identification);
        } else {
            candidateScore = candidateScore6;
        }
        if (candidateScore == null || !isClearWinner(identification, 76, 7)) {
            identification.model = candidateScore != null ? candidateScore.model : "";
            if (candidateScore != null) {
                identification.brand = candidateScore.brand.isEmpty() ? identification.brand : candidateScore.brand;
                identification.family = candidateScore.family.isEmpty() ? identification.family : candidateScore.family;
                identification.modelConfidence = Math.min(74, Math.max(35, candidateScore.totalScore));
                z = false;
            } else {
                z = false;
                identification.modelConfidence = 0;
            }
            identification.marketReady = z;
            identification.disproofPassed = z;
            if (identification.nextPhotoRequest.isEmpty()) {
                identification.nextPhotoRequest = fallbackNextPhoto(identification);
                if (identification.rejectedCandidates.isEmpty()) {
                    str4 = "I candidati migliori sono troppo vicini: una nuova foto discriminante vale più di un'altra ricerca generica.";
                } else {
                    str4 = "I vincoli deterministici hanno escluso candidati incompatibili, ma non resta ancora una prova web sufficiente per chiudere l'identità.";
                }
                identification.nextPhotoReason = str4;
            }
            identification.verificationSummary = tournamentSummary(identification, "Nessun candidato ha ancora una prova sufficiente per la verifica finale.");
            finalizeOutput(identification, identifierChoosePrimary);
            return identification;
        }
        if (i3 < 4 && UniversalConsistencyGate.verificationBudgetAvailable(usage)) {
            OpenAiClient.Response responseWebStage6 = openAiClient2.webStage("verify", buildVerifyPrompt(identification, candidateScore, str3, identifierChoosePrimary));
            int i4 = i3 + 1;
            collectStage(identification, usage, responseWebStage6, "verify+disproof");
            applyVerification(identification, responseWebStage6.payload, candidateScore, identifierChoosePrimary);
        } else {
            identification.brand = candidateScore.brand.isEmpty() ? identification.brand : candidateScore.brand;
            identification.family = candidateScore.family.isEmpty() ? identification.family : candidateScore.family;
            identification.model = candidateScore.model;
            identification.modelConfidence = Math.min(82, candidateScore.totalScore);
            identification.marketReady = false;
            identification.verificationSummary = tournamentSummary(identification, "Budget web esaurito prima della prova esatta: candidato mantenuto ma non confermato.");
            if (identification.nextPhotoRequest.isEmpty()) {
                identification.nextPhotoRequest = fallbackNextPhoto(identification);
                identification.nextPhotoReason = "Il budget web è stato usato: la prossima informazione deve arrivare dall'oggetto, non da una ricerca più generica.";
            }
        }
        finalizeOutput(identification, identifierChoosePrimary);
        return identification;
    }

    private static List<String> cropForVision(List<String> images) {
        // Never replace the only evidentiary view with an automatic crop. A wrong
        // foreground box can remove a label, connector or variant discriminator.
        return images == null ? new ArrayList<>() : new ArrayList<>(images);
    }

    private static String buildVisionPrompt(Models.LocalScan local, String details) {
        StringBuilder strongCodes = new StringBuilder();
        if (local != null) {
            for (Models.Identifier id : local.identifiers) {
                if (LocalVisionEngine.isStrongIdentifierLabel(id.label)) {
                    if (strongCodes.length() > 0) {
                        strongCodes.append(" | ");
                    }
                    strongCodes.append(id.label).append(':').append(id.value).append(" [photo ").append(id.imageIndex + 1).append(']');
                }
            }
        }
        String ocr = local == null ? "" : truncate(local.joinedText(), 5200);
        String strTrim = "none";
        StringBuilder sbAppend = new StringBuilder().append("FLIPCHECK v0.75 UNIVERSAL VISUAL OBSERVER. Analyze any physical object without category-specific rules. Your primary job is OBSERVATION, not identification. Separate literal observations from hypotheses. Return a concise category/category_key and all literal visible text. A visible word is NOT automatically a brand: it can be a control label, technology, retailer, service, location, compatibility mark or reflected/background text. Set brand only when the word/logo is visibly attached to the foreground object and is functioning as its manufacturer mark. Return brand_role_confidence 0-100 and a brief brand_role_reason; downstream brand locking requires >=85. Put visible_labels in objects {text,type}, where type is brand_logo, manufacturer_text, identifier, control, descriptor or unknown. FIRST perform FOREGROUND GROUNDING: determine the approximate boundary of the single physical object being identified and explicitly ignore the photographer/person reflection, room reflections, furniture, wall, floor, packaging, nearby objects and background clutter. Glossy glass and mirrors can reflect shapes that are NOT components. THEN inventory the physical object itself in dynamic key=value variant_facts and spatial_signature: major components, count and type of controls, openings/doors/lids, handles, displays, ports/connectors, mounting/placement clues, materials, proportions, articulated parts and their relative positions. A component may be called a handle, dial, door, display, connector or opening only when its attachment/edge relationship to the foreground object is visually supported. If a feature could be a reflection or background element, mark it uncertain or omit it. Use image-axis orientation literally: horizontal means left-to-right in the photo, vertical means top-to-bottom. Do not convert orientation based on a guessed product class. Include only what is genuinely visible. visual_fingerprint must summarize the foreground physical part graph and geometry without naming a brand/model and must exclude reflections/background. variant_facts has NO fixed schema: create useful generic keys that describe what is genuinely visible. Never invent a model code, part number, serial, SKU, barcode, brand or count. If uncertain, omit it. photo_views describes each supplied view generically. candidate_hints may contain up to 5 non-binding PHYSICAL PRODUCT-CLASS hypotheses, never brand/model names. User details are UNTRUSTED hints. If exact brand/model is not literally printed, leave family/model empty. category_confidence is only confidence in object type. JSON ONLY with: title,category,category_key,brand,brand_evidence,brand_role_confidence,brand_role_reason,family,model,category_confidence,family_confidence,identity_confidence,identity_reason,distinctive_terms,variant_facts,visible_labels,spatial_signature,candidate_hints,fast_candidates,photo_views,visual_fingerprint. LOCALLY READ IDENTIFIERS=").append(strongCodes.length() == 0 ? "none" : strongCodes).append(" | LOCAL OCR=").append(ocr.isEmpty() ? "none" : ocr).append(" | USER_HINT_UNTRUSTED=");
        if (details != null && !details.trim().isEmpty()) {
            strTrim = details.trim();
        }
        return sbAppend.append(strTrim).toString();
    }

    private static String buildDiscoveryPrompt(Models.Identification o, List<String> rare, String details, Models.Identifier primary) {
        return BrandEntityPolicy.promptBlock(o) + "\nFLIPCHECK v0.72 UNIVERSAL VISIBLE-BRAND-LOCK TOURNAMENT. Build a grounded tournament for the SAME physical entity photographed. Use at most TWO web searches here and do not repeat a hypothesis lane already disproved by structural/image comparison. If HARD_CONSTRAINTS contains brand=X, EVERY web query MUST start with the exact brand X and every candidate MUST belong to X. Never search a competing brand, a brandless equivalent, a generic substitute or a visually similar product from another maker. Product-class hypotheses may compete only INSIDE the visible brand namespace. If BRAND_ENTITY_LOCK=resolved, the FIRST discovery searches MUST target the verified manufacturer ecosystem (prefer site:OFFICIAL_DOMAIN and its catalog/manual/product pages). A third-party page may support a concrete model only when it explicitly names that same manufacturer and model; a retailer/site sharing the brand word is not evidence. Keep STRUCTURE evidence independent from LANE_1/LANE_2/LANE_3. Never combine incompatible lanes into one catch-all query. When category_confidence is below 92, a single class hypothesis may not dominate merely because it was listed first. Require either stronger image/structure agreement or independent source evidence before collapsing the class. Literal text is ambiguous until broad evidence links it to an entity that actually makes the visually compatible product class. STRICTLY DO NOT issue site: searches during discovery; domain-specific verification is allowed only later after a concrete candidate exists. For each candidate add candidate_facts: search_lane=structure|lane_1|lane_2|lane_3|literal|hint, brand_entity_validated=true|false, domain_locked=true|false, same_entity_role=true|false, relationship_only=true|false. Score identifier/text/layout/web independently 0-100. Major physical-component mismatch is a strong negative; missing evidence is UNKNOWN, not contradiction. For every candidate add geometry_relation=same|compatible|conflict and major_geometry_mismatch=true|false to candidate_facts. layout_score must be based ONLY on foreground geometry actually visible in the photographed object, never on matching words or product category. A candidate with geometry_relation=conflict cannot be the leader regardless of text/web score. Strong contradictions require high-reliability evidence. If HARD_CONSTRAINTS contains brand=X, verification is restricted to X and any search query must include X; another brand can never be an alternative candidate. Return max 6 diverse concrete candidates. Leave brand/family/model empty rather than inventing identity from a generic class or popular domain. JSON ONLY: {\"candidates\":[{\"brand\":\"\",\"family\":\"\",\"model\":\"\",\"identifier_score\":0,\"text_score\":0,\"layout_score\":0,\"web_score\":0,\"candidate_facts\":[],\"contradictions\":[],\"evidence\":\"\"}],\"next_photo_request\":\"\",\"next_photo_reason\":\"\"}. HARD_CONSTRAINTS=" + o.hardConstraints + " | " + UniversalSearchPlan.policyBlock(o) + " | VISIBLE_TEXT=" + o.visibleLabels + " | LAYOUT=" + o.spatialSignature + " | SOFT_VISUAL_FACTS=" + o.visualFacts + " | SHAPE=" + o.visualFingerprint + " | RARE=" + rare + " | USER_HINT_UNTRUSTED=" + (details == null ? "" : details);
    }

    private static String buildComparePrompt(Models.Identification o, String details, Models.Identifier primary) {
        return BrandEntityPolicy.promptBlock(o) + "\nFLIPCHECK v0.72 UNIVERSAL VISIBLE-BRAND-LOCK CANDIDATE COMPARISON. Compare the grounded candidates by trying to disprove each one. Use HARD_CONSTRAINTS only as eliminators; other OCR is soft evidence. The candidate itself must be the same photographed entity, not a related/compatible/host product. Use visible text, geometry/layout and dynamic visual facts as soft evidence. Prefix contradictions STRONG: only for direct source-backed identity mismatches and WEAK: for ambiguous visual differences. Do not add category-specific rules. Do not prefer a candidate merely because it matches USER_HINT or because its domain name resembles visible text. Re-score identifier/text/layout/web 0-100 and preserve grounded candidate_facts. If domain_locked=true and brand_entity_validated=false, cap it as weak evidence. Prefer structural/image agreement plus concrete product identity over brand-domain popularity. When candidates come from different search_lane values, compare their major visible components and geometry first; a lane with a structural contradiction cannot win on text/web score. Explicitly test orientation, aspect/proportions, control topology, openings/doors/lids, handle/bar attachment and major component count when visible. Record geometry_relation=same|compatible|conflict and major_geometry_mismatch=true|false. If HARD_CONSTRAINTS contains brand=X, reject every candidate whose brand is missing or different from X before comparing scores; any web search used in comparison must include X. If unresolved, request ONE additional photo that maximizes information: another side/view or the most distinctive visible marking/detail, without assuming a category. JSON ONLY with the same discovery schema. Return max 4 candidates; keep evidence and contradictions concise. HARD_CONSTRAINTS=" + o.hardConstraints + " | CANDIDATES=" + candidatesCompact(o.candidates) + " | VIEWS=" + o.photoViews + " | TEXT=" + o.visibleLabels + " | LAYOUT=" + o.spatialSignature + " | FACTS=" + o.visualFacts + " | USER_HINT_UNTRUSTED=" + (details == null ? "" : details);
    }

    private static String buildVerifyPrompt(Models.Identification o, Models.CandidateScore c, String details, Models.Identifier primary) {
        return BrandEntityPolicy.promptBlock(o) + "\nFLIPCHECK v0.72 UNIVERSAL VISIBLE-BRAND-LOCK FINAL VERIFICATION. Candidate=" + c.displayName() + ". Perform one targeted grounded search and explicitly PROVE and DISPROVE the exact identity. Extract identity-bearing attributes dynamically from the candidate source; do not assume a category schema. Compare them with observed OCR/identifiers and with credible competing sources. A mismatch is STRONG only when supported by high-reliability direct observation or independent source proof for the same physical entity; uncertain one-off OCR and visual inference are WEAK. Missing/unreadable information is UNKNOWN and is not a conflict. Set exact_identity_supported=true only when a real source directly names this exact candidate/reference/edition. source_identity_confidence measures that source-to-identity link. Set visual_reference_checked=true only if you actually inspected a real image tied to this exact candidate and compared it to the user object; visual_match_confidence is then image-to-image confidence. If multiple variants share the same visible appearance, visual similarity alone cannot prove the exact variant. A user hint or Vision hypothesis is never proof. conflict_level must be none, weak or strong. attribute_conflicts contains ONLY concrete source-backed attribute mismatches. Prefix contradictions STRONG: or WEAK:. Find the strongest surviving alternative and one concrete differentiator. If unresolved, request ONE additional view/detail with highest information gain, without category-specific assumptions. JSON ONLY: {\"confirmed\":false,\"same_entity_role\":true,\"relationship_only\":false,\"exact_identity_supported\":false,\"source_identity_confidence\":0,\"visual_reference_checked\":false,\"visual_match_confidence\":0,\"conflict_level\":\"none\",\"conflict_evidence_confidence\":0,\"attribute_conflicts\":[],\"brand\":\"\",\"family\":\"\",\"model\":\"\",\"model_proof\":\"none\",\"matched_visual_facts\":[],\"matched_layout_tokens\":[],\"contradictions\":[],\"disproof_passed\":false,\"strongest_alternative\":\"\",\"evidence\":\"\",\"next_photo_request\":\"\",\"next_photo_reason\":\"\"}. HARD_CONSTRAINTS_LOCAL_OCR_ONLY=" + o.hardConstraints + " | CANDIDATE_FACTS=" + c.candidateFacts + " | TEXT=" + o.visibleLabels + " | LAYOUT=" + o.spatialSignature + " | FACTS=" + o.visualFacts + " | SHAPE=" + o.visualFingerprint + " | USER_HINT_UNTRUSTED=" + (details == null ? "" : details);
    }

    private static void applyVisibleBrandAnchor(Models.Identification out, JSONArray labels) {
        if (out == null) {
            return;
        }
        String existing = clean(out.brand);
        String evidence = normalizeBrandEvidence(out.brandEvidence);
        if (!existing.isEmpty() && (evidence.equals(EnvironmentCompat.MEDIA_UNKNOWN) || evidence.equals("visual_guess"))) {
            Iterator<String> it = out.visibleLabels.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                String x = it.next();
                if (canon(existing).equals(canon(x)) && !canon(x).isEmpty()) {
                    out.brandEvidence = "visible_brand_text";
                    String evidence2 = out.brandEvidence;
                    break;
                }
            }
        }
        if (BrandAnchorPolicy.isLocked(out) || labels == null) {
            return;
        }
        for (int i = 0; i < labels.length(); i++) {
            JSONObject x2 = labels.optJSONObject(i);
            if (x2 != null) {
                String text = clean(x2.optString("text", x2.optString("label", x2.optString("value", ""))));
                String type = clean(x2.optString("type", x2.optString("role", ""))).toLowerCase(Locale.ROOT);
                if (text.length() >= 2 && text.length() <= 64) {
                    boolean logo = type.contains("logo");
                    boolean brand = type.contains("brand") || type.contains("manufacturer");
                    if (logo || brand) {
                        out.brand = text;
                        out.brandEvidence = logo ? "visible_logo" : "visible_brand_text";
                        out.brandRoleConfidence = Math.max(out.brandRoleConfidence, 95);
                        out.brandRoleReason = "Visible label explicitly classified as foreground manufacturer mark.";
                        return;
                    }
                }
            }
        }
    }

    private static List<String> visibleLabelStrings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) {
            return out;
        }
        for (int i = 0; i < a.length(); i++) {
            Object raw = a.opt(i);
            String text = "";
            if (raw instanceof JSONObject) {
                JSONObject o = (JSONObject) raw;
                text = clean(o.optString("text", o.optString("label", o.optString("value", ""))));
            } else if (raw != null && raw != JSONObject.NULL) {
                text = clean(String.valueOf(raw));
            }
            if (!text.isEmpty() && !text.startsWith("{") && !text.startsWith("[")) {
                boolean dup = false;
                Iterator<String> it = out.iterator();
                while (true) {
                    if (!it.hasNext()) {
                        break;
                    }
                    String old = it.next();
                    if (old.equalsIgnoreCase(text)) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    out.add(text);
                }
            }
        }
        return out;
    }

    private static void parseVisionCandidates(JSONArray a, Models.Identification o) {
        if (a == null || o == null) {
            return;
        }
        for (int i = 0; i < a.length() && i < 5; i++) {
            JSONObject x = a.optJSONObject(i);
            if (x != null) {
                String label = join(clean(x.optString("brand", "")), clean(x.optString("family", "")), clean(x.optString("model", "")));
                if (!label.isEmpty()) {
                    o.visionCandidates.add("Hypothesis only: " + label + " (" + clamp(x.optInt("confidence", 0)) + "%)");
                }
            }
        }
    }

    private static void parseTournamentCandidates(JSONObject payload, Models.Identification o, Models.Identifier primary, boolean replace) {
        JSONArray a = payload == null ? null : payload.optJSONArray("candidates");
        if (a == null) {
            return;
        }
        Map<String, Models.CandidateScore> map = new LinkedHashMap<>();
        if (!replace) {
            for (Models.CandidateScore old : o.candidates) {
                map.put(candidateKey(old), old);
            }
        }
        for (int i = 0; i < a.length() && i < 6; i++) {
            JSONObject x = a.optJSONObject(i);
            if (x != null) {
                Models.CandidateScore c = new Models.CandidateScore();
                c.brand = clean(x.optString("brand", ""));
                c.family = clean(x.optString("family", ""));
                c.model = clean(x.optString("model", ""));
                if (!c.model.isEmpty()) {
                    c.identifierScore = clamp(x.optInt("identifier_score", 0));
                    c.textScore = clamp(x.optInt("text_score", 0));
                    c.layoutScore = clamp(x.optInt("layout_score", 0));
                    c.webScore = clamp(x.optInt("web_score", 0));
                    c.evidence = clean(x.optString("evidence", ""));
                    c.candidateFacts.addAll(strings(x.optJSONArray("candidate_facts")));
                    c.contradictions.addAll(strings(x.optJSONArray("contradictions")));
                    c.totalScore = computeCandidateTotal(c, o, primary);
                    UniversalConsistencyGate.calibrateCandidate(c);
                    map.put(candidateKey(c), c);
                }
            }
        }
        o.candidates.clear();
        o.candidates.addAll(map.values());
    }

    private static int computeCandidateTotal(Models.CandidateScore c, Models.Identification o, Models.Identifier primary) {
        int numerator = 0;
        int denominator = 0;
        if (primary != null) {
            numerator = 0 + (c.identifierScore * 30);
            denominator = 0 + 30;
        }
        if (!o.visibleLabels.isEmpty() || !o.distinctiveTerms.isEmpty()) {
            numerator += c.textScore * 25;
            denominator += 25;
        }
        if (!o.spatialSignature.isEmpty() || !o.visualFingerprint.isEmpty()) {
            numerator += c.layoutScore * 30;
            denominator += 30;
        }
        int denominator2 = denominator + 30;
        int base = denominator2 == 0 ? c.webScore : Math.round((numerator + (c.webScore * 30)) / denominator2);
        int penalty = Math.min(45, c.contradictions.size() * 15);
        if (trustedBrand(o) && !o.brand.isEmpty() && !c.brand.isEmpty() && !canon(c.brand).equals(canon(o.brand))) {
            penalty += 25;
        }
        return clamp(base - penalty);
    }

    static void sortCandidates(Models.Identification o) {
        Collections.sort(o.candidates, Comparator.comparingInt(new ToIntFunction() {
            @Override
            public final int applyAsInt(Object obj) {
                return ((Models.CandidateScore) obj).totalScore;
            }
        }).reversed());
        if (o.candidates.size() < 2) {
            if (o.candidates.size() != 1) {
                o.tournamentMargin = 0;
                return;
            } else {
                o.tournamentMargin = o.candidates.get(0).totalScore;
                return;
            }
        }
        o.tournamentMargin = Math.max(0, o.candidates.get(0).totalScore - o.candidates.get(1).totalScore);
    }

    private static Models.CandidateScore topCandidate(Models.Identification o) {
        if (o.candidates.isEmpty()) {
            return null;
        }
        return o.candidates.get(0);
    }

    private static boolean isClearWinner(Models.Identification o, int minScore, int minMargin) {
        Models.CandidateScore top = topCandidate(o);
        if (top == null || top.hardRejected || UniversalConsistencyGate.strongCandidateConflict(top)) {
            return false;
        }
        if (o.candidates.size() == 1 && top.hardMatchWeight >= 45) {
            return true;
        }
        if (top.totalScore < minScore) {
            return false;
        }
        if (top.contradictions.size() < 2 || top.hardMatchWeight >= 60) {
            return o.candidates.size() == 1 || o.tournamentMargin >= minMargin;
        }
        return false;
    }

    static void applyVerification(Models.Identification o, JSONObject r, Models.CandidateScore top, Models.Identifier primary) {
        boolean directProof;
        int conf;
        String str;
        boolean strongConflict = UniversalConsistencyGate.strongVerificationConflict(r);
        if (strongConflict) {
            o.finalContradictions.clear();
            o.finalContradictions.addAll(strings(r == null ? null : r.optJSONArray("contradictions")));
            UniversalConsistencyGate.copyVerificationConflicts(r, o.finalContradictions);
            o.verificationSummary = r == null ? "" : clean(r.optString("evidence", ""));
            if (top != null) {
                top.hardRejected = true;
                top.totalScore = 0;
                top.candidateFacts.add("consistency_strong_conflict=true");
                for (String x : o.finalContradictions) {
                    if (!top.contradictions.contains(x)) {
                        top.contradictions.add(x);
                    }
                }
                o.candidates.remove(top);
                if (!o.rejectedCandidates.contains(top)) {
                    o.rejectedCandidates.add(top);
                }
            }
            sortCandidates(o);
            Models.CandidateScore fallback = topCandidate(o);
            o.brand = fallback == null ? "" : fallback.brand;
            o.family = fallback == null ? "" : fallback.family;
            o.model = fallback != null ? fallback.model : "";
            o.modelConfidence = fallback == null ? 0 : Math.min(68, Math.max(35, fallback.totalScore));
            o.modelProof = "none";
            o.marketReady = false;
            o.disproofPassed = false;
            applyNextPhoto(r, o);
            if (o.nextPhotoRequest.isEmpty()) {
                o.nextPhotoRequest = fallbackNextPhoto(o);
                o.nextPhotoReason = "Il candidato precedente è stato escluso da una contraddizione di identità; serve la prova che separa i candidati rimasti.";
            }
            o.decisionReason = "CONSISTENCY GATE v0.65: candidato esatto respinto da una contraddizione source-backed; la somiglianza visiva non può prevalere.";
            return;
        }
        boolean confirmed = r != null && r.optBoolean("confirmed", false);
        boolean disproof = r != null && r.optBoolean("disproof_passed", false);
        String rb = r == null ? "" : clean(r.optString("brand", ""));
        String rf = r == null ? "" : clean(r.optString("family", ""));
        String rm = r == null ? "" : clean(r.optString("model", ""));
        if (!rb.isEmpty()) {
            o.brand = rb;
        } else if (top != null && !top.brand.isEmpty()) {
            o.brand = top.brand;
        }
        if (!rf.isEmpty()) {
            o.family = rf;
        } else if (top != null && !top.family.isEmpty()) {
            o.family = top.family;
        }
        if (!rm.isEmpty()) {
            o.model = rm;
        } else if (top != null) {
            o.model = top.model;
        }
        o.modelProof = r != null ? clean(r.optString("model_proof", "none")).toLowerCase(Locale.ROOT) : "none";
        o.matchedVisualFacts.clear();
        o.matchedLayoutTokens.clear();
        o.finalContradictions.clear();
        addValidatedMatches(o.matchedVisualFacts, strings(r == null ? null : r.optJSONArray("matched_visual_facts")), o.visualFacts);
        addValidatedMatches(o.matchedLayoutTokens, strings(r == null ? null : r.optJSONArray("matched_layout_tokens")), layoutVocabulary(o));
        o.finalContradictions.addAll(strings(r == null ? null : r.optJSONArray("contradictions")));
        UniversalConsistencyGate.copyVerificationConflicts(r, o.finalContradictions);
        int weakConflicts = UniversalConsistencyGate.verificationWeakConflictCount(r);
        o.disproofPassed = disproof;
        o.verificationSummary = r == null ? "" : clean(r.optString("evidence", ""));
        applyNextPhoto(r, o);
        int topScore = top == null ? 0 : top.totalScore;
        boolean exactIdentifier = "exact_identifier".equals(o.modelProof) && primary != null && EvidenceReliabilityGate.isHardIdentifier(o.localScan, primary);
        boolean directProof2 = "direct_product_page".equals(o.modelProof) || "exact_manual".equals(o.modelProof) || "exact_catalog".equals(o.modelProof) || "exact_retailer".equals(o.modelProof) || "exact_identifier".equals(o.modelProof);
        boolean exactSupport = UniversalConsistencyGate.exactSourceSupport(r) || (exactIdentifier && primary != null);
        boolean visualEvidence = UniversalConsistencyGate.independentVisualIdentityEvidence(top, r);
        if (confirmed) {
            directProof = directProof2;
            if (!disproof) {
                conf = Math.min(76, Math.max(55, topScore));
            } else if (exactIdentifier) {
                conf = 97;
            } else if (directProof && exactSupport && visualEvidence) {
                conf = 95;
            } else if (directProof && exactSupport) {
                conf = 88;
            } else {
                conf = visualEvidence ? Math.min(86, Math.max(78, topScore)) : Math.min(82, Math.max(70, topScore));
            }
        } else {
            directProof = directProof2;
            conf = Math.min(68, Math.max(30, topScore));
        }
        if (weakConflicts > 0) {
            conf = Math.max(0, conf - Math.min(12, weakConflicts * 4));
        }
        o.modelConfidence = clamp(UniversalConsistencyGate.capVerifiedConfidence(conf, top, r, exactIdentifier, disproof));
        o.marketReady = confirmed && disproof && directProof && exactSupport && o.modelConfidence >= 85;
        if (o.marketReady) {
            o.categoryConfidence = Math.max(o.categoryConfidence, 95);
            o.familyConfidence = Math.max(o.familyConfidence, 90);
            o.nextPhotoRequest = "";
            o.nextPhotoReason = "";
            if (o.modelConfidence > 90) {
                str = "Identità esatta: fonte diretta + coerenza attributi + prova visiva indipendente (o identificatore locale esatto) + disproof superato.";
            } else {
                str = "Identità verificata e coerente; confidenza calibrata sotto 90% perché manca una prova visiva indipendente dell'esatto modello.";
            }
            o.decisionReason = str;
            return;
        }
        o.decisionReason = confirmed ? "Candidato supportato ma non supera ancora tutti i requisiti universali di identità esatta." : "La prova esatta non è sufficiente.";
        if (o.nextPhotoRequest.isEmpty()) {
            o.nextPhotoRequest = fallbackNextPhoto(o);
            o.nextPhotoReason = "Serve una prova indipendente che distingua l'identità esatta dai candidati visivamente simili.";
        }
    }

    static void collectStage(Models.Identification o, Models.Usage usage, OpenAiClient.Response r, String stage) {
        usage.add(r.usage);
        o.webStages.add(stage);
        for (String q : r.queries) {
            if (!containsIgnoreCase(o.webQueries, q)) {
                o.webQueries.add(q);
            }
        }
        for (Models.Source s : r.sources) {
            boolean duplicate = false;
            Iterator<Models.Source> it = o.sources.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                Models.Source old = it.next();
                if (!s.url.isEmpty() && s.url.equals(old.url)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                o.sources.add(s);
            }
        }
    }

    static void finalizeOutput(Models.Identification o, Models.Identifier primary) {
        BrandEntityPolicy.filterSources(o);
        BrandBlindPolicy.sanitizeBrandEvidence(o, o == null ? null : o.localScan);
        ConfirmationIntegrityPolicy.enforce(o);
        scoreSources(o, primary);
        if (o.marketReady && !o.model.isEmpty()) {
            String exact = join(o.brand, o.family, o.model);
            if (!exact.isEmpty()) {
                o.title = exact;
            }
        } else if (o.title.isEmpty() || "Oggetto non identificato".equalsIgnoreCase(o.title)) {
            String x = join(o.brand, o.family, o.model);
            o.title = x.isEmpty() ? o.category.isEmpty() ? "Oggetto" : o.category : x;
        }
        if (o.verificationSummary.isEmpty()) {
            o.verificationSummary = tournamentSummary(o, o.decisionReason);
        }
        if (o.marketReady) {
            return;
        }
        if (o.nextPhotoRequest == null || o.nextPhotoRequest.trim().isEmpty()) {
            o.nextPhotoRequest = fallbackNextPhoto(o);
            o.nextPhotoReason = "Serve una prova visiva aggiuntiva per ridurre l'incertezza residua.";
        }
    }

    private static void scoreSources(Models.Identification o, Models.Identifier primary) {
        String model = canon(o.model);
        String brand = canon(o.brand);
        String id = primary == null ? "" : canon(primary.value);
        for (Models.Source s : o.sources) {
            String hay = canon(s.title + " " + s.snippet + " " + s.url);
            int score = 0;
            if (!model.isEmpty() && hay.contains(model)) {
                score = 0 + 50;
            }
            if (!brand.isEmpty() && hay.contains(brand)) {
                score += 15;
            }
            if (!id.isEmpty() && hay.contains(id)) {
                score += 25;
            }
            String low = (s.url + " " + s.title).toLowerCase(Locale.ROOT);
            if (low.matches(".*(official|support|manual|product|catalog|parts|oem|datasheet|specification|checklist).*")) {
                score += 10;
            }
            s.relevance = Math.max(s.relevance, clamp(score));
            s.strong = s.relevance >= 50;
        }
    }

    private static void applyNextPhoto(JSONObject r, Models.Identification o) {
        if (r == null) {
            return;
        }
        String req = clean(r.optString("next_photo_request", ""));
        String why = clean(r.optString("next_photo_reason", ""));
        if (!req.isEmpty() && !"null".equalsIgnoreCase(req)) {
            o.nextPhotoRequest = req;
        }
        if (why.isEmpty() || "null".equalsIgnoreCase(why)) {
            return;
        }
        o.nextPhotoReason = why;
    }

    private static void stabilizeCategoryConfidence(Models.Identification o) {
        if (o.category == null || o.category.trim().isEmpty()) {
            return;
        }
        if (o.categoryConfidence != 0) {
            if (o.categoryConfidence >= 45 || "other".equals(o.categoryKey)) {
                return;
            }
            o.categoryConfidence = 55;
            return;
        }
        o.categoryConfidence = "other".equals(o.categoryKey) ? 65 : 80;
    }

    private static String fallbackNextPhoto(Models.Identification o) {
        if (o != null && o.primaryIdentifier != null && !o.primaryIdentifier.trim().isEmpty()) {
            return "Aggiungi una seconda vista dell'oggetto da un lato diverso, mantenendo visibili i dettagli distintivi";
        }
        if (o == null) {
            return "Aggiungi una seconda foto dell'oggetto da un'angolazione diversa, più ravvicinata e senza sfondo inutile";
        }
        if (!o.visibleLabels.isEmpty() || !o.visualFacts.isEmpty()) {
            return "Aggiungi una seconda vista oppure un primo piano del logo, scritte, codice o dettaglio più distintivo";
        }
        return "Aggiungi una seconda foto dell'oggetto da un'angolazione diversa, più ravvicinata e senza sfondo inutile";
    }

    private static void addValidatedMatches(List<String> dst, List<String> returned, List<String> observed) {
        LinkedHashSet linkedHashSet = new LinkedHashSet();
        for (String x : returned) {
            String strCanonWords = canonWords(x);
            if (!strCanonWords.isEmpty()) {
                for (String obs : observed) {
                    String strCanonWords2 = canonWords(obs);
                    if (!strCanonWords2.isEmpty() && (strCanonWords2.equals(strCanonWords) || strCanonWords2.contains(strCanonWords) || strCanonWords.contains(strCanonWords2))) {
                        if (linkedHashSet.add(strCanonWords2)) {
                            dst.add(obs);
                        }
                    }
                }
            }
        }
    }

    private static List<String> layoutVocabulary(Models.Identification o) {
        List<String> all = new ArrayList<>();
        all.addAll(o.visibleLabels);
        all.addAll(o.spatialSignature);
        return all;
    }

    private static String tournamentSummary(Models.Identification o, String prefix) {
        StringBuilder b = new StringBuilder(prefix == null ? "" : prefix);
        if (!o.hardConstraints.isEmpty()) {
            if (b.length() > 0) {
                b.append(' ');
            }
            b.append("Vincoli deterministici: ").append(o.hardConstraints).append('.');
        }
        if (!o.rejectedCandidates.isEmpty()) {
            if (b.length() > 0) {
                b.append(' ');
            }
            b.append("Candidati esclusi per incompatibilità dura: ").append(o.rejectedCandidates.size()).append('.');
        }
        if (!o.candidates.isEmpty()) {
            if (b.length() > 0) {
                b.append(' ');
            }
            b.append("Leader: ").append(o.candidates.get(0).displayName()).append(" score ").append(o.candidates.get(0).totalScore).append("; margine ").append(o.tournamentMargin).append(" punti.");
        }
        return b.toString();
    }

    private static String candidatesCompact(List<Models.CandidateScore> xs) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < xs.size() && i < 4; i++) {
            Models.CandidateScore c = xs.get(i);
            if (b.length() > 0) {
                b.append(" || ");
            }
            b.append(c.displayName()).append(" [id=").append(c.identifierScore).append(", text=").append(c.textScore).append(", layout=").append(c.layoutScore).append(", web=").append(c.webScore).append(", total=").append(c.totalScore).append(", candidateFacts=").append(c.candidateFacts).append(", hardMatches=").append(c.hardMatches).append(", contradictions=").append(c.contradictions).append(']');
        }
        return b.toString();
    }

    private static boolean trustedBrand(Models.Identification o) {
        return BrandBlindPolicy.trustedObservedBrand(o);
    }

    private static String normalizeBrandEvidence(String s) {
        String x = clean(s).toLowerCase(Locale.ROOT).replace(' ', '_');
        if (x.equals("visible_logo") || x.equals("visible_brand_text") || x.equals("explicit_label") || x.equals("ocr_brand") || x.equals("visual_guess")) {
            return x;
        }
        return EnvironmentCompat.MEDIA_UNKNOWN;
    }

    private static boolean isDirectModelIdentifier(Models.Identifier id) {
        if (id == null) {
            return false;
        }
        String l = id.label.toUpperCase(Locale.ROOT);
        return l.startsWith("MODEL") || l.equals("TYPE") || l.equals("ITEM");
    }

    private static Models.Identifier choosePrimary(List<Models.Identifier> ids) {
        int s;
        Models.Identifier best = null;
        int bestScore = -1;
        for (Models.Identifier id : ids) {
            if (id != null && id.value != null && !id.value.trim().isEmpty()) {
                String l = id.label.toUpperCase(Locale.ROOT);
                if (!l.equals("REV") && !l.equals("YEAR") && LocalVisionEngine.isStrongIdentifierLabel(id.label) && (s = priority(id)) > bestScore) {
                    bestScore = s;
                    best = id;
                }
            }
        }
        return best;
    }

    private static int priority(Models.Identifier id) {
        int n;
        String l = id.label.toUpperCase(Locale.ROOT);
        if (l.startsWith("MODEL")) {
            n = 140;
        } else if (l.equals("SKU") || l.equals("REF") || l.equals("TYPE") || l.equals("ITEM")) {
            n = 125;
        } else if (l.equals("PN") || l.equals("PART")) {
            n = 120;
        } else {
            n = l.equals("BARCODE") ? 115 : 0;
        }
        if (id.origin.contains("_labeled")) {
            n += 12;
        }
        return id.origin.startsWith("mlkit") ? n + 5 : n;
    }

    private static String buildBrandAgnosticQuery(Models.Identification o, Models.Identifier primary, List<String> rare) {
        StringBuilder q = new StringBuilder();
        if (primary != null && !primary.value.trim().isEmpty()) {
            q.append('"').append(primary.value.replace("\"", "")).append('"');
        } else {
            int used = 0;
            for (String x : o.visibleLabels) {
                if (useful(x)) {
                    if (q.length() > 0) {
                        q.append(' ');
                    }
                    q.append('"').append(x.replace("\"", "")).append('"');
                    used++;
                    if (used >= 3) {
                        break;
                    }
                }
            }
            for (String x2 : rare) {
                if (used >= 3) {
                    break;
                }
                if (useful(x2)) {
                    if (q.length() > 0) {
                        q.append(' ');
                    }
                    q.append('"').append(x2.replace("\"", "")).append('"');
                    used++;
                }
            }
        }
        if (!o.category.isEmpty()) {
            if (q.length() > 0) {
                q.append(' ');
            }
            q.append(o.category.length() > 48 ? o.category.substring(0, 48) : o.category);
        }
        if (trustedBrand(o) && !o.brand.isEmpty()) {
            q.insert(0, o.brand + " ");
        }
        return q.toString().trim();
    }

    private static List<String> rareTerms(List<String> vision, String ocr) {
        Set<String> all = new LinkedHashSet<>();
        for (String x : vision) {
            if (useful(x)) {
                all.add(clean(x));
            }
        }
        if (ocr != null) {
            for (String str : ocr.split("[\\r\\n]+")) {
                String line = clean(str);
                if (useful(line)) {
                    all.add(line);
                }
            }
        }
        List<String> out = new ArrayList<>(all);
        Collections.sort(out, Comparator.comparingInt(new ToIntFunction() {
            @Override
            public final int applyAsInt(Object obj) {
                return IdentificationEngine.rarityScore((String) obj);
            }
        }).reversed());
        if (out.size() > 8) {
            return new ArrayList(out.subList(0, 8));
        }
        return out;
    }

    public static int rarityScore(String s) {
        String u = clean(s).toUpperCase(Locale.ROOT);
        int score = Math.min(40, u.length());
        int words = u.isEmpty() ? 0 : u.split("\\s+").length;
        int score2 = score + Math.min(24, words * 5);
        if (u.matches(".*[A-Z].*\\d.*|.*\\d.*[A-Z].*")) {
            score2 += 28;
        }
        return u.contains(" ") ? score2 + 10 : score2;
    }

    private static boolean useful(String s) {
        if (s == null) {
            return false;
        }
        String x = clean(s);
        if (x.length() < 4 || x.length() > 72) {
            return false;
        }
        String u = x.toUpperCase(Locale.ROOT);
        if (u.matches("(AUTO|OFF|ON|RESET|START|STOP|POWER|MENU|OK|SET|MODE|PROGRAM|MANUAL|ENTER|CLEAR|HOME|BACK)")) {
            return false;
        }
        return !u.matches("[0-9 .:/-]+");
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) {
            return out;
        }
        for (int i = 0; i < a.length(); i++) {
            String s = clean(a.optString(i, ""));
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static boolean containsIgnoreCase(List<String> xs, String q) {
        for (String x : xs) {
            if (x.equalsIgnoreCase(q)) {
                return true;
            }
        }
        return false;
    }

    private static String candidateKey(Models.CandidateScore c) {
        return canon(c.brand + "|" + c.family + "|" + c.model);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static String canon(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String canonWords(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static int clamp(int n) {
        return Math.max(0, Math.min(100, n));
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : s.length() <= n ? s : s.substring(0, n);
    }

    private static String join(String... xs) {
        StringBuilder b = new StringBuilder();
        for (String x : xs) {
            if (x != null && !x.trim().isEmpty()) {
                if (b.length() > 0) {
                    b.append(' ');
                }
                b.append(x.trim());
            }
        }
        return b.toString();
    }
}
