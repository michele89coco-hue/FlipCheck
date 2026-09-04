package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;

/** Derives independent confidences from evidence quality, coverage and conflict state. */
final class HierarchicalConfidencePolicy {
    private HierarchicalConfidencePolicy() {}

    static void apply(Models.Identification id, IdentityProfileEngine.Assessment a) {
        if (id == null || a == null) return;
        NormalizedPhotoIdentity n = PhotographicFactNormalizer.require(id);
        List<CanonicalFieldKey> core = new ArrayList<>();
        core.add(CanonicalFieldKey.BRAND);
        if (a.profile == IdentityProfileEngine.Profile.SEALED_TRADING_CARD_PRODUCT) {
            core.add(CanonicalFieldKey.PRODUCT_LINE); core.add(CanonicalFieldKey.PHYSICAL_SET_OR_RELEASE_YEAR);
            core.add(CanonicalFieldKey.PRODUCT_TYPE);
        } else if (a.profile == IdentityProfileEngine.Profile.SPORTS_CARD) {
            core.add(CanonicalFieldKey.PRODUCT_LINE); core.add(CanonicalFieldKey.SUBJECT);
            core.add(CanonicalFieldKey.PHYSICAL_SET_OR_RELEASE_YEAR);
        } else if (a.profile == IdentityProfileEngine.Profile.TCG) {
            core.add(CanonicalFieldKey.SUBJECT); core.add(CanonicalFieldKey.LANGUAGE);
            core.add(CanonicalFieldKey.HP_OR_PV); core.add(CanonicalFieldKey.ATTACK_NAME);
            core.add(CanonicalFieldKey.LAYOUT_SIGNATURE);
        } else {
            core.add(CanonicalFieldKey.PRODUCT_LINE); core.add(CanonicalFieldKey.MODEL_CODE);
            core.add(CanonicalFieldKey.DESIGN);
        }
        int present=0,total=0,quality=0,methods=0;
        for (CanonicalFieldKey key : core) {
            NormalizedPhotoIdentity.Fact f = bestAcrossAliases(n,key);
            total++;
            if (f == null) continue;
            present++; quality += adjusted(f); methods |= methodBit(f);
        }
        int coverage = total == 0 ? 0 : present * 100 / total;
        int average = present == 0 ? 0 : quality / present;
        int independence = Integer.bitCount(methods) * 4;
        int main = clamp((average * 3 + coverage * 2) / 5 + independence);
        if (!a.complete) main = Math.min(main, 79);
        if (hasCoreConflict(n)) main = Math.min(main, 59);
        id.mainIdentityConfidence = main;

        int[] votes=profileVotes(id.canonicalProfileVotes);int winner=0,runner=0;for(int v:votes){if(v>winner){runner=winner;winner=v;}else if(v>runner)runner=v;}
        int categoryEvidence=winner==0?average:Math.min(100,55+winner*3);int margin=Math.max(0,winner-runner);
        id.categoryConfidence=clamp((categoryEvidence*70+average*20+Math.min(100,margin*10)*10)/100);
        int familyQuality = mean(adjusted(bestBrand(n)), adjusted(bestFamily(n)));
        id.familyConfidence = clamp(familyQuality + (bestFamily(n) == null ? 0 : 5));

        int exact = main;
        if (!safe(id.numberConflicts).isEmpty()) exact = Math.min(exact, 44);
        else if (!safe(a.tuple.cardNumber).isEmpty() && !a.tuple.cardNumberVerified) exact = Math.min(exact, 74);
        id.modelConfidence = clamp(exact);
        id.coreIdentityConfidence=id.mainIdentityConfidence;id.exactIdentityConfidence=id.modelConfidence;

        List<CanonicalFieldKey> variant = java.util.Arrays.asList(CanonicalFieldKey.FINISH,
                CanonicalFieldKey.PHYSICAL_PARALLEL_CANDIDATE, CanonicalFieldKey.PARALLEL_COLOR,
                CanonicalFieldKey.EDITION, CanonicalFieldKey.PRINTING);
        int variantTotal=0,variantCount=0;
        for(CanonicalFieldKey key:variant){NormalizedPhotoIdentity.Fact f=n.bestFact(key);if(f!=null){variantTotal+=adjusted(f);variantCount++;}}
        id.variantConfidence=variantCount==0?0:clamp(variantTotal/variantCount+(id.rareVariantPhysicalProof?8:0));
        id.marketConfidence=id.priceAvailable?clamp(id.priceConfidence):0;
        if(id.catalogVerified&&id.webContributionScore>0)id.marketConfidence=id.priceAvailable?clamp((id.priceConfidence+id.webContributionScore)/2):0;
        PhotographicFactNormalizer.syncDebug(id,n);
    }

