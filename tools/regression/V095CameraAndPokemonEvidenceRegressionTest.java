package com.flipcheck.nativebeta;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regressions copied from the user's v0.94 Samsung and Base Set field tests. */
public final class V095CameraAndPokemonEvidenceRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {
        Models.Identification magikarp = baseSet("Magikarp", "35/102");
        magikarp.brand = "Illus. Mitsushiro Arita";
        magikarp.brandLabels.add("Illus. Mitsushiro Arita");
        magikarp.photoIdentityFields.add("illustrator=Mitsushiro Arita");
        magikarp.photoIdentityFields.add("first_edition_stamp=present");
        magikarp.photoIdentityFields.add(
                "first_edition_stamp_position=left_below_artwork");
        CollectibleCardIdentityPolicy.sanitizeObservation(magikarp, magikarp.localScan);
        require("Pokémon".equals(magikarp.brand),
                "illustrator credit must never replace the Pokémon brand");
        require(!magikarp.brandLabels.contains("Illus. Mitsushiro Arita"),
                "illustrator credit must be removed from brand anchors");
        require(!CollectibleCardIdentityPolicy.variantUnresolved(magikarp, null),
                "localized 1st Edition stamp must close Magikarp printing");
        magikarp.model = "Pokémon Base Set Magikarp 35/102 — Shadowless";
        CollectibleCardIdentityPolicy.normalizeConfirmedIdentity(magikarp, null);
        require(magikarp.model.contains("1st Edition")
                        && !magikarp.model.contains("Shadowless"),
                "localized stamp must force the Magikarp title to 1st Edition");

        Models.Identification mewtwo = baseSet("Mewtwo", "10/102");
        mewtwo.photoIdentityFields.add("first_edition_stamp=absent");
        mewtwo.photoIdentityFields.add("first_edition_stamp_area_clear=true");
        mewtwo.photoIdentityFields.add(
                "first_edition_stamp_position=not_applicable");
        // Reproduce the false positive caused by sleeve/glare in v0.94.
        mewtwo.photoIdentityFields.add("illustration_frame_drop_shadow=present");
        mewtwo.photoIdentityFields.add("copyright_layout=unlimited");
        mewtwo.photoIdentityFields.add("copyright_year_1999=present");
        mewtwo.photoIdentityFields.add("nintendo_copyright_99=present");
        mewtwo.model = "Pokémon Base Set Mewtwo 10/102 — Unlimited";
        require(!CollectibleCardIdentityPolicy.variantUnresolved(mewtwo, null),
                "physical 1999 copyright cue must resolve Shadowless");
        CollectibleCardIdentityPolicy.normalizeConfirmedIdentity(mewtwo, null);
        require(mewtwo.model.contains("Shadowless")
                        && !mewtwo.model.contains("Unlimited"),
                "Shadowless must replace the erroneous Unlimited title");

        String root = System.getProperty("flipcheck.project.dir", ".");
        String activity = Files.readString(Path.of(root,
                "app/src/main/java/com/flipcheck/nativebeta/MainActivity.java"),
                StandardCharsets.UTF_8);
        String provider = Files.readString(Path.of(root,
                "app/src/main/java/com/flipcheck/nativebeta/CameraCaptureProvider.java"),
                StandardCharsets.UTF_8);
        String manifest = Files.readString(Path.of(root,
                "app/src/main/AndroidManifest.xml"), StandardCharsets.UTF_8);
        require(activity.contains("CameraCaptureProvider.createDestination")
                        && activity.contains("grantCameraUriToHandlers")
                        && activity.contains("cameraImageHasContent"),
                "camera must use an app-owned full-resolution content URI");
        require(provider.contains("camera-captures")
                        && provider.contains("ParcelFileDescriptor.MODE_TRUNCATE"),
                "camera provider must expose only its private capture directory");
        require(manifest.contains(".CameraCaptureProvider")
                        && manifest.contains("android:grantUriPermissions=\"true\""),
                "camera provider must be declared with URI grants");

        String prompt = IdentificationPipelineV082.multimodalPromptForTest(
                mewtwo.localScan, "");
        require(prompt.contains("nintendo_copyright_99=present|absent|unclear")
                        && prompt.contains("ordinary final Wizards copyright")
                        && prompt.contains("illustrator credit")
                        && prompt.contains("never a brand_logo"),
                "prompt must request the decisive Base Set and artist-role evidence");

        System.out.println("V095CameraAndPokemonEvidenceRegressionTest: PASS");
    }

    private static Models.Identification baseSet(String subject, String number) {
        Models.Identification id = new Models.Identification();
        id.category = "Pokémon trading card";
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
