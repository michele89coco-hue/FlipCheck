package com.flipcheck.nativebeta;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Field-aware normalization. Contextual OCR repair is never applied to arbitrary text. */
final class TypedFieldNormalizerV2 {
    private TypedFieldNormalizerV2() {}

    static void normalize(ImmutableEvidenceLedgerV2 ledger){
        List<EvidenceAtom> snapshot=new ArrayList<>(ledger.all());
        for(EvidenceAtom atom:snapshot){
            // Derived atoms retain their parent's raw text for audit, not as a new input.
            // Re-reading it would turn a derived denominator back into the full fraction.
            if(!atom.parentEvidenceId.isEmpty())continue;
            String normalized=normalizeValue(atom.field,atom.rawValue,atom.semanticScope);
            String field=canonicalField(atom.field,atom.semanticScope);
            if(collectorRepair(atom,field,normalized)&&!corroboratedCollector(ledger,atom,normalized))continue;
            if(!normalized.equals(atom.normalizedValue)||!field.equals(atom.field))
                ledger.appendNormalization(atom,normalized,field,atom.semanticScope);
            if(field.equals("firstEditionMark")&&normalized.equals("PRESENT"))ledger.appendNormalization(atom,"FIRST_EDITION","edition","edition");
            if(field.equals("collectorNumber")){String total=printedTotal(normalized);if(!total.isEmpty())ledger.appendNormalization(atom,total,"printedTotal","collector_number_denominator");}
        }
    }

    private static boolean collectorRepair(EvidenceAtom atom,String field,String normalized){
        if(!(field.equals("collectorNumber")||field.equals("physicalCardNumber")||field.equals("catalogCardNumber")))return false;
        return !safe(atom.rawValue).replaceAll("\\s+","").equalsIgnoreCase(normalized)
                &&safe(atom.rawValue).toUpperCase(Locale.ROOT).matches(".*[ILOSBZ].*");
    }

    /** OCR confusable repair becomes canonical only after an independent compatible atom exists. */
    private static boolean corroboratedCollector(ImmutableEvidenceLedgerV2 ledger,EvidenceAtom atom,String normalized){
        for(EvidenceAtom other:ledger.all()){
            if(other.id.equals(atom.id)||other.parentEvidenceId.equals(atom.id)||atom.parentEvidenceId.equals(other.id))continue;
            String field=canonicalField(other.field,other.semanticScope);
            if(!(field.equals("collectorNumber")||field.equals("physicalCardNumber")||field.equals("catalogCardNumber")))continue;
            if(normalized.equalsIgnoreCase(normalizeCollector(other.rawValue)))return true;
        }
        return false;
    }

    static String canonicalField(String raw,String scope){
        String key=canonKey(raw),s=canonKey(scope);
        Map<String,String> m=aliases();String mapped=m.get(key);
        if(mapped==null&&(key.endsWith("_text")||key.endsWith("_token")))mapped=m.get(key.replaceFirst("_(text|token)$",""));
        String canonical=mapped==null?camel(key):mapped;
        if(canonical.equals("cardRole")&&s.contains("evolution"))return "evolutionStage";
        if((key.equals("season")||key.equals("year")||canonical.equals("productReleaseYear")||canonical.equals("setSeason"))
                &&s.contains("statistic"))return "statisticsSeason";
        return canonical;
    }

    static String semanticScope(String field,String rawScope){
        String s=canonKey(rawScope),f=canonicalField(field,rawScope);
        if(s.contains("ui")||s.contains("screen"))return "UI_OVERLAY";
        if(s.contains("watermark"))return "EXTERNAL_WATERMARK";
        if(s.contains("market")||s.contains("listing"))return "MARKET_TEXT";
        if(f.equals("statisticsSeason")||f.equals("statisticsNumber"))return "OBJECT_STATISTIC";
        if(f.equals("hp")||f.equals("attackDamage")||f.equals("attacks"))return "OBJECT_RULES_TEXT";
        if(f.equals("edition")||f.equals("finish")||f.equals("printVariant")||f.equals("commercialFormat"))return "OBJECT_VARIANT";
        if(f.equals("configuration")||f.equals("packageCount")||f.equals("cardsPerPack"))return "OBJECT_CONFIGURATION";
        if(f.endsWith("Number")||f.endsWith("Code")||f.equals("barcode")||f.equals("model")||f.equals("sku"))return "OBJECT_IDENTIFIER";
        if(!s.isEmpty())return s.toUpperCase(Locale.ROOT);
        return "OBJECT_IDENTITY";
    }

