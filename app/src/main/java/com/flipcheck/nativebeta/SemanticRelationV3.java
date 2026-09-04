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
        if(a.equalsIgnoreCase(b))return Relation.EXACT;
        if(TypedFieldNormalizerV2.ambiguous(a)||TypedFieldNormalizerV2.ambiguous(b))return Relation.AMBIGUOUS;
        String x=TypedFieldNormalizerV2.normalizeValue(field,a,""),y=TypedFieldNormalizerV2.normalizeValue(field,b,"");
        if(words(x).equals(words(y))||TypedFieldNormalizerV2.equivalent(field,x,y))return Relation.CANONICAL_EQUIVALENT;
        if(season(field)){if(seasonContains(x,y))return x.length()<y.length()?Relation.PARENT:Relation.CHILD;return Relation.INCOMPATIBLE;}
        if(design(field)){
            Set<String>xs=tokens(x),ys=tokens(y);if(xs.isEmpty()||ys.isEmpty())return Relation.AMBIGUOUS;
            Set<String>common=new LinkedHashSet<>(xs);common.retainAll(ys);double coverage=(double)common.size()/Math.min(xs.size(),ys.size());
            if(coverage>=.85d)return Relation.CANONICAL_EQUIVALENT;if(coverage>=.55d)return Relation.COMPATIBLE_EXTENSION;if(coverage>=.30d)return Relation.AMBIGUOUS;return Relation.INCOMPATIBLE;
        }
        if(safe(field).equals("configuration")){if(!numericTokens(x).equals(numericTokens(y)))return Relation.INCOMPATIBLE;Set<String>xs=tokens(x),ys=tokens(y);if(xs.containsAll(ys)||ys.containsAll(xs))return Relation.COMPATIBLE_EXTENSION;return Relation.INCOMPATIBLE;}
        if(hierarchical(field)){
            Set<String> xs=tokens(x),ys=tokens(y);if(xs.isEmpty()||ys.isEmpty())return Relation.AMBIGUOUS;
            if(field.equals("productType")){xs.remove("PRODUCT");ys.remove("PRODUCT");}
            if(xs.isEmpty()||ys.isEmpty())return Relation.AMBIGUOUS;
            if(ys.containsAll(xs))return Relation.PARENT;if(xs.containsAll(ys))return Relation.CHILD;
            // Shared family words do not establish equivalence between sibling lines.
            // Only containment above supports a parent/child relation; aliases need
            // explicit normalization rather than a token-overlap shortcut.
        }
        return Relation.INCOMPATIBLE;
    }

    static boolean compatible(Relation relation){return relation==Relation.EXACT||relation==Relation.CANONICAL_EQUIVALENT||relation==Relation.PARENT||relation==Relation.CHILD||relation==Relation.COMPATIBLE_EXTENSION;}
    static int matchWeight(Relation r){switch(r){case EXACT:return 100;case CANONICAL_EQUIVALENT:return 96;case PARENT:case CHILD:return 82;case COMPATIBLE_EXTENSION:return 70;default:return 0;}}
    private static boolean hierarchical(String f){String x=safe(f);return x.equals("productLine")||x.equals("setName")||x.equals("subSeries")||x.equals("productFamily")||x.equals("productType");}
    private static boolean season(String f){String x=safe(f);return x.equals("productReleaseYear")||x.equals("setSeason");}
    private static boolean design(String f){String x=safe(f);return x.equals("controlLayout")||x.equals("shortcutButtons")||x.equals("navigationLayout")||x.equals("numericKeypad")||x.equals("voiceControl")||x.equals("layoutSignature")||x.equals("printedLabel");}
    private static boolean seasonContains(String a,String b){String x=digits(a),y=digits(b);return x.length()==4&&y.startsWith(x)||y.length()==4&&x.startsWith(y);}
    private static String digits(String x){return safe(x).replaceAll("[^0-9]","");}
    private static Set<String> tokens(String raw){Set<String>out=new LinkedHashSet<>();for(String t:words(raw).split(" "))if(t.length()>1&&!t.equals("SERIES")&&!t.equals("THE"))out.add(t);return out;}
    private static Set<String> numericTokens(String raw){Set<String>out=new LinkedHashSet<>();for(String t:words(raw).split(" "))if(t.matches("\\d+"))out.add(t);return out;}
    private static String words(String raw){return Normalizer.normalize(safe(raw),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    private static String safe(String v){return v==null?"":v.trim();}
}
