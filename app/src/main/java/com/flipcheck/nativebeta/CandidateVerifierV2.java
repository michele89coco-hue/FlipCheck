package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** v1.33 evidence-derived tournament. Every candidate is scored and disproved in isolation. */
final class CandidateVerifierV2 {
    private CandidateVerifierV2() {}

    static List<IdentityCandidateV2> verify(List<IdentityCandidateV2> candidates,ImmutableEvidenceLedgerV2 ledger,DomainProfileRouterV2.Profile profile){
        List<IdentityCandidateV2>out=new ArrayList<>();if(candidates==null)return out;
        for(IdentityCandidateV2 c:candidates){score(c,ledger,profile);out.add(c);}
        out.sort(Comparator.comparingInt((IdentityCandidateV2 x)->x.totalScore).reversed());
        // Prefer the complete seasonal record of the same verified card. Keep the
        // entire record isolated; never borrow just its year for another winner.
        IdentityCandidateV2 leaderRecord=null;
        for(IdentityCandidateV2 c:out)if(!c.rejected&&c.disproofPassed&&c.totalScore>=45){leaderRecord=c;break;}
        if(leaderRecord!=null)for(IdentityCandidateV2 c:new ArrayList<>(out))if(seasonRefines(c,leaderRecord)){out.remove(c);out.add(0,c);break;}
        boolean leader=false;for(IdentityCandidateV2 c:out){if(c.rejected)c.status="INCOMPATIBLE";else if(c.disproofPassed&&c.totalScore>=45&&!leader){c.status="WINNER";leader=true;}else if(c.totalScore>=55)c.status="AMBIGUOUS";else c.status="LOW_SUPPORT";}
        return out;
    }

    static boolean seasonRefines(IdentityCandidateV2 specific,IdentityCandidateV2 parent){
        if(specific==parent||specific.domain!=DomainProfileRouterV2.Profile.SPORTS_CARD||specific.rejected||!specific.retrieved||!specific.disproofPassed||specific.webSourceQuality<60)return false;
        if(!parent.value("productReleaseYear").matches("[0-9]{4}")||!specific.value("productReleaseYear").matches("[0-9]{4}-[0-9]{2,4}"))return false;
        if(!SemanticRelationV3.compatible(SemanticRelationV3.relate("productReleaseYear",parent.value("productReleaseYear"),specific.value("productReleaseYear"))))return false;
        for(String f:new String[]{"manufacturer","productLine","athlete","catalogCardNumber"})
            if(specific.value(f).isEmpty()||!SemanticRelationV3.compatible(SemanticRelationV3.relate(f,parent.value(f),specific.value(f))))return false;
        for(String f:new String[]{"cardRole","subSeries","edition","finish"})
            if(!specific.value(f).equalsIgnoreCase(parent.value(f)))return false;
        return true;
    }

