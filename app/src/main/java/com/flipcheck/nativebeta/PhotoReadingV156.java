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
        return "LITERAL-FIRST INVENTORY. Read all supplied views together. Start with complete readable labels and codes, "
                + "then names, set/product line, year, language and distinguishing details. Copy exact text character by character, "
                + "including prefixes, suffixes and fractions; do not replace a printed label with a synonym. "
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
        if(previous==null||!previous.photoReadingOnly||!"PHOTO_READ".equals(previous.identityStatus)
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
        if(!id.photoReadingOnly||"TECHNICAL_FAILURE".equals(id.identityStatus)||"CONFLICTED".equals(id.identityStatus))return;
        Map<String,String> labels=new LinkedHashMap<>();
        labels.put("printedLabel","Testo etichetta");labels.put("cardName","Carta");labels.put("athlete","Soggetto");
        labels.put("brand","Marca");labels.put("manufacturer","Produttore");labels.put("game","Gioco");
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
        StringBuilder summary=new StringBuilder();
        for(Map.Entry<String,String> entry:labels.entrySet()){
            String value=SlabEvidenceV155.value(ledger,entry.getKey());if(value.isEmpty())continue;
            if(summary.length()>0)summary.append('\n');summary.append(entry.getValue()).append(": ").append(value);
        }
        id.photoReadingSummary=summary.toString();if(summary.length()==0)return;
        String name=first(id.model,SlabEvidenceV155.value(ledger,"cardName"),SlabEvidenceV155.value(ledger,"athlete"));
        String set=first(id.family,id.slabSetName);
        String number=first(id.physicalCardNumber,id.slabCardNumber);
        id.title=(name+" "+set+(number.isEmpty()?"":" #"+number)).trim();
        if(id.title.isEmpty())id.title=first(id.brand,"Dati letti dalla foto");
        id.identityConfirmed=false;id.closureResult=false;id.marketReady=false;id.catalogVerified=false;
        id.identityStatus="PHOTO_READ";id.overallStatus="PHOTO_READ";id.decision="PHOTO_READ";
        id.coreIdentityStatus="READ_FROM_PHOTO";id.exactIdentityStatus="TO_VERIFY";
        id.closureBasis="literal_photo_reading";id.closureLevel="OBSERVATION";
        id.webStatus="NOT_RUN";id.marketStatus="NOT_RUN";id.verificationSummary="Dati letti dalla foto. Verifica catalogo non eseguita.";
    }
    private static String first(String... values){for(String value:values)if(value!=null&&!value.isEmpty())return value;return "";}
}
