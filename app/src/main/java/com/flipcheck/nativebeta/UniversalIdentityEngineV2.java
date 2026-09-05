package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/** v1.35 production route: Vision -> discovery Web -> physical review -> verification Web -> reduce once. */
final class UniversalIdentityEngineV2 {
    private UniversalIdentityEngineV2() {}
    static boolean enabled(){return true;}

    static Models.Identification identify(Models.LocalScan local,List<String>images,String details,OpenAiClient client,Models.Usage usage)throws Exception{
        return identify(local,images,details,client,usage,false,null);
    }

    static Models.Identification readPhoto(Models.LocalScan local,List<String> images,String details,OpenAiClient client,Models.Usage usage)throws Exception{
        return identify(local,images,details,client,usage,true,null);
    }

    static Models.Identification verifyAfterReading(Models.LocalScan local,List<String> images,String details,OpenAiClient client,Models.Usage usage,Models.Identification previous,Models.Usage previousUsage)throws Exception{
        OpenAiClient.Response cached=PhotoReadingV156.cachedResponse(previous,previousUsage,images,details);
        return identify(local,images,details,client,usage,false,cached);
    }

    private static Models.Identification identify(Models.LocalScan local,List<String>images,String details,OpenAiClient client,Models.Usage usage,boolean photoOnly,OpenAiClient.Response cached)throws Exception{
        Models.Identification id=new Models.Identification();id.localScan=local;id.finalStateReducerVersion=FinalStateReducerV2.VERSION;id.uploadedImageCount=originalImageCount(local,images);
        id.photoReadingOnly=photoOnly;id.photoInputSignature=PhotoReadingV156.signature(images,details);
        ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestLocal(local,ledger);id.localOcrFactCount=modalityCount(ledger,EvidenceAtom.Modality.LOCAL_OCR);
        String technical="";ObservationExtractorV2.Result primary=new ObservationExtractorV2.Result();List<IdentityCandidateV2>focusedHypotheses=new ArrayList<>();
        if(images==null||images.isEmpty()){id.missingDiscriminativeFields="complete_object_photo";id.pipelineFailureDomain="NO_IMAGE";return finish(id,ledger,DomainProfileRouterV2.Profile.GENERIC_OBJECT,new ArrayList<>(),"",usage);}
        OpenAiClient.Response first=null;boolean retryAllowed=true;long retryDelayMillis=0;
        try{first=cached!=null?cached:client.observeFullV154(new ArrayList<>(images),primaryPrompt(local,details));collect(id,usage,first,"v154_vision1_observation");if(cached!=null)id.v2RecoveryTrace="vision1_reused_from_photo_reading:no_new_api_call";}
        catch(Exception failure){technical=technicalStatus(failure);retryAllowed=!(failure instanceof ApiCallFailure)||((ApiCallFailure)failure).retryable();retryDelayMillis=failure instanceof ApiCallFailure?((ApiCallFailure)failure).retryAfterMillis:0;id.v2CallReasons=append(id.v2CallReasons,"primary_vision:"+technicalDetail(failure));}
        if(needsTechnicalRetry(first)&&first!=null&&first.payload!=null&&first.payload.length()>0){primary=ObservationExtractorV2.ingestPrimary(first.payload,ledger);TypedFieldNormalizerV2.normalize(ledger);id.v2RecoveryTrace=append(id.v2RecoveryTrace,"partial_primary_facts_salvaged");}
        if(!photoOnly&&retryAllowed&&needsTechnicalRetry(first)&&usage!=null&&usage.costUsd+.004d<=.025d){
            if(retryDelayMillis>0)Thread.sleep(retryDelayMillis);
            id.technicalRetryCount++;
            try{OpenAiClient.Response retry=client.observeTechnicalRecovery(new ArrayList<>(images),primaryPrompt(local,details)+"\nTECHNICAL_RETRY=compact_valid_json");collect(id,usage,retry,"v132_primary_technical_retry");first=retry;technical="";}
            catch(Exception failure){technical=technicalStatus(failure);id.v2CallReasons=append(id.v2CallReasons,"technical_retry:"+technicalDetail(failure));}
        }
        if(first!=null&&first.payload!=null){primary=ObservationExtractorV2.ingestPrimary(first.payload,ledger);id.visionResponseStatus=first.technicalStatus;id.visionFinishReason=first.incompleteReason;recordViews(id,primary.views,ledger,id.uploadedImageCount);}
        if(first==null||needsTechnicalRetry(first)){id.pipelineFailureDomain=empty(technical)?"PRIMARY_VISION_INVALID":technical;return finish(id,ledger,DomainProfileRouterV2.route(primary.category,ledger),primary.hypotheses,id.pipelineFailureDomain,usage);}
        TypedFieldNormalizerV2.normalize(ledger);DomainProfileRouterV2.Profile profile=DomainProfileRouterV2.route(primary.category,ledger);id.v2Profile=DomainProfileRouterV2.categoryKey(profile);id.categoryKey=id.v2Profile;
        if(!primary.contentSufficient){id.missingDiscriminativeFields="identity_bearing_surface";return finish(id,ledger,profile,primary.hypotheses,"",usage);}
        if(photoOnly){
            id.webStatus="NOT_RUN";
            id.v2RecoveryTrace="protocol=PHOTO_READING;logical_stages=1;web_calls=0";
            return finish(id,ledger,profile,new ArrayList<>(),"",usage);
        }

        // Four logical stages, each called once. A failed search does not prevent
        // a fresh physical review and the independent final retrieval attempt.
        List<IdentityCandidateV2> hypotheses=HypothesisGeneratorV2.merge(primary.hypotheses,focusedHypotheses);
        OpenAiClient.Response discovery=webPass(client,images,
                CandidateRetrieverV2.prompt(profile,ledger,hypotheses)
                + "\nDISCOVERY PASS: retrieve plausible isolated records. Start from robust observed text. "
                + "If an identifier or season is uncertain, also search without that constraint. "
                + "Return competing editions/variants and the fields that distinguish them; do not force a winner.",
                id,usage,"v154_web1_discovery");
        List<IdentityCandidateV2> discovered=parseWeb(discovery,profile,ledger,hypotheses,id,"web1");
        List<IdentityCandidateV2> preliminary=CandidateVerifierV2.verify(discovered,ledger,profile);
        String fields=reviewFields(profile,ledger,preliminary);
        ImagePreparationV2.Prepared prepared=ImagePreparationV2.reviewAll(images,profile);
        id.v2ImagePreparationTrace=prepared.trace.toString();
        id.additionalVisionReason="post_discovery_physical_review:"+fields;
        try{
            OpenAiClient.Response reviewed=client.observeReviewV154(prepared.images,
                    reviewPrompt(profile,fields,prepared.trace));
            collect(id,usage,reviewed,"v154_vision2_physical_review");id.discriminativeVisionCount++;
            if(needsTechnicalRetry(reviewed)){
                id.pipelineFailureDomain="SECOND_VISION_INVALID";
                id.v2RecoveryTrace=append(id.v2RecoveryTrace,"vision2_invalid:preserved_primary_evidence");
            }else{
                ObservationExtractorV2.Result f=ObservationExtractorV2.ingestFocused(reviewed.payload,ledger,profile,prepared.cropId);
                focusedHypotheses.addAll(f.hypotheses);TypedFieldNormalizerV2.normalize(ledger);
                recordViews(id,f.views,ledger,id.uploadedImageCount);
            }
        }catch(Exception failure){
            id.pipelineFailureDomain="SECOND_VISION_"+technicalStatus(failure);
            id.v2RecoveryTrace=append(id.v2RecoveryTrace,"vision2_failed="+technicalStatus(failure));
        }
        hypotheses=HypothesisGeneratorV2.merge(primary.hypotheses,focusedHypotheses);
        // Reparse discovery against the new evidence. Verifier rejection flags are
        // intentionally not reused after a corrected physical reading.
        discovered=parseWeb(discovery,profile,ledger,hypotheses,id,"web1_recheck");
        OpenAiClient.Response confirmation=webPass(client,images,
                CandidateRetrieverV2.prompt(profile,ledger,hypotheses)
                + "\nFINAL VERIFICATION PASS after a fresh physical inspection. Actively test the leading records "
                + "against alternatives using the newly observed fields. Retrieve the exact checklist row/product reference "
                + "and variant/edition/print-run documentation where relevant. Prefer an independent authoritative source "
                + "when available. Reusing a page is not independent corroboration. A previous candidate is a lead, never proof. "
                + "Do not repeat only the broad discovery search. Missing evidence must remain unknown. "
                + "Never treat a serial fraction as a collector fraction, a copyright as a release season, or foil as an exact parallel. "
                + "Never invent an image comparison: layout_match requires a real documented reference. "
                + "Return every still plausible record, including alternatives that contradict the initial hypothesis."
                + "\nDISCOVERY_LEADS_NON_BINDING="+candidateSummary(discovered)
                + "\nPHYSICAL_CONFLICTS="+conflictSummary(ledger,profile),
                id,usage,"v154_web2_verification");
        List<IdentityCandidateV2> finalRecords=parseWeb(confirmation,profile,ledger,hypotheses,id,"web2");
        List<IdentityCandidateV2> all=new ArrayList<>(hypotheses);
        // Last-pass records take precedence for the same catalog identity. Never
        // combine fields from two responses or stack confidence for duplicated pages.
        all.addAll(mergeWebRecords(discovered,finalRecords));
        List<IdentityCandidateV2> ranked=CandidateVerifierV2.verify(all,ledger,profile);
        id.v2RecoveryTrace=append(id.v2RecoveryTrace,"protocol=VISION_WEB_VISION_WEB;logical_stages=4");
        return finish(id,ledger,profile,ranked,"",usage);
    }

