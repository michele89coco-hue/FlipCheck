package com.flipcheck.nativebeta;

/** Keeps commercial seasons precise and distinct from copyright/statistical years. */
final class SeasonNormalizer {
    private SeasonNormalizer() {}
    static String normalize(String raw){String v=clean(raw).replace('–','-').replace('—','-');
        if(v.matches("(?:19|20)\\d{2}[/.-]\\d{2}"))return v.substring(0,4)+"-"+v.substring(5);
        if(v.matches("(?:19|20)\\d{2}[/.-](?:19|20)\\d{2}"))return v.substring(0,4)+"-"+v.substring(7);
        return v;}
    static boolean compatible(String a,String b){String x=normalize(a),y=normalize(b);if(x.isEmpty()||y.isEmpty())return true;
        if(x.equalsIgnoreCase(y))return true;String xd=x.replaceAll("[^0-9]",""),yd=y.replaceAll("[^0-9]","");
        return xd.equals(yd)||xd.startsWith(yd)||yd.startsWith(xd);}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
