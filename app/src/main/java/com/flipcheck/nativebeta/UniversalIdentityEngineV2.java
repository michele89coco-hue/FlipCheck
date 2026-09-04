package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

/** v1.32 production route: extract -> normalize -> recover -> retrieve -> verify -> reduce once. */
final class UniversalIdentityEngineV2 {
    private UniversalIdentityEngineV2() {}
    static boolean enabled(){return true;}

    static Models.Identification identify(Models.LocalScan local,List<String>images,String details,OpenAiClient client,Models.Usage usage)throws Exception{
        Models.Identification id=new Models.Identification();id.localScan=local;id.finalStateReducerVersion=FinalStateReducerV2.VERSION;
        ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestLocal(local,ledger);
        String technical="";ObservationExtractorV2.Result primary=new ObservationExtractorV2.Result();List<IdentityCandidateV2>focusedHypotheses=new ArrayList<>();
        if(images==null||images.isEmpty()){id.missingDiscriminativeFields="complete_object_photo";id.pipelineFailureDomain="NO_IMAGE";return finish(id,ledger,DomainProfileRouterV2.Profile.GENERIC_OBJECT,new ArrayList<>(),"",usage);}
        OpenAiClient.Response first=null;
        try{first=client.observe(new ArrayList<>(images),primaryPrompt(local,details));collect(id,usage,first,"v132_primary_observation");}
        catch(Exception failure){technical=technicalStatus(failure);id.v2CallReasons=append(id.v2CallReasons,"primary_vision:"+technical);}
        if(needsTechnicalRetry(first)&&first!=null&&first.payload!=null&&first.payload.length()>0){primary=ObservationExtractorV2.ingestPrimary(first.payload,ledger);TypedFieldNormalizerV2.normalize(ledger);id.v2RecoveryTrace=append(id.v2RecoveryTrace,"partial_primary_facts_salvaged");}
        if(needsTechnicalRetry(first)&&usage!=null&&usage.costUsd+.004d<=.025d){
            try{OpenAiClient.Response retry=client.observeTechnicalRecovery(new ArrayList<>(images),primaryPrompt(local,details)+"\nTECHNICAL_RETRY=compact_valid_json");collect(id,usage,retry,"v132_primary_technical_retry");id.technicalRetryCount++;first=retry;technical="";}
            catch(Exception failure){technical=technicalStatus(failure);}
        }
        if(first!=null&&first.payload!=null){primary=ObservationExtractorV2.ingestPrimary(first.payload,ledger);id.visionResponseStatus=first.technicalStatus;id.visionFinishReason=first.incompleteReason;}
        if(first==null||needsTechnicalRetry(first)){id.pipelineFailureDomain=empty(technical)?"PRIMARY_VISION_INVALID":technical;return finish(id,ledger,DomainProfileRouterV2.route(primary.category,ledger),primary.hypotheses,id.pipelineFailureDomain,usage);}
        TypedFieldNormalizerV2.normalize(ledger);DomainProfileRouterV2.Profile profile=DomainProfileRouterV2.route(primary.category,ledger);id.v2Profile=DomainProfileRouterV2.categoryKey(profile);id.categoryKey=id.v2Profile;
        if(!primary.contentSufficient){id.missingDiscriminativeFields="identity_bearing_surface";return finish(id,ledger,profile,primary.hypotheses,"",usage);}

        AdaptiveRecoveryPlannerV2.Plan plan=AdaptiveRecoveryPlannerV2.afterPrimary(profile,ledger,usage);id.v2RecoveryTrace=trace(plan);
        if(plan.action==AdaptiveRecoveryPlannerV2.Action.FOCUSED_VISION){
            ImagePreparationV2.Prepared prepared=ImagePreparationV2.focused(images,profile,plan.discriminator);id.v2ImagePreparationTrace=prepared.trace.toString();id.additionalVisionReason=plan.reason+":"+plan.discriminator;id.v2CallReasons=append(id.v2CallReasons,"focused_vision:"+plan.reason+":"+plan.discriminator);
            if(!prepared.images.isEmpty())try{OpenAiClient.Response focused=client.observeFocusedV2(prepared.images,focusedPrompt(profile,plan.discriminator,ledger));collect(id,usage,focused,"v132_focused_"+plan.discriminator);id.discriminativeVisionCount++;if(focused!=null&&focused.payload!=null){ObservationExtractorV2.Result f=ObservationExtractorV2.ingestFocused(focused.payload,ledger,profile,prepared.cropId);focusedHypotheses.addAll(f.hypotheses);TypedFieldNormalizerV2.normalize(ledger);}}
            catch(Exception failure){id.v2RecoveryTrace=append(id.v2RecoveryTrace,"focused_failed="+technicalStatus(failure));}
            plan=AdaptiveRecoveryPlannerV2.afterFocused(profile,ledger,usage);id.v2RecoveryTrace=append(id.v2RecoveryTrace,trace(plan));
        }
        List<IdentityCandidateV2>hypotheses=HypothesisGeneratorV2.merge(primary.hypotheses,focusedHypotheses);List<IdentityCandidateV2>all=new ArrayList<>(hypotheses);
        if(plan.action==AdaptiveRecoveryPlannerV2.Action.IDENTITY_WEB||AdaptiveRecoveryPlannerV2.needsWeb(profile,ledger)&&usage!=null&&usage.webCalls==0&&usage.costUsd+.008d<=.025d){
            id.v2CallReasons=append(id.v2CallReasons,"identity_web:"+plan.reason+":"+plan.discriminator);try{OpenAiClient.Response web=client.identityWebSearchV2(CandidateRetrieverV2.prompt(profile,ledger,hypotheses));collect(id,usage,web,"v132_identity_web");id.webStatus=web==null?"FAILED":"COMPLETED";if(web!=null&&web.payload!=null){for(String q:CandidateRetrieverV2.queries(web.payload))if(!contains(id.webQueries,q))id.webQueries.add(q);all.addAll(CandidateRetrieverV2.parse(web.payload,profile,ledger));TypedFieldNormalizerV2.normalize(ledger);}}
            catch(Exception failure){id.webStatus="FAILED";id.v2RecoveryTrace=append(id.v2RecoveryTrace,"identity_web_failed="+technicalStatus(failure));}
        }else if(AdaptiveRecoveryPlannerV2.needsWeb(profile,ledger)&&usage!=null&&usage.webCalls==0){id.webStatus="SKIPPED_BUDGET";id.v2RecoveryTrace=append(id.v2RecoveryTrace,"identity_web_skipped=budget_cap");}
        List<IdentityCandidateV2>ranked=CandidateVerifierV2.verify(all,ledger,profile);return finish(id,ledger,profile,ranked,"",usage);
    }

