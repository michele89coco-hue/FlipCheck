package com.flipcheck.nativebeta;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compares only like-for-like catalog levels; parent, insert and parallel are complementary axes. */
final class CatalogHierarchy {
    private CatalogHierarchy() {}

    static boolean sameLevelConflict(IdentityProfileEngine.PhotoTuple p, Models.CandidateScore c) {
        if (p == null || c == null) return false;
        if (conflict(p.mainSet, c.mainSet)) return true;
        if (conflict(p.insertSubset, c.subset)) return true;
        if (conflict(p.designFamily, c.designFamily)) return true;
        if (conflict(p.subSeries, c.subSeries)) return true;
        // A legacy flat family is compatible when it can be placed at any observed hierarchy level.
        if (!empty(c.family) && !empty(p.family) && !compatible(p.family,c.family)
                && !compatible(p.mainSet,c.family) && !compatible(p.insertSubset,c.family)
                && !compatible(p.designFamily,c.family) && !compatible(p.subSeries,c.family)) return true;
        return false;
    }

    static boolean candidateContainsObservedHierarchy(IdentityProfileEngine.PhotoTuple p, Models.CandidateScore c) {
        List<String> candidate = axes(c);
        for (String token : p.distinctiveTokens) {
            if (empty(token)) continue;
            boolean found=false; for(String axis:candidate)if(compatible(axis,token)){found=true;break;}
            if(!found)return false;
        }
        return !sameLevelConflict(p,c);
    }

    static String describe(Models.CandidateScore c) {
        return "mainSet="+c.mainSet+"; subset="+c.subset+"; designFamily="+c.designFamily
                +"; subSeries="+c.subSeries+"; parallelFamily="+c.parallelFamily
                +"; parallelColor="+c.parallelColor+"; printRun="+c.printRun
                +"; format="+c.format+"; productCode="+c.productCode;
    }

    static List<String> axes(Models.CandidateScore c){List<String>x=new ArrayList<>();add(x,c.family);add(x,c.mainSet);add(x,c.subset);add(x,c.designFamily);add(x,c.subSeries);add(x,c.model);x.addAll(c.distinguishingTokens);return x;}
    static boolean compatible(String a,String b){String x=canon(a),y=canon(b);return !x.isEmpty()&&!y.isEmpty()&&(x.equals(y)||x.contains(y)||y.contains(x));}
    static boolean conflict(String a,String b){return !empty(a)&&!empty(b)&&!compatible(a,b);}
    private static void add(List<String>x,String v){if(!empty(v))x.add(v);}
    private static String canon(String x){return Normalizer.normalize(clean(x),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}
    private static boolean empty(String x){return clean(x).isEmpty();}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
