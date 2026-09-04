package com.flipcheck.nativebeta;

import java.util.Locale;

/** The only authority allowed to confirm identity. It reads photo/user evidence, never Web fields. */
final class PhotographicIdentityClosure {
    static final String ROUTE="photographic_identity_closure";
    private PhotographicIdentityClosure() {}

    static boolean canClose(Models.Identification id){
        if(id==null)return false;
        PhotographicFactNormalizer.normalize(id,"can_close");
        PhysicalCardNumberPolicy.normalize(id);PhysicalSerialPolicy.normalize(id);PhysicalVariantPolicy.normalize(id);
        IdentityProfileEngine.prepare(id);
        CandidateCanonicalizer.canonicalize(id,"before_photographic_can_close");
        return IdentityProfileEngine.assess(id).complete;
    }

    static boolean apply(Models.Identification id,String stage){
        if(id==null)return false;
        if(isTerminal(id)){enforce(id);return true;}
        PhotographicFactNormalizer.normalize(id,"closure_"+safe(stage));
        PhysicalCardNumberPolicy.normalize(id);PhysicalSerialPolicy.normalize(id);PhysicalVariantPolicy.normalize(id);
        IdentityProfileEngine.prepare(id);
        CandidateCanonicalizer.canonicalize(id,"before_photographic_closure");
        IdentityProfileEngine.Assessment a=IdentityProfileEngine.assess(id);
        HierarchicalConfidencePolicy.apply(id,a);
        id.closureBasis="photographic_tuple";id.blockingReason=a.blockingReason;
        id.missingDiscriminativeFields=a.missingDiscriminative;id.missingNonblockingFields=a.missingNonblocking;
        updateHierarchicalStates(id,a,false);
        if(!a.complete){id.identityStatus="UNRESOLVED";id.hierarchicalStatus=id.coreIdentityStatus.equals("PROBABLE")
                ?HierarchicalIdentityStatus.MAIN_IDENTITY_PROBABLE.name():HierarchicalIdentityStatus.INSUFFICIENT_EVIDENCE.name();
            id.finalDecisionReason="profile="+a.profile+"; core="+id.coreIdentityStatus+"; blocker="+a.blockingReason+"; missing="+a.missingDiscriminative;return false;}
        String brand=a.tuple.brand,family=IdentityProfileEngine.family(a),model=IdentityProfileEngine.model(a);
        Models.CandidateScore c=new Models.CandidateScore();c.brand=brand;c.family=family;c.model=model;
        c.probableReference=join(brand,family,model);c.probableReferenceConfidence=id.mainIdentityConfidence;
        c.identifierScore=a.tuple.cardNumberVerified||a.tuple.modelPhysical?Math.min(99,id.modelConfidence+6):Math.min(82,id.modelConfidence);
        c.textScore=id.mainIdentityConfidence;c.layoutScore=Math.min(98,id.mainIdentityConfidence+(a.tuple.frontBack?5:2));c.totalScore=id.mainIdentityConfidence;
        c.physicalIdentifierScore=c.identifierScore;c.printedTextScore=c.textScore;c.catalogScore=0;c.webEvidenceScore=0;
        c.conflictPenalty=0;c.missingFieldPenalty=0;
        c.evidence="Identità chiusa esclusivamente dalla tupla fotografica del profilo "+a.profile.name().toLowerCase(Locale.ROOT)+".";
        add(c,"physical_tuple_confirmation=true");add(c,"closure_route="+ROUTE);add(c,"closure_stage="+safe(stage));
        id.candidates.clear();id.candidates.add(c);id.canonicalCandidateCount=1;id.tournamentMargin=95;id.brand=brand;id.family=family;id.model=model;
        id.confirmedBrand=brand;id.confirmedFamily=family;id.confirmedModel=model;
        id.brandEvidence="photographic_evidence_ledger";id.brandRoleConfidence=Math.max(94,id.brandRoleConfidence);
        id.familyConfidence=Math.max(id.familyConfidence,Math.min(96,id.mainIdentityConfidence));
        id.photoIdentityComplete=true;id.photoIdentityPhysicalBinding=true;id.photoIdentityKind=ROUTE;
        id.photoIdentityConfidence=Math.max(id.photoIdentityConfidence,id.mainIdentityConfidence);id.marketReady=true;
        id.disproofPassed=!hasIdentifierConflict(id);id.modelProof=ROUTE;id.identityConfirmed=true;
        updateHierarchicalStates(id,a,true);
        id.identityStatus="CONFIRMED";id.decision="CONFIRMED";
        id.language=a.tuple.language;
        id.evolutionStage=a.tuple.evolutionStage;id.hpOrPv=a.tuple.hp;id.attackNames=a.tuple.attacks.toString();
        id.physicalCollectorNumber=a.profile==IdentityProfileEngine.Profile.TCG?a.tuple.collectorNumber:"";
        id.productType=a.tuple.productType;id.sealedFormat=a.tuple.format;id.productConfiguration=a.tuple.configuration;
        id.featuredSubjects.clear();id.featuredSubjects.addAll(a.tuple.featuredSubjects);
        if(a.profile==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT)id.title=CanonicalIdentityComposer.sealedTitle(id);
        else id.title=join(id.confirmedBrand,id.confirmedFamily,id.confirmedModel);
        id.nextPhotoRequest="";id.nextPhotoReason="";id.requestedPhotoReason="";id.blockingReason="";id.missingDiscriminativeFields="";
        id.verificationSummary="Identità verificata dalla foto — prezzi/comparabili sono uno stato indipendente.";
        id.decisionReason="closure_route="+ROUTE+"; closure_basis=photographic_tuple; closure_stage="+safe(stage)
                +"; missing_nonblocking_fields="+safe(id.missingNonblockingFields)+"; price_state_independent=true";
        id.finalDecisionReason=id.decisionReason+"; core_identity_status="+id.coreIdentityStatus+"; exact_identity_status="+id.exactIdentityStatus+"; variant_status="+id.variantStatus+"; identifier_status="+PhotographicFactNormalizer.require(id).identifierStatus;
        addOnce(id.observedEvidence,"closure_route="+ROUTE);addOnce(id.verifiedEvidence,"identity_status=CONFIRMED");
        return true;
    }

