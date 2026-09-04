package com.flipcheck.nativebeta;

/** Evidence-derived profile vote; diagnostic and decision consume the same result. */
final class CanonicalProfileVoting {
    private CanonicalProfileVoting() {}
    static void apply(Models.Identification id,NormalizedPhotoIdentity n){if(id==null||n==null)return;
        int sports=0,tcg=0,sealed=0,generic=0;
        String hint=(n.categoryHint+" "+n.best(CanonicalFieldKey.PRODUCT_TYPE)).toLowerCase(java.util.Locale.ROOT);
        if(any(hint,"sports_card","sports card","sport trading"))sports+=8;
        if(any(hint,"tcg","trading card game","collectible card game"))tcg+=8;
        if(any(hint,"sealed","box","pack","confezione"))sealed+=8;
        if(any(hint,"other_collectible","generic","device","controller","appliance","remote"))generic+=6;
        if(!n.best(CanonicalFieldKey.TEAM).isEmpty())sports+=4;if(!n.best(CanonicalFieldKey.SPORT).isEmpty())sports+=3;
        if(!n.best(CanonicalFieldKey.STATISTICS).isEmpty()||!n.best(CanonicalFieldKey.GRAPHIC_NUMBER).isEmpty())sports+=2;
        if(!n.best(CanonicalFieldKey.HP_OR_PV).isEmpty())tcg+=4;if(!n.values(CanonicalFieldKey.ATTACK_NAME).isEmpty())tcg+=5;
        if(!n.best(CanonicalFieldKey.EVOLUTION_STAGE).isEmpty())tcg+=3;
        if(!n.best(CanonicalFieldKey.CONFIGURATION).isEmpty())sealed+=5;if(!n.best(CanonicalFieldKey.FORMAT).isEmpty())sealed+=4;
        if(!n.values(CanonicalFieldKey.FEATURED_SUBJECT).isEmpty())sealed+=2;
        if(!n.best(CanonicalFieldKey.MODEL_CODE).isEmpty()||!n.best(CanonicalFieldKey.BARCODE).isEmpty())generic+=4;
        int max=Math.max(Math.max(sports,tcg),Math.max(sealed,generic));String selected=max<=0?"other_collectible":max==sealed?"sealed_trading_card_product":max==sports?"sports_card":max==tcg?"tcg":"other_collectible";
        n.profile=selected;id.canonicalProfile=selected;id.queryProfile=selected;
        id.canonicalProfileVotes="sports="+sports+", tcg="+tcg+", sealed="+sealed+", generic="+generic+", selected="+selected;
    }
    private static boolean any(String x,String...v){for(String s:v)if(x.contains(s))return true;return false;}
}