    static String normalizeValue(String field,String raw,String scope){
        String value=safe(raw).replaceAll("\\s+"," ");
        String f=canonicalField(field,scope);
        if(f.equals("collectorNumber")||f.equals("physicalCardNumber")||f.equals("catalogCardNumber"))return normalizeCollector(value);
        if(f.equals("edition")){String c=words(value);if(c.matches(".*\\b(1ST|FIRST|EDITION 1|1A EDIZIONE)\\b.*")&&!c.contains(" OR "))return "FIRST_EDITION";if(c.equals("UNLIMITED"))return "UNLIMITED";return value;}
        if(f.equals("firstEditionMark")){
            String c=words(value);
            if(c.contains("ABSENT")||c.contains("NOT PRESENT"))return "ABSENT";
            if(c.equals("PRESENT")||c.equals("1ST EDITION")||c.equals("FIRST EDITION")||c.equals("EDITION 1")||c.equals("EDITION 1 LOGO")||c.equals("FIRST EDITION LOGO")||c.equals("1ST EDITION LOGO"))return "PRESENT";
            return value;
        }
        if(f.equals("productReleaseYear")||f.equals("setSeason")||f.equals("statisticsSeason"))return SeasonNormalizer.normalize(value);
        if(f.equals("finish")){String c=words(value);if(c.equals("NON HOLO")||c.equals("NONHOLO"))return "NON_HOLO";if(c.contains("REVERSE")&&c.contains("HOLO"))return "REVERSE_HOLO";if(c.contains("HOLO")||c.contains("HOLOGRAPHIC"))return "HOLO";}
        if(f.equals("manufacturer")||f.equals("brand"))return displayWords(value);
        if(f.equals("productLine")||f.equals("setName"))return value.replaceAll("(?i)\\bupdates\\b","Update").replaceAll("\\s+"," ").trim();
        return value;
    }

    static boolean equivalent(String field,String a,String b){
        String x=normalizeValue(field,a,""),y=normalizeValue(field,b,"");
        if(words(x).equals(words(y)))return true;
        if(field.equals("productLine")||field.equals("setName"))return tokenSet(x).equals(tokenSet(y));
        return false;
    }

    static boolean ambiguous(String value){String c=words(value);return c.contains(" OR ")||c.contains("POSSIBLE")||c.contains("MAY BE")||c.contains("UNKNOWN")||c.contains("UNRESOLVED");}

    private static String normalizeCollector(String input){
        String compact=safe(input).toUpperCase(Locale.ROOT).replaceAll("\\s+","").replace('|','/');
        if(compact.matches("[A-Z]{1,5}\\d+[A-Z]?(?:/[A-Z0-9]+)?")&&!compact.matches("[ILOSBZ0-9]+/[ILOSBZ0-9]+"))return compact;
        if(compact.matches("[ILOSBZ0-9]+/[ILOSBZ0-9]+")){
            String repaired=compact.replace('I','1').replace('L','1').replace('O','0')
                    .replace('S','5').replace('B','8').replace('Z','2');
            if(repaired.matches("\\d{1,5}/\\d{1,5}"))return repaired;
        }
        if(compact.matches("[ILOSBZ0-9]{1,5}")){
            String repaired=compact.replace('I','1').replace('L','1').replace('O','0')
                    .replace('S','5').replace('B','8').replace('Z','2');
            if(repaired.matches("\\d{1,5}"))return repaired;
        }
        return compact;
    }

