package com.flipcheck.nativebeta;

import java.lang.reflect.Method;
import org.json.JSONObject;

/** General card-resolution regressions derived from real TCG and sports scans. */
public final class V103UniversalCardResolutionRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        preferTcgCollectorFraction();
        mergeEquivalentCatalogHits();
        exposeSpecificProbableWithoutFalseConfirmation();
        boundSecondVisualVerification();
        System.out.println("V103UniversalCardResolutionRegressionTest: PASS");
    }

    private static void preferTcgCollectorFraction() {
        Models.Identification id = new Models.Identification();
        id.category = "Pokémon trading card";
        id.categoryKey = "pokemon_tcg_card";
        id.brand = "Pokémon";
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityFields.add("manufacturer=Pokémon");
        id.photoIdentityFields.add("set=Jungle");
        id.photoIdentityFields.add("subject=Vileplume");
        id.photoIdentityFields.add("card_number=45"); // Pokedex number, not catalog identity.
        id.photoIdentityFields.add("holo=holographic");
        id.visualFacts.add("collector_number=15/64");
        id.visualFacts.add("first_edition_stamp=present");
        id.localScan = new Models.LocalScan();
        id.localScan.textByImage.add("VILEPLUME 80 HP\n15/64\nLV. 35 #45");
        CollectibleCardIdentityPolicy.sanitizeObservation(id, id.localScan);
        require("15/64".equals(CollectibleCardIdentityPolicy.observedCardNumber(id, id.localScan)),
                "TCG checklist fraction must outrank a Pokedex/narrative number");
        require(id.photoIdentityFields.contains("card_number=15/64")
                        && id.photoIdentityFields.stream().noneMatch(x -> x.equals("card_number=45")),
                "collector fraction must survive sanitation and replace the narrative number");
    }

    private static void mergeEquivalentCatalogHits() throws Exception {
        Models.Identification id = new Models.Identification();
        Models.CandidateScore a = candidate("Pokémon", "Jungle", "15/64",
                "Pokémon Jungle Vileplume 15/64 1st Edition Holo", 96);
        Models.CandidateScore b = candidate("Pokemon", "Jungle", "15/64",
                "Pokemon Jungle Vileplume 15/64 1st Edition Holo", 94);
        id.candidates.add(a);
        id.candidates.add(b);
        Method merge = IdentificationPipelineV082.class
                .getDeclaredMethod("mergeEquivalentCandidates", Models.Identification.class);
        merge.setAccessible(true);
        merge.invoke(null, id);
        require(id.candidates.size() == 1 && id.candidates.get(0).totalScore == 96,
                "duplicate spellings of one catalog identity must not create margin zero");
    }

    private static void exposeSpecificProbableWithoutFalseConfirmation() {
        Models.Identification id = new Models.Identification();
        id.category = "sports collectible card";
        id.categoryKey = "sports_collectible_card";
        id.photoIdentityFields.add("player=Luka Doncic");
        id.photoIdentityFields.add("card_number=280");
        Models.CandidateScore c = candidate("Panini", "2018-19 Panini Prizm Basketball", "",
                "2018-19 Panini Prizm Luka Doncic Green Prizm #280 RC", 82);
        CollectibleCardIdentityPolicy.exposeBestSpecificProbable(id, c);
        require(!id.marketReady && id.model.contains("Luka Doncic")
                        && id.model.contains("Green Prizm") && id.model.contains("#280"),
                "a card near miss must show the specific best result without claiming confirmation");
    }

    private static void boundSecondVisualVerification() throws Exception {
        Models.Identification id = completeVileplume();
        Models.CandidateScore c = candidate("Pokémon", "Jungle", "Vileplume 15/64",
                "Pokémon Jungle Vileplume 15/64 1st Edition Holo", 96);
        id.candidates.add(c);
        Models.Usage allowed = new Models.Usage();
        allowed.requests = 1;
        allowed.webCalls = 1;
        allowed.costUsd = 0.012;
        require(BorderlineIdentityAdjudicator.eligible(id, allowed),
                "a complete, grounded card near miss should receive one visual verification");
        Models.Usage expensive = new Models.Usage();
        expensive.requests = 1;
        expensive.webCalls = 1;
        expensive.costUsd = 0.018;
        require(!BorderlineIdentityAdjudicator.eligible(id, expensive),
                "the extra visual verification must remain below the two-cent scan cap");

        OpenAiClient.Response response = new OpenAiClient.Response();
        response.payload = new JSONObject().put("supported", true)
                .put("same_entity", true).put("contradiction", false)
                .put("identity_confidence", 97)
                .put("normalized_identity",
                        "Pokémon Jungle Vileplume 15/64 1st Edition Holo")
                .put("reason", "collector number, subject, set, edition and finish agree");
        require(BorderlineIdentityAdjudicator.apply(id, response) && id.marketReady,
                "successful visual adjudication must close a coherent exact card reference");
    }

    private static Models.Identification completeVileplume() {
        Models.Identification id = new Models.Identification();
        id.category = "Pokémon trading card";
        id.categoryKey = "pokemon_tcg_card";
        id.brand = "Pokémon";
        id.photoViews.add("front");
        id.photoIdentityComplete = true;
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityConfidence = 97;
        id.photoIdentityKind = "composite_markings";
        id.photoIdentityName = "Pokémon Jungle Vileplume 15/64 1st Edition Holo";
        id.photoIdentityFields.add("manufacturer=Pokémon");
        id.photoIdentityFields.add("set=Jungle");
        id.photoIdentityFields.add("subject=Vileplume");
        id.photoIdentityFields.add("card_number=15/64");
        id.photoIdentityFields.add("holo=holographic");
        id.photoIdentityFields.add("edition=1st Edition");
        return id;
    }

    private static Models.CandidateScore candidate(String brand, String family,
                                                    String model, String probable, int score) {
        Models.CandidateScore c = new Models.CandidateScore();
        c.brand = brand;
        c.family = family;
        c.model = model;
        c.probableReference = probable;
        c.probableReferenceConfidence = 98;
        c.totalScore = score;
        c.textScore = 99;
        c.webScore = 95;
        c.candidateFacts.add("source_grounded=true");
        c.candidateFacts.add("same_entity_role=true");
        c.candidateFacts.add("relationship_only=false");
        c.candidateFacts.add("disproof_passed=true");
        c.candidateFacts.add("source_exact_reference=true");
        c.candidateFacts.add("exact_reference_complete=true");
        c.candidateFacts.add("source_identity_confidence=98");
        return c;
    }
}