    private static void score(IdentityCandidateV2 c,ImmutableEvidenceLedgerV2 ledger,DomainProfileRouterV2.Profile profile){
        int retrievedLayoutMatch=c.layoutMatch;
        c.matchedEvidence.clear();c.unmatchedEvidence.clear();c.trueConflicts.clear();c.fieldRelations.clear();c.disproofPassed=false;c.disproofResult="NOT_EXECUTED";c.disproofReason="";if(c.rejected){c.totalScore=0;c.disproofResult="FAILED";c.disproofReason=c.rejectionReason;return;}
        int identifiers=0,identifierComparisons=0,text=0,textComparisons=0,logos=0,config=0,design=0,designComparisons=0,coreMatches=0,coreConflicts=0,observedCompared=0;
        for(String field:c.fields.keySet()){
            String candidate=c.value(field);EvidenceAtom observed=observedFor(ledger,field,profile);if(observed==null)continue;observedCompared++;
            SemanticRelationV3.Relation relation=SemanticRelationV3.relate(photoField(field,profile),observed.normalizedValue,candidate);if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD&&field.equals("manufacturer")
                    &&relation==SemanticRelationV3.Relation.INCOMPATIBLE&&candidate.contains("/")){
                // Catalogs may list a publisher and its printed brand together.
                // Only an exact component corroborates the photographed label.
                for(String component:candidate.split("/"))if(TypedFieldNormalizerV2.equivalent("brand",observed.normalizedValue,component)){
                    relation=SemanticRelationV3.Relation.COMPATIBLE_EXTENSION;break;
                }
            }
            c.fieldRelations.put(field,relation);
            if(SemanticRelationV3.compatible(relation)){
                addUnique(c.matchedEvidence,observed.id);int weight=SemanticRelationV3.matchWeight(relation);
                if(identifier(field)){identifiers+=weight;identifierComparisons++;}else {text+=weight;textComparisons++;}
                if(designField(field)){design+=weight;designComparisons++;}
                if(field.equals("manufacturer")){logos=Math.max(logos,weight);coreMatches++;}
                else if(field.equals("configuration")){config=Math.max(config,weight);coreMatches++;}
                else if(coreField(field,profile))coreMatches++;
            }else if(relation==SemanticRelationV3.Relation.INCOMPATIBLE){
                c.unmatchedEvidence.add(field+":"+observed.normalizedValue+"<>"+candidate);
                if((coreField(field,profile)||exclusiveVariantConflict(field,observed.normalizedValue,candidate,profile))&&observed.reliable()){
                    c.trueConflicts.add(field+":"+observed.normalizedValue+"!="+candidate+"@"+observed.id);coreConflicts++;
                }
            }
        }
        validateReportedMatches(c,ledger);
        c.observedIdentifierMatch=average(identifiers,identifierComparisons);c.observedTextMatch=average(text,textComparisons);c.logoMatch=logos;c.configurationMatch=config;c.layoutMatch=designComparisons==0&&c.retrieved&&c.exactReference&&!c.value("layoutSignature").isEmpty()?retrievedLayoutMatch:designComparisons==0?0:average(design,designComparisons);
        c.catalogMatch=c.retrieved&&c.exactReference?Math.max(70,c.webSourceQuality):c.retrieved?Math.min(72,c.webSourceQuality):0;
        c.contradictionPenalty=Math.min(100,coreConflicts*45);c.inferenceOnlyPenalty=c.retrieved?0:Math.max(c.inferenceOnlyPenalty,25);

        CatalogConsistencyV3.Result consistency=CatalogConsistencyV3.check(c,profile);
        if(!consistency.coherent){c.rejected=true;c.rejectionReason=consistency.reason;c.disproofResult="FAILED";c.disproofReason=consistency.reason;}
        if(coreConflicts>0){c.rejected=true;c.rejectionReason="strong_observed_field_conflict";c.disproofResult="FAILED";c.disproofReason=join(c.trueConflicts);}

        int components=c.observedIdentifierMatch*25+c.observedTextMatch*20+c.logoMatch*10+c.layoutMatch*15+c.catalogMatch*15+c.webSourceQuality*10+c.configurationMatch*5;
        c.totalScore=Math.max(0,Math.min(100,components/100-c.contradictionPenalty-c.inferenceOnlyPenalty));
        if(!c.retrieved)c.totalScore=Math.min(c.totalScore,74);if(observedCompared==0&&c.retrieved)c.totalScore=Math.min(c.totalScore,54);
        if(c.rejected){c.totalScore=0;return;}