    private static void updateHierarchicalStates(Models.Identification id,IdentityProfileEngine.Assessment a,boolean closed){
        IdentityProfileEngine.PhotoTuple t=a.tuple;id.categoryStatus=a.profile==IdentityProfileEngine.Profile.OTHER_COLLECTIBLE
                &&safe(id.category).isEmpty()?"UNRESOLVED":"CONFIRMED";
        boolean core;
        if(a.profile==IdentityProfileEngine.Profile.SPORTS_CARD)core=!safe(t.brand).isEmpty()&&!safe(t.family).isEmpty()&&!safe(t.subject).isEmpty();
        else if(a.profile==IdentityProfileEngine.Profile.TCG)core=!safe(t.brand).isEmpty()&&!safe(t.subject).isEmpty()&&t.frontComplete;
        else if(a.profile==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT)core=!safe(t.brand).isEmpty()&&!safe(t.family).isEmpty();
        else if(IdentityProfileEngine.electronics(a.profile))core=!safe(t.brand).isEmpty()
                &&(t.modelPhysical||t.layoutDistinctive&&(!safe(t.controlLayout).isEmpty()||!safe(t.shortcutButtons).isEmpty()||!safe(t.brandMark).isEmpty()));
        else core=t.modelPhysical||(!safe(t.family).isEmpty()&&!safe(t.design).isEmpty());
        id.coreIdentityStatus=core?(closed?"CONFIRMED":"PROBABLE"):"UNRESOLVED";
        id.hierarchicalStatus=core?(closed?HierarchicalIdentityStatus.MAIN_IDENTITY_CONFIRMED.name():HierarchicalIdentityStatus.MAIN_IDENTITY_PROBABLE.name()):
                ("CONFIRMED".equals(id.categoryStatus)?HierarchicalIdentityStatus.CATEGORY_IDENTIFIED.name():HierarchicalIdentityStatus.INSUFFICIENT_EVIDENCE.name());
        if(!closed)id.exactIdentityStatus="UNRESOLVED";
        else if(a.profile==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT&&safe(t.format).isEmpty())id.exactIdentityStatus="FORMAT_UNRESOLVED";
        else if((a.profile==IdentityProfileEngine.Profile.SPORTS_CARD||a.profile==IdentityProfileEngine.Profile.TCG)
                &&!safe(t.cardNumber).isEmpty()&&!t.cardNumberVerified)id.exactIdentityStatus="NUMBER_UNVERIFIED";
        else if(a.profile==IdentityProfileEngine.Profile.SPORTS_CARD&&safe(t.cardNumber).isEmpty())id.exactIdentityStatus="CORE_CONFIRMED_NUMBER_UNRESOLVED";
        else if(a.profile==IdentityProfileEngine.Profile.TCG&&safe(t.family).isEmpty())id.exactIdentityStatus="SET_UNRESOLVED";
        else if(a.profile==IdentityProfileEngine.Profile.TCG&&safe(t.collectorNumber).isEmpty())id.exactIdentityStatus="FINGERPRINT_CONFIRMED";
        else if(IdentityProfileEngine.electronics(a.profile)&&safe(t.modelCode).isEmpty())id.exactIdentityStatus="MODEL_TO_VERIFY";
        else id.exactIdentityStatus="CONFIRMED";
        if(!safe(t.serial).isEmpty()||!safe(t.level).isEmpty()||id.rareVariantPhysicalProof){id.variantStatus="CONFIRMED";if(closed)id.hierarchicalStatus=HierarchicalIdentityStatus.VARIANT_CONFIRMED.name();}
        else if(!safe(t.finish).isEmpty()){id.variantStatus="FINISH_CONFIRMED_PARALLEL_UNRESOLVED";if(closed)id.hierarchicalStatus=HierarchicalIdentityStatus.VARIANT_PROBABLE.name();}
        else id.variantStatus="NOT_OBSERVED";
    }

