package com.flipcheck.nativebeta;

import java.util.Locale;

/** A photographed grading label describes the enclosed card; it is not card-surface print. */
final class SlabEvidenceV155 {
    private SlabEvidenceV155() {}
    static boolean labelContext(String role,String location){
        String s=(role+" "+location).toLowerCase(Locale.ROOT).replace('_',' ');
        return s.contains("grading label")||s.contains("slab label")||s.contains("grading company")
                ||s.contains("grading serial")||s.contains("slab serial")||s.contains("grader");
    }
    static String field(String original,String role,String location){
        if(!labelContext(role,location))return original;
        String context=(role+" "+location).toLowerCase(Locale.ROOT).replace('_',' ');
        if(original.equals("brand")||original.equals("manufacturer"))return "gradingCompany";
        if(original.equals("physicalSerial"))return "gradingCertification";
        if(original.equals("physicalCardNumber")||original.equals("collectorNumber"))return "slabCardNumber";
        if(original.equals("setName")||original.equals("productLine"))return "slabSetName";
        if(original.equals("productReleaseYear"))return "slabYear";
        if(original.equals("language"))return "slabLanguage";
        if(original.equals("edition"))return "slabEdition";
        if(original.equals("finish"))return "slabFinish";
        if(original.equals("condition"))return "gradingCondition";
        if(context.contains("subgrade"))return "gradingSubgrades";
        if(context.contains("overall grade"))return "gradingGrade";
        return original;
    }
    static void normalize(ImmutableEvidenceLedgerV2 ledger){
        for(EvidenceAtom a:new java.util.ArrayList<>(ledger.all())){
            if(!a.parentEvidenceId.isEmpty()||a.epistemicLevel!=EvidenceAtom.EpistemicLevel.OBSERVED)continue;
            // A grading label commonly prints YEAR + SET in one line. Preserve
            // that literal parent but compare the separately typed set and year.
            // Do not remove numbers elsewhere in a set name or in arbitrary text.
            if(a.field.equals("slabSetName")){
                java.util.regex.Matcher m=java.util.regex.Pattern.compile("^((?:19|20)[0-9]{2}(?:[-/][0-9]{2}(?:[0-9]{2})?)?)\\s+([\\p{L}].*)$").matcher(a.rawValue.trim());
                if(m.matches()){
                    ledger.appendNormalization(a,SeasonNormalizer.normalize(m.group(1)),"slabYear","grading label year");
                    ledger.appendNormalization(a,m.group(2).trim(),"slabSetName","grading label set");
                }
            }
            if(a.field.equals("gradingGrade")){
                java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(10|[1-9](?:\\.[05])?)(?:\\s+(.*))?$").matcher(a.rawValue.trim());
                if(m.matches()){
                    ledger.appendNormalization(a,m.group(1),"gradingGrade","grading grade");
                    if(m.group(2)!=null)ledger.appendNormalization(a,m.group(2),"gradingCondition","grading condition");
                }
            }
            // These readings were transported as generic text in build154.
            if(a.field.equals("statisticsSeason")&&labelContext(a.semanticScope,a.boundingBox)
                    &&a.rawValue.toLowerCase(Locale.ROOT).matches(".*(?:centering|corners|surface|edges).*"))
                ledger.appendNormalization(a,a.rawValue,"gradingSubgrades","grading subgrades");
            if(a.field.equals("printedLabel")&&a.semanticScope.equals("SET_AND_YEAR_LABEL")&&labelContext(a.semanticScope,a.boundingBox)){
                java.util.regex.Matcher m=java.util.regex.Pattern.compile("^([12][0-9]{3})\\s+([\\p{L}][\\p{L} -]+)$").matcher(a.rawValue.trim());
                if(m.matches()){
                    ledger.appendNormalization(a,m.group(1),"slabYear","grading label year");
                    ledger.appendNormalization(a,m.group(2).trim(),"slabSetName","grading label set");
                }
            }
        }
    }
    static EvidenceAtom observed(ImmutableEvidenceLedgerV2 ledger,String field){
        EvidenceAtom a=ledger.strongest(field,EvidenceAtom.EpistemicLevel.OBSERVED);
        return a!=null&&a.localized()&&a.reliable()?a:null;
    }
    static String value(ImmutableEvidenceLedgerV2 ledger,String field){EvidenceAtom a=observed(ledger,field);return a==null?"":a.normalizedValue;}
    static String fallbackField(String candidateField){
        if(candidateField.equals("catalogCardNumber"))return "slabCardNumber";
        if(candidateField.equals("setName"))return "slabSetName";
        if(candidateField.equals("productReleaseYear"))return "slabYear";
        if(candidateField.equals("language"))return "slabLanguage";
        if(candidateField.equals("edition"))return "slabEdition";
        if(candidateField.equals("finish"))return "slabFinish";
        return "";
    }
    /** A numerator-only label can support a full TCG number only with the same set and name. */
    static SemanticRelationV3.Relation numberRelation(EvidenceAtom observed,IdentityCandidateV2 c,ImmutableEvidenceLedgerV2 ledger){
        String a=TypedFieldNormalizerV2.normalizeValue("collectorNumber",observed.normalizedValue,"");
        String b=TypedFieldNormalizerV2.normalizeValue("collectorNumber",c.value("catalogCardNumber"),"");
        if(!a.matches("[A-Z]*[0-9]+[A-Z]?")||!b.matches("[A-Z]*[0-9]+[A-Z]?/[0-9]+")||!b.substring(0,b.indexOf('/')).equals(a))
            return SemanticRelationV3.relate("collectorNumber",a,b);
        String set=value(ledger,"setName");if(set.isEmpty())set=value(ledger,"slabSetName");
        String name=value(ledger,"cardName");
        if(!set.isEmpty()&&!name.isEmpty()
                &&SemanticRelationV3.compatible(SemanticRelationV3.relate("setName",set,c.value("setName")))
                &&TypedFieldNormalizerV2.equivalent("cardName",name,c.value("cardName")))
            return SemanticRelationV3.Relation.PARENT;
        return SemanticRelationV3.Relation.AMBIGUOUS;
    }
}
