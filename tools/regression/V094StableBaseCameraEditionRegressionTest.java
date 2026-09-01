package com.flipcheck.nativebeta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regressions for the v0.94 rebuild on the stable v0.92 decision base. */
public final class V094StableBaseCameraEditionRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        Models.Identification magikarp = baseSet("Magikarp", "35/102");
        magikarp.photoIdentityFields.add("first_edition_stamp=present");
        magikarp.photoIdentityFields.add(
                "first_edition_stamp_position=left_below_artwork");
        magikarp.photoIdentityFields.add("illustration_frame_drop_shadow=present");
        require(!CollectibleCardIdentityPolicy.variantUnresolved(magikarp, null),
                "localized 1st Edition stamp must outrank a weaker shadow guess");
        magikarp.model = "Pokémon Base Set Magikarp 35/102 Unlimited";
        CollectibleCardIdentityPolicy.normalizeConfirmedIdentity(magikarp, null);
        require(magikarp.model.contains("1st Edition")
                        && !magikarp.model.contains("Unlimited"),
                "confirmed title must become 1st Edition");

        Models.Identification falseStamp = baseSet("Magikarp", "35/102");
        falseStamp.photoIdentityFields.add("first_edition_stamp=present");
        falseStamp.photoIdentityFields.add("first_edition_stamp_position=other");
        require(CollectibleCardIdentityPolicy.variantUnresolved(falseStamp, null),
                "1st Edition text outside the physical stamp area must not close printing");

        Models.Identification shadowless = baseSet("Mewtwo", "10/102");
        shadowless.photoIdentityFields.add("first_edition_stamp=absent");
        shadowless.photoIdentityFields.add("first_edition_stamp_area_clear=true");
        shadowless.photoIdentityFields.add("first_edition_stamp_position=not_applicable");
        shadowless.photoIdentityFields.add("illustration_frame_drop_shadow=absent");
        shadowless.photoIdentityFields.add("copyright_layout=shadowless");
        shadowless.photoIdentityFields.add("nintendo_copyright_99=present");
        require(!CollectibleCardIdentityPolicy.variantUnresolved(shadowless, null),
                "stable v0.92 Shadowless closure must remain intact");

        String root = System.getProperty("flipcheck.project.dir", ".");
        String activity = Files.readString(Path.of(root,
                "app/src/main/java/com/flipcheck/nativebeta/MainActivity.java"),
                StandardCharsets.UTF_8);
        require(activity.contains("recoverCameraImageIfWritten")
                        && activity.contains("cameraImageHasContent")
                        && activity.contains("PREF_PENDING_CAMERA_URI"),
                "camera must recover a written MediaStore image without a callback");
        require(activity.contains("prepareForNewScanIfEmpty")
                        && activity.contains("detailsInput.setText(\"\")"),
                "a new object scan must not inherit the previous optional hint");

        String prompt = IdentificationPipelineV082.multimodalPromptForTest(
                shadowless.localScan, "");
        require(prompt.contains("first_edition_stamp_position=left_below_artwork")
                        && prompt.contains("position must be not_applicable"),
                "prompt must localize the physical 1st Edition stamp");

        System.out.println("V094StableBaseCameraEditionRegressionTest: PASS");
    }

    private static Models.Identification baseSet(String subject, String number) {
        Models.Identification id = new Models.Identification();
        id.category = "collectible trading card";
        id.categoryKey = "pokemon_tcg_card";
        id.brand = "Pokémon";
        id.family = "Base Set";
        id.photoIdentityComplete = true;
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityConfidence = 97;
        id.photoIdentityKind = "composite_markings";
        id.photoIdentityName = "Pokémon Base Set " + subject + " " + number;
        id.photoViews.add("front view");
        id.photoIdentityFields.add("manufacturer=Pokémon");
        id.photoIdentityFields.add("set=Base Set");
        id.photoIdentityFields.add("subject=" + subject);
        id.photoIdentityFields.add("card_number=" + number);
        id.localScan = new Models.LocalScan();
        id.localScan.textByImage.add(subject + "\n" + number);
        return id;
    }
}
