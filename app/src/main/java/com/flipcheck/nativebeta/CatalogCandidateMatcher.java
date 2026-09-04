package com.flipcheck.nativebeta;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Compares every catalog result with the complete photographic fingerprint. */
final class CatalogCandidateMatcher {
    static final class Result {
        Models.CandidateScore accepted; final List<Models.CandidateScore> rejected=new ArrayList<>();
        final List<String> matched=new ArrayList<>(),conflicts=new ArrayList<>();
        Models.CandidateScore runnerUp; boolean disproofPassed; boolean ambiguous;
    }
    private CatalogCandidateMatcher() {}

    static Result evaluate(Models.Identification id, OpenAiClient.Response response){Result result=new Result();if(id==null||response==null||response.payload==null)return result;
        JSONObject payload=response.payload;JSONArray array=payload.optJSONArray("candidates");List<Models.CandidateScore> candidates=new ArrayList<>();
        if(array!=null)for(int i=0;i<array.length()&&i<12;i++){JSONObject x=array.optJSONObject(i);if(x!=null)candidates.add(CandidateCanonicalizer.fromJson(x));}
        if(candidates.isEmpty()){Models.CandidateScore legacy=legacyCandidate(payload);if(hasIdentityField(legacy))candidates.add(legacy);}
        IdentityProfileEngine.PhotoTuple photo=IdentityProfileEngine.tuple(id);IdentityProfileEngine.Profile profile=IdentityProfileEngine.profile(id,photo);
        int best=-1;for(Models.CandidateScore candidate:candidates){compare(candidate,photo,profile,id);candidate.webScore=webScore(candidate);
            candidate.physicalIdentifierScore=candidate.identifierScore;candidate.printedTextScore=candidate.textScore;
            candidate.catalogScore=candidate.webScore;candidate.webEvidenceScore=candidate.webScore;
            candidate.conflictPenalty=candidate.hardRejected?100:candidate.contradictions.size()*12;
            candidate.missingFieldPenalty=countMissing(candidate)*4;candidate.totalScore=combined(candidate);
            if(candidate.hardRejected){result.rejected.add(candidate);continue;}if(candidate.webScore>best){result.runnerUp=result.accepted;best=candidate.webScore;result.accepted=candidate;}else if(result.runnerUp==null||candidate.webScore>result.runnerUp.webScore)result.runnerUp=candidate;}
        for(Models.CandidateScore c:result.rejected)result.conflicts.addAll(c.hardViolations);
        if(result.accepted!=null&&result.runnerUp!=null&&materiallyDifferent(result.accepted,result.runnerUp,profile)
                &&result.accepted.webScore-result.runnerUp.webScore<10){result.ambiguous=true;result.conflicts.add("DISPROOF_AMBIGUOUS_RUNNER_UP");result.accepted=null;}
        result.disproofPassed=result.accepted!=null&&!result.ambiguous;
        if(result.accepted!=null)result.matched.addAll(result.accepted.hardMatches);return result;}

    private static Models.CandidateScore legacyCandidate(JSONObject p){JSONObject x=new JSONObject();copyFirst(p,x,"catalog_number","source_reported_catalog_number","source_confirmed_catalog_number");copyFirst(p,x,"release_year","source_reported_release_year","source_confirmed_release_year");
        copyFirst(p,x,"product_line","source_reported_product_line","source_confirmed_product_line");copyFirst(p,x,"edition","source_reported_variant","source_confirmed_variant");copy(p,x,"source_catalog_title","product_name");
        String[] direct={"brand","manufacturer","publisher","game","subject","card_name","team","sport","language","hp","evolution_stage","copyright_year","layout_signature","format","configuration","product_type","attacks","attack_names","source_url","source_authority","main_set","insert_subset","design_family","sub_series","distinguishing_tokens","parallel_family","parallel_color","print_run","serial_number","product_code","package_count","cards_per_pack","autograph_guarantee","memorabilia_guarantee","sealed_status"};
        for(String k:direct)if(p.has(k))try{x.put(k,p.opt(k));}catch(Exception ignored){}
        Models.CandidateScore c=CandidateCanonicalizer.fromJson(x);c.sourceUrl=clean(p.optString("source_url",c.sourceUrl));return c;}
    private static void copy(JSONObject from,JSONObject to,String source,String target){if(!from.has(source))return;try{to.put(target,from.opt(source));}catch(Exception ignored){}}
    private static void copyFirst(JSONObject from,JSONObject to,String target,String...sources){for(String source:sources)if(from.has(source)){copy(from,to,source,target);return;}}

