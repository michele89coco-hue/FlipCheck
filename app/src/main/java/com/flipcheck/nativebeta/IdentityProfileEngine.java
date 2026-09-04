package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Profile and fingerprint engine consuming only NormalizedPhotoIdentity. */
final class IdentityProfileEngine {
    enum Profile { SPORTS_CARD, TCG, SEALED_TRADING_CARD_PRODUCT, CONSUMER_ELECTRONICS,
        CONSUMER_ELECTRONICS_ACCESSORY, TELEVISION_REMOTE_CONTROL,
        AUDIO_VIDEO_REMOTE_CONTROL, APPLIANCE_REMOTE_CONTROL, OTHER_COLLECTIBLE }
    static final class Assessment {
        final Profile profile; final PhotoTuple tuple; final boolean complete;
        final String blockingReason,missingDiscriminative,missingNonblocking;
        Assessment(Profile p,PhotoTuple t,boolean complete,String blocking,String missing,String nonblocking){this.profile=p;this.tuple=t;this.complete=complete;this.blockingReason=blocking;this.missingDiscriminative=missing;this.missingNonblocking=nonblocking;}
    }
    static final class PhotoTuple {
        String brand="",manufacturer="",publisher="",family="",mainSet="",insertSubset="",designFamily="",subSeries="",subject="",team="",year="",copyrightYear="",statisticalSeason="",
                cardNumber="",verifiedCardNumber="",collectorNumber="",serial="",level="",color="",finish="",edition="",printing="",format="",
                parallelFamily="",printRun="",configuration="",sport="",modelCode="",barcode="",design="",language="",hp="",attackText="",layout="",
                illustration="",productType="",rookieMarker="",evolutionStage="",graphicNumber="",attackDamage="",cardType="";
        String controlLayout="",shortcutButtons="",brandMark="",navigationLayout="",numericKeypad="",voiceControl="";
        String packageCount="",cardsPerPack="",autographGuarantee="",memorabiliaGuarantee="",sealedStatus="";
        final List<String> attacks=new ArrayList<>(),featuredSubjects=new ArrayList<>(),distinctiveTokens=new ArrayList<>();
        boolean frontComplete,frontBack,layoutDistinctive,modelPhysical,cardNumberVerified;
    }
    private IdentityProfileEngine() {}

    static PhotoTuple prepare(Models.Identification id){PhotoTuple t=tuple(id);Profile p=profile(id,t);NormalizedPhotoIdentity n=PhotographicFactNormalizer.require(id);
        n.profile=p.name().toLowerCase(Locale.ROOT);id.canonicalProfile=n.profile;id.queryProfile=n.profile;
        n.fingerprintComponents.clear();n.fingerprintScore=fingerprint(p,t,n.fingerprintComponents);
        id.fingerprintScore=n.fingerprintScore;id.fingerprintComponents=n.fingerprintComponents.toString();
        id.closureInputSnapshot=snapshot(p,t,n.fingerprintScore);PhotographicFactNormalizer.syncDebug(id,n);return t;}