    private static OpenAiClient.Response webPass(OpenAiClient client,List<String> images,String prompt,
            Models.Identification id,Models.Usage usage,String stage){
        id.v2CallReasons=append(id.v2CallReasons,stage);
        try{
            OpenAiClient.Response r=client.identityWebSearchV2(images,prompt);collect(id,usage,r,stage);
            if(needsTechnicalRetry(r)||r.usage==null||r.usage.webCalls<1){
                id.webStatus="RETRYABLE_TECHNICAL";id.pipelineFailureDomain=stage+"_INVALID_OR_NO_SEARCH";
                id.v2RecoveryTrace=append(id.v2RecoveryTrace,stage+"=INVALID_OR_NO_SEARCH");return null;
            }
            id.webStatus="COMPLETED";
            if(id.pipelineFailureDomain.startsWith("v154_web"))id.pipelineFailureDomain="NONE";
            id.v2RecoveryTrace=append(id.v2RecoveryTrace,stage+"=COMPLETED");return r;
        }catch(Exception failure){
            id.webStatus="RETRYABLE_TECHNICAL";id.pipelineFailureDomain=stage+"_"+technicalStatus(failure);
            id.v2RecoveryTrace=append(id.v2RecoveryTrace,stage+"="+technicalStatus(failure));return null;
        }
    }

