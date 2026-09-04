package com.flipcheck.nativebeta;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Rebuilds conflicts from evidence. Missing data and hierarchy differences are never conflicts. */
final class DocumentedConflictPolicy {
    private DocumentedConflictPolicy() {}

    static boolean reconcile(Models.Identification id) {
        if (id == null) return false;
        id.documentedConflicts.clear();
        Map<String,Models.EvidenceFact> physical = new LinkedHashMap<>();
        Models.EvidenceFact catalog = null;
        for (Models.EvidenceFact f : id.evidenceLedger) {
            if (f == null || f.confidence < 70 || !identifierKey(f.key)) continue;
            String key=canon(f.value);if(key.isEmpty())continue;
            if (EvidenceLedger.WEB_CATALOG.equals(f.origin)) {
                if(catalog==null||f.confidence>catalog.confidence)catalog=f;
            } else if (EvidenceLedger.isPixelOrigin(f.origin)||EvidenceLedger.LOCAL_OCR.equals(f.origin)) {
                Models.EvidenceFact old=physical.get(key);
                if(old==null||f.confidence>old.confidence)physical.put(key,f);
            }
        }
        Models.EvidenceFact first=null,second=null;
        for(Models.EvidenceFact f:physical.values()){if(first==null)first=f;else {second=f;break;}}
        Models.EvidenceFact selected=bestFor(id,physical);
        boolean selectedCatalogAgreement=selected!=null&&catalog!=null&&canon(selected.value).equals(canon(catalog.value));
        if(first!=null&&second!=null&&(!selectedCatalogAgreement||Math.abs(first.confidence-second.confidence)<10))
            add(id,"collectorNumber",first,second,"HARD",selectedCatalogAgreement);
        if(selected!=null&&catalog!=null&&!canon(selected.value).equals(canon(catalog.value)))
            add(id,"collectorNumber",selected,catalog,"HARD",true);
        if(id.documentedConflicts.isEmpty()) {
            id.numberConflicts="";
            removePrefix(id,"identifierConflict=");
            return false;
        }
        Models.Conflict c=id.documentedConflicts.get(0);
        id.numberConflicts="field="+c.field+"; valueA="+c.valueA+"; evidenceA="+c.evidenceA
                +"; valueB="+c.valueB+"; evidenceB="+c.evidenceB+"; severity="+c.severity;
        return true;
    }

    static boolean hasHardConflict(Models.Identification id){
        if(id==null)return false;reconcile(id);
        for(Models.Conflict c:id.documentedConflicts)if(c.complete()&&"HARD".equals(c.severity))return true;
        return false;
    }

    private static Models.EvidenceFact bestFor(Models.Identification id,Map<String,Models.EvidenceFact> values){
        String wanted=canon(!safe(id.physicalCollectorNumber).isEmpty()?id.physicalCollectorNumber:id.physicalCardNumber);
        if(!wanted.isEmpty()&&values.containsKey(wanted))return values.get(wanted);
        Models.EvidenceFact best=null;for(Models.EvidenceFact f:values.values())if(best==null||f.confidence>best.confidence)best=f;return best;
    }
    private static void add(Models.Identification id,String field,Models.EvidenceFact a,Models.EvidenceFact b,String severity,boolean attempted){
        Models.Conflict c=new Models.Conflict(field,a.value,EvidenceLedger.debug(a),b.value,EvidenceLedger.debug(b),severity,attempted);
        if(c.complete())id.documentedConflicts.add(c);
    }
    private static boolean identifierKey(String k){CanonicalFieldKey key=CanonicalFieldKey.fromAlias(k);return key==CanonicalFieldKey.CARD_NUMBER_CANDIDATE
            ||key==CanonicalFieldKey.COLLECTOR_NUMBER_CANDIDATE||key==CanonicalFieldKey.SOURCE_CONFIRMED_CATALOG_NUMBER;}
    private static String canon(String x){return safe(x).toUpperCase(Locale.ROOT).replaceFirst("^#","").replaceAll("[^A-Z0-9]","");}
    private static void removePrefix(Models.Identification id,String prefix){for(int i=id.finalContradictions.size()-1;i>=0;i--)if(id.finalContradictions.get(i).startsWith(prefix))id.finalContradictions.remove(i);}
    private static String safe(String x){return x==null?"":x.trim();}
}
