package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Parses Vision into located observations plus separate, non-binding hypotheses. */
final class ObservationExtractorV2 {
    static final class Result {String category="";boolean contentSufficient;final List<String> views=new ArrayList<>();final List<IdentityCandidateV2> hypotheses=new ArrayList<>();}
    private ObservationExtractorV2() {}

    static Result ingestPrimary(JSONObject payload,ImmutableEvidenceLedgerV2 ledger){
        Result out=new Result();if(payload==null)return out;
        out.category=safe(payload.optString("category","generic_object"));out.contentSufficient=payload.optBoolean("content_sufficient",false);
        strings(payload.optJSONArray("views"),out.views);
        DomainProfileRouterV2.Profile tentative=DomainProfileRouterV2.route(out.category,ledger);
        ingestFacts(payload.optJSONArray("facts"),ledger,EvidenceAtom.Modality.PRIMARY_VISION,"primary_vision",tentative);
        ingestCandidates(payload.optJSONArray("candidates"),out.hypotheses,tentative,"PRIMARY_VISION_HYPOTHESIS",ledger);
        return out;
    }

    static Result ingestFocused(JSONObject payload,ImmutableEvidenceLedgerV2 ledger,DomainProfileRouterV2.Profile profile,String cropId){
        Result out=new Result();out.category=DomainProfileRouterV2.categoryKey(profile);if(payload==null)return out;
        out.contentSufficient=payload.optBoolean("content_sufficient",payload.optBoolean("applicable",false));
        strings(payload.optJSONArray("views"),out.views);
        JSONArray facts=payload.optJSONArray("facts");if(facts==null)facts=payload.optJSONArray("evidence_facts");
        ingestFacts(facts,ledger,EvidenceAtom.Modality.FOCUSED_VISION,"focused_vision",profile,cropId);
        ingestCandidates(payload.optJSONArray("candidates"),out.hypotheses,profile,"FOCUSED_VISION_HYPOTHESIS",ledger);
        return out;
    }

    static void ingestLocal(Models.LocalScan local,ImmutableEvidenceLedgerV2 ledger){if(local==null)return;
        for(int image=0;image<local.textByImage.size();image++){String block=safe(local.textByImage.get(image));if(!block.isEmpty())ledger.append("printedLabel",block,EvidenceAtom.EpistemicLevel.OBSERVED,EvidenceAtom.Modality.LOCAL_OCR,"on_device",image,"unknown","local_ocr_text_block","","object_printed_text",78,72,"local_scan","");}
        for(Models.Identifier item:local.identifiers){if(item==null)continue;String modality=safe(item.origin).toLowerCase(Locale.ROOT);
            EvidenceAtom.Modality m=modality.contains("barcode")?EvidenceAtom.Modality.BARCODE_SCAN:EvidenceAtom.Modality.LOCAL_OCR;
            String region=modality.isEmpty()?"":"local_ocr:"+modality;
            ledger.append(item.label,item.value,EvidenceAtom.EpistemicLevel.OBSERVED,m,"on_device",item.imageIndex,"unknown",region,"",item.label,confidence(item,m),75,"local_scan","");}
    }

    static Result ingestEditionInspection(JSONObject payload,ImmutableEvidenceLedgerV2 ledger,String cropId){
        Result out=new Result();if(payload==null)return out;
        strings(payload.optJSONArray("views"),out.views);
        JSONArray facts=payload.optJSONArray("facts");if(facts==null)return out;
        JSONArray marks=new JSONArray();
        for(int i=0;i<facts.length();i++){
            JSONObject f=facts.optJSONObject(i);if(f==null)continue;
            String field=TypedFieldNormalizerV2.canonicalField(first(f,"key","field"),first(f,"role","semantic_role"));
            if(field.equals("firstEditionMark"))marks.put(f);
        }
        // The recovery may repair only the requested attribute, never overwrite
        // an already established name, number, set, or finish.
        ingestFacts(marks,ledger,EvidenceAtom.Modality.FOCUSED_VISION,"edition_inspection",DomainProfileRouterV2.Profile.TCG_CARD,cropId);
        return out;
    }