    private static List<IdentityCandidateV2> parseWeb(OpenAiClient.Response r,DomainProfileRouterV2.Profile profile,
            ImmutableEvidenceLedgerV2 ledger,List<IdentityCandidateV2> hypotheses,Models.Identification id,String stage){
        if(r==null||r.payload==null)return new ArrayList<>();
        for(String q:CandidateRetrieverV2.queries(r.payload))if(!contains(id.webQueries,q))id.webQueries.add(q);
        List<IdentityCandidateV2> parsed=CandidateRetrieverV2.parse(r.payload,profile,ledger);
        CandidateRetrieverV2.bindToolSources(parsed,r.sources);
        String violation=CandidateRetrieverV2.neutralQueryViolation(r.payload,profile,hypotheses,ledger,r.queries);
        if(!empty(violation)){rejectBatch(parsed,"neutral_query_policy:"+violation);id.v2RecoveryTrace=append(id.v2RecoveryTrace,stage+"_rejected="+violation);}
        return parsed;
    }

    static List<IdentityCandidateV2> mergeWebRecords(List<IdentityCandidateV2> first,List<IdentityCandidateV2> last){
        java.util.LinkedHashMap<String,IdentityCandidateV2> records=new java.util.LinkedHashMap<>();
        for(IdentityCandidateV2 c:first)records.put(recordKey(c),c);
        for(IdentityCandidateV2 c:last){String key=recordKey(c);IdentityCandidateV2 old=records.get(key);
            if(old==null||!c.rejected||old.rejected)records.put(key,c);}
        return new ArrayList<>(records.values());
    }
    private static String recordKey(IdentityCandidateV2 c){
        StringBuilder b=new StringBuilder(c.domain.name());
        for(String f:new String[]{"manufacturer","productLine","setName","subSeries","athlete","cardName","model",
                "catalogCardNumber","productReleaseYear","edition","finish","commercialFormat","configuration","productCode","language"})
            b.append('|').append(TypedFieldNormalizerV2.normalizeValue(f,c.value(f),"").toUpperCase(Locale.ROOT));
        return b.toString();
    }
    static String reviewFields(DomainProfileRouterV2.Profile profile,ImmutableEvidenceLedgerV2 ledger,List<IdentityCandidateV2> records){
        java.util.LinkedHashSet<String> fields=new java.util.LinkedHashSet<>(AdaptiveRecoveryPlannerV2.criticalMissing(profile,ledger));
        for(IdentityCandidateV2 c:records)for(java.util.Map.Entry<String,SemanticRelationV3.Relation> e:c.fieldRelations.entrySet())
            if(!SemanticRelationV3.compatible(e.getValue()))fields.add(e.getKey());
        for(IdentityCandidateV2 c:records)for(String f:c.fields.keySet())for(IdentityCandidateV2 other:records)
            if(!c.value(f).isEmpty()&&!other.value(f).isEmpty()&&!TypedFieldNormalizerV2.equivalent(f,c.value(f),other.value(f)))fields.add(f);
        if(DomainProfileRouterV2.cards(profile))java.util.Collections.addAll(fields,"physicalSerial","edition","firstEditionMark","finish","subSeries","productReleaseYear","manufacturer");
        return String.join(",",fields);
    }
    static String reviewPrompt(DomainProfileRouterV2.Profile profile,String fields,List<String> imageMap){
        return "PHYSICAL REVIEW after catalog discovery. Re-read the original object independently; no catalog values are provided. "
                + "Priority fields identified by the comparison: "+fields+". "
                + "For graded cards transcribe the grading label separately using gradingCompany, gradingGrade, gradingCertification, gradingSubgrades, "
                + "slabSetName, slabCardNumber, slabYear, slabLanguage, slabEdition and slabFinish. These describe the enclosed card, not its manufacturer or physical print run. "
                + "Inspect all supplied originals, then details. Transcribe all identity-bearing text including brand, complete product line, "
                + "set/subseries, name, card/collector number, serial, product release season, copyright, language, edition and finish. "
                + "For comics inspect title, publisher, issue/volume, printing and indicia; for electronics inspect full model/part number and revision. "
                + "Use productReleaseYear for a season in the product identification line, never statisticsSeason. "
                + "Separate manufacturer from copyright licensor, serial from card number, jersey number from identifier. "
                + "For TCG inspect First Edition lettering and location separately from expansion, rarity and evolution symbols. "
                + "Report ABSENT only after inspecting a readable edition region. For sports inspect both faces including every edge serial. "
                + "If a character is unreadable, report the uncertainty; do not choose the expected catalog value. "
                + "A reflective finish or PRIZM mark does not prove a named parallel. Preserve the literal full legal/product line as printedLabel "
                + "and also emit its separately observed fields with locations. No Web information may become a photographic fact. "
                + "PROFILE="+DomainProfileRouterV2.categoryKey(profile)+" IMAGE_MAP="+imageMap;
    }
    private static String candidateSummary(List<IdentityCandidateV2> records){
        StringBuilder b=new StringBuilder();for(IdentityCandidateV2 c:records){
            b.append(c.display()).append(" source=").append(c.sourceUrl).append(" unknown=").append(c.unknownFields).append("; ");
            if(b.length()>2600)break;}return clip(b.toString(),3000);
    }
    private static String conflictSummary(ImmutableEvidenceLedgerV2 ledger,DomainProfileRouterV2.Profile profile){
        StringBuilder b=new StringBuilder();for(ConflictResolverV2.Conflict c:ConflictResolverV2.resolve(ledger,profile))b.append(c.field).append('=').append(c.valueA).append(" versus ").append(c.valueB).append(';');
        return clip(b.toString(),1000);
    }