    private static void compare(Models.CandidateScore c,IdentityProfileEngine.PhotoTuple p,IdentityProfileEngine.Profile profile,Models.Identification id){
        matchText(c,"brand",p.brand,c.brand,true,false);matchHierarchy(c,p,profile);
        matchText(c,"subject",p.subject,c.subject,profile!=IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT,false);
        if(profile==IdentityProfileEngine.Profile.SPORTS_CARD){matchSeason(c,p.year,c.year,true);matchText(c,"team",p.team,c.team,false,false);matchIdentifier(c,p.cardNumber,c.cardNumber,!empty(p.cardNumber),false);matchText(c,"layout",p.layout,c.layoutSignature,true,false);}
        else if(profile==IdentityProfileEngine.Profile.TCG){matchIdentifier(c,p.cardNumber,c.cardNumber,p.cardNumberVerified,true);matchText(c,"language",p.language,c.language,false,false);
            matchText(c,"hp",p.hp,c.hpOrPv,true,false);matchText(c,"evolutionStage",p.evolutionStage,c.evolutionStage,false,false);matchAttacks(c,p.attacks,c.attackNames);
            matchText(c,"layout",p.layout,c.layoutSignature,true,false);matchText(c,"finish",p.finish,c.finish,false,false);
            String webEdition=first(c.edition,c.printing);if(TcgPhysicalEditionPolicy.webEditionConflicts(id,webEdition))veto(c,"PHYSICAL_EDITION_CONFLICT:"+p.edition+"<>"+webEdition);
            else matchText(c,"edition",p.edition,webEdition,true,false);
            matchCopyright(c,p.copyrightYear,c.copyrightYear,c.year);matchSeason(c,p.year,c.year,true);}
        else if(profile==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT){matchSeason(c,p.year,c.year,true);matchText(c,"sport",p.sport,c.sport,true,false);
            matchText(c,"configuration",p.configuration,c.configuration,true,false);matchText(c,"format",p.format,c.format,false,false);
            matchText(c,"packageCount",p.packageCount,c.packageCount,true,false);matchText(c,"cardsPerPack",p.cardsPerPack,c.cardsPerPack,true,false);}
        requireAnchors(c,profile,p);
    }
    private static void matchHierarchy(Models.CandidateScore c,IdentityProfileEngine.PhotoTuple p,IdentityProfileEngine.Profile profile){
        if(CatalogHierarchy.sameLevelConflict(p,c)){veto(c,"PRODUCT_HIERARCHY_CONFLICT:"+p.family+"<>"+CatalogHierarchy.describe(c));return;}
        if(!empty(p.mainSet)&&(!empty(c.mainSet)||!empty(c.family))){String w=empty(c.mainSet)?c.family:c.mainSet;if(CatalogHierarchy.compatible(p.mainSet,w))matched(c,"mainSet="+w,12);}
        if(!empty(p.insertSubset)&&!empty(c.subset)){if(CatalogHierarchy.compatible(p.insertSubset,c.subset))matched(c,"subset="+c.subset,12);else veto(c,"SUBSET_CONFLICT:"+p.insertSubset+"<>"+c.subset);}
        if(!empty(p.designFamily)&&!empty(c.designFamily)){if(CatalogHierarchy.compatible(p.designFamily,c.designFamily))matched(c,"designFamily="+c.designFamily,8);else veto(c,"DESIGN_FAMILY_CONFLICT:"+p.designFamily+"<>"+c.designFamily);}
        if(!empty(p.subSeries)&&!empty(c.subSeries)){if(CatalogHierarchy.compatible(p.subSeries,c.subSeries))matched(c,"subSeries="+c.subSeries,12);else veto(c,"SUBSERIES_CONFLICT:"+p.subSeries+"<>"+c.subSeries);}
        if(!empty(p.family)&&CatalogHierarchy.candidateContainsObservedHierarchy(p,c))matched(c,"productHierarchy="+first(c.family,c.mainSet,c.subset,c.subSeries),14);
        else if(!p.distinctiveTokens.isEmpty()&&ProfileQueryBuilder.isSealedProfile(profile))veto(c,"DISTINCTIVE_TOKEN_LOST:"+p.distinctiveTokens);
        else if(!p.distinctiveTokens.isEmpty())missing(c,"catalog_did_not_report_all_distinctive_tokens="+p.distinctiveTokens);
        matchText(c,"parallelFamily",p.parallelFamily,first(c.parallelFamily,c.parallel),true,false);
        matchText(c,"parallelColor",p.color,c.parallelColor,true,false);matchText(c,"printRun",p.printRun,c.printRun,true,false);
    }
    private static void matchIdentifier(Models.CandidateScore c,String photo,String web,boolean verified,boolean alphaCollectorIsHard){if(empty(photo)||empty(web))return;if(identifier(photo).equals(identifier(web)))matched(c,"identifier="+web,24);else if(verified||alphaCollectorIsHard&&photo.matches("(?i).*[A-Z].*"))veto(c,"IDENTIFIER_CONFLICT:"+photo+"<>"+web);else missing(c,"unverified_identifier_alternative="+photo+"<>"+web);}
    private static void matchSeason(Models.CandidateScore c,String photo,String web,boolean hard){if(empty(photo)||empty(web))return;if(SeasonNormalizer.compatible(photo,web))matched(c,"season="+SeasonNormalizer.normalize(web),12);else if(hard)veto(c,"SEASON_CONFLICT:"+photo+"<>"+web);else missing(c,"season");}
    private static void matchCopyright(Models.CandidateScore c,String photo,String webCopyright,String webRelease){if(empty(photo))return;String candidate=empty(webCopyright)?webRelease:webCopyright;if(empty(candidate))return;
        int a=year(photo),b=year(candidate);if(a>0&&b>0&&Math.abs(a-b)>1)veto(c,"COPYRIGHT_RELEASE_CONFLICT:"+photo+"<>"+candidate);else matched(c,"copyright="+photo,8);}
    private static void matchAttacks(Models.CandidateScore c,List<String> photo,List<String> web){if(photo.isEmpty()||web.isEmpty()){missing(c,"attacks_unreported");return;}int matched=0;for(String a:photo)for(String b:web)if(semanticAttackCompatible(a,b)){matched++;break;}
        if(matched>0)matched(c,"attacks="+matched+"/"+photo.size(),Math.min(18,6+matched*4));
        if(matched<photo.size())missing(c,"attacks_partial_or_translated="+matched+"/"+photo.size());}
    private static void matchText(Models.CandidateScore c,String field,String photo,String web,boolean hard,boolean requirePhotoTokens){if(empty(photo)||empty(web))return;
        boolean ok=requirePhotoTokens?tokenCoverage(web,photo)>=.84d:textCompatible(photo,web,false);if(ok)matched(c,field+"="+web,field.equals("subject")?18:10);else if(hard)veto(c,field.toUpperCase(Locale.ROOT)+"_CONFLICT:"+photo+"<>"+web);else missing(c,field+"_different");}
    private static void requireAnchors(Models.CandidateScore c,IdentityProfileEngine.Profile profile,IdentityProfileEngine.PhotoTuple p){int matches=0;for(String value:c.hardMatches)if(value!=null&&!value.startsWith("weight="))matches++;
        if(profile==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT){if(empty(c.family)||matches<2)veto(c,"INSUFFICIENT_SEALED_CATALOG_ANCHORS");}
        else if(profile==IdentityProfileEngine.Profile.TCG){if(empty(c.subject)||matches<2)veto(c,"INSUFFICIENT_TCG_CATALOG_ANCHORS");}
        else if(profile==IdentityProfileEngine.Profile.SPORTS_CARD){if(empty(c.subject)||matches<2)veto(c,"INSUFFICIENT_SPORTS_CATALOG_ANCHORS");}
    }
    private static int webScore(Models.CandidateScore c){if(c.hardRejected)return 0;int authority=authority(c.sourceAuthority),matched=0;for(String x:c.hardMatches)matched+=x.startsWith("weight=")?parse(x.substring(7)):0;
        return clamp((authority*35+Math.min(100,matched)*65)/100);}
    private static int combined(Models.CandidateScore c){if(c.hardRejected)return 0;return clamp((c.webEvidenceScore*35+c.catalogScore*20+c.printedTextScore*15+c.layoutScore*15+c.physicalIdentifierScore*15)/100-c.conflictPenalty-c.missingFieldPenalty);}
    private static int authority(String a){String x=canon(a);if(x.contains("OFFICIAL"))return 100;if(x.contains("CHECKLIST")||x.contains("AUTHORITATIVE"))return 92;if(x.contains("DATABASE")||x.contains("SPECIALIST"))return 82;if(x.contains("MARKET"))return 55;if(x.contains("SNIPPET"))return 25;return 70;}
    private static void matched(Models.CandidateScore c,String value,int weight){c.hardMatches.add(value);c.hardMatches.add("weight="+weight);c.hardMatchWeight+=weight;}
    private static void missing(Models.CandidateScore c,String value){c.candidateFacts.add("not_applicable_or_unreported="+value);}
    private static void veto(Models.CandidateScore c,String reason){c.hardRejected=true;c.hardViolations.add(reason);c.contradictions.add("STRONG:"+reason);}
    private static boolean hasIdentityField(Models.CandidateScore c){return !empty(c.brand)||!empty(c.family)||!empty(c.subject)||!empty(c.cardNumber)||!empty(c.year);}
    private static boolean materiallyDifferent(Models.CandidateScore a,Models.CandidateScore b,IdentityProfileEngine.Profile p){if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT)return different(a.format,b.format)||different(a.productCode,b.productCode)||different(a.subSeries,b.subSeries);return different(a.cardNumber,b.cardNumber)||different(a.subset,b.subset)||different(a.parallelFamily,b.parallelFamily)||different(a.parallelColor,b.parallelColor)||different(a.printing,b.printing);}
    private static boolean different(String a,String b){return !empty(a)&&!empty(b)&&!canon(a).equals(canon(b));}
    private static String first(String...x){for(String v:x)if(!empty(v))return clean(v);return "";}
    private static boolean textCompatible(String a,String b,boolean exact){String x=semanticCanon(a),y=semanticCanon(b);if(x.isEmpty()||y.isEmpty())return true;if(exact)return x.equals(y);return x.equals(y)||x.contains(y)||y.contains(x);}
    private static String semanticCanon(String x){return canon(x).replace("HOLOGRAPHIC","HOLO").replace("HOLOFOIL","HOLO").replace("FOIL HOLO","HOLO");}
    private static boolean semanticAttackCompatible(String a,String b){String x=canon(a),y=canon(b);if(x.isEmpty()||y.isEmpty())return false;if(x.equals(y)||x.contains(y)||y.contains(x))return true;
        java.util.Set<String> xs=new java.util.LinkedHashSet<>(),ys=new java.util.LinkedHashSet<>();for(String t:x.split(" "))if(t.length()>3)xs.add(t);for(String t:y.split(" "))if(t.length()>3)ys.add(t);int common=0;for(String t:xs)if(ys.contains(t))common++;return common>=Math.min(2,Math.min(xs.size(),ys.size()));}
    private static double tokenCoverage(String hay,String needle){String h=" "+canon(hay)+" ",n=canon(needle);if(n.isEmpty())return 1;int total=0,found=0;for(String t:n.split(" "))if(t.length()>1){total++;if(h.contains(" "+t+" "))found++;}return total==0?1:(double)found/total;}
    private static String identifier(String x){return canon(x).replace(" ","");}
    private static int year(String x){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?:19|20)\\d{2}").matcher(clean(x));return m.find()?Integer.parseInt(m.group()):0;}
    private static int parse(String x){try{return Integer.parseInt(x);}catch(Exception e){return 0;}}
    private static int countMissing(Models.CandidateScore c){int n=0;for(String x:c.candidateFacts)if(x.startsWith("not_applicable_or_unreported="))n++;return Math.min(10,n);}
    private static int clamp(int x){return Math.max(0,Math.min(100,x));}
    private static String canon(String x){return Normalizer.normalize(clean(x),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}
    private static boolean empty(String x){return clean(x).isEmpty();}private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
