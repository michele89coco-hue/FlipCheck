package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.json.JSONObject;

/** Reconciles identity-critical sports-card markings using the second physical Vision pass only. */
final class SportsCardPhysicalDetailVerifier {
    private static final Pattern FRACTION=Pattern.compile("^[0-9]{1,6}/[0-9]{1,6}$");
    private SportsCardPhysicalDetailVerifier() {}

    static boolean requiresSecondVision(Models.Identification id) {
        if(id==null)return false;
        NormalizedPhotoIdentity n=PhotographicFactNormalizer.require(id);
        IdentityProfileEngine.PhotoTuple tuple=IdentityProfileEngine.tuple(id);
        if(IdentityProfileEngine.profile(id,tuple)!=IdentityProfileEngine.Profile.SPORTS_CARD)return false;
        return !n.values(CanonicalFieldKey.PHYSICAL_SERIAL_CANDIDATE).isEmpty()
                ||!n.values(CanonicalFieldKey.PHYSICAL_PARALLEL_CANDIDATE).isEmpty();
    }

    static String prompt(Models.Identification id) {
        return "Reinspect the SAME supplied sports card photos. Focus on the exact printed serial/print-run area and the physical parallel surface. "
                + "Never use a checklist, candidate, OCR guess, rating/stat or Web value as a physical marking. "
                + "Return physical_serial_marking only as the literal x/y visible on the card. serial_area_clear=true only when both numerator and denominator are sharply readable. "
                + "FIRST_PASS_CRITICAL_FIELDS="+criticalFields(id)
                +"\nPHOTO_VIEWS="+id.photoViews
                +"\nPHYSICAL_FIELDS="+id.photoIdentityFields
                +"\nVISUAL_FACTS="+id.visualFacts;
    }

    static boolean apply(Models.Identification id, OpenAiClient.Response response) {
        if (id==null) return false;
        if (response==null || !response.complete || response.payload==null
                || response.payload.length()==0 || !safe(response.parseError).isEmpty()) {
            markUnresolvedAfterAttempt(id); return false;
        }
        JSONObject p=response.payload;
        if (!p.optBoolean("same_physical_card",false)) {
            markUnresolvedAfterAttempt(id); return false;
        }
        String firstSerial=normalizeFraction(PhotographicFactNormalizer.require(id)
                .best(CanonicalFieldKey.PHYSICAL_SERIAL_CANDIDATE));
        boolean clear=p.optBoolean("serial_area_clear",false);
        int imageIndex=Math.max(0,p.optInt("image_index",0));
        String serialLocation=safe(p.optString("serial_location","serial_area"));
        String serial=normalizeFraction(p.optString("physical_serial_marking",""));
        removeKeys(id,"physical_serial_marking","physical_print_run","card_surface_serial",
                "serial","serial_number","serial_binding","serial_area_clear");
        if (clear && !serial.isEmpty()) {
            add(id.photoIdentityFields,"physical_serial_marking="+serial);
            add(id.photoIdentityFields,"serial_binding=physical_card_surface");
            add(id.photoIdentityFields,"serial_area_clear=true");
            id.physicalSerial=serial;
            id.physicalSerialOrigin="vision2:physical_card_surface";
            EvidenceLedger.addPhotoFact(id,"physical_serial_marking",serial,"localized_print_run",98,
                    imageIndex,"",serialLocation,"specimen_serial");
            EvidenceLedger.addPhotoFact(id,"serial_binding","physical_card_surface","position",98,
                    imageIndex,"",serialLocation,"binding");
            if(!firstSerial.isEmpty())id.photoIdentityName=replaceLiteral(
                    id.photoIdentityName,firstSerial,serial);
        } else {
            add(id.photoIdentityFields,"physical_serial_marking=unresolved");
            add(id.photoIdentityFields,"serial_area_clear=false");
            id.physicalSerial="";
            id.physicalSerialOrigin="";
            if(!firstSerial.isEmpty())id.photoIdentityName=replaceLiteral(
                    id.photoIdentityName,firstSerial,"");
        }
        replacePhysical(id,"physical_parallel",p.optString("physical_parallel",""));
        replacePhysical(id,"parallel_color",p.optString("parallel_color",""));
        replacePhysical(id,"finish",p.optString("finish",""));
        String parallel=safe(p.optString("physical_parallel",""));
        String parallelLocation=safe(p.optString("parallel_location","parallel_marker_area"));
        if(!unresolved(parallel))EvidenceLedger.addPhotoFact(id,"physical_parallel",parallel,
                "localized_parallel_marker",96,imageIndex,"",parallelLocation,"parallel");
        String color=safe(p.optString("parallel_color",""));
        if(!unresolved(color))EvidenceLedger.addPhotoFact(id,"parallel_color",color,
                "localized_surface_color",90,imageIndex,"",parallelLocation,"parallel_color");
        String finish=safe(p.optString("finish",""));
        if(!unresolved(finish))EvidenceLedger.addPhotoFact(id,"finish",finish,
                "localized_surface_finish",90,imageIndex,"",parallelLocation,"finish");
        id.criticalCardDetailVerified=true;
        id.criticalCardDetailNeedsSecondVision=false;
        if(clear&&!serial.isEmpty()){
            id.photoIdentityAmbiguous=false;id.photoAlternativeCount=1;
            id.discriminativeFieldVisible=true;id.discriminativeField="";
        }
        return true;
    }

