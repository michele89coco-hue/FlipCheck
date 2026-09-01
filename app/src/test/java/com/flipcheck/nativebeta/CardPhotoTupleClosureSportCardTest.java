package com.flipcheck.nativebeta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CardPhotoTupleClosureSportCardTest {

    @Test
    public void bonifaceWithPhysicalSerialShouldCloseAsExact() {
        Models.Identification id = createBaseSportsIdentity();
        id.photoIdentityFields.add("set=Select Road to FIFA World Cup 2026 Soccer");
        id.photoIdentityFields.add("season=2025-26");
        id.photoIdentityFields.add("player=Victor Boniface");
        id.photoIdentityFields.add("team=Nigeria");
        id.photoIdentityFields.add("card_number=21");
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
    }

    @Test
    public void curry77MustCloseEvenWhenChecklistWebDisagrees() {
        Models.Identification id = createBaseSportsIdentity();
        id.photoIdentityFields.add("player=Stephen Curry");
        id.photoIdentityFields.add("team=Golden State Warriors");
        id.photoIdentityFields.add("set=Adrenalyn XL");
        id.photoIdentityFields.add("season=2009-10");
        id.photoIdentityFields.add("card_number=77");
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
        assertEquals("#77", id.model.replaceAll(".*(#[0-9]+).*", "$1"));
    }

    @Test
    public void missingPhysicalIdentifierMustAskAnotherPhoto() {
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
}