    private static void ingestFacts(JSONArray facts,ImmutableEvidenceLedgerV2 ledger,EvidenceAtom.Modality modality,String source,DomainProfileRouterV2.Profile profile){ingestFacts(facts,ledger,modality,source,profile,"");}
    private static void ingestFacts(JSONArray facts,ImmutableEvidenceLedgerV2 ledger,EvidenceAtom.Modality modality,String source,DomainProfileRouterV2.Profile profile,String cropId){
        if(facts==null)return;for(int i=0;i<facts.length();i++){JSONObject f=facts.optJSONObject(i);if(f==null)continue;
            String rawField=safe(first(f,"key","field")),raw=safe(first(f,"value","rawTextOrSymbol","raw_value"));
            int image=f.has("image")?f.optInt("image",-1):f.optInt("image_index",-1);String location=safe(first(f,"location","region","boundingBox"));
            String side=safe(f.optString("side","unknown")),role=safe(first(f,"role","semantic_role","semanticScope"));
            String field=TypedFieldNormalizerV2.canonicalField(profileField(rawField,role,location,profile),role+" "+location);int confidence=f.optInt("confidence",0);
            // A number in a descriptive text box is not a second subject name.
            // Keep the transcription without guessing that it is a collector number.
            if(profile==DomainProfileRouterV2.Profile.TCG_CARD&&field.equals("cardName")
                    &&raw.matches("#?\\s*[0-9]+(?:/[0-9]+)?")&&role.toLowerCase(Locale.ROOT).contains("number"))field="printedLabel";
            if(profile==DomainProfileRouterV2.Profile.TCG_CARD){
                String semanticRole=role.toLowerCase(Locale.ROOT).replace('-', ' ');
                if(field.equals("edition")&&semanticRole.contains("evolution"))field="evolutionStage";
                // Preserve the appearance for a separate physical inspection. A
                // guessed expansion label must not swallow an edition-shaped mark.
                if((field.equals("setName")||field.equals("setSymbol"))
                        &&semanticRole.matches(".*(?:raw|appearance).*symbol.*|.*symbol.*appearance.*"))field="visualSymbol";
                String descriptiveContext=(role+" "+location).toLowerCase(Locale.ROOT).replace('-', ' ');
                // Species/flavour classifications printed below the artwork are
                // descriptors, not a second card identity. Keep the literal text,
                // while leaving an actual name/title fact untouched.
                if(field.equals("cardName")&&descriptiveContext.matches(
                        ".*(species|creature type|pokemon type|pokémon type|flavor|flavour|descriptor|classification).*"))field="printedLabel";
                // A full collector fraction is not a denominator merely because the
                // transport key says printedTotal. Preserve its located transcription.
                if(field.equals("printedTotal")&&raw.matches("[A-Za-z]*[0-9]+\\s*/\\s*[0-9]+")
                        &&role.toLowerCase(Locale.ROOT).matches(".*(set numbering|collector|card number).*"))field="collectorNumber";
                if(field.equals("collectorNumber")&&raw.matches("#?\\s*[0-9]+")
                        &&location.toLowerCase(Locale.ROOT).matches(".*(flavor[ -]text|species|descriptive text).*")
                        &&hasLocatedCollectorFraction(facts,ledger))field="printedLabel";
            }
            if(profile==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT&&field.equals("commercialFormat")
                    &&raw.toLowerCase(Locale.ROOT).replaceAll("[*!]", "").trim().matches(
                        "[0-9]+ (?:autographs?(?: cards?)?|cards?|packs?) (?:in every|in each|per) (?:box|pack|case)"))field="configuration";
            if(profile==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT&&field.equals("productType")
                    &&raw.toLowerCase(Locale.ROOT).matches(".+\\bseries\\b")
                    &&(role+" "+location).toLowerCase(Locale.ROOT).matches(".*(?:text|title|logo|label).*"))field="subSeries";
            String groundingRole=role;
            if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD){
                String labelRole=role.toLowerCase(Locale.ROOT).replace('-', ' ');
                String labelContext=(labelRole+" "+location.toLowerCase(Locale.ROOT));
                // Focused readers sometimes transport a publisher logo through the
                // subject/cardName key. Reassign it only when its semantic role is a
                // brand/manufacturer mark at a logo and literal OCR corroborates it.
                if(field.equals("athlete")&&labelContext.matches(".*(brand|manufacturer|publisher).*\\b(mark|logo)\\b.*")){
                    if(localTextSupports(ledger,raw)){field="brand";groundingRole="printed brand label";}
                    else field="printedLabel";
                }
                if((field.equals("brand")||field.equals("manufacturer"))
                        &&labelRole.matches("(?:manufacturer/brand|brand/manufacturer) mark(?:ing)?"))groundingRole="printed manufacturer label";
                if(field.equals("productLine")&&labelRole.matches("set/product line mark(?:ing)?"))groundingRole="printed product line label";
                if((field.equals("brand")||field.equals("manufacturer"))&&labelRole.equals("brand")
                        &&location.toLowerCase(Locale.ROOT).contains("logo"))groundingRole="printed brand label";
            }
            EvidenceAtom.EpistemicLevel requested=groundingLevel(field,raw,groundingRole+(rawField.equalsIgnoreCase("brand_mark")?" brand_mark":""),modality,ledger,confidence);
            if(profile==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT
                    &&(field.equals("productType")||field.equals("commercialFormat"))
                    &&raw.toLowerCase(Locale.ROOT).matches(".*\\b(hobby|blaster|jumbo|retail|mega|sapphire)\\b.*")
                    &&!role.toLowerCase(Locale.ROOT).matches(".*(printed|literal|visible text|ocr).*"))
                requested=EvidenceAtom.EpistemicLevel.INFERRED;
            EvidenceAtom atom=ledger.append(field,raw,requested,modality,source,image,side,location,cropId,role,confidence,quality(location,raw),modality==EvidenceAtom.Modality.PRIMARY_VISION?"primary_observation":"focused_observation","");
            if(atom!=null&&atom.epistemicLevel==EvidenceAtom.EpistemicLevel.INFERRED){/* intentionally demoted */}
        }}

    private static boolean hasLocatedCollectorFraction(JSONArray facts,ImmutableEvidenceLedgerV2 ledger){
        for(EvidenceAtom atom:ledger.current("collectorNumber"))if(atom.epistemicLevel==EvidenceAtom.EpistemicLevel.OBSERVED
                &&atom.localized()&&atom.reliable()&&atom.normalizedValue.matches("[A-Za-z]*[0-9]+/[0-9]+"))return true;
        for(int i=0;i<facts.length();i++){
            JSONObject f=facts.optJSONObject(i);if(f==null||f.optInt("confidence",0)<72)continue;
            String key=TypedFieldNormalizerV2.canonicalField(first(f,"key","field"),"");
            if(!(key.equals("collectorNumber")||key.equals("printedTotal")))continue;
            int image=f.has("image")?f.optInt("image",-1):f.optInt("image_index",-1);
            if(image>=0&&!safe(first(f,"location","region","boundingBox")).isEmpty()
                    &&first(f,"value","rawTextOrSymbol","raw_value").matches("[A-Za-z]*[0-9]+\\s*/\\s*[0-9]+"))return true;
        }
        return false;
    }
    private static void ingestCandidates(JSONArray candidates,List<IdentityCandidateV2> out,DomainProfileRouterV2.Profile profile,String source,ImmutableEvidenceLedgerV2 ledger){if(candidates==null)return;
        for(int i=0;i<candidates.length();i++){JSONObject c=candidates.optJSONObject(i);if(c==null)continue;IdentityCandidateV2 x=new IdentityCandidateV2(source+"-"+(i+1),profile,source);
            add(x,"manufacturer",first(c,"brand","manufacturer"));add(x,profile==DomainProfileRouterV2.Profile.TCG_CARD?"setName":"productLine",first(c,"product_line","family","set_name"));
            if(profile==DomainProfileRouterV2.Profile.TCG_CARD)add(x,"cardName",first(c,"subject","card_name"));else if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD)add(x,"athlete",first(c,"subject","athlete"));add(x,"productReleaseYear",c.optString("year",""));
            add(x,profile==DomainProfileRouterV2.Profile.TCG_CARD?"collectorNumber":"physicalCardNumber",c.optString("card_number",""));add(x,"language",c.optString("language",""));
            add(x,"edition",c.optString("edition",""));add(x,"finish",c.optString("finish",""));add(x,"commercialFormat",c.optString("format",""));add(x,"model",c.optString("model",""));
            x.inferenceOnlyPenalty=25;x.totalScore=Math.min(74,c.optInt("confidence",0));x.status="LOW_SUPPORT";out.add(x);}}

    private static EvidenceAtom.EpistemicLevel groundingLevel(String field,String raw,String role,EvidenceAtom.Modality modality,ImmutableEvidenceLedgerV2 ledger,int confidence){
        String f=safe(field),r=safe(role).toLowerCase(Locale.ROOT);
        if(r.equals("full product line")||r.equals("full product line and subseries"))r="product line";
        // Catalog symbol names are classifications, unlike the raw visualSymbol appearance.
        if(f.equals("setSymbol"))return EvidenceAtom.EpistemicLevel.INFERRED;
        boolean identityLabel=f.equals("manufacturer")||f.equals("brand")||f.equals("game")||f.equals("setName")||f.equals("productLine")||f.equals("subSeries");
        if(!identityLabel)return EvidenceAtom.EpistemicLevel.OBSERVED;
        // A weaker, uncorroborated focused classification cannot become a hard
        // fact merely because it was returned later. Retain both hypotheses for
        // independent catalog disproof against the physical subject and number.
        if(modality==EvidenceAtom.Modality.FOCUSED_VISION&&!localTextSupports(ledger,raw))
            for(EvidenceAtom prior:ledger.current(f))
                if(prior.modality==EvidenceAtom.Modality.PRIMARY_VISION&&prior.confidence>confidence
                        &&SemanticRelationV3.relate(f,prior.normalizedValue,raw)==SemanticRelationV3.Relation.INCOMPATIBLE)
                    return EvidenceAtom.EpistemicLevel.INFERRED;
        boolean literalRole=r.contains("printed")||r.contains("ocr")||r.contains("brand_mark")||r.contains("visible_text")||r.contains("logo")||r.endsWith("_text")||r.equals("product_line")||r.startsWith("product line")||r.equals("subseries")||r.equals("sub-series")||r.equals("set_name")||r.equals("game_brand")||r.equals("manufacturer_brand")||r.equals("manufacturer mark")||r.equals("manufacturer")||r.startsWith("sealed_brand_line");
        // A set-name inferred from a symbol classification is a hypothesis; the observed item is the symbol's appearance, not the catalog set label.
        if((f.equals("setName")||f.equals("productLine"))&&r.contains("symbol"))return EvidenceAtom.EpistemicLevel.INFERRED;
        return literalRole&&!TypedFieldNormalizerV2.ambiguous(raw)&&confidence>=72&&(localTextSupports(ledger,raw)||modality==EvidenceAtom.Modality.FOCUSED_VISION||r.equals("brand_logo")||r.contains("brand_mark"))?EvidenceAtom.EpistemicLevel.OBSERVED:EvidenceAtom.EpistemicLevel.INFERRED;
    }

    private static boolean localTextSupports(ImmutableEvidenceLedgerV2 ledger,String value){String target=canon(value);if(target.isEmpty())return false;for(EvidenceAtom a:ledger.current("printedLabel")){String hay=canon(a.rawValue);if(hay.contains(target)||tokenCoverage(hay,target)>=.75d)return true;}return false;}
    private static double tokenCoverage(String hay,String target){String[]parts=target.split(" ");int total=0,found=0;for(String p:parts){if(p.length()<2)continue;total++;if((" "+hay+" ").contains(" "+p+" "))found++;}return total==0?0d:(double)found/total;}
    private static String canon(String v){return safe(v).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static void strings(JSONArray a,List<String>out){if(a==null)return;for(int i=0;i<a.length();i++){String v=safe(a.optString(i,""));if(!v.isEmpty()&&!out.contains(v))out.add(v);}}

    private static String profileField(String field,String role,String location,DomainProfileRouterV2.Profile profile){String k=safe(field).toLowerCase(Locale.ROOT).replace('-','_');String r=safe(role).toLowerCase(Locale.ROOT);
        // A service logo on a control is not the maker of the physical accessory.
        String context=(r+" "+safe(location)).toLowerCase(Locale.ROOT);
        String canonical=TypedFieldNormalizerV2.canonicalField(field,role);
        String semantic=r.replace('_',' ').replace('-',' ').replaceAll("\\s+"," ").trim();
        if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD){
            if(semantic.matches("(?:printed )?(?:card|collector) number")||canonical.equals("collectorNumber"))return "physicalCardNumber";
            if(semantic.startsWith("product line"))return "productLine";
            if(canonical.equals("cardName"))return "athlete";
        }
        if(profile==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT){
            if(canonical.equals("athlete"))return "featuredSubjects";
            if(canonical.equals("setName"))return "subSeries";
            if(canonical.equals("printedLabel")&&semantic.contains("season")&&!semantic.contains("statistic"))return "productReleaseYear";
            if(canonical.equals("statisticsSeason")&&semantic.contains("season")
                    &&!context.matches(".*(statistic|career|table|biograph).*"))return "productReleaseYear";
            if(canonical.equals("printedLabel")&&semantic.contains("configuration")&&!semantic.contains("code"))return "configuration";
        }
        if((context.contains("set branding")||context.contains("set_branding"))
                &&(canonical.equals("brand")||canonical.equals("manufacturer")))
            return profile==DomainProfileRouterV2.Profile.TCG_CARD?"setName":"productLine";
        if(profile==DomainProfileRouterV2.Profile.TCG_CARD&&(k.equals("edition_mark_appearance")||r.equals("edition mark")))return "firstEditionMark";

        if(profile==DomainProfileRouterV2.Profile.TELEVISION_REMOTE_CONTROL
                &&(canonical.equals("brand")||canonical.equals("manufacturer"))
                &&context.matches(".*(button|shortcut|streaming|keycap|app key).*"))return "controlLabel";
        if(profile==DomainProfileRouterV2.Profile.SEALED_TRADING_CARD_PRODUCT){
            if(k.equals("sealed_configuration_text"))return "configuration";
            if(k.equals("sealed_brand_line_configuration"))return "productLine";
        }
        if(profile==DomainProfileRouterV2.Profile.TCG_CARD&&(r.equals("franchise_text")||r.equals("game_title")))return "game";
        if(k.equals("text")||k.equals("raw_text")||k.equals("printed_text")){
            if(r.equals("product_line"))return profile==DomainProfileRouterV2.Profile.TCG_CARD?"setName":"productLine";
            if(r.equals("series_text")||r.equals("sub_series"))return "subSeries";
            if(r.equals("release_season")||r.equals("printed_season"))return "productReleaseYear";
            if(r.equals("sealed_configuration"))return "configuration";
            // Composite branding text stays a transcription: do not invent a brand split.
            if(r.equals("product_branding"))return "printedLabel";
        }
        if((k.equals("name")||k.equals("subject")||k.equals("subject_name"))&&profile==DomainProfileRouterV2.Profile.TCG_CARD)return "cardName";
        if((k.equals("subject")||k.equals("subject_name")||k.equals("player_name")||k.equals("playername")||k.equals("featured_subject")||k.equals("featuredsubject"))&&profile==DomainProfileRouterV2.Profile.SPORTS_CARD)return "athlete";
        if((k.equals("card_number")||TypedFieldNormalizerV2.canonicalField(field,role).equals("collectorNumber"))&&profile==DomainProfileRouterV2.Profile.TCG_CARD)return "collectorNumber";
        if(k.equals("card_number")&&profile==DomainProfileRouterV2.Profile.SPORTS_CARD)return "physicalCardNumber";
        if((k.equals("year")||k.equals("season"))&&r.contains("stat"))return "statisticsSeason";
        if(k.equals("year")||k.equals("season")||k.equals("physical_set_or_release_year"))return "productReleaseYear";
        if((k.equals("family")||k.equals("product_line"))&&profile==DomainProfileRouterV2.Profile.TCG_CARD)return "setName";
        return field;}
    private static String first(JSONObject x,String...keys){for(String k:keys){String v=x.optString(k,"");if(!safe(v).isEmpty())return v;}return "";}
    private static void add(IdentityCandidateV2 c,String field,String value){String v=safe(value);if(!v.isEmpty())c.fields.put(field,TypedFieldNormalizerV2.normalizeValue(field,v,""));}
    private static int quality(String location,String value){return safe(location).isEmpty()?35:Math.min(100,65+Math.min(25,safe(value).length()));}
    private static int confidence(Models.Identifier id,EvidenceAtom.Modality modality){return modality==EvidenceAtom.Modality.BARCODE_SCAN?98:safe(id.origin).toLowerCase(Locale.ROOT).contains("labeled")?82:68;}
    private static String safe(String value){return value==null?"":value.trim();}
}
