package com.flipcheck.nativebeta;

import java.util.Locale;

/** Role- and location-based repairs; never substitutes a known card identity. */
final class ObservationSemanticsV157 {
    private ObservationSemanticsV157() {}
    static String field(String field,String role,String location,DomainProfileRouterV2.Profile profile){
        String context=words(role+" "+location);
        if(profile==DomainProfileRouterV2.Profile.SPORTS_CARD&&field.equals("hp"))return "playerMeasurements";
        if((field.equals("productReleaseYear")||field.equals("statisticsSeason"))
                &&context.contains("copyright")&&!SlabEvidenceV155.labelContext(role,location))return "copyrightYear";
        return field;
    }
    static boolean literalProductLine(String field,String raw,String role,String location){
        if(!field.equals("productLine")||TypedFieldNormalizerV2.ambiguous(raw))return false;
        String r=words(role),l=words(location);
        if(r.matches(".*(?:symbol|guess|infer|resembl|style|family similarity).*"))return false;
        // A located, explicitly named product-line wordmark does not need a
        // second OCR engine to recognize the same stylized lettering.
        return r.matches("(?:printed |literal )?(?:set/)?product line(?: mark)?")
                &&l.matches(".*(?:logo|emblem|wordmark|nameplate|printed title).*")
                &&raw.trim().matches("[\\p{L}0-9][\\p{L}0-9 &'.:/-]{1,70}");
    }
    static String namedManufacturerLogo(String field,String raw,String role,String location){
        if(!field.equals("physicalFeature")||EvidenceProofPolicyV3.legalOwnershipContext(role,location)
                ||SlabEvidenceV155.labelContext(role,location))return "";
        if(!words(role).matches("(?:visible |printed )?(?:manufacturer|brand|publisher) (?:mark|logo|wordmark)"))return "";
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("^([\\p{L}0-9][\\p{L}0-9 &'.-]{1,50}) (?:logo|wordmark)$",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(raw.trim());
        return m.matches()?m.group(1).trim():"";
    }
    private static String words(String s){return s.toLowerCase(Locale.ROOT).replace('_',' ').replaceAll("\\s+"," ").trim();}
}