    static Models.Identification replay(Models.LocalScan local,JSONObject primaryPayload,JSONObject focusedPayload,JSONObject webPayload,Models.Usage usage){Models.Identification id=new Models.Identification();id.localScan=local;ImmutableEvidenceLedgerV2 ledger=new ImmutableEvidenceLedgerV2();ObservationExtractorV2.ingestLocal(local,ledger);ObservationExtractorV2.Result p=ObservationExtractorV2.ingestPrimary(primaryPayload,ledger);TypedFieldNormalizerV2.normalize(ledger);DomainProfileRouterV2.Profile profile=DomainProfileRouterV2.route(p.category,ledger);List<IdentityCandidateV2>focus=new ArrayList<>();if(focusedPayload!=null){focus.addAll(ObservationExtractorV2.ingestFocused(focusedPayload,ledger,profile,"replay-focused").hypotheses);TypedFieldNormalizerV2.normalize(ledger);}List<IdentityCandidateV2>all=HypothesisGeneratorV2.merge(p.hypotheses,focus);if(webPayload!=null)all.addAll(CandidateRetrieverV2.parse(webPayload,profile,ledger));TypedFieldNormalizerV2.normalize(ledger);return finish(id,ledger,profile,CandidateVerifierV2.verify(all,ledger,profile),"",usage);}

