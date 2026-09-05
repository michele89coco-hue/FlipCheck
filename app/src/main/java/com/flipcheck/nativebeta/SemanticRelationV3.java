package com.flipcheck.nativebeta;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Field-aware semantic relation used by the v1.33 candidate tournament. */
final class SemanticRelationV3 {
    enum Relation { EXACT, CANONICAL_EQUIVALENT, PARENT, CHILD, COMPATIBLE_EXTENSION, AMBIGUOUS, INCOMPATIBLE }
    private SemanticRelationV3() {}

    static Relation relate(String field,String left,String right){
        String a=safe(left),b=safe(right);if(a.isEmpty()||b.isEmpty())return Relation.AMBIGUOUS;
        if(field.equals("configuration")&&(uncertainQuantityClause(a)||uncertainQuantityClause(b))){
            Relation known=configurationQuantities(a,b);
            return known==null?Relation.AMBIGUOUS:known;
        }
        if(a.equalsIgnoreCase(b))return Relation.EXACT;
        if(field.equals("manufacturer")||field.equals("brand")){
            String corporateA=corporateRoot(a),corporateB=corporateRoot(b);
            if(!corporateA.isEmpty()&&corporateA.equals(corporateB))return Relation.CANONICAL_EQUIVALENT;
        }
        if(TypedFieldNormalizerV2.ambiguous(a)||TypedFieldNormalizerV2.ambiguous(b))return Relation.AMBIGUOUS;
        String x=TypedFieldNormalizerV2.normalizeValue(field,a,""),y=TypedFieldNormalizerV2.normalizeValue(field,b,"");
        if(words(x).equals(words(y))||TypedFieldNormalizerV2.equivalent(field,x,y))return Relation.CANONICAL_EQUIVALENT;
        if(season(field)){if(seasonContains(x,y))return x.length()<y.length()?Relation.PARENT:Relation.CHILD;return Relation.INCOMPATIBLE;}
        if(design(field)){
            if(unspecifiedDesign(x)||unspecifiedDesign(y))return Relation.AMBIGUOUS;
            if(field.equals("voiceControl")){
                int aVoice=voicePresence(x),bVoice=voicePresence(y);
                if(aVoice!=0&&bVoice!=0)return aVoice==bVoice?Relation.COMPATIBLE_EXTENSION:Relation.INCOMPATIBLE;
            }
            Set<String>xs=tokens(x),ys=tokens(y);if(xs.isEmpty()||ys.isEmpty())return Relation.AMBIGUOUS;
            Set<String>common=new LinkedHashSet<>(xs);common.retainAll(ys);double coverage=(double)common.size()/Math.min(xs.size(),ys.size());
            if(coverage>=.85d)return Relation.CANONICAL_EQUIVALENT;if(coverage>=.55d)return Relation.COMPATIBLE_EXTENSION;if(coverage>=.30d)return Relation.AMBIGUOUS;return Relation.INCOMPATIBLE;
        }
        if(safe(field).equals("configuration")){Relation quantities=configurationQuantities(x,y);if(quantities!=null)return quantities;if(numericTokens(x).isEmpty()!=numericTokens(y).isEmpty())return Relation.AMBIGUOUS;if(!numericTokens(x).equals(numericTokens(y)))return Relation.INCOMPATIBLE;Set<String>xs=configurationTokens(x),ys=configurationTokens(y);if(xs.containsAll(ys)||ys.containsAll(xs))return Relation.COMPATIBLE_EXTENSION;return Relation.INCOMPATIBLE;}
        if(hierarchical(field)){
            if(field.equals("productType")&&(genericContainer(x)||genericContainer(y)))return Relation.AMBIGUOUS;
            Set<String> xs=tokens(x),ys=tokens(y);if(xs.isEmpty()||ys.isEmpty())return Relation.AMBIGUOUS;
            if(field.equals("productType")){
                xs=productTypeTokens(x);ys=productTypeTokens(y);
            }
            if(xs.isEmpty()||ys.isEmpty())return Relation.AMBIGUOUS;
            if(ys.containsAll(xs))return Relation.PARENT;if(xs.containsAll(ys))return Relation.CHILD;
            // Shared family words do not establish equivalence between sibling lines.
            // Only containment above supports a parent/child relation; aliases need
            // explicit normalization rather than a token-overlap shortcut.
        }
        return Relation.INCOMPATIBLE;
    }

    private static String corporateRoot(String value){return words(value).replaceFirst("(?: (?:INCORPORATED|INC|LIMITED|LTD|LLC|CORPORATION|CORP|INTERNATIONAL))+$","").trim();}
    private static Set<String> productTypeTokens(String value){
        Set<String> out=tokens(value);out.remove("PRODUCT");
        // Container and sealed-product descriptions are the same type of object.
        // Keep sport/game and specific format words, so sibling products still differ.
        if(out.contains("TRADING")&&(out.contains("CARD")||out.contains("CARDS"))
                &&(out.contains("BOX")||out.contains("SEALED")||out.contains("UNOPENED"))){
            out.remove("BOX");out.remove("SEALED");out.remove("UNOPENED");
            out.remove("CARDS");out.add("CARD");out.add("PACKAGED");
        }
        return out;
    }
    private static boolean genericContainer(String value){return words(value).matches("(?:SEALED |UNOPENED |PACKAGED )?(?:BOX|PACKAGE|PACK|PRODUCT)");}
    private static boolean unspecifiedDesign(String value){
        String s=words(value);
        return s.contains("NOT DOCUMENTED")||s.contains("NOT SPECIFIED")||s.contains("NOT ESTABLISHED")||s.equals("N A");
    }
    private static int voicePresence(String value){
        String s=words(value);
        if(!s.contains("VOICE")&&!s.contains("MICROPHONE"))return 0;
        if(s.matches(".*\\b(NO|WITHOUT|ABSENT)\\b.*"))return -1;
        return 1;
    }

