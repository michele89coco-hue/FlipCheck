package com.flipcheck.nativebeta;

import java.util.Locale;

/** Converts internal taxonomy into a useful human category without promoting a model. */
final class CategoryPresentationPolicy {
    private CategoryPresentationPolicy() {}
    static String humanCategory(String key){String x=safe(key).toLowerCase(Locale.ROOT).replace('-','_');
        if(x.contains("sealed"))return "Prodotto sigillato da collezione";
        if(x.contains("tcg"))return "Carta TCG";
        if(x.contains("sport")&&x.contains("card"))return "Carta sportiva";
        if(x.contains("smartphone")||x.contains("mobile"))return "Smartphone";
        if(x.contains("controller")||x.contains("irrig"))return "Centralina irrigazione";
        if(x.contains("appliance"))return "Elettrodomestico";
        if(x.contains("remote"))return "Telecomando";
        if(x.contains("tool"))return "Utensile";return "Prodotto da identificare";}
    static void apply(Models.Identification id){if(id==null)return;String raw=safe(id.category);String key=(safe(id.categoryKey)+" "+raw).toLowerCase(Locale.ROOT).replace('-','_');
        if(!generic(raw)&&!key.equals("other"))return;
        String physical=PhotographicFactNormalizer.require(id).best(CanonicalFieldKey.PRODUCT_TYPE);
        if(!physical.isEmpty()){id.category=localizedCategory(physical);return;}
        if(key.contains("irrig")||key.contains("controller"))id.category="Centralina irrigazione";
        else if(key.contains("remote"))id.category="Telecomando";
        else if(key.contains("appliance"))id.category="Elettrodomestico";
        else if(key.contains("tool"))id.category="Utensile";
        else id.category="Prodotto da identificare";
    }
    private static String localizedCategory(String value){String x=safe(value).toLowerCase(Locale.ROOT).replace('-','_');
        if(x.contains("irrig")&&(x.contains("controller")||x.contains("timer")))return "Centralina irrigazione";
        if(x.contains("remote"))return "Telecomando";
        if(x.contains("appliance"))return "Elettrodomestico";
        if(x.contains("tool"))return "Utensile";
        return title(value);
    }
    private static boolean generic(String x){String v=safe(x).toLowerCase(Locale.ROOT);return v.isEmpty()||v.equals("other")||v.equals("other collectible")||v.equals("collectible")||v.equals("object")||v.equals("unknown");}
    private static String title(String x){String v=safe(x).replace('_',' ').replace('-',' ');return v.isEmpty()?v:Character.toUpperCase(v.charAt(0))+v.substring(1);}
    private static String safe(String x){return x==null?"":x.trim();}
}