    private static NormalizedPhotoIdentity.Fact bestAcrossAliases(NormalizedPhotoIdentity n,CanonicalFieldKey key){
        if(key==CanonicalFieldKey.BRAND)return bestBrand(n);if(key==CanonicalFieldKey.PRODUCT_LINE)return bestFamily(n);return n.bestFact(key);}
    private static NormalizedPhotoIdentity.Fact bestBrand(NormalizedPhotoIdentity n){return best(n.bestFact(CanonicalFieldKey.MANUFACTURER),n.bestFact(CanonicalFieldKey.PUBLISHER),n.bestFact(CanonicalFieldKey.BRAND));}
    private static NormalizedPhotoIdentity.Fact bestFamily(NormalizedPhotoIdentity n){return best(n.bestFact(CanonicalFieldKey.PRODUCT_LINE),n.bestFact(CanonicalFieldKey.SET),n.bestFact(CanonicalFieldKey.SERIES),n.bestFact(CanonicalFieldKey.PRODUCT_NAME));}
    private static NormalizedPhotoIdentity.Fact best(NormalizedPhotoIdentity.Fact...facts){NormalizedPhotoIdentity.Fact out=null;for(NormalizedPhotoIdentity.Fact f:facts)if(f!=null&&(out==null||adjusted(f)>adjusted(out)))out=f;return out;}
    private static int adjusted(NormalizedPhotoIdentity.Fact f){if(f==null)return 0;double factor=f.quality==NormalizedPhotoIdentity.Quality.DIRECT_PHOTO_OBSERVATION?1d:
            f.quality==NormalizedPhotoIdentity.Quality.VISION_STRUCTURED_SUMMARY?.78d:f.quality==NormalizedPhotoIdentity.Quality.LOCAL_OCR_HINT?.68d:
            f.quality==NormalizedPhotoIdentity.Quality.WEB_CATALOG_EVIDENCE?.86d:f.quality==NormalizedPhotoIdentity.Quality.USER_HINT?.45d:.35d;return (int)Math.round(f.confidence*factor);}
    private static int methodBit(NormalizedPhotoIdentity.Fact f){if(f==null)return 0;if(f.quality==NormalizedPhotoIdentity.Quality.DIRECT_PHOTO_OBSERVATION)return 1;if(f.quality==NormalizedPhotoIdentity.Quality.LOCAL_OCR_HINT)return 2;if(f.quality==NormalizedPhotoIdentity.Quality.WEB_CATALOG_EVIDENCE)return 4;if(f.quality==NormalizedPhotoIdentity.Quality.USER_HINT)return 8;return 16;}
    private static boolean hasCoreConflict(NormalizedPhotoIdentity n){for(String x:n.semanticConflicts)if(x.startsWith("productLine")||x.startsWith("brand")||x.startsWith("subject"))return true;return false;}
    private static int mean(int...v){int sum=0,count=0;for(int x:v)if(x>0){sum+=x;count++;}return count==0?0:sum/count;}
    private static int clamp(int x){return Math.max(0,Math.min(100,x));}
    private static int[] profileVotes(String x){int[] out=new int[4];String[] names={"sports","tcg","sealed","generic"};String s=safe(x);for(int i=0;i<names.length;i++){java.util.regex.Matcher m=java.util.regex.Pattern.compile(names[i]+"=(\\d+)").matcher(s);if(m.find())try{out[i]=Integer.parseInt(m.group(1));}catch(Exception ignored){}}return out;}
    private static String safe(String x){return x==null?"":x.trim();}
}
