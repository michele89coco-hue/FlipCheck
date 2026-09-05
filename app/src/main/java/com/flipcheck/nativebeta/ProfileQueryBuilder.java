package com.flipcheck.nativebeta;

/** Produces catalog/market queries only from fields allowed by the photographed profile. */
final class ProfileQueryBuilder {
    private ProfileQueryBuilder() {}

    static String seed(Models.Identification id){
        IdentityProfileEngine.PhotoTuple t=IdentityProfileEngine.tuple(id);
        IdentityProfileEngine.Profile p=IdentityProfileEngine.profile(id,t);
        id.queryProfile=p.name().toLowerCase(java.util.Locale.ROOT);
        if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT){
            id.queryFieldsIncluded="brand,mainProductLine,subSeries,distinctivePrintedTokens,season,sport/game,configuration,format,modelCode";
            id.queryFieldsExcluded="featuredSubjects,subject,cardNumber,stats,hp,activationCode,graded/raw";
            String commercial=commercialHierarchy(t);
            return join("sealed",contains(commercial,t.brand)?"":t.brand,commercial,t.year,t.sport,t.configuration,t.format,t.modelCode);
        }
        if(p==IdentityProfileEngine.Profile.TCG){
            String candidate=t.cardNumberVerified?t.verifiedCardNumber:t.cardNumber;
            id.queryFieldsIncluded="game/publisher,subject,set,language,attacks,hp,copyrightYear,layout,collectorNumberCandidate,physicalEdition,physicalFinish";
            id.queryFieldsExcluded="deviceModel,partNumber,featuredSubjects,stats,activationCode,webContestedEdition";
            return join("trading card",t.brand,t.subject,candidate,t.family,t.year,t.copyrightYear,t.language,t.edition,t.finish,t.hp,join(t.attacks.toArray(new String[0])),t.layout);
        }
        if(p==IdentityProfileEngine.Profile.SPORTS_CARD){
            String variant=id.rareVariantPhysicalProof?join(id.physicalParallel,id.parallelColor,id.physicalSerial):"";
            String candidate=t.cardNumberVerified?t.verifiedCardNumber:t.cardNumber;
            id.queryFieldsIncluded="publisher,mainSet,insertSubset,designFamily,season,subject,cardNumberCandidate"+(id.rareVariantPhysicalProof?",parallelFamily,parallelColor,physicalSerial,printRun":"");
            id.queryFieldsExcluded="ratings,statistics,jerseyNumber,graphicNumber,activationCode,unprovedParallel,featuredSubjects";
            return join("sports card",t.brand,t.mainSet,t.family,t.insertSubset,t.designFamily,t.year,t.subject,candidate,variant,t.parallelFamily,t.printRun);
        }
        if(IdentityProfileEngine.electronics(p)){
            id.queryFieldsIncluded="objectType,observedBrand,brandMark,controlLayout,shortcutButtons,navigationLayout,numericKeypad,voiceControl,modelCode";
            id.queryFieldsExcluded="buttonNumbers,uiOverlay,marketText,unlocalizedBrandHypothesis";
            return join(t.productType,t.brand,t.brandMark,t.controlLayout,t.shortcutButtons,t.navigationLayout,t.numericKeypad,t.voiceControl,t.modelCode);
        }
        id.queryFieldsIncluded="brand,modelCode,productFamily,distinctiveDesign";
        id.queryFieldsExcluded="featuredSubjects,stats,hp,activationCode,ocrNoise";
        return join(t.brand,t.family,t.modelCode,t.design);
    }

    static String expectedMarketState(Models.Identification id){IdentityProfileEngine.Profile p=IdentityProfileEngine.profile(id,IdentityProfileEngine.tuple(id));if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT)return "SEALED";return id!=null&&id.physicallyGraded?"GRADED":"RAW";}
    static String discovery(Models.Identification id){IdentityProfileEngine.PhotoTuple t=IdentityProfileEngine.tuple(id);IdentityProfileEngine.Profile p=IdentityProfileEngine.profile(id,t);
        if(p==IdentityProfileEngine.Profile.SPORTS_CARD)return join("sports card",t.brand,t.mainSet,t.family,t.insertSubset,t.designFamily,t.year,t.subject);
        if(p==IdentityProfileEngine.Profile.TCG)return join("trading card",t.brand,t.subject,t.year,t.copyrightYear,t.language,t.hp,join(t.attacks.toArray(new String[0])),t.layout);
        return seed(id);}
    static String verification(Models.Identification id){IdentityProfileEngine.PhotoTuple t=IdentityProfileEngine.tuple(id);String candidate=t.cardNumberVerified?"":t.cardNumber;
        return join(discovery(id),candidate.isEmpty()?"":"observed identifier candidate "+candidate);}
    static String disproof(Models.Identification id){NormalizedPhotoIdentity n=PhotographicFactNormalizer.require(id);return join(discovery(id),
            n.identifierAlternatives.isEmpty()?"":"compare identifier alternatives "+n.identifierAlternatives);}
    static String stagedPlan(Models.Identification id){return "DISCOVERY_WITHOUT_CONTESTED="+discovery(id)+" | PRECISE_DISCOVERY="+seed(id)+" | VERIFY_OBSERVED="+verification(id)
            +" | DISPROVE_ALTERNATIVES="+disproof(id)+" | MARKET_AFTER_IDENTITY_ONLY=true";}
    static java.util.List<String> exactQueries(Models.Identification id){java.util.List<String> q=new java.util.ArrayList<>();IdentityProfileEngine.PhotoTuple t=IdentityProfileEngine.tuple(id);IdentityProfileEngine.Profile p=IdentityProfileEngine.profile(id,t);
        if(p==IdentityProfileEngine.Profile.TCG){String code=firstClean(t.cardNumber,id.collectorNumberCandidate,id.cardNumberCandidate);String attacks=firstAttacks(t.attacks,2);
            add(q,join(t.brand,t.subject,code));add(q,join(t.brand,t.subject,code,t.family,t.language));add(q,join(t.brand,t.subject,code,t.family,t.edition));add(q,join(t.brand,t.subject,code,t.family,t.edition,t.finish,t.language,t.copyrightYear,t.year,attacks));}
        else if(p==IdentityProfileEngine.Profile.SPORTS_CARD){String code=firstClean(t.cardNumber,id.cardNumberCandidate);
            add(q,join(t.year,t.brand,t.mainSet,t.family,t.insertSubset,t.subject,code,"checklist"));add(q,join(t.year,t.mainSet,t.family,t.subject,t.team,"checklist"));add(q,join(t.brand,t.mainSet,t.family,t.subject,code));if(id.rareVariantPhysicalProof)add(q,join(t.subject,t.parallelFamily,t.color,t.printRun,t.serial));}
        else if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT){String commercial=commercialHierarchy(t);add(q,join(t.year,contains(commercial,t.brand)?"":t.brand,commercial,t.sport,t.configuration));add(q,join(t.year,contains(commercial,t.brand)?"":t.brand,commercial,t.format,"SKU"));add(q,join(t.year,contains(commercial,t.brand)?"":t.brand,commercial,"commercial format configuration"));}
        else if(IdentityProfileEngine.electronics(p)){add(q,seed(id));add(q,join(t.productType,t.shortcutButtons,t.navigationLayout,t.brandMark));add(q,join(t.brand,t.productType,t.modelCode));}
        else {add(q,seed(id));add(q,discovery(id));}
        while(q.size()>4)q.remove(q.size()-1);
        id.exactResolutionQueries.clear();id.exactResolutionQueries.addAll(q);return q;}
    static boolean isSealed(Models.Identification id){if(IdentityPresentationV2.owns(id))return IdentityPresentationV2.sealed(id);return IdentityProfileEngine.profile(id,IdentityProfileEngine.tuple(id))==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT;}
    static boolean isSealedProfile(IdentityProfileEngine.Profile p){return p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT;}
    private static void add(java.util.List<String>x,String v){v=sanitize(v);if(!v.isEmpty()&&!x.contains(v)&&x.size()<4)x.add(v);}
    private static String firstAttacks(java.util.List<String>x,int max){StringBuilder b=new StringBuilder();for(String v:x){v=sanitize(v);if(v.isEmpty())continue;if(b.length()>0)b.append(' ');b.append('"').append(v).append('"');if(--max==0)break;}return b.toString();}
    private static String firstClean(String...x){for(String v:x){v=sanitize(v);if(!v.isEmpty())return v;}return "";}
    private static String sanitize(String x){String v=x==null?"":x.trim().replaceAll("\\s+"," ");String low=v.toLowerCase(java.util.Locale.ROOT);return low.equals("null")||low.equals("empty")||low.equals("unknown")||low.equals("unresolved")||low.contains("not visibly specified")?"":v;}
    private static String commercialHierarchy(IdentityProfileEngine.PhotoTuple t){String out=hierarchy(t.family,t.subSeries);for(String token:t.distinctiveTokens)out=hierarchy(out,token);return out;}
    private static String hierarchy(String...xs){String out="";for(String x:xs){String c=canon(x);if(c.isEmpty()||contains(out,x))continue;out=join(out,x);}return out;}
    private static boolean contains(String text,String part){String p=canon(part);return !p.isEmpty()&&(" "+canon(text)+" ").contains(" "+p+" ");}
    private static String join(String...xs){StringBuilder b=new StringBuilder();for(String x:xs){x=sanitize(x);if(x.isEmpty())continue;String have=" "+canon(b.toString())+" ",next=canon(x);if(!next.isEmpty()&&have.contains(" "+next+" "))continue;if(b.length()>0)b.append(' ');b.append(x);}return b.toString().replaceAll("\\s+"," ").trim();}
    private static String canon(String x){return x==null?"":x.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}
}