    static Assessment assess(Models.Identification id){PhotoTuple t=prepare(id);Profile p=profile(id,t);List<String> missing=new ArrayList<>();
        if(id==null)return new Assessment(p,t,false,"no_identification","identification","");
        if(UniversalIdentityClosure.externalWatermarkObscuresIdentity(id))return new Assessment(p,t,false,"external_watermark_obscures_identity","identity_surface","");
        if(strongPhotoConflict(id))return new Assessment(p,t,false,"photographic_contradiction","conflicting_physical_fact","");
        if(coreSemanticConflict(PhotographicFactNormalizer.require(id)))return new Assessment(p,t,false,"semantic_identity_conflict","conflicting_product_line_or_subject",nonblocking(p,t));
        boolean concreteAmbiguity=id.photoIdentityAmbiguous&&Math.max(id.photoAlternativeCount,id.canonicalCandidateCount)>=2
                &&!id.discriminativeFieldVisible&&!clean(id.discriminativeField).isEmpty();
        if(concreteAmbiguity)return new Assessment(p,t,false,"materially_distinct_photo_candidates",clean(id.discriminativeField),nonblocking(p,t));
        boolean complete;
        switch(p){
            case SPORTS_CARD:
                need(missing,"manufacturer_or_publisher",t.brand);need(missing,"set_or_product_line",t.family);need(missing,"athlete_or_subject",t.subject);
                complete=missing.isEmpty()&&(id.photoIdentityComplete||t.frontBack||!t.cardNumber.isEmpty()||!t.level.isEmpty()||!t.edition.isEmpty()||!t.serial.isEmpty()||t.layoutDistinctive);
                if(!complete&&missing.isEmpty())missing.add("identity_bearing_card_view");break;
            case TCG:
                need(missing,"game_or_publisher",t.brand);need(missing,"subject",t.subject);if(!t.frontComplete)missing.add("complete_identity_bearing_front");
                complete=missing.isEmpty()&&PhotographicFactNormalizer.require(id).fingerprintScore>=65;
                if(!complete&&missing.isEmpty())missing.add("unique_front_fingerprint");break;
            case SEALED_TRADING_CARD_PRODUCT:
                need(missing,"manufacturer",t.brand);need(missing,"product_line",t.family);
                if(t.year.isEmpty()&&t.productType.isEmpty())missing.add("release_or_product_type");
                complete=missing.isEmpty()&&(t.frontComplete||id.photoIdentityComplete||t.modelPhysical);
                if(!complete&&missing.isEmpty())missing.add("complete_identity_bearing_front");break;
            case TELEVISION_REMOTE_CONTROL: case AUDIO_VIDEO_REMOTE_CONTROL: case APPLIANCE_REMOTE_CONTROL:
            case CONSUMER_ELECTRONICS: case CONSUMER_ELECTRONICS_ACCESSORY:
                need(missing,"observed_or_strongly_supported_brand",t.brand);
                boolean distinctive=!t.controlLayout.isEmpty()||!t.shortcutButtons.isEmpty()
                        ||!t.navigationLayout.isEmpty()||!t.brandMark.isEmpty();
                complete=missing.isEmpty()&&(t.modelPhysical||distinctive&&t.layoutDistinctive);
                if(!complete&&missing.isEmpty())missing.add("distinctive_control_layout_or_model_code");break;
            default:
                need(missing,"brand_or_publisher",t.brand);boolean named=t.modelPhysical&&!t.modelCode.isEmpty();
                boolean composite=!t.family.isEmpty()&&!t.design.isEmpty()&&t.layoutDistinctive;complete=missing.isEmpty()&&(named||composite);
                if(!complete&&missing.isEmpty())missing.add("physical_model_code_or_distinctive_design_tuple");
        }
        return new Assessment(p,t,complete,"",join(missing),nonblocking(p,t));
    }

    static String model(Assessment a){PhotoTuple t=a.tuple;if(a.profile==Profile.SPORTS_CARD||a.profile==Profile.TCG)
        return CanonicalIdentityComposer.cardModel(t.subject,t.verifiedCardNumber,"","","","","");
        if(a.profile==Profile.SEALED_TRADING_CARD_PRODUCT)return clean(t.format);
        if(electronics(a.profile))return join(t.modelPhysical?t.modelCode:"",t.productType);
        return join(t.modelPhysical?t.modelCode:"",t.design,t.edition);}
    static String family(Assessment a){PhotoTuple t=a.tuple;if(t.year.isEmpty()||canon(t.family).contains(canon(t.year)))return t.family;return join(t.year,t.family);}
    static boolean concreteResolvableAmbiguity(Models.Identification id){if(id==null)return false;prepare(id);return id.photoIdentityAmbiguous
            &&Math.max(id.photoAlternativeCount,id.canonicalCandidateCount)>=2&&!id.discriminativeFieldVisible&&!clean(id.discriminativeField).isEmpty();}

