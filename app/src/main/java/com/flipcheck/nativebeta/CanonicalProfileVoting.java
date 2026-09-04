package com.flipcheck.nativebeta;

/** Evidence-derived profile vote; diagnostic and decision consume the same result. */
final class CanonicalProfileVoting {
    private CanonicalProfileVoting() {}
    static void apply(Models.Identification id,NormalizedPhotoIdentity n){if(id==null||n==null)return;
        int sports=0,tcg=0,sealed=0,electronics=0,remote=0,generic=0;
        String hint=(n.categoryHint+" "+n.best(CanonicalFieldKey.PRODUCT_TYPE)).toLowerCase(java.util.Locale.ROOT);
        if(any(hint,"sports_card","sports card","sport trading"))sports+=8;
        if(any(hint,"tcg","trading card game","collectible card game"))tcg+=8;
        if(any(hint,"sealed","box","pack","confezione"))sealed+=8;
        if(any(hint,"other_collectible","generic"))generic+=4;
        if(any(hint,"consumer_electronics","electronics","device","controller","appliance"))electronics+=7;
        if(any(hint,"television_remote_control","tv remote","remote control","remote"))remote+=10;
        if(!n.best(CanonicalFieldKey.TEAM).isEmpty())sports+=4;if(!n.best(CanonicalFieldKey.SPORT).isEmpty())sports+=3;
        if(!n.best(CanonicalFieldKey.STATISTICS).isEmpty()||!n.best(CanonicalFieldKey.GRAPHIC_NUMBER).isEmpty())sports+=2;
        if(!n.best(CanonicalFieldKey.HP_OR_PV).isEmpty())tcg+=4;if(!n.values(CanonicalFieldKey.ATTACK_NAME).isEmpty())tcg+=5;
        if(!n.best(CanonicalFieldKey.EVOLUTION_STAGE).isEmpty())tcg+=3;
        if(!n.best(CanonicalFieldKey.CONFIGURATION).isEmpty())sealed+=5;if(!n.best(CanonicalFieldKey.FORMAT).isEmpty())sealed+=4;
        if(!n.values(CanonicalFieldKey.FEATURED_SUBJECT).isEmpty())sealed+=2;
        if(!n.best(CanonicalFieldKey.MODEL_CODE).isEmpty()||!n.best(CanonicalFieldKey.BARCODE).isEmpty())generic+=4;
        if(!n.best(CanonicalFieldKey.CONTROL_LAYOUT).isEmpty()||!n.best(CanonicalFieldKey.SHORTCUT_BUTTONS).isEmpty()
                ||!n.best(CanonicalFieldKey.NAVIGATION_LAYOUT).isEmpty()||!n.best(CanonicalFieldKey.NUMERIC_KEYPAD).isEmpty())remote+=8;
        int max=Math.max(Math.max(Math.max(sports,tcg),Math.max(sealed,generic)),Math.max(electronics,remote));String selected=max<=0?"other_collectible":max==sealed?"sealed_trading_card_product":max==sports?"sports_card":max==tcg?"tcg":max==remote?"television_remote_control":max==electronics?"consumer_electronics":"other_collectible";
        n.profile=selected;id.canonicalProfile=selected;id.queryProfile=selected;
        id.canonicalProfileVotes="sports="+sports+", tcg="+tcg+", sealed="+sealed+", electronics="+electronics+", remote="+remote+", generic="+generic+", selected="+selected;
    }
    private static boolean any(String x,String...v){for(String s:v)if(x.contains(s))return true;return false;}
}