    static Models.Identification replay(Models.LocalScan local,JSONObject primaryPayload,JSONObject focusedPayload,JSONObject webPayload,Models.Usage usage){Models.Identification id=new Models.Identification();id.localScan=local;ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestLocal(local,ledger);ObservationExtractorV2.Result p=ObservationExtractorV2.ingestPrimary(primaryPayload,ledger);id.uploadedImageCount=Math.max(1,p.views.size());recordViews(id,p.views,ledger,id.uploadedImageCount);TypedFieldNormalizerV2.normalize(ledger);DomainProfileRouterV2.Profile profile=DomainProfileRouterV2.route(p.category,ledger);List<IdentityCandidateV2>focus=new ArrayList<>();if(focusedPayload!=null){ObservationExtractorV2.Result f=ObservationExtractorV2.ingestFocused(focusedPayload,ledger,profile,"replay-focused");focus.addAll(f.hypotheses);recordViews(id,f.views,ledger,id.uploadedImageCount);TypedFieldNormalizerV2.normalize(ledger);}List<IdentityCandidateV2>all=HypothesisGeneratorV2.merge(p.hypotheses,focus);if(webPayload!=null)all.addAll(CandidateRetrieverV2.parseReplay(webPayload,profile,ledger));return finish(id,ledger,profile,CandidateVerifierV2.verify(all,ledger,profile),"",usage);}