    static boolean compatible(Relation relation){return relation==Relation.EXACT||relation==Relation.CANONICAL_EQUIVALENT||relation==Relation.PARENT||relation==Relation.CHILD||relation==Relation.COMPATIBLE_EXTENSION;}
    static int matchWeight(Relation r){switch(r){case EXACT:return 100;case CANONICAL_EQUIVALENT:return 96;case PARENT:case CHILD:return 82;case COMPATIBLE_EXTENSION:return 70;default:return 0;}}
    private static boolean hierarchical(String f){String x=safe(f);return x.equals("productLine")||x.equals("setName")||x.equals("subSeries")||x.equals("productFamily")||x.equals("productType");}
    private static boolean season(String f){String x=safe(f);return x.equals("productReleaseYear")||x.equals("setSeason");}
    private static boolean design(String f){String x=safe(f);return x.equals("controlLayout")||x.equals("shortcutButtons")||x.equals("navigationLayout")||x.equals("numericKeypad")||x.equals("voiceControl")||x.equals("layoutSignature")||x.equals("printedLabel");}
    private static boolean seasonContains(String a,String b){String x=digits(a),y=digits(b);return x.length()==4&&y.startsWith(x)||y.length()==4&&x.startsWith(y);}
    private static String digits(String x){return safe(x).replaceAll("[^0-9]","");}
    private static Set<String> tokens(String raw){Set<String>out=new LinkedHashSet<>();for(String t:words(raw).split(" "))if(t.length()>1&&!t.equals("SERIES")&&!t.equals("THE"))out.add(t);return out;}
    private static Relation configurationQuantities(String left,String right){
        java.util.Map<String,long[]> a=quantities(left),b=quantities(right);
        if(a.isEmpty()||b.isEmpty())return uncertainQuantityClause(left)||uncertainQuantityClause(right)?Relation.AMBIGUOUS:null;
        int shared=0;
        for(String key:a.keySet())if(b.containsKey(key)){
            shared++;long[] x=a.get(key),y=b.get(key);
            if(x[0]*y[1]!=y[0]*x[1])return Relation.INCOMPATIBLE;
        }
        // Missing pack/count information is unknown, not a contradiction.
        if(shared==0)return Relation.AMBIGUOUS;
        return Relation.COMPATIBLE_EXTENSION;
    }
    private static java.util.Map<String,long[]> quantities(String value){
        java.util.Map<String,long[]> out=new java.util.LinkedHashMap<>();
        StringBuilder known=new StringBuilder();
        for(String clause:value.split("[;\\n]"))if(!uncertainQuantityClause(clause))known.append(clause).append("; ");
        String normalized=words(known.toString()).replace("IN EVERY","PER").replace("IN EACH","PER");
        java.util.regex.Matcher m=java.util.regex.Pattern.compile(
                "\\b([0-9]{1,6}) (AUTOGRAPHS?(?: CARDS?)?|CARDS?|PACKS?) PER (?:([0-9]{1,6}) )?(BOXES|BOX|PACKS?|CASES?)\\b").matcher(normalized);
        while(m.find()){
            String item=m.group(2).startsWith("AUTOGRAPH")?"AUTOGRAPH":m.group(2).replaceAll("S$","");
            String container=m.group(4).equals("BOXES")?"BOX":m.group(4).replaceAll("S$","");
            long denominator=m.group(3)==null?1:Long.parseLong(m.group(3));
            if(denominator==0)return new java.util.LinkedHashMap<>();
            String key=item+"/"+container;long[] quantity={Long.parseLong(m.group(1)),denominator};
            if(out.containsKey(key)){
                long[] old=out.get(key);
                if(old[0]*quantity[1]!=quantity[0]*old[1])return new java.util.LinkedHashMap<>();
            }
            out.put(key,quantity);
        }
        if(java.util.regex.Pattern.compile("[0-9]").matcher(m.reset().replaceAll("")).find())return new java.util.LinkedHashMap<>();
        return out;
    }
    private static boolean uncertainQuantityClause(String value){
        return words(value).matches(".*\\b(?:NOT ESTABLISHED|NOT SHOWN|NOT SPECIFIED|NOT CONFIRMED|UNKNOWN|UNVERIFIED)\\b.*");
    }
    static boolean completeBoxConfiguration(String value){
        java.util.Map<String,long[]> q=quantities(value);
        return q.containsKey("CARD/PACK")&&q.containsKey("PACK/BOX")
                &&q.get("CARD/PACK")[0]>0&&q.get("PACK/BOX")[0]>0;
    }
    private static Set<String> configurationTokens(String value){
        String normalized=words(value).replace("IN EVERY", "PER").replace("IN EACH", "PER").replace("BOXES", "BOX");
        return tokens(normalized);
    }
    private static Set<String> numericTokens(String raw){Set<String>out=new LinkedHashSet<>();for(String t:words(raw).split(" "))if(t.matches("\\d+"))out.add(t);return out;}
    private static String words(String raw){return Normalizer.normalize(safe(raw),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static String safe(String v){return v==null?"":v.trim();}
}
