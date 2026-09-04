package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Verifiable market ledger; price exists only from a homogeneous sourced sold bucket. */
final class ComparablePricePolicy {
    private static final Pattern NUMBER=Pattern.compile("(?i)(?:#|\\bNO\\.?\\s*)(NNO|[A-Z0-9][A-Z0-9/-]{0,12})");
    private ComparablePricePolicy() {}
    static void apply(Models.Identification id,JSONObject payload,OpenAiClient.Response response){if(id==null)return;
        id.marketComparables.clear();id.excludedComparablesWithReason="";Map<String,List<Double>> sold=new LinkedHashMap<>(),active=new LinkedHashMap<>();Map<String,String> labels=new LinkedHashMap<>();
        JSONArray input=payload==null?null:payload.optJSONArray("comparables");
        if(input!=null)for(int i=0;i<input.length();i++){JSONObject c=input.optJSONObject(i);if(c==null)continue;
            String url=clean(c.optString("source_url","")),title=clean(c.optString("title",""));
            String status=canon(c.optString("sale_status","")),state=canon(c.optString("item_state",""));
            String condition=clean(c.optString("condition","")),company=clean(c.optString("grading_company","")),grade=clean(c.optString("grade",""));
            String currency=clean(c.optString("currency","")).toUpperCase(Locale.ROOT),date=clean(c.optString("date",""));double price=c.optDouble("price",Double.NaN);
            boolean source=sourcePresent(response,url),identityMatch=c.optBoolean("identity_match",false);
            boolean variantSpecific=c.optBoolean("variant_specific",false);
            String variantKey=clean(c.optString("variant_key",""));
            boolean soldStatus=status.equals("SOLD"),activeStatus=status.equals("ACTIVE LISTING");
            String expected=canon(ProfileQueryBuilder.expectedMarketState(id));
            String verifiedVariant=id.rareVariantPhysicalProof?clean(id.physicalParallel):"";
            String comparedNumber=numberFromTitle(title),identityNumber=clean(id.sourceConfirmedCatalogNumber);
            if(identityNumber.isEmpty())identityNumber=clean(PhysicalCardNumberPolicy.verifiedValue(id));
            boolean numberMatch=comparedNumber.isEmpty()||identityNumber.isEmpty()||canon(comparedNumber).equals(canon(identityNumber));
            boolean exactOpen=!ExactCatalogResolver.marketReady(id)||exactOpen(id.exactIdentityStatus);
            String structuredConflict=comparableConflict(id,c);
            String reason=!source?"excluded: source URL not retrieved":!identityMatch?"excluded: comparable identity/variant mismatch":
                    !structuredConflict.isEmpty()?"excluded: non-homogeneous comparable "+structuredConflict:
                    !numberMatch?"EXCLUDED_NUMBER_MISMATCH: comparable="+comparedNumber+" identity="+identityNumber:
                    !clean(id.numberConflicts).isEmpty()?"CONFLICT_SIGNAL: unresolved physical/catalog number conflict":
                    variantSpecific&&!verifiedVariant.isEmpty()&&!canon(verifiedVariant).equals(canon(variantKey))?"EXCLUDED_VARIANT_MISMATCH: comparable="+variantKey+" identity="+verifiedVariant:
                    variantSpecific&&!PhysicalVariantPolicy.canUseVariant(id,variantKey)?"excluded: variant lacks direct photographic proof":
                    exactOpen?"excluded: exact identity or commercial variant unresolved":
                    !state.equals(expected)?"excluded: item state differs from photographed product profile":
                    Double.isNaN(price)||price<=0||currency.isEmpty()?"excluded: invalid price/currency":
                    !(soldStatus||activeStatus)?"excluded: unsupported sale status":"included: sourced homogeneous bucket";
            boolean included=reason.startsWith("included");
            id.marketComparables.add(new Models.MarketComparable(url,title,state,condition,company,grade,currency,price,date,soldStatus,included,reason));
            if(!included&&!id.excludedComparablesWithReason.contains(reason))id.excludedComparablesWithReason+=
                    (id.excludedComparablesWithReason.isEmpty()?"":" | ")+title+" => "+reason;
            if(!included)continue;
            String bucket=state.equals("SEALED")?"sealed":state.equals("GRADED")&&!company.isEmpty()&&!grade.isEmpty()?company.toUpperCase(Locale.ROOT)+" "+grade:"raw/ungraded";
            String key=currency+"|"+canon(bucket);labels.put(key,currency+" "+bucket);
            (soldStatus?sold:active).computeIfAbsent(key,k->new ArrayList<>()).add(price);
            EvidenceLedger.addWebMarketFact(id,"market_comparable",title,80,url);
            SourceClassificationPolicy.importAndMark(id,response,url,"market",80);
        }
        id.comparablesSummary=join(summarize(sold,labels,"vendite concluse"),summarize(active,labels,"annunci attivi"));
        if(!ExactCatalogResolver.marketReady(id)||!id.identityConfirmed||"CONFLICTED".equals(id.identityStatus)||!clean(id.numberConflicts).isEmpty()){id.priceAvailable=false;id.priceConfidence=0;id.marketConfidence=0;id.priceSummary="Comparabili sospesi: identità/SKU non ancora verificato";id.marketStatus="IDENTITY_OR_SKU_PENDING";id.marketDecisionStatus=HierarchicalIdentityStatus.MARKET_UNAVAILABLE.name();return;}
        String best="";int count=0;for(Map.Entry<String,List<Double>>e:sold.entrySet())if(e.getValue().size()>count){best=e.getKey();count=e.getValue().size();}
        if(count>=2){List<Double>v=new ArrayList<>(sold.get(best));Collections.sort(v);double median=v.size()%2==1?v.get(v.size()/2):(v.get(v.size()/2-1)+v.get(v.size()/2))/2d;
            id.priceAvailable=true;id.priceConfidence=Math.min(90,65+count*5);id.marketStatus="AVAILABLE_VERIFIED_SOLD";
            id.marketConfidence=id.priceConfidence;id.marketDecisionStatus="MARKET_AVAILABLE";
            id.priceSummary=labels.get(best)+" · vendite concluse omogenee · mediana "+money(median)+" · range "+money(v.get(0))+"–"+money(v.get(v.size()-1));
        }else{id.priceAvailable=false;id.priceConfidence=0;id.marketConfidence=0;id.marketStatus="INSUFFICIENT_VERIFIED_SOLD";id.marketDecisionStatus=HierarchicalIdentityStatus.MARKET_UNAVAILABLE.name();id.priceSummary="mercato non disponibile/non affidabile";if(id.comparablesSummary.isEmpty())id.comparablesSummary="comparabili non disponibili";}
    }
    private static String summarize(Map<String,List<Double>>g,Map<String,String>labels,String prefix){StringBuilder b=new StringBuilder();for(Map.Entry<String,List<Double>>e:g.entrySet()){List<Double>v=new ArrayList<>(e.getValue());Collections.sort(v);if(b.length()>0)b.append(" | ");b.append(prefix).append(" · ").append(labels.get(e.getKey())).append(" · n=").append(v.size()).append(" · ").append(money(v.get(0))).append("–").append(money(v.get(v.size()-1)));}return b.toString();}
    private static boolean sourcePresent(OpenAiClient.Response r,String url){if(r==null||url.isEmpty())return false;String target=norm(url);for(Models.Source s:r.sources)if(s!=null&&norm(s.url).equals(target))return true;return false;}
    private static String join(String a,String b){if(a.isEmpty())return b;if(b.isEmpty())return a;return a+" | "+b;}
    private static String money(double x){return Math.rint(x)==x?String.valueOf((long)x):String.format(Locale.ROOT,"%.2f",x);}
    private static String norm(String x){String v=clean(x).toLowerCase(Locale.ROOT);while(v.endsWith("/"))v=v.substring(0,v.length()-1);return v;}
    private static String numberFromTitle(String title){Matcher m=NUMBER.matcher(clean(title));return m.find()?clean(m.group(1)):"";}
    private static String canon(String x){return clean(x).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();}
    private static boolean exactOpen(String status){String s=clean(status);return s.equals("NUMBER_UNVERIFIED")||s.equals("NUMBER_CONFLICT")||s.equals("FORMAT_UNRESOLVED")||s.equals("SET_UNRESOLVED")||s.equals("CORE_CONFIRMED_NUMBER_UNRESOLVED");}
    private static String comparableConflict(Models.Identification id,JSONObject c){IdentityProfileEngine.PhotoTuple t=IdentityProfileEngine.tuple(id);
        String[][] fields={{"release_year",first(id.sourceConfirmedReleaseYear,t.year)},{"card_number",first(id.sourceConfirmedCatalogNumber,t.verifiedCardNumber)},
                {"language",t.language},{"format",first(id.sourceConfirmedFormat,t.format)},{"parallel_family",first(id.sourceConfirmedParallelFamily,t.parallelFamily)},
                {"parallel_color",first(id.sourceConfirmedParallelColor,t.color)},{"print_run",first(id.sourceConfirmedPrintRun,t.printRun)},
                {"main_set",first(id.sourceConfirmedMainSet,t.mainSet)},{"insert_subset",first(id.sourceConfirmedSubset,t.insertSubset)},
                {"sub_series",first(id.sourceConfirmedSubSeries,t.subSeries)}};
        for(String[] f:fields){String actual=clean(c.optString(f[0],"")),expected=clean(f[1]);if(!actual.isEmpty()&&!expected.isEmpty()&&!CatalogHierarchy.compatible(actual,expected))return f[0]+"="+actual+" expected="+expected;}
        String line=clean(c.optString("product_line","")),expectedLine=first(id.sourceConfirmedProductLine,t.family);if(!line.isEmpty()&&!expectedLine.isEmpty()&&!CatalogHierarchy.compatible(line,expectedLine))return "product_line="+line+" expected="+expectedLine;return "";}
    private static String first(String...x){for(String v:x)if(!clean(v).isEmpty())return clean(v);return "";}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
