package com.flipcheck.nativebeta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Literal-first photo reading, adapted from the supplied v0.26 observer. */
final class PhotoReadingV156 {
    private PhotoReadingV156() {}

    static String prompt(){
        return "INDEPENDENT PHOTO READING. Read the pixels before forming an identity. Phone time, battery, network indicators and gallery counters are UI, never object identifiers. LITERAL-FIRST INVENTORY. Read all supplied views together. Start with complete readable labels and codes, "
                + "then names, set/product line, year, language and distinguishing details. Copy exact text character by character, "
                + "including prefixes, suffixes and fractions; do not replace a printed label with a synonym. "
                + "Inspect every supplied face: all four corners, edge text, nameplates and logos before transcribing statistics or biography. "
                + "A clear product-line wordmark is literal text even if local OCR would fail. Use brand for a visible maker name, "
                + "productReleaseYear only for a printed product season, copyrightYear for copyright, and playerMeasurements for athlete height/weight. "
                + "State an uncertain identifier as uncertain; never complete it from a gallery counter or a status-bar number. "
                + "Record a full identification label as printedLabel as well as its separate fields. "
                + "For a slab, inspect its entire label and the enclosed card separately. Split the label's year from slabSetName; "
                + "preserve grader, overall grade, subgrades, certificate, card name, number, set, language and stated finish. "
                + "The certificate is not a card print-run serial. A slab identity is a label reading, not authentication. "
                + "For raw cards inspect card number, serial, edition marks, foil location, HP/PV and attack text; "
                + "for comics title, publisher, issue and indicia; for electronics complete MODEL/P-N/REF and revision. "
                + "For objects without a code preserve rare literal labels and the order and positions of controls. "
                + "Describe unknown logos/symbols as physical features. Keep interpretations in candidates, not literal facts. ";
    }

