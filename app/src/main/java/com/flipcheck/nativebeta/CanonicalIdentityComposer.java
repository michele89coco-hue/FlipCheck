package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the public identity exclusively from normalized physical fields. */
final class CanonicalIdentityComposer {
    private CanonicalIdentityComposer() {}

    static String cardModel(String subject, String number, String level, String color,
                            String finish, String edition, String serial) {
        List<String> parts=new ArrayList<>();
        add(parts,subject,false);
        if(!empty(number))add(parts,"No. "+number,false);
        StringBuilder b=new StringBuilder();for(String x:parts){if(b.length()>0)b.append(' ');b.append(x);}return b.toString();
    }

    static void refreshConfirmedCard(Models.Identification id) {
        if(id==null||!UniversalIdentityClosure.isTerminal(id))return;
        NormalizedPhotoIdentity n=PhotographicFactNormalizer.require(id);
        String subject=n.subject();
        String edition=n.edition();
        String model=cardModel(subject,PhysicalCardNumberPolicy.verifiedValue(id),"","","","","");
        id.confirmedModel=model; id.model=model;
        if(!id.candidates.isEmpty()){id.candidates.get(0).model=model;id.candidates.get(0).probableReference=join(id.confirmedBrand,id.confirmedFamily,model);}
    }

    static void refreshCatalogReleaseDisplay(Models.Identification id){if(id==null||empty(id.sourceConfirmedReleaseYear)||!UniversalIdentityClosure.isTerminal(id))return;
        String physical=SeasonNormalizer.normalize(PhotographicFactNormalizer.require(id).physicalYear());String family=clean(id.confirmedFamily);
        id.sourceConfirmedReleaseYear=SeasonNormalizer.normalize(id.sourceConfirmedReleaseYear);
        if(!empty(physical)&&family.startsWith(physical+" "))family=clean(family.substring(physical.length()));
        if(!canon(family).contains(canon(id.sourceConfirmedReleaseYear)))family=join(id.sourceConfirmedReleaseYear,family);
        id.confirmedFamily=family;id.family=family;
    }

    static String sealedTitle(Models.Identification id){if(id==null)return "Prodotto sigillato";NormalizedPhotoIdentity n=PhotographicFactNormalizer.require(id);
        List<String> parts=new ArrayList<>();String brand=clean(id.confirmedBrand);
        String line=first(id.sourceConfirmedMainSet,id.sourceConfirmedProductLine,n.mainSet(),n.productLine());
        String sub=first(id.sourceConfirmedSubSeries,n.subSeries());
        if(startsWithOrContainsToken(line,sub))sub="";
        for(String token:n.distinctiveTokens())if(!startsWithOrContainsToken(line+" "+sub,token))sub=join(sub,token);
        add(parts,SeasonNormalizer.normalize(first(id.sourceConfirmedReleaseYear,n.physicalYear())),false);
        if(!startsWithTokens(line,brand))add(parts,brand,false);add(parts,line,false);add(parts,sub,false);
        String sport=n.best(CanonicalFieldKey.SPORT);if(!startsWithOrContainsToken(line,sport))add(parts,sport,false);
        String core=join(parts.toArray(new String[0]));String format=first(id.sourceConfirmedFormat,n.best(CanonicalFieldKey.FORMAT));
        return empty(core)?"Prodotto sigillato":core+" — "+(empty(format)?"prodotto sigillato":format+" sigillato");}

    private static boolean startsWithTokens(String text,String prefix){String t=words(text),p=words(prefix);return !p.isEmpty()&&(t.equals(p)||t.startsWith(p+" "));}
    private static boolean startsWithOrContainsToken(String text,String token){String t=" "+words(text)+" ",p=words(token);return !p.isEmpty()&&t.contains(" "+p+" ");}
    private static String words(String x){return clean(x).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}

    private static void add(List<String> out,String raw,boolean rejectBoolean) {
        String v=clean(raw); if(empty(v)||unresolved(v))return;
        String c=canon(v); if(rejectBoolean&&(c.equals("PRESENT")||c.equals("TRUE")||c.equals("YES")||c.equals("VISIBLE")))return;
        for(String old:out){String o=canon(old);if(o.equals(c)||o.contains(" "+c+" ")||c.contains(" "+o+" "))return;}
        out.add(v);
    }
    private static boolean unresolved(String x){String v=clean(x).toLowerCase(Locale.ROOT).replace('_',' ');return v.equals("unresolved")||v.equals("unknown")||v.equals("unclear")||v.equals("not visible")||v.equals("none visible")||v.equals("none")||v.equals("n/a");}
    private static String join(String...xs){StringBuilder b=new StringBuilder();for(String x:xs)if(!empty(x)){if(b.length()>0)b.append(' ');b.append(clean(x));}return b.toString();}
    private static String first(String...xs){for(String x:xs)if(!empty(x))return clean(x);return "";}
    private static String canon(String x){return (" "+clean(x).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim()+" ").replaceAll("\\s+"," ");}
    private static boolean empty(String x){return clean(x).isEmpty();}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
