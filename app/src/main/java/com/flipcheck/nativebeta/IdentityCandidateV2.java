package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Candidate at one explicit identity level. */
final class IdentityCandidateV2 {
    final String candidateId;
    final DomainProfileRouterV2.Profile domain;
    final String source;
    final Map<String,String> fields=new LinkedHashMap<>();
    final List<String> matchedEvidence=new ArrayList<>();
    final List<String> reportedMatchedFields=new ArrayList<>();
    final List<String> reportedContradictedFields=new ArrayList<>();
    final List<String> unmatchedEvidence=new ArrayList<>();
    final List<String> trueConflicts=new ArrayList<>();
    final List<String> unknownFields=new ArrayList<>();
    final Map<String,SemanticRelationV3.Relation> fieldRelations=new LinkedHashMap<>();
    int observedIdentifierMatch,observedTextMatch,logoMatch,layoutMatch,catalogMatch,
            webSourceQuality,configurationMatch,contradictionPenalty,inferenceOnlyPenalty,totalScore;
    boolean retrieved,reportedDisproofPassed,disproofPassed,rejected,exactReference;
    String sourceId="",sourceUrl="",sourceTitle="",sourceAuthority="",sourceRecordId="",sourcePageScope="",identityLevel="CORE_IDENTITY",status="AMBIGUOUS",disproofResult="NOT_EXECUTED",disproofReason="",rejectionReason="";
    IdentityCandidateV2(String id,DomainProfileRouterV2.Profile domain,String source){this.candidateId=id;this.domain=domain;this.source=source;}
    String value(String field){String v=fields.get(field);return v==null?"":v;}
    String display(){return join(value("productReleaseYear"),value("manufacturer"),value("productLine"),value("setName"),value("subSeries"),value("cardName"),value("athlete"),value("model"),number());}
    String number(){String x=value("collectorNumber");if(x.isEmpty())x=value("physicalCardNumber");if(x.isEmpty())x=value("catalogCardNumber");return x.isEmpty()?"":"#"+x;}
    private static String join(String...values){StringBuilder b=new StringBuilder();for(String v:values)if(v!=null&&!v.trim().isEmpty()){String x=v.trim();if(b.toString().toLowerCase().contains(x.toLowerCase()))continue;if(b.length()>0)b.append(' ');b.append(x);}return b.toString();}
}
