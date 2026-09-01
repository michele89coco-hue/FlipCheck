package com.flipcheck.nativebeta;

/** Regressions from the v0.88 Micah Peavy, Yamal, Topps and Orbit tests. */
public final class V089VisualSerialAndScopeRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        Models.Identification peavy = card();
        peavy.photoIdentityName = "Topps Chrome Micah Peavy rookie autograph";
        peavy.visualFacts.add("physical_serial_marking=1/1");
        peavy.localScan.textByImage.add("TOPPS CERTIFIED AUTOGRAPH ISSUE\nMICAH PEAVY\nRC\n#14");
        CollectibleCardIdentityPolicy.sanitizeObservation(peavy, peavy.localScan);
        require(peavy.photoIdentityFields.contains("serial=1/1")
                        && peavy.photoIdentityFields.contains(
                        "serial_binding=physical_card_surface"),
                "a visually localized corner 1/1 must bind even when OCR misses it");

        Models.Identification hintOnly = card();
        hintOnly.localScan.textByImage.add("TOPPS\nMICAH PEAVY");
        CollectibleCardIdentityPolicy.sanitizeObservation(hintOnly, hintOnly.localScan);
        require(hintOnly.photoIdentityFields.stream().noneMatch(
                        x -> x.toLowerCase().startsWith("serial=")),
                "a user hint without localized visual proof must not create a serial");

        Models.Identification yamal = card();
        yamal.photoIdentityName = "Panini Obsidian Supernova Lamine Yamal No. 8";
        yamal.visibleLabels.add("8/9");
        yamal.localScan.textByImage.add("NO. 8\n8/9");
        CollectibleCardIdentityPolicy.sanitizeObservation(yamal, yamal.localScan);
        Models.CandidateScore base = new Models.CandidateScore();
        base.model = "Supernova #8 Lamine Yamal /120";
        base.probableReference = "Supernova #8 Lamine Yamal /120";
        base.candidateFacts.add("print_run=/120");
        CollectibleCardIdentityPolicy.applyCandidateGate(yamal, base);
        require(!base.model.contains("/120") && !base.probableReference.contains("/120")
                        && base.candidateFacts.stream().noneMatch(x -> x.contains("/120")),
                "a base checklist /120 must not be presented as the photographed /9 copy");
        require(base.candidateFacts.contains("physical_serial=8/9"),
                "the physical /9 must remain in candidate facts");

        Models.CandidateScore duplicate = new Models.CandidateScore();
        duplicate.brand = "Topps";
        duplicate.family = "2025-26 Topps Chrome Update Basketball";
        require(duplicate.displayName().equals(duplicate.family),
                "candidate labels must not duplicate Topps");

        require(IdentificationPipelineV082.isEvidenceGap(
                        "Exact model marking is not visible on the photographed unit."),
                "missing Orbit model marking is an evidence gap, not a family contradiction");
        require(IdentificationPipelineV082.isEvidenceGap(
                        "Retrieved source text was not sufficient to verify the physical front-panel image."),
                "missing visual source comparison must not destroy supported Orbit family evidence");

        UniversalRecognitionLadder.apply(hintOnly);
        require(hintOnly.nextPhotoRequest.toLowerCase().contains("retro completo")
                        && hintOnly.nextPhotoRequest.toLowerCase().contains("quattro angoli"),
                "card guidance must request the back and corner close-ups");

        System.out.println("V089VisualSerialAndScopeRegressionTest: PASS");
    }

    private static Models.Identification card() {
        Models.Identification id = new Models.Identification();
        id.category = "collectible sports trading card";
        id.categoryKey = "sports_trading_card";
        id.categoryConfidence = 99;
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityConfidence = 96;
        id.photoViews.add("front");
        id.photoIdentityFields.add("manufacturer=Topps");
        id.photoIdentityFields.add("subject=Micah Peavy");
        id.localScan = new Models.LocalScan();
        return id;
    }
}
