package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Technical-failure safety net. Local OCR may vote for a profile and retain
 * literals, but it never fabricates a direct photographic identity binding.
 */
final class LocalEvidenceBootstrap {
    private static final Pattern HP = Pattern.compile("(?i)(?:\\bHP\\b|\\bPV\\b)\\s*[:.-]?\\s*(\\d{1,4})|\\b(\\d{1,4})\\s*(?:HP|PV)\\b");
    private static final Pattern COLLECTOR = Pattern.compile("(?i)\\b([A-Z]{0,4}\\d{1,5}[A-Z]{0,3}/[A-Z]{0,4}\\d{1,5}[A-Z]{0,3})\\b");
    private static final Pattern CONFIG = Pattern.compile("(?i)\\b(?:autograph|memorabilia|auto|relic|packs?|cards?)\\b.*\\b(?:box|pack|per)\\b|\\b(?:box|pack)\\b.*\\b(?:autograph|memorabilia|cards?|packs?)\\b");
    private LocalEvidenceBootstrap() {}

    static void apply(Models.Identification id, Models.LocalScan local) {
        if(id==null||local==null)return;
        int before=id.evidenceLedger.size(),tcg=0,sealed=0,generic=0;
        for(int image=0;image<local.textByImage.size();image++){
            String page=local.textByImage.get(image);if(page==null)continue;
            List<String> lines=new ArrayList<>();
            for(String raw:page.split("[\\r\\n]+")){String line=clean(raw);if(!line.isEmpty())lines.add(line);}
            int hpLine=-1;
            for(int i=0;i<lines.size();i++){
                String line=lines.get(i),location="ocr_line_"+(i+1);Matcher hp=HP.matcher(line);
                if(externalListingText(line)){
                    EvidenceLedger.addLocalOcrFact(id,"visual_description",line,45,image,location,"marketplace_listing_text");
                    continue;
                }
                EvidenceLedger.addLocalOcrFact(id,"visual_description",line,55,image,location,"raw_ocr_text");
                if(hp.find()){
                    String value=clean(hp.group(1)==null?hp.group(2):hp.group(1));
                    EvidenceLedger.addLocalOcrFact(id,"hp",value,76,image,location,"hp_or_pv");
                    hpLine=i;tcg+=3;
                }
                Matcher collector=COLLECTOR.matcher(line);
                while(collector.find()){
                    String value=clean(collector.group(1));
                    boolean alphanumeric=value.matches("(?i).*[A-Z].*");
                    EvidenceLedger.addLocalOcrFact(id,"collector_marking",value,
                            alphanumeric?78:68,image,location,"collector_number_candidate");
                    tcg+=alphanumeric?4:2;
                }
                if(CONFIG.matcher(line).find()){
                    EvidenceLedger.addLocalOcrFact(id,"configuration",line,72,image,location,"sealed_configuration");
                    sealed+=3;
                }
                String lower=line.toLowerCase(Locale.ROOT);
                if(lower.contains("sealed")||lower.contains("hobby box")||lower.contains("blaster box")
                        ||lower.contains("mega box")){sealed+=3;}
                if(lower.contains("model")||lower.contains("p/n")||lower.contains("part no"))generic+=2;
            }
            if(hpLine>=0){
                String subject=nearestName(lines,hpLine);
                if(!subject.isEmpty()){
                    EvidenceLedger.addLocalOcrFact(id,"subject_name",subject,68,image,
                            "ocr_line_"+(lines.indexOf(subject)+1),"card_name_candidate");tcg+=2;
                }
                int attacks=0;
                for(int i=hpLine+1;i<lines.size()&&attacks<4;i++){
                    String line=lines.get(i);
                    if(attackLike(line)){
                        EvidenceLedger.addLocalOcrFact(id,"attack_name",line,62,image,
                                "ocr_line_"+(i+1),"attack_name_candidate");attacks++;tcg++;
                    }
                }
            }
        }
        id.localOcrFactCount=Math.max(0,id.evidenceLedger.size()-before);
        id.canonicalProfileVotes="tcg="+tcg+", sealed="+sealed+", generic="+generic;
        if(tcg>=6){
            if(empty(id.categoryKey)||"other".equals(id.categoryKey))id.categoryKey="tcg";
            if(empty(id.category)||genericCategory(id.category))id.category="Carta TCG";
            id.categoryStatus="PROBABLE_FROM_LOCAL_OCR";
        }else if(sealed>=5){
            if(empty(id.categoryKey)||"other".equals(id.categoryKey))id.categoryKey="sealed_trading_card_product";
            if(empty(id.category)||genericCategory(id.category))id.category="Prodotto sigillato da collezione";
            id.categoryStatus="PROBABLE_FROM_LOCAL_OCR";
        }
    }

    private static String nearestName(List<String> lines,int hpLine){
        for(int i=hpLine;i>=0;i--){String x=lines.get(i);Matcher hp=HP.matcher(x);
            if(hp.find()&&hp.start()>0){String prefix=clean(x.substring(0,hp.start()));if(nameLike(prefix))return prefix;}
            if(i<hpLine&&nameLike(x))return x;}
        return "";
    }
    private static boolean attackLike(String x){String v=clean(x);if(v.length()<3||v.length()>42||v.matches(".*\\d{3,}.*")||COLLECTOR.matcher(v).find())return false;
        int letters=0;for(int i=0;i<v.length();i++)if(Character.isLetter(v.charAt(i)))letters++;
        return letters>=3&&!v.toLowerCase(Locale.ROOT).matches(".*\\b(?:illustrator|copyright|weakness|resistance|ritiro|debolezza|resistenza|stage|fase|phase|livello)\\b.*");}
    private static boolean nameLike(String x){String v=clean(x);if(v.length()<3||v.length()>32||v.matches(".*\\d.*"))return false;
        return v.matches("[\\p{L}][\\p{L} .'-]{2,31}")&&!v.toLowerCase(Locale.ROOT).matches("(?:base|stage|basic|evolves from|livello|carta|pokemon|pokémon|energy|energia)");}
    private static boolean genericCategory(String x){String v=clean(x).toLowerCase(Locale.ROOT);return v.equals("oggetto")||v.equals("object")||v.contains("other collectible");}
    private static boolean externalListingText(String x){String v=clean(x).toLowerCase(Locale.ROOT);return v.matches(".*(?:€|£|\\$|buy it now|venduto|sold|spedizione|shipping|offerta|seller|item number|watchlist|ebay).*" );}
    private static boolean empty(String x){return clean(x).isEmpty();}
    private static String clean(String x){return x==null?"":x.trim().replaceAll("\\s+"," ");}
}