    static void markUnresolvedAfterAttempt(Models.Identification id) {
        if (id==null) return;
        String firstSerial=normalizeFraction(PhotographicFactNormalizer.require(id)
                .best(CanonicalFieldKey.PHYSICAL_SERIAL_CANDIDATE));
        boolean hadSerial=!firstSerial.isEmpty();
        removeKeys(id,"physical_serial_marking","physical_print_run","card_surface_serial",
                "serial","serial_number","serial_binding","serial_area_clear");
        if (hadSerial) {
            add(id.photoIdentityFields,"physical_serial_marking=unresolved");
            add(id.photoIdentityFields,"serial_area_clear=false");
        }
        id.physicalSerial="";
        id.physicalSerialOrigin="";
        if(hadSerial)id.photoIdentityName=replaceLiteral(id.photoIdentityName,firstSerial,"");
        id.criticalCardDetailVerified=true;
        id.criticalCardDetailNeedsSecondVision=false;
    }

    private static List<String> criticalFields(Models.Identification id) {
        List<String> out=new ArrayList<>();
        collect(id.visualFacts,out); collect(id.photoIdentityFields,out); return out;
    }
    private static void collect(List<String> source,List<String> out) {
        for(String value:source){String k=key(value);if(k.equals("physical_serial_marking")
                ||k.equals("physical_print_run")||k.equals("card_surface_serial")
                ||k.equals("serial")||k.equals("serial_number")||k.equals("physical_parallel")
                ||k.equals("parallel")||k.equals("parallel_color")||k.equals("finish"))out.add(value);}
    }
    private static void replacePhysical(Models.Identification id,String key,String value) {
        removeKeys(id,key); String v=safe(value); if(!unresolved(v))add(id.photoIdentityFields,key+"="+v);
    }
    private static void removeKeys(Models.Identification id,String...keys) {
        java.util.HashSet<String> wanted=new java.util.HashSet<>();
        for(String k:keys)wanted.add(k);
        id.visualFacts.removeIf(x->wanted.contains(key(x)));
        id.photoIdentityFields.removeIf(x->wanted.contains(key(x)));
    }
    private static String raw(Models.Identification id,String...keys) {
        for(String wanted:keys){for(String x:id.photoIdentityFields)if(key(x).equals(wanted))return value(x);
            for(String x:id.visualFacts)if(key(x).equals(wanted))return value(x);}return "";
    }
    private static String normalizeFraction(String x){String v=safe(x).replace(" ","");return FRACTION.matcher(v).matches()?v:"";}
    private static String replaceLiteral(String source,String from,String to){return safe(source).replace(from,to).replaceAll("\\s+"," ").trim();}
    private static boolean unresolved(String x){String v=safe(x).toLowerCase(Locale.ROOT);return v.isEmpty()||v.equals("unknown")||v.equals("unresolved")||v.equals("unclear")||v.equals("not visible")||v.equals("non leggibile");}
    private static String key(String x){String v=safe(x);int p=v.indexOf('=');if(p<1)p=v.indexOf(':');return p<1?"":v.substring(0,p).trim().toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');}
    private static String value(String x){String v=safe(x);int p=v.indexOf('=');if(p<1)p=v.indexOf(':');return p<0?"":safe(v.substring(p+1));}
    private static void add(List<String> out,String value){for(String old:out)if(old.equalsIgnoreCase(value))return;out.add(value);}
    private static String safe(String x){return x==null?"":x.trim();}
}
