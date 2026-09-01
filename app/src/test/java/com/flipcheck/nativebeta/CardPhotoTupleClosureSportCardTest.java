package com.flipcheck.nativebeta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CardPhotoTupleClosureSportCardTest {

    @Test
    public void paniniSelectRoadToFifaWorldCup2026VictorBonifaceShouldCloseAsExact() {
        Models.Identification id = new Models.Identification();
        id.categoryKey = "sports card";
        id.category = "Sports card";
        id.localScan = new Models.LocalScan();

        id.photoViews.add("front");
        id.photoViews.add("back");

        id.photoIdentityFields.add("manufacturer=Panini");
        id.photoIdentityFields.add("set=Select Road to FIFA World Cup 2026 Soccer");
        id.photoIdentityFields.add("season=2025-26");
        id.photoIdentityFields.add("player=Victor Boniface");
        id.photoIdentityFields.add("team=Nigeria");
        id.photoIdentityFields.add("card_number=21");
        id.photoIdentityFields.add("tier=Terrace");
        id.photoIdentityFields.add("finish=Prizm");
        id.photoIdentityFields.add("serial_hint=No.");

        boolean canClose = CardPhotoTupleClosure.canClose(id);
        assertTrue(canClose);

        assertTrue(CardPhotoTupleClosure.apply(id));
        ConfirmationIntegrityPolicy.enforce(id);

        assertTrue(id.marketReady);
        assertEquals("physical_card_tuple", id.modelProof);
        assertEquals("CONFIRMED · IDENTITÀ VERIFICATA",
                EvidencePolicy.publicStatus(id));
    }
}