    static PhotoTuple tuple(Models.Identification id){PhotoTuple t=new PhotoTuple();if(id==null)return t;NormalizedPhotoIdentity n=PhotographicFactNormalizer.require(id);
        t.manufacturer=n.best(CanonicalFieldKey.MANUFACTURER);t.publisher=n.best(CanonicalFieldKey.PUBLISHER);t.brand=n.brand();
        t.family=n.productLine();t.mainSet=n.mainSet();t.insertSubset=n.insertSubset();t.designFamily=n.designFamily();t.subSeries=n.subSeries();
        t.subject=n.subject();t.team=n.best(CanonicalFieldKey.TEAM);t.language=n.language();
        t.year=SeasonNormalizer.normalize(n.physicalYear());t.copyrightYear=n.best(CanonicalFieldKey.COPYRIGHT_YEAR);t.statisticalSeason=n.best(CanonicalFieldKey.STATISTICAL_SEASON);
        t.cardNumber=first(n.physicalCollectorNumber,n.physicalCardNumber);t.collectorNumber=n.physicalCollectorNumber;t.serial=n.physicalSerial;
        t.cardNumberVerified=n.cardNumberVerified||n.collectorNumberVerified;t.verifiedCardNumber=t.cardNumberVerified?t.cardNumber:"";
        t.level=first(n.physicalParallel,n.best(CanonicalFieldKey.INSERT_LEVEL));t.parallelFamily=n.parallelFamily();t.printRun=n.printRun();t.color=n.rareVariantPhysicalProof?n.parallelColor:"";t.finish=n.finish;
        t.edition=n.edition();t.printing=n.best(CanonicalFieldKey.PRINTING);t.rookieMarker=n.best(CanonicalFieldKey.ROOKIE_MARKER);
        t.format=n.best(CanonicalFieldKey.FORMAT);t.configuration=n.best(CanonicalFieldKey.CONFIGURATION);t.sport=n.best(CanonicalFieldKey.SPORT);
        t.productType=n.best(CanonicalFieldKey.PRODUCT_TYPE);t.modelCode=n.best(CanonicalFieldKey.MODEL_CODE);t.barcode=n.best(CanonicalFieldKey.BARCODE);
        t.packageCount=n.best(CanonicalFieldKey.PACKAGE_COUNT);t.cardsPerPack=n.best(CanonicalFieldKey.CARDS_PER_PACK);
        t.autographGuarantee=n.best(CanonicalFieldKey.AUTOGRAPH_GUARANTEE);t.memorabiliaGuarantee=n.best(CanonicalFieldKey.MEMORABILIA_GUARANTEE);
        t.sealedStatus=n.best(CanonicalFieldKey.SEALED_STATUS);t.distinctiveTokens.addAll(n.distinctiveTokens());
        t.design=n.best(CanonicalFieldKey.DESIGN);t.hp=n.best(CanonicalFieldKey.HP_OR_PV);t.attacks.addAll(new LinkedHashSet<>(n.values(CanonicalFieldKey.ATTACK_NAME)));
        t.evolutionStage=n.best(CanonicalFieldKey.EVOLUTION_STAGE);
        t.graphicNumber=n.best(CanonicalFieldKey.GRAPHIC_NUMBER);t.attackDamage=n.best(CanonicalFieldKey.ATTACK_DAMAGE);t.cardType=n.best(CanonicalFieldKey.CARD_TYPE);
        t.attackText=n.best(CanonicalFieldKey.ATTACK_TEXT);t.layout=n.best(CanonicalFieldKey.LAYOUT_SIGNATURE);t.illustration=n.best(CanonicalFieldKey.ARTWORK_SIGNATURE);
        t.controlLayout=n.best(CanonicalFieldKey.CONTROL_LAYOUT);t.shortcutButtons=n.best(CanonicalFieldKey.SHORTCUT_BUTTONS);
        t.brandMark=n.best(CanonicalFieldKey.BRAND_MARK);t.navigationLayout=n.best(CanonicalFieldKey.NAVIGATION_LAYOUT);
        t.numericKeypad=n.best(CanonicalFieldKey.NUMERIC_KEYPAD);t.voiceControl=n.best(CanonicalFieldKey.VOICE_CONTROL);
        t.featuredSubjects.addAll(n.values(CanonicalFieldKey.FEATURED_SUBJECT));
        NormalizedPhotoIdentity.Fact model=n.bestFact(CanonicalFieldKey.MODEL_CODE);t.modelPhysical=model!=null&&model.direct()&&!clean(model.location).isEmpty();
        String views=id.photoViews.toString().toLowerCase(Locale.ROOT);boolean front=views.isEmpty()||views.contains("front")||views.contains("fronte"),back=views.contains("back")||views.contains("retro")||views.contains("rear")||views.contains("reverse");
        t.frontBack=front&&back;t.frontComplete=front&&truth(n.best(CanonicalFieldKey.FRONT_COMPLETE));if(!t.frontComplete&&front&&id.photoIdentityComplete)t.frontComplete=true;
        t.layoutDistinctive=!t.layout.isEmpty();return t;}
    static Profile profile(Models.Identification id,PhotoTuple t){NormalizedPhotoIdentity n=PhotographicFactNormalizer.require(id);String voted=clean(n.profile);
        if(voted.equals("sealed_trading_card_product"))return Profile.SEALED_TRADING_CARD_PRODUCT;if(voted.equals("sports_card"))return Profile.SPORTS_CARD;if(voted.equals("tcg"))return Profile.TCG;
        if(voted.equals("television_remote_control"))return Profile.TELEVISION_REMOTE_CONTROL;
        if(voted.equals("audio_video_remote_control"))return Profile.AUDIO_VIDEO_REMOTE_CONTROL;
        if(voted.equals("appliance_remote_control"))return Profile.APPLIANCE_REMOTE_CONTROL;
        if(voted.equals("consumer_electronics_accessory"))return Profile.CONSUMER_ELECTRONICS_ACCESSORY;
        if(voted.equals("consumer_electronics"))return Profile.CONSUMER_ELECTRONICS;
        String x=clean(n.categoryHint).toLowerCase(Locale.ROOT).replace('-','_');
        if(!t.format.isEmpty()||containsAny(x,"sealed","hobby_box","blaster_box","mega_box")||containsAny(t.productType.toLowerCase(Locale.ROOT),"box","sealed"))return Profile.SEALED_TRADING_CARD_PRODUCT;
        if(containsAny(x,"sports","sport_card")||!t.sport.isEmpty()||!t.team.isEmpty())return Profile.SPORTS_CARD;
        if(containsAny(x,"tcg","trading card game","collectible card game")||!t.hp.isEmpty()||!t.attacks.isEmpty()||!n.values(CanonicalFieldKey.COLLECTOR_NUMBER_CANDIDATE).isEmpty())return Profile.TCG;
        if(containsAny(x,"television_remote_control","tv_remote","television remote")||containsAny(t.productType.toLowerCase(Locale.ROOT),"television remote","tv remote"))return Profile.TELEVISION_REMOTE_CONTROL;
        if(containsAny(x,"audio_video_remote_control","av_remote"))return Profile.AUDIO_VIDEO_REMOTE_CONTROL;
        if(containsAny(x,"appliance_remote_control"))return Profile.APPLIANCE_REMOTE_CONTROL;
        if(containsAny(x,"remote","consumer_electronics_accessory","electronic accessory"))return Profile.CONSUMER_ELECTRONICS_ACCESSORY;
        if(containsAny(x,"consumer_electronics","electronics"))return Profile.CONSUMER_ELECTRONICS;
        return Profile.OTHER_COLLECTIBLE;}