    private static Map<String,String> aliases(){Map<String,String>m=new LinkedHashMap<>();
        put(m,"manufacturer","manufacturer","maker","publisher","producer");put(m,"brand","brand","brand_mark","brand_logo");put(m,"game","game","tcg_game");
        put(m,"productLine","product_line","family","series","set_or_product_line");put(m,"setName","set","set_name","main_set");
        put(m,"cardName","card_name","subject_name","character");put(m,"athlete","athlete","player","subject");
        put(m,"evolutionStage","stage","evolution_stage");put(m,"cardRole","card_role","cardrole");
        put(m,"subSeries","sub_series","subseries","series_text");put(m,"setSymbol","set_symbol","setsymbol");put(m,"visualSymbol","set_symbol_appearance","symbol_appearance","raw_set_symbol_appearance","visual_symbol");
        put(m,"collectorNumber","collector_number","collector_marking","physical_collector_number","tcg_number");put(m,"printedTotal","printed_total","set_total","collector_total");
        put(m,"physicalCardNumber","physical_card_number","physical_card_number_marking","card_number");
        put(m,"catalogCardNumber","catalog_card_number","source_confirmed_catalog_number");
        put(m,"physicalSerial","physical_serial","serial_fraction","physical_print_run");put(m,"graphicNumber","graphic_number");put(m,"jerseyNumber","jersey_number");
        put(m,"statisticsNumber","statistics","statistics_number","rating");put(m,"productReleaseYear","release_year","physical_year","product_year","physical_set_or_release_year","release_season");
        put(m,"setSeason","set_season");put(m,"statisticsSeason","statistics_season","statistical_season","stats_season");
        put(m,"copyrightYear","copyright_year");put(m,"edition","edition","print_edition");put(m,"firstEditionMark","first_edition_mark","first_edition_logo","edition_mark");
        put(m,"finish","finish","holo_status","holo");put(m,"printVariant","printing","print_variant","shadow_status");
        put(m,"language","language");put(m,"hp","hp","hp_or_pv","pv");put(m,"attacks","attack","attack_name","attacks");
        put(m,"attackDamage","attack_damage","damage");put(m,"rarity","rarity");put(m,"artist","artist","illustrator");
        put(m,"configuration","configuration","autograph_guarantee","autographs_per_box");put(m,"commercialFormat","format","commercial_format","sealed_format");
        put(m,"productType","product_type","object_type");put(m,"model","model","model_name","exact_model");
        put(m,"productCode","product_code","model_code","part_number");put(m,"sku","sku","stock_keeping_unit");
        put(m,"barcode","barcode","ean","upc","gtin");put(m,"controlLayout","control_layout","button_layout");
        put(m,"shortcutButtons","shortcut_buttons","remote_feature");put(m,"printedLabel","printed_label","distinctive_printed_token");
        put(m,"navigationLayout","navigation_layout");put(m,"numericKeypad","numeric_keypad");put(m,"voiceControl","voice_control");
        put(m,"layoutSignature","layout_signature","layout");put(m,"frontComplete","front_complete");put(m,"featuredSubject","featured_subject","featured_subjects");
        put(m,"sport","sport");put(m,"team","team");return m;}
    private static void put(Map<String,String>m,String value,String...keys){m.put(canonKey(value),value);for(String k:keys)m.put(canonKey(k),value);}
    private static String camel(String key){StringBuilder b=new StringBuilder();boolean upper=false;for(char c:key.toCharArray()){if(c=='_'){upper=true;continue;}b.append(upper?Character.toUpperCase(c):c);upper=false;}return b.toString();}
    private static String canonKey(String raw){return safe(raw).replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_').replaceAll("[^a-z0-9_]+","").replaceAll("_+","_");}
    private static String words(String raw){return Normalizer.normalize(safe(raw),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static String tokenSet(String raw){java.util.TreeSet<String>s=new java.util.TreeSet<>();for(String t:words(raw).split(" "))if(t.length()>1&&!t.equals("SERIES"))s.add(t);return s.toString();}
    private static String displayWords(String raw){String v=safe(raw);return v.isEmpty()?v:Character.toUpperCase(v.charAt(0))+v.substring(1);}
    private static String printedTotal(String collector){String v=safe(collector).toUpperCase(Locale.ROOT);int slash=v.indexOf('/');if(slash<1||slash>=v.length()-1)return "";String total=v.substring(slash+1);return total.matches("\\d{1,5}")?total:"";}
    private static String safe(String value){return value==null?"":value.trim();}
}