    static boolean isTerminal(Models.Identification id){return id!=null&&(id.identityConfirmed||ROUTE.equalsIgnoreCase(safe(id.modelProof))
            ||safe(id.decisionReason).contains("closure_route="+ROUTE));}
    static boolean enforce(Models.Identification id){if(!isTerminal(id))return false;
        id.identityConfirmed=true;id.closureBasis="photographic_tuple";
        boolean conflict=hasIdentifierConflict(id)||"NUMBER_CONFLICT".equals(id.exactIdentityStatus)||"CONFLICTED".equals(id.identityStatus);
        if(conflict){id.identityStatus="CONFLICTED";id.decision="CONFLICTED";id.hierarchicalStatus=HierarchicalIdentityStatus.CONFLICTED.name();
            id.marketReady=false;id.disproofPassed=false;id.blockingReason="identifier_conflict";}
        else{id.identityStatus="CONFIRMED";id.decision="CONFIRMED";id.marketReady=true;id.blockingReason="";id.missingDiscriminativeFields="";}
        id.photoIdentityComplete=true;id.photoIdentityPhysicalBinding=true;id.modelProof=ROUTE;
        id.nextPhotoRequest="";id.nextPhotoReason="";id.requestedPhotoReason="";
        if(safe(id.confirmedBrand).isEmpty())id.confirmedBrand=safe(id.brand);if(safe(id.confirmedFamily).isEmpty())id.confirmedFamily=safe(id.family);
        if(safe(id.confirmedModel).isEmpty())id.confirmedModel=safe(id.model);id.brand=id.confirmedBrand;id.family=id.confirmedFamily;id.model=id.confirmedModel;
        if(!safe(id.decisionReason).contains("closure_route="+ROUTE))id.decisionReason="closure_route="+ROUTE+"; closure_basis=photographic_tuple; closure_reason=terminal_state_restored";
        if(safe(id.categoryStatus).equals("UNRESOLVED"))id.categoryStatus="CONFIRMED";
        if(safe(id.coreIdentityStatus).equals("UNRESOLVED"))id.coreIdentityStatus="CONFIRMED";
        return true;
    }
    static boolean mayRequestAnotherPhoto(Models.Identification id){return IdentityProfileEngine.concreteResolvableAmbiguity(id);}
    static String missingFields(Models.Identification id){IdentityProfileEngine.Assessment a=IdentityProfileEngine.assess(id);if(!a.blockingReason.isEmpty())return a.blockingReason+(a.missingDiscriminative.isEmpty()?"":":"+a.missingDiscriminative);return a.missingDiscriminative;}
    static String missingDecisiveField(Models.Identification id){return IdentityProfileEngine.assess(id).missingDiscriminative;}
    static String targetedPhotoRequest(Models.Identification id){if(!mayRequestAnotherPhoto(id))return "";IdentityProfileEngine.PhotoTuple t=IdentityProfileEngine.prepare(id);
        IdentityProfileEngine.Profile p=IdentityProfileEngine.profile(id,t);id.requestedPhotoProfile=p.name().toLowerCase(Locale.ROOT);String field=safe(id.discriminativeField).toLowerCase(Locale.ROOT);
        if(p==IdentityProfileEngine.Profile.TCG){
            if(field.contains("collector")||field.contains("card_number")||field.equals("number"))return "Fotografa in primo piano l’angolo inferiore della carta, dove è stampato il collector number.";
            if(field.contains("set")||field.contains("edition")||field.contains("printing"))return "Fotografa in primo piano l’area con simbolo del set o marcatura dell’edizione.";
            if(field.contains("finish")||field.contains("holo")||field.contains("reverse"))return "Fotografa il fronte inclinato alla luce, mettendo a fuoco la finitura holo/reverse da distinguere.";
            if(field.contains("back")&&!hasView(id,"back","retro","reverse"))return "Fotografa il retro completo solo per distinguere le due edizioni rimaste.";return "";}
        if(p==IdentityProfileEngine.Profile.SPORTS_CARD){
            if(field.contains("card_number")||field.equals("number"))return hasView(id,"back","retro","reverse")?"Fotografa in primo piano la zona del retro in cui è stampato il numero carta.":"Fotografa il retro completo e nitido, includendo la zona del numero carta.";
            if(field.contains("serial"))return "Fotografa in primo piano la tiratura x/y sulla superficie della carta.";
            if(field.contains("parallel"))return "Fotografa in primo piano la marcatura fisica o il pattern che distingue il parallel.";
            if(field.contains("front")&&!hasView(id,"front","fronte"))return "Fotografa il fronte completo della carta.";
            if(field.contains("back")&&!hasView(id,"back","retro","reverse"))return "Fotografa il retro completo della carta.";return "";}
        if(p==IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT){
            if(field.contains("barcode")||field.contains("sku")||field.contains("product_code"))return "Fotografa il lato del box con barcode, SKU o codice prodotto.";
            if(field.contains("format"))return "Fotografa il lato che indica il formato del prodotto, ad esempio Hobby, Blaster o Mega.";
            if(field.contains("year")||field.contains("season"))return "Fotografa il lato che riporta stagione o anno del prodotto.";
            if(field.contains("configuration"))return "Fotografa il lato con numero di pacchetti, carte per pacchetto o hit guarantee.";
            if(field.contains("seal"))return "Fotografa i sigilli e i lati completi del box.";return "";}
        if(IdentityProfileEngine.electronics(p)){
            id.requestedPhotoMissingField="exactModel";id.requestedPhotoSide="PHOTO_BACK";
            id.requestedPhotoRegion="rear_label";id.requestedPhotoExpectedEvidence="model, part number, barcode";
            return "Fotografa il retro includendo l’intera targhetta o etichetta e qualsiasi codice MODEL, P/N o sigla di riferimento.";
        }
        if(field.contains("model")||field.contains("product_code")||field.contains("reference")||field.contains("barcode")||field.contains("serial"))return "Per identificare il modello, fotografa la targhetta con MODEL, P/N, seriale, barcode o codice prodotto: di solito è sul retro, sotto l’apparecchio o dentro lo sportello.";
        return "";}
    static String webSeed(Models.Identification id){return ProfileQueryBuilder.seed(id);}
    private static void add(Models.CandidateScore c,String x){if(!c.candidateFacts.contains(x))c.candidateFacts.add(x);}
    private static void addOnce(java.util.List<String>x,String v){if(!x.contains(v))x.add(v);}
    private static boolean hasView(Models.Identification id,String...tokens){String x=id==null?"":id.photoViews.toString().toLowerCase(Locale.ROOT);for(String token:tokens)if(x.contains(token))return true;return false;}
    private static String join(String...xs){StringBuilder b=new StringBuilder();for(String x:xs){x=safe(x);if(x.isEmpty())continue;if(b.length()>0)b.append(' ');b.append(x);}return b.toString();}
    private static String safe(String x){return x==null?"":x.trim();}
    private static boolean hasIdentifierConflict(Models.Identification id){return DocumentedConflictPolicy.hasHardConflict(id);}
}