    static String signature(List<String> images,String details)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        update(digest,details==null?"":details);
        if(images!=null)for(String image:images)update(digest,image==null?"":image);
        StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static void update(MessageDigest digest,String value){
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);
    }

    static OpenAiClient.Response cachedResponse(Models.Identification previous,Models.Usage previousUsage,List<String> images,String details)throws Exception{
        if(previous==null||!previous.photoReadingOnly||!("PHOTO_READ".equals(previous.identityStatus)||"CONFLICTED".equals(previous.identityStatus))
                ||previousUsage==null||previousUsage.requests!=1||previousUsage.webCalls!=0
                ||!signature(images,details).equals(previous.photoInputSignature))return null;
        for(String stage:previous.v2StagePayloads)if(stage.startsWith("v154_vision1_observation\n")){
            OpenAiClient.Response response=new OpenAiClient.Response();
            response.payload=new JSONObject(stage.substring(stage.indexOf('\n')+1));
            if(!response.payload.optBoolean("content_sufficient",false))return null;
            // The full scan's totals include this already-paid first stage once.
            response.usage=previousUsage;return response;
        }
        return null;
    }

    /** Presentation only: no hypothesis or catalog value is promoted to a reading. */
    static void present(Models.Identification id,ImmutableEvidenceLedgerV2 ledger){
        if(!id.photoReadingOnly||"TECHNICAL_FAILURE".equals(id.identityStatus))return;
        Map<String,String> labels=new LinkedHashMap<>();
        labels.put("printedLabel","Testo etichetta");labels.put("cardName","Carta");labels.put("athlete","Soggetto");
        labels.put("team","Squadra");labels.put("productReleaseYear","Stagione stampata");labels.put("copyrightYear","Anno copyright");
        labels.put("playerMeasurements","Dati fisici atleta");labels.put("brand","Marca");labels.put("manufacturer","Produttore");labels.put("game","Gioco");
        labels.put("setName","Set sulla carta");labels.put("productLine","Linea prodotto");labels.put("slabSetName","Set in etichetta");
        labels.put("slabYear","Anno in etichetta");labels.put("slabCardNumber","Numero in etichetta");
        labels.put("collectorNumber","Numero sulla carta");labels.put("physicalCardNumber","Numero sulla carta");
        labels.put("physicalSerial","Seriale copia");labels.put("language","Lingua sulla carta");labels.put("slabLanguage","Lingua in etichetta");
        labels.put("finish","Finitura osservata");labels.put("slabFinish","Finitura in etichetta");labels.put("edition","Edizione");
        labels.put("hp","HP/PV");labels.put("attacks","Attacchi");labels.put("gradingCompany","Grader");
        labels.put("gradingGrade","Voto");labels.put("gradingCondition","Condizione in etichetta");
        labels.put("gradingSubgrades","Sottovoti");labels.put("gradingCertification","Certificato");
        labels.put("model","Modello");labels.put("productCode","Codice prodotto");labels.put("barcode","Barcode");
        labels.put("commercialFormat","Formato");labels.put("configuration","Configurazione");
        labels.put("controlLabel","Scritte sui comandi");labels.put("physicalFeature","Dettagli visibili");
        java.util.Set<String> conflictedFields=new java.util.LinkedHashSet<>();
        StringBuilder conflictText=new StringBuilder();
        for(ConflictResolverV2.Conflict conflict:ConflictResolverV2.resolve(ledger,
                DomainProfileRouterV2.route(id.v2Profile,ledger))){
            conflictedFields.add(conflict.field);
            if(conflictText.length()>0)conflictText.append('\n');
            conflictText.append(labels.containsKey(conflict.field)?labels.get(conflict.field):conflict.field)
                    .append(": ").append(conflict.valueA).append(" / ").append(conflict.valueB);
        }
        id.photoReadingConflicts=conflictText.toString();
        StringBuilder summary=new StringBuilder();
        for(Map.Entry<String,String> entry:labels.entrySet()){
            String field=entry.getKey();if(conflictedFields.contains(field))continue;
            java.util.Set<String> values=new java.util.LinkedHashSet<>();
            for(EvidenceAtom atom:ledger.current(field)){
                if(atom.epistemicLevel!=EvidenceAtom.EpistemicLevel.OBSERVED||!atom.localized()||!atom.reliable()
                        ||atom.modality==EvidenceAtom.Modality.LOCAL_OCR||"UI_OVERLAY".equals(atom.semanticScope))continue;
                String label=entry.getValue();
                if(field.equals("physicalCardNumber")||field.equals("collectorNumber")){
                    // The photo reader must not silently undo the reducer's
                    // uncertain-number handling, as build156 did for 46/74.
                    if(atom.confidence<90)label="Numero da rileggere";
                    else if(id.physicalCardNumber.isEmpty())label="Numero letto (da verificare)";
                }
                String line=label+": "+atom.normalizedValue;
                if(!values.add(line))continue;
                if(summary.length()>0)summary.append('\n');summary.append(line);
            }
        }
        id.photoReadingSummary=summary.toString();if(summary.length()==0)return;
        String name=stable(ledger,conflictedFields,"cardName","athlete","model");
        String set=stable(ledger,conflictedFields,"setName","productLine","slabSetName");
        String brand=stable(ledger,conflictedFields,"brand","manufacturer","game");
        String year=stable(ledger,conflictedFields,"productReleaseYear","slabYear");
        String number=stable(ledger,conflictedFields,"physicalCardNumber","collectorNumber","slabCardNumber");
        EvidenceAtom n=SlabEvidenceV155.observed(ledger,"physicalCardNumber");
        if(n==null)n=SlabEvidenceV155.observed(ledger,"collectorNumber");
        if(n!=null&&n.confidence<90)number="";
        id.title=(brand+" "+year+" "+set+" "+name+(number.isEmpty()?"":" #"+number)).trim().replaceAll("\\s+"," ");
        if(id.title.isEmpty())id.title="Dati letti dalla foto";
        id.identityConfirmed=false;id.closureResult=false;id.marketReady=false;id.catalogVerified=false;
        boolean conflict="CONFLICTED".equals(id.identityStatus);
        id.identityStatus=conflict?"CONFLICTED":"PHOTO_READ";id.overallStatus=id.identityStatus;id.decision=id.identityStatus;
        id.coreIdentityStatus=conflict?"CONFLICTED":"READ_FROM_PHOTO";id.exactIdentityStatus="TO_VERIFY";
        if(empty(id.edition)&&DomainProfileRouterV2.cards(DomainProfileRouterV2.route(id.v2Profile,ledger)))id.variantStatus="TO_VERIFY";
        id.closureBasis="literal_photo_reading";id.closureLevel="OBSERVATION";
        id.nextPhotoRequest="";id.nextPhotoReason="";id.requestedPhotoReason="";
        id.webStatus="NOT_RUN";id.marketStatus="NOT_RUN";id.verificationSummary="Dati letti dalla foto. Verifica catalogo non eseguita.";
    }
    static boolean hasReading(Models.Identification id){return id!=null&&id.photoReadingOnly&&id.photoReadingSummary!=null&&!id.photoReadingSummary.isEmpty();}
    private static String stable(ImmutableEvidenceLedgerV2 ledger,java.util.Set<String> conflicts,String... fields){
        for(String field:fields)if(!conflicts.contains(field)){
            EvidenceAtom atom=SlabEvidenceV155.observed(ledger,field);
            if(atom!=null&&atom.modality!=EvidenceAtom.Modality.LOCAL_OCR)return atom.normalizedValue;
        }
        return "";
    }
    private static boolean empty(String s){return s==null||s.isEmpty();}
    private static String first(String... values){for(String value:values)if(value!=null&&!value.isEmpty())return value;return "";}
}