        boolean subseriesSupported=profile!=DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT||supportsObservedSubseries(c,ledger);
        boolean enough=disproofEvidence(profile,c,coreMatches)&&subseriesSupported;
        c.disproofPassed=c.retrieved&&consistency.coherent&&coreConflicts==0&&enough;c.disproofResult=c.disproofPassed?"PASSED":"INSUFFICIENT";c.disproofReason=c.disproofPassed?"isolated_record_matches_grounded_evidence":insufficientReason(profile,c,coreMatches);
        if(c.disproofPassed)c.totalScore=Math.min(100,c.totalScore+5);if(c.totalScore==100&&(!c.trueConflicts.isEmpty()||!c.unknownFields.isEmpty()&&!c.exactReference))c.totalScore=99;
        if(!subseriesSupported)c.disproofReason="observed_subseries_not_supported_by_record";
    }

    private static boolean supportsObservedSubseries(IdentityCandidateV2 c,ImmutableEvidenceLedgerV2 ledger){
        EvidenceAtom a=ledger.strongest("subSeries",EvidenceAtom.EpistemicLevel.OBSERVED);
        if(a==null||!a.localized())return true;
        String hierarchy=c.value("productLine")+" "+c.value("setName")+" "+c.value("subSeries");
        return SemanticRelationV3.compatible(SemanticRelationV3.relate("subSeries",a.normalizedValue,hierarchy));
    }

    private static boolean exclusiveVariantConflict(String field,String observed,String candidate,DomainProfileRouterV2.Profile p){
        if(!DomainProfileRouterV2.cards(p))return false;
        String a=TypedFieldNormalizerV2.normalizeValue(field,observed,""),b=TypedFieldNormalizerV2.normalizeValue(field,candidate,"");
        if(field.equals("edition"))return a.matches("FIRST_EDITION|UNLIMITED")&&b.matches("FIRST_EDITION|UNLIMITED")&&!a.equals(b);
        if(field.equals("finish"))return a.matches("HOLO|NON_HOLO|REVERSE_HOLO")&&b.matches("HOLO|NON_HOLO|REVERSE_HOLO")&&!a.equals(b);
        return false;
    }

    private static String insufficientReason(DomainProfileRouterV2.Profile p,IdentityCandidateV2 c,int coreMatches){
        List<String> reasons=new ArrayList<>();
        if(!c.retrieved)reasons.add("candidate_is_inference_only");
        boolean card=p==DomainProfileRouterV2.Profile.TCG_CARD||p==DomainProfileRouterV2.Profile.SPORTS_CARD;
        int minimumQuality=DomainProfileRouterV2.electronics(p)?65:60;
        if(c.webSourceQuality<minimumQuality)reasons.add("source_quality="+c.webSourceQuality+"<"+minimumQuality+" (scale=0..100)");
        int minimumMatches=p==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL?3:2;
        if(coreMatches<minimumMatches)reasons.add("grounded_core_matches="+coreMatches+"<"+minimumMatches);
        if(p==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT&&!hasFamilyMatch(c))reasons.add("manufacturer_or_product_line_grounding_missing");
        if(card){
            if(!c.exactReference)reasons.add("exact_catalog_reference_missing");
            if(c.sourceRecordId.isEmpty())reasons.add("isolated_record_id_missing");
            if(c.observedIdentifierMatch<82)reasons.add("physical_identifier_match="+c.observedIdentifierMatch+"<82");
        }else if(p==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL){
            if(c.layoutMatch<70)reasons.add("distinctive_layout_match="+c.layoutMatch+"<70");
        }else if(DomainProfileRouterV2.electronics(p)&&c.observedIdentifierMatch<82&&c.layoutMatch<75)
            reasons.add("model_identifier_or_distinctive_layout_missing");
        return reasons.isEmpty()?"insufficient_independent_grounding":join(reasons);
    }

    private static boolean disproofEvidence(DomainProfileRouterV2.Profile p,IdentityCandidateV2 c,int coreMatches){
        if(p==DomainProfileRouterV2.Profile.TCG_CARD||p==DomainProfileRouterV2.Profile.SPORTS_CARD)return c.exactReference&&!c.sourceRecordId.isEmpty()&&c.observedIdentifierMatch>=82&&coreMatches>=2&&c.webSourceQuality>=60;
        if(p==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT)return coreMatches>=2&&c.webSourceQuality>=60&&hasFamilyMatch(c);
        if(p==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL)return c.layoutMatch>=70&&coreMatches>=3&&c.webSourceQuality>=65;
        if(DomainProfileRouterV2.electronics(p))return (c.observedIdentifierMatch>=82||c.layoutMatch>=75)&&coreMatches>=2&&c.webSourceQuality>=65;
        return coreMatches>=2&&c.webSourceQuality>=60;
    }

    private static boolean hasFamilyMatch(IdentityCandidateV2 c){
        for(String field:new String[]{"manufacturer","productLine","setName","subSeries"}){
            SemanticRelationV3.Relation r=c.fieldRelations.get(field);
            if(r!=null&&SemanticRelationV3.compatible(r))return true;
        }
        return false;
    }

    private static int validateReportedMatches(IdentityCandidateV2 c,ImmutableEvidenceLedgerV2 ledger){int count=0;for(String raw:c.reportedMatchedFields){String field=reportedField(raw),candidate=c.value(field);if(candidate.isEmpty())continue;EvidenceAtom observed=ledger.strongest(field,EvidenceAtom.EpistemicLevel.OBSERVED);if(observed!=null&&observed.localized()&&SemanticRelationV3.compatible(SemanticRelationV3.relate(field,observed.normalizedValue,candidate))){addUnique(c.matchedEvidence,observed.id);count++;}}return count;}
    private static String reportedField(String raw){String x=raw==null?"":raw.trim();int cut=x.indexOf('=');if(cut<0)cut=x.indexOf(':');if(cut>0)x=x.substring(0,cut);return TypedFieldNormalizerV2.canonicalField(x,"");}
    private static EvidenceAtom observedFor(ImmutableEvidenceLedgerV2 ledger,String field,DomainProfileRouterV2.Profile profile){EvidenceAtom observed=ledger.strongest(photoField(field,profile),EvidenceAtom.EpistemicLevel.OBSERVED);if(observed==null&&field.equals("manufacturer"))observed=ledger.strongest("brand",EvidenceAtom.EpistemicLevel.OBSERVED);return observed!=null&&observed.localized()?observed:null;}
    private static String photoField(String field,DomainProfileRouterV2.Profile p){if(field.equals("catalogCardNumber"))return p==DomainProfileRouterV2.Profile.TCG_CARD?"collectorNumber":"physicalCardNumber";return field;}
    private static boolean identifier(String f){return f.endsWith("Number")||f.endsWith("Code")||f.equals("model")||f.equals("barcode");}
    private static boolean coreField(String f,DomainProfileRouterV2.Profile p){if(f.equals("manufacturer")||f.equals("productLine")||f.equals("setName")||f.equals("productReleaseYear"))return true;if(p==DomainProfileRouterV2.Profile.TCG_CARD)return f.equals("cardName")||f.equals("catalogCardNumber");if(p==DomainProfileRouterV2.Profile.SPORTS_CARD)return f.equals("athlete")||f.equals("catalogCardNumber");if(p==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT)return f.equals("configuration")||f.equals("productType")||f.equals("subSeries");if(p==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL)return f.equals("model")||designField(f);return f.equals("model");}
    private static boolean designField(String f){return f.equals("controlLayout")||f.equals("shortcutButtons")||f.equals("navigationLayout")||f.equals("numericKeypad")||f.equals("voiceControl")||f.equals("layoutSignature")||f.equals("printedLabel");}
    private static int average(int total,int count){return count<=0?0:Math.min(100,total/count);}
    private static void addUnique(List<String>list,String value){if(value!=null&&!value.isEmpty()&&!list.contains(value))list.add(value);}
    private static String join(List<String>values){StringBuilder b=new StringBuilder();for(String v:values){if(b.length()>0)b.append(" | ");b.append(v);}return b.toString();}
}
