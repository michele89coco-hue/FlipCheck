package com.flipcheck.nativebeta;

import java.util.Locale;

/** TCG identity is front-led; a common back is never requested as a discriminator. */
final class TcgFrontIdentityPolicy {
    private TcgFrontIdentityPolicy() {}

    static boolean isTcg(Models.Identification id) {
        if(id==null)return false;
        String x=(safe(id.categoryKey)+" "+safe(id.category)+" "+id.photoIdentityFields+" "+id.visualFacts)
                .toLowerCase(Locale.ROOT).replace('-','_');
        boolean sports=x.contains("sports")||x.contains("athlete=")||x.contains("player=")
                ||x.contains("team=")||x.contains("position=");
        return !sports&&(x.contains("tcg")||x.contains("trading card game")
                ||x.contains("collectible card game")||x.contains("collector_number="));
    }

    static boolean frontComplete(Models.Identification id) {
        if(id==null||!hasFront(id))return false;
        return truth(field(id,"front_complete","complete_front"));
    }

    static String secondVisionPrompt(Models.Identification id) {
        return "TCG FRONT-ONLY DIAGNOSTIC. Reinspect the SAME front image at all rotations and zoom/crop the lower edge, especially the collector-number/set-code area. "
                +"The common card back is not required. Return only fields physically visible on the card surface. "
                +"A collector/card number is allowed only with physical_card_number_marking=<literal>, card_number_binding=physical_card_surface, "
                +"card_number_semantic=collector_number and card_number_location=<precise front location>. "
                +"Ignore listing/app UI, site text, phone model, date/time, external watermark and unrelated OCR. "
                +"If the whole front and all four edges are sharp and visible, return front_complete=true. If the lower edge is cropped/blurred/covered, return front_complete=false.\n"
                +PhysicalIdentityRecovery.prompt(id);
    }

    static String nextPhotoRequest(Models.Identification id) {
        if(frontComplete(id))return "";
        return "Fotografa il fronte completo e nitido, includendo tutti e quattro i bordi e soprattutto il codice/numero in basso";
    }

    private static boolean hasFront(Models.Identification id){if(id.photoViews.isEmpty())return true;for(String v:id.photoViews){String x=safe(v).toLowerCase(Locale.ROOT);if(x.contains("front")||x.contains("fronte"))return true;}return false;}
    private static String field(Models.Identification id,String...keys){for(String k:keys){for(String x:id.photoIdentityFields)if(key(x).equals(k))return val(x);for(String x:id.visualFacts)if(key(x).equals(k))return val(x);}return "";}
    private static boolean truth(String x){String v=safe(x).toLowerCase(Locale.ROOT);return v.equals("true")||v.equals("yes")||v.equals("1")||v.equals("complete")||v.equals("visible");}
    private static String key(String x){String v=safe(x);int p=v.indexOf('=');if(p<1)p=v.indexOf(':');return p<1?"":v.substring(0,p).trim().toLowerCase(Locale.ROOT).replace('-','_').replace(' ','_');}
    private static String val(String x){String v=safe(x);int p=v.indexOf('=');if(p<1)p=v.indexOf(':');return p<0?"":safe(v.substring(p+1));}
    private static String safe(String x){return x==null?"":x.trim();}
}