    private static int fingerprint(Profile p,PhotoTuple t,List<String> components){int score=0;
        if(!t.brand.isEmpty()){score+=15;components.add("brand:15");}if(!t.subject.isEmpty()){score+=25;components.add("subject:25");}
        if(p==Profile.TCG){if(!t.family.isEmpty()){score+=15;components.add("set_or_line:15");}if(!t.language.isEmpty()){score+=10;components.add("language:10");}
            if(!t.hp.isEmpty()){score+=8;components.add("hp_or_pv:8");}if(!t.attacks.isEmpty()){int points=Math.min(15,t.attacks.size()*5);score+=points;components.add("attack_names("+t.attacks.size()+"):"+points);}
            if(!t.attackText.isEmpty()){score+=8;components.add("attack_text:8");}if(!t.collectorNumber.isEmpty()){int pts=t.cardNumberVerified?20:8;score+=pts;components.add((t.cardNumberVerified?"verified":"candidate")+"_collector_number:"+pts);}
            if(!t.layout.isEmpty()||!t.illustration.isEmpty()){score+=12;components.add("layout_or_artwork:12");}if(!t.finish.isEmpty()){score+=5;components.add("finish:5");}
            if(!t.edition.isEmpty()){score+=5;components.add("edition_or_printing:5");}}
        else if(p==Profile.SPORTS_CARD){if(!t.family.isEmpty()){score+=25;components.add("set_or_product_line:25");}if(!t.cardNumber.isEmpty()){int pts=t.cardNumberVerified?20:8;score+=pts;components.add((t.cardNumberVerified?"verified":"candidate")+"_card_number:"+pts);}
            if(!t.team.isEmpty()){score+=10;components.add("team:10");}if(!t.layout.isEmpty()){score+=10;components.add("layout:10");}}
        else if(p==Profile.SEALED_TRADING_CARD_PRODUCT){if(!t.family.isEmpty()){score+=30;components.add("product_line:30");}if(!t.year.isEmpty()){score+=15;components.add("release_or_season:15");}if(!t.sport.isEmpty()){score+=10;components.add("sport_or_game:10");}if(!t.format.isEmpty()){score+=20;components.add("format:20");}if(!t.configuration.isEmpty()){score+=15;components.add("configuration:15");}}
        else if(electronics(p)){if(t.modelPhysical){score+=40;components.add("physical_model_code:40");}if(!t.brandMark.isEmpty()){score+=25;components.add("brand_mark:25");}
            if(!t.controlLayout.isEmpty()||!t.layout.isEmpty()){score+=20;components.add("control_layout:20");}if(!t.shortcutButtons.isEmpty()){score+=10;components.add("shortcut_buttons:10");}if(!t.navigationLayout.isEmpty()){score+=8;components.add("navigation_layout:8");}}
        else{if(t.modelPhysical){score+=45;components.add("physical_model_code:45");}if(!t.design.isEmpty()){score+=20;components.add("design:20");}}return Math.min(100,score);}
    private static String nonblocking(Profile p,PhotoTuple t){List<String>x=new ArrayList<>();if(p==Profile.SPORTS_CARD){optional(x,"physical_set_or_release_year",t.year);optional(x,"physical_card_number",t.cardNumber);optional(x,"physical_serial",t.serial);optional(x,"barcode",t.barcode);}else if(p==Profile.TCG){optional(x,"physical_collector_number",t.collectorNumber);optional(x,"set",t.family);optional(x,"finish",t.finish);optional(x,"edition_or_printing",t.edition);}else if(p==Profile.SEALED_TRADING_CARD_PRODUCT){optional(x,"physical_set_or_release_year",t.year);optional(x,"commercial_format",t.format);optional(x,"pack_configuration",t.configuration);optional(x,"barcode_or_product_code",join(t.barcode,t.modelCode));}else if(electronics(p)){optional(x,"exact_model",t.modelCode);optional(x,"rear_label",t.modelCode);}else{optional(x,"barcode",t.barcode);optional(x,"edition",t.edition);}return join(x);}
    private static String snapshot(Profile p,PhotoTuple t,int score){return "profile="+p.name().toLowerCase(Locale.ROOT)+"; brand="+t.brand+"; productLine="+t.family+"; mainSet="+t.mainSet+"; subset="+t.insertSubset+"; subSeries="+t.subSeries+"; distinctiveTokens="+t.distinctiveTokens+"; subject="+t.subject+"; season="+t.year+"; cardNumberCandidate="+t.cardNumber+"; cardNumberVerified="+t.cardNumberVerified+"; graphicNumber="+t.graphicNumber+"; physicalSerial="+t.serial+"; parallelFamily="+t.parallelFamily+"; parallelColor="+t.color+"; printRun="+t.printRun+"; evolutionStage="+t.evolutionStage+"; hp="+t.hp+"; attacks="+t.attacks+"; copyright="+t.copyrightYear+"; layout="+t.layout+"; finish="+t.finish+"; format="+t.format+"; configuration="+t.configuration+"; fingerprintScore="+score;}
    private static boolean strongPhotoConflict(Models.Identification id){for(String r:id.finalContradictions){String x=clean(r).toLowerCase(Locale.ROOT);if((x.contains("strong")||x.contains("forte"))&&(x.contains("photo")||x.contains("physical")||x.contains("fotograf")))return true;}return false;}
    private static boolean coreSemanticConflict(NormalizedPhotoIdentity n){for(String x:n.semanticConflicts){String v=clean(x);
        if((v.startsWith("productLineConflict")||v.startsWith("productLineAlternatives"))&&n.productLineConflictResolvedBySource())continue;
        if(v.startsWith("productLineConflict")||v.startsWith("productLineAlternatives")||v.startsWith("setConflict")||v.startsWith("seriesConflict")||v.startsWith("productNameConflict")||v.startsWith("subjectConflict"))return true;}return false;}
    private static boolean containsAny(String x,String...terms){for(String t:terms)if(x.contains(t))return true;return false;}
    static boolean electronics(Profile p){return p==Profile.CONSUMER_ELECTRONICS||p==Profile.CONSUMER_ELECTRONICS_ACCESSORY
            ||p==Profile.TELEVISION_REMOTE_CONTROL||p==Profile.AUDIO_VIDEO_REMOTE_CONTROL||p==Profile.APPLIANCE_REMOTE_CONTROL;}
    private static void need(List<String>x,String name,String value){if(clean(value).isEmpty())x.add(name);}private static void optional(List<String>x,String name,String value){if(clean(value).isEmpty())x.add(name);}
    private static boolean truth(String x){String v=clean(x).toLowerCase(Locale.ROOT);return v.equals("true")||v.equals("yes")||v.equals("1")||v.equals("complete")||v.equals("visible");}
    private static String first(String...xs){for(String x:xs)if(!clean(x).isEmpty())return clean(x);return "";}
    private static String join(List<String>x){StringBuilder b=new StringBuilder();for(String v:x){if(b.length()>0)b.append(',');b.append(v);}return b.toString();}
    private static String join(String...x){StringBuilder b=new StringBuilder();for(String v:x)if(!clean(v).isEmpty()){if(b.length()>0)b.append(' ');b.append(clean(v));}return b.toString();}
    private static String canon(String x){return clean(x).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