    private static Models.Identification finish(Models.Identification id,ImmutableEvidenceLedgerV2 ledger,DomainProfileRouterV2.Profile profile,List<IdentityCandidateV2>ranked,String technical,Models.Usage usage){List<ConflictResolverV2.Conflict>conflicts=ConflictResolverV2.resolve(ledger,profile);id.estimatedAnalysisCostUsd=usage==null?0d:usage.costUsd;FinalStateReducerV2.reduce(id,ledger,profile,ranked,conflicts,technical);return id;}
    private static void collect(Models.Identification id,Models.Usage usage,OpenAiClient.Response r,String stage){if(r==null)return;if(r.payload!=null)id.v2StagePayloads.add(stage+"\n"+r.payload.toString());if(usage!=null&&r.usage!=null)usage.add(r.usage);if(!contains(id.webStages,stage))id.webStages.add(stage);for(String q:r.queries)if(!contains(id.webQueries,q))id.webQueries.add(q);for(Models.Source incoming:r.sources){if(incoming==null)continue;boolean duplicate=false;for(Models.Source existing:id.sources)if(!empty(incoming.url)&&incoming.url.equals(existing.url)){duplicate=true;break;}if(!duplicate)id.sources.add(incoming);}id.v2CallReasons=append(id.v2CallReasons,stage);}
    private static String primaryPrompt(Models.LocalScan local,String details){return PhotoReadingV156.prompt()+"UNIVERSAL IDENTITY ENGINE V3 evidence contract. First group only coherent views of the same physical subject; ignore app/browser UI, conversations and external documents, but inspect the physical object shown inside a screenshot. Section A contains only literal localized observations. A catalog label inferred from a symbol (set name, brand or product family) belongs in candidates unless the literal text/logo is actually visible. Never describe a symbol as a catalog set name in facts. Each fact must include image, side, exact region and raw visible value. Section B candidates are isolated hypotheses and never become facts. Separate copyright owner/licensor from manufacturer; product release season from release year and statistics season; card/collector number from jersey/statistics/decorative numbers/serial; card role BASE from edition; sealed configuration from card number. Preserve stable remote-control labels and topology. For slabbed cards preserve all label data using gradingCompany, gradingGrade, gradingCertification, gradingSubgrades, slabSetName, slabCardNumber, slabYear and slabLanguage. A grading company is not the card manufacturer and its certificate is not a card serial. USER_HINT_SOFT="+clip(details,300);}
    private static String focusedPrompt(DomainProfileRouterV2.Profile profile,String field,ImmutableEvidenceLedgerV2 ledger){String task="Inspect only the named discriminator";if(profile==DomainProfileRouterV2.Profile.TCG_CARD)task="Transcribe card name, exact collector number, raw set-symbol appearance, First Edition mark, finish, language and copyright from the supplied crops. Inspect the whole original and each crop before deciding a mark is missing. Return an explicit firstEditionMark fact with raw lettering and location when visible; if unreadable, report that in missing_discriminators. Inventory edition, expansion, rarity and energy symbols separately by location; a star beside the collector number is a rarity mark, not automatically the expansion symbol. Do not omit the edition inspection because the collector number is already legible";else if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD)task="Transcribe an exact card number only from a printed card-identifier label; keep jersey, graphic, copyright, decorative-circle and statistics numbers separate. Inspect both coherent subject views and transcribe the full set season";else if(profile==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT)task="Transcribe literal manufacturer logo/text, full product line including subseries, season, printed configuration and the raw letters/shapes of packaging format badges; keep badge appearance separate from inferred commercial format";else if(profile==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL)task="Transcribe visible brand text only if present and record the complete distinctive button topology; shape-only brands remain candidates";return "PROFILE="+DomainProfileRouterV2.categoryKey(profile)+" DECISIVE_FIELD="+field+". "+task+". Never turn a catalog interpretation into an observed fact. EXISTING_OBSERVED="+clip(observedSummary(ledger),900);}
    private static String observedSummary(ImmutableEvidenceLedgerV2 l){StringBuilder b=new StringBuilder();for(EvidenceAtom a:l.byLevel(EvidenceAtom.EpistemicLevel.OBSERVED))if(a.localized()){if(b.length()>0)b.append(" | ");b.append(a.field).append('=').append(a.normalizedValue);}return b.toString();}
    private static boolean needsTechnicalRetry(OpenAiClient.Response r){return r==null||!r.complete||!empty(r.parseError)||r.payload==null||r.payload.length()==0;}
    private static String trace(AdaptiveRecoveryPlannerV2.Plan p){return p.action+":"+p.discriminator+":"+p.reason+":gain="+p.expectedInformationGain+":cost="+p.estimatedCost;}
    private static String technicalDetail(Exception x){return technicalStatus(x)+(x instanceof ApiCallFailure?":"+x.getMessage()+":retry_after_ms="+((ApiCallFailure)x).retryAfterMillis:":"+x.getClass().getSimpleName());}
    private static String technicalStatus(Exception x){if(x instanceof ApiCallFailure)return ((ApiCallFailure)x).domain();if(x instanceof java.net.SocketTimeoutException)return "TIMEOUT";String m=safe(x.getMessage()).toLowerCase(Locale.ROOT);return m.contains("timeout")?"TIMEOUT":m.contains("json")?"INVALID_JSON":m.contains("network")||m.contains("connect")?"NETWORK_ERROR":"VISION_TECHNICAL";}
    private static boolean contains(List<String>values,String v){for(String x:values)if(x.equalsIgnoreCase(v))return true;return false;}
    private static int originalImageCount(Models.LocalScan local,List<String>images){if(local!=null&&!local.textByImage.isEmpty())return local.textByImage.size();return images==null?0:images.size();}
    private static void recordViews(Models.Identification id,List<String>reported,ImmutableEvidenceLedgerV2 ledger,int count){if(reported!=null)for(String v:reported)if(!empty(v)&&!contains(id.photoViews,v))id.photoViews.add(v);if(id.photoViews.isEmpty()&&count>0){boolean observed=false;for(EvidenceAtom a:ledger.byLevel(EvidenceAtom.EpistemicLevel.OBSERVED))if(a.localized()){observed=true;break;}if(observed)for(int i=0;i<count;i++)id.photoViews.add("image="+i+":uploaded");}id.evidenceLedgerStatus=id.photoViews.isEmpty()?"UNSTRUCTURED":"STRUCTURED";}
    private static void rejectBatch(List<IdentityCandidateV2>candidates,String reason){if(candidates==null)return;for(IdentityCandidateV2 c:candidates){c.rejected=true;c.rejectionReason=reason;c.disproofResult="FAILED";c.disproofReason=reason;}}
    private static int modalityCount(ImmutableEvidenceLedgerV2 ledger,EvidenceAtom.Modality modality){int count=0;for(EvidenceAtom atom:ledger.all())if(atom.modality==modality)count++;return count;}
    private static String append(String a,String b){return empty(a)?safe(b):empty(b)?safe(a):safe(a)+" | "+safe(b);}
    private static String clip(String v,int n){String x=safe(v);return x.length()<=n?x:x.substring(0,n);}
    private static boolean empty(String v){return safe(v).isEmpty();}private static String safe(String v){return v==null?"":v.trim();}
}
