package com.flipcheck.nativebeta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class CardPhotoTupleClosureSportCardTest {

    @Test
    public void tcgFrontSideWithCompletePhysicalTupleShouldBeVerified() {
        Models.Identification id = createBaseTcgIdentity();
        id.photoIdentityFields.add("set=Pokemon Jungle");
        id.photoIdentityFields.add("subject=Vileplume");
        id.photoIdentityFields.add("card_number=15/64");
        id.photoIdentityFields.add("variant=Holo");
        id.photoIdentityFields.add("edition=1st Edition");
        id.photoIdentityFields.add("finish=shadowed");
        id.photoIdentityFields.add("language=English");
        id.photoIdentityFields.add("serial=15/64");
        id.photoIdentityFields.add("serial_binding=physical_card_surface");
        id.photoViews.clear();
        id.photoViews.add("front");

        assertTrue(CardPhotoTupleClosure.canClose(id));
        assertTrue(CardPhotoTupleClosure.apply(id));
        ConfirmationIntegrityPolicy.enforce(id);

        assertTrue(id.marketReady);
        assertEquals("physical_card_tuple", id.modelProof);
        assertEquals("CONFIRMED · IDENTITÀ VERIFICATA",
                EvidencePolicy.publicStatus(id));
        assertEquals("Pokemon Jungle Vileplume", id.family);
    }

    @Test
    public void sportsFrontBackWithCompleteTupleShouldBeVerified() {
        Models.Identification id = createBaseSportsIdentity();
        id.photoIdentityFields.add("set=Select Road to FIFA World Cup 2026");
        id.photoIdentityFields.add("season=2025-26");
        id.photoIdentityFields.add("player=Victor");
        id.photoIdentityFields.add("team=Nigeria");
        id.photoIdentityFields.add("card_number=21");
        id.photoIdentityFields.add("insert_or_base=Base Terrace");
        id.photoIdentityFields.add("tier=Terrace");
        id.photoIdentityFields.add("finish=Prizm");
        id.photoIdentityFields.add("serial=2/5");
        id.photoIdentityFields.add("serial_binding=physical_card_surface");

        assertTrue(CardPhotoTupleClosure.canClose(id));
        assertTrue(CardPhotoTupleClosure.apply(id));
        ConfirmationIntegrityPolicy.enforce(id);

        assertTrue(id.marketReady);
        assertEquals("physical_card_tuple", id.modelProof);
        assertEquals("CONFIRMED · IDENTITÀ VERIFICATA",
                EvidencePolicy.publicStatus(id));
        assertTrue(id.model.contains("#21"));
        assertTrue(id.candidates.stream().anyMatch(candidate ->
                candidate.candidateFacts.contains("physical_tuple_serial=2/5")));
    }

    @Test
    public void kobeCaseFrontAndBackWithCompleteTupleShouldClose() {
        Models.Identification id = createBaseSportsIdentity();
        id.photoViews.clear();
        id.photoViews.add("front");
        id.photoViews.add("back");
        id.photoIdentityFields.add("set=1997-98 Metal Universe");
        id.photoIdentityFields.add("season=1997-98");
        id.photoIdentityFields.add("player=Kobe Bryant");
        id.photoIdentityFields.add("team=Los Angeles Lakers");
        id.photoIdentityFields.add("card_number=81");
        id.photoIdentityFields.add("parallel=Holo");
        id.photoIdentityFields.add("variant=Shadowless");

        assertTrue(CardPhotoTupleClosure.canClose(id));
        assertTrue(CardPhotoTupleClosure.apply(id));
        ConfirmationIntegrityPolicy.enforce(id);

        assertTrue(id.marketReady);
        assertEquals("physical_card_tuple", id.modelProof);
        assertEquals("CONFIRMED · IDENTITÀ VERIFICATA",
                EvidencePolicy.publicStatus(id));
    }

    @Test
    public void curry77MustRemainPhysicalAndIgnoreChecklistConflict() {
        Models.Identification id = createBaseSportsIdentity();
        id.photoIdentityFields.add("player=Stephen Curry");
        id.photoIdentityFields.add("team=Golden State Warriors");
        id.photoIdentityFields.add("set=Adrenalyn XL");
        id.photoIdentityFields.add("season=2009-10");
        id.photoIdentityFields.add("card_number=2");
        id.photoIdentityFields.add("physical_card_number_marking=77");
        id.photoIdentityFields.add("position=GUARD");
        id.photoIdentityFields.add("def=72");
        id.photoIdentityFields.add("off=90");

        Models.CandidateScore conflicting = new Models.CandidateScore();
        conflicting.candidateFacts.add("catalog_card_number=67");
        conflicting.candidateFacts.add("catalog_card_number=89");
        conflicting.candidateFacts.add("source_discrepancy=true");
        conflicting.hardViolations.add("web_checklist_disagreement");
        id.candidates.add(conflicting);

        assertTrue(CardPhotoTupleClosure.canClose(id));
        assertTrue(CardPhotoTupleClosure.apply(id));
        ConfirmationIntegrityPolicy.enforce(id);

        assertTrue(id.marketReady);
        assertEquals("CONFIRMED · IDENTITÀ VERIFICATA",
                EvidencePolicy.publicStatus(id));
        assertEquals("#77", id.model.replaceAll(".*(#[0-9/]+).*", "$1"));
    }

    @Test
    public void exactCatalogProofMustNotReplacePhysicalCardTuple77() {
        Models.Identification id = createBaseSportsIdentity();
        id.photoIdentityFields.add("player=Stephen Curry");
        id.photoIdentityFields.add("team=Golden State Warriors");
        id.photoIdentityFields.add("set=Adrenalyn XL");
        id.photoIdentityFields.add("season=2009-10");
        id.photoIdentityFields.add("physical_card_number_marking=77");
        id.photoIdentityFields.add("position=GUARD");
        id.photoIdentityFields.add("def=72");
        id.photoIdentityFields.add("off=90");

        assertTrue(CardPhotoTupleClosure.apply(id));
        assertEquals("physical_card_tuple", id.modelProof);
        assertEquals("#77", id.model.replaceAll(".*(#[0-9/]+).*", "$1"));

        OpenAiClient.Response response = new OpenAiClient.Response();
        response.complete = true;
        response.parseError = "";
        JSONObject payload = new JSONObject();
        payload.put("supported", true);
        payload.put("same_physical_card", true);
        payload.put("disproof_passed", true);
        payload.put("exact_reference_complete", true);
        payload.put("identity_confidence", 98);
        payload.put("manufacturer", "Panini");
        payload.put("set_or_series", "Adrenalyn XL");
        payload.put("subject", "Stephen Curry");
        payload.put("normalized_identity", "Panini Adrenalyn XL Stephen Curry #2");
        payload.put("visual_reference_checked", true);
        payload.put("visual_match_confidence", 99);
        payload.put("source_url", "https://example.com/curry");
        payload.put("card_number", "2");
        payload.put("same_entity_role", true);
        payload.put("relationship_only", false);
        payload.put("evidence", "mocked");
        payload.put("exact_composite_tuple_match", false);
        payload.put("matched_physical_fields", new JSONArray().put("manufacturer").put("subject")
                .put("set_or_series").put("card_number"));
        response.payload = payload;
        Models.Source source = new Models.Source();
        source.url = "https://example.com/curry";
        response.sources.add(source);

        boolean recovered = ExactCardCatalogRecovery.apply(id, response);
        assertTrue(recovered);
        assertEquals("physical_card_tuple", id.modelProof);
        assertEquals("#77", id.model.replaceAll(".*(#[0-9/]+).*", "$1"));
        assertTrue(id.marketReady);
    }

    @Test
    public void sportsCardWithPhysicalNumberMarkingShouldIgnoreChecklistConflict() {
        Models.Identification id = createBaseSportsIdentity();
        id.photoIdentityFields.add("player=Generic Athlete");
        id.photoIdentityFields.add("team=Generic Team");
        id.photoIdentityFields.add("set=Upper Deck");
        id.photoIdentityFields.add("season=2003");
        id.photoIdentityFields.add("card_number=99");
        id.photoIdentityFields.add("physical_card_number_marking=15/64");
        id.photoIdentityFields.add("parallel=Holo");

        Models.CandidateScore conflicting = new Models.CandidateScore();
        conflicting.candidateFacts.add("catalog_card_number=14");
        conflicting.candidateFacts.add("catalog_card_number=16");
        conflicting.hardViolations.add("web_checklist_disagreement");
        id.candidates.add(conflicting);

        assertTrue(CardPhotoTupleClosure.canClose(id));
        assertTrue(CardPhotoTupleClosure.apply(id));
        ConfirmationIntegrityPolicy.enforce(id);

        assertEquals("CONFIRMED · IDENTITÀ VERIFICATA",
                EvidencePolicy.publicStatus(id));
        assertEquals("#15/64", id.model.replaceAll(".*(#[0-9/]+).*", "$1"));
    }

    @Test
    public void physicalSerialMarkingShouldBeKeptInPhysicalTupleFacts() {
        Models.Identification id = createBaseSportsIdentity();
        id.photoIdentityFields.add("player=Generic Player");
        id.photoIdentityFields.add("team=Some Team");
        id.photoIdentityFields.add("set=Panini Prizm");
        id.photoIdentityFields.add("season=2024");
        id.photoIdentityFields.add("card_number=45");
        id.photoIdentityFields.add("physical_card_number_marking=45");
        id.photoIdentityFields.add("physical_serial_marking=2/5");
        id.photoIdentityFields.add("position=FORWARD");

        assertTrue(CardPhotoTupleClosure.canClose(id));
        assertTrue(CardPhotoTupleClosure.apply(id));
        ConfirmationIntegrityPolicy.enforce(id);

        assertEquals("#45", id.model.replaceAll(".*(#[0-9]+).*", "$1"));
        assertTrue(id.candidates.stream().anyMatch(candidate ->
                candidate.candidateFacts.contains("physical_tuple_serial=2/5")));
    }

    @Test
    public void exactParallelUnresolvedMustNotForceNeedAnotherPhotoWhenTupleIsComplete() {
        Models.Identification id = createBaseSportsIdentity();
        id.photoIdentityFields.add("set=Select Road to FIFA World Cup 2026");
        id.photoIdentityFields.add("season=2025-26");
        id.photoIdentityFields.add("player=Victor");
        id.photoIdentityFields.add("team=Nigeria");
        id.photoIdentityFields.add("card_number=21");
        id.photoIdentityFields.add("insert_or_base=Base Terrace");
        id.photoIdentityFields.add("serial=21/99");
        id.photoIdentityFields.add("serial_binding=physical_card_surface");

        Models.CandidateScore unresolved = new Models.CandidateScore();
        unresolved.candidateFacts.add("exact_parallel_name=Base Terrace");
        unresolved.hardViolations.add("parallel unresolved");
        id.candidates.add(unresolved);
        id.finalContradictions.add("exact parallel unresolved");

        assertTrue(CardPhotoTupleClosure.canClose(id));
        assertTrue(CardPhotoTupleClosure.apply(id));
        ConfirmationIntegrityPolicy.enforce(id);

        assertTrue(id.marketReady);
        assertEquals("CONFIRMED · IDENTITÀ VERIFICATA",
                EvidencePolicy.publicStatus(id));
        assertTrue(id.verificationSummary.toLowerCase().contains("variante non"));
        assertTrue(id.nextPhotoRequest == null || id.nextPhotoRequest.isEmpty());
    }

    @Test
    public void unresolvedVariantShouldNotTriggerSecondVisionWhenOnlyOnePlausibleCandidate() {
        Models.Identification id = createBaseSportsIdentity();
        id.photoIdentityFields.add("set=Select Road to FIFA World Cup 2026");
        id.photoIdentityFields.add("season=2025-26");
        id.photoIdentityFields.add("player=Victor");
        id.photoIdentityFields.add("team=Nigeria");
        id.photoIdentityFields.add("card_number=21");
        id.photoIdentityFields.add("insert_or_base=Base Terrace");
        id.photoViews.clear();
        id.photoViews.add("front");
        id.photoViews.add("back");

        Models.CandidateScore unresolved = new Models.CandidateScore();
        unresolved.candidateFacts.add("exact_parallel_name=Base Terrace");
        unresolved.hardViolations.add("parallel unresolved");
        id.candidates.add(unresolved);
        id.finalContradictions.add("exact parallel unresolved");

        Models.Usage usage = new Models.Usage();
        usage.requests = 1;
        usage.costUsd = 0.010d;

        assertTrue(CardPhotoTupleClosure.canClose(id));
        assertFalse(CardPhotoTupleClosure.shouldRunVariantVisionPass(id, usage));
    }

    @Test
    public void insufficientSportsCardShouldRemainNeedAnotherPhoto() {
        Models.Identification id = createBaseSportsIdentity();
        id.photoIdentityFields.add("set=Panini Select");
        id.photoIdentityFields.add("season=2025");
        id.photoIdentityFields.add("player=Some Player");
        id.photoIdentityFields.add("team=Some Team");

        assertFalse(CardPhotoTupleClosure.canClose(id));
        assertFalse(id.marketReady);
        assertTrue(EvidencePolicy.publicStatus(id).startsWith("NEED_ANOTHER_PHOTO"));
    }

    private Models.Identification createBaseSportsIdentity() {
        Models.Identification id = new Models.Identification();
        id.categoryKey = "sports card";
        id.category = "Sports card";
        id.localScan = new Models.LocalScan();
        id.photoViews.add("front");
        id.photoViews.add("back");
        id.photoIdentityFields.add("manufacturer=Panini");
        return id;
    }

    private Models.Identification createBaseTcgIdentity() {
        Models.Identification id = new Models.Identification();
        id.categoryKey = "trading card game";
        id.category = "Trading card game";
        id.localScan = new Models.LocalScan();
        id.photoViews.add("front");
        id.photoIdentityFields.add("manufacturer=Pokemon");
        return id;
    }
}