    private static Models.Identification finish(Models.Identification id,ImmutableEvidenceLedgerV2 ledger,DomainProfileRouterV2.Profile profile,List<IdentityCandidateV2>ranked,String technical,Models.Usage usage){List<ConflictResolverV2.Conflict>conflicts=ConflictResolverV2.resolve(ledger,profile);id.estimatedAnalysisCostUsd=usage==null?0d:usage.costUsd;FinalStateReducerV2.reduce(id,ledger,profile,ranked,conflicts,technical);return id;}
    private static void collect(Models.Identification id,Models.Usage usage,OpenAiClient.Response r,String stage){if(r==null)return;IdentificationEngine.collectStage(id,usage,r,stage);id.v2CallReasons=append(id.v2CallReasons,stage);}
    private static String primaryPrompt(Models.LocalScan local,String details){return "UNIVERSAL IDENTITY ENGINE V2. Section A facts: only localized visible text, logo, symbol, code, finish or layout. Each fact must include exact image, side and region. If a value cannot be localized, omit it from facts. Section B candidates: independent hypotheses with alternatives and reasons; never copy them into facts. Separate product release year from statistics season, card/collector number from jersey/statistics/serial, and sealed configuration from card number. Preserve stable remote-control labels and topology. USER_HINT_SOFT="+clip(details,300)+" LOCAL_OCR_SIZE="+(local==null?0:local.joinedText().length());}
    private static String focusedPrompt(DomainProfileRouterV2.Profile profile,String field,ImmutableEvidenceLedgerV2 ledger){return "PROFILE="+DomainProfileRouterV2.categoryKey(profile)+" DECISIVE_FIELD="+field+". Inspect only this discriminator. For cards transcribe the exact number/year/edition from its physical region and keep statistics separate. For sealed products read the actual manufacturer, line and printed configuration. For remotes, a brand without a visible logo remains a candidate hypothesis; preserve button topology for Web verification. EXISTING_OBSERVED="+clip(observedSummary(ledger),900);}
    private static String observedSummary(ImmutableEvidenceLedgerV2 l){StringBuilder b=new StringBuilder();for(EvidenceAtom a:l.byLevel(EvidenceAtom.EpistemicLevel.OBSERVED))if(a.localized()){if(b.length()>0)b.append(" | ");b.append(a.field).append('=').append(a.normalizedValue);}return b.toString();}
    private static boolean needsTechnicalRetry(OpenAiClient.Response r){return r==null||!r.complete||!empty(r.parseError)||r.payload==null||r.payload.length()==0;}
    private static String trace(AdaptiveRecoveryPlannerV2.Plan p){return p.action+":"+p.discriminator+":"+p.reason+":gain="+p.expectedInformationGain+":cost="+p.estimatedCost;}
    private static String technicalStatus(Exception x){String m=safe(x.getMessage()).toLowerCase(Locale.ROOT);return m.contains("timeout")?"TIMEOUT":m.contains("json")?"INVALID_JSON":m.contains("network")||m.contains("connect")?"NETWORK_ERROR":"VISION_TECHNICAL";}
    private static boolean contains(List<String>values,String v){for(String x:values)if(x.equalsIgnoreCase(v))return true;return false;}
    private static String append(String a,String b){return empty(a)?safe(b):empty(b)?safe(a):safe(a)+" | "+safe(b);}
    private static String clip(String v,int n){String x=safe(v);return x.length()<=n?x:x.substring(0,n);}
    private static boolean empty(String v){return safe(v).isEmpty();}private static String safe(String v){return v==null?"":v.trim();}
}
