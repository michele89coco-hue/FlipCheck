package com.flipcheck.nativebeta;

/** Universal Base Set edition-mark/layout regressions; no production card-name branches. */
public final class V098UniversalBaseSetAxesRegressionTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Models.Identification machamp = baseSet("Machamp", "8/102");
        addStamp(machamp);
        addShadowlessLayout(machamp);
        machamp.model = "Pokémon Base Set Machamp Holo 8/102 — 1st Edition";
        CollectibleCardIdentityPolicy.normalizeConfirmedIdentity(machamp, null);
        require(machamp.model.contains("1st Edition Shadowless"),
                "Machamp must retain both the edition mark and Shadowless layout");

        Models.Identification arbitrary = baseSet("Arbitrary specimen", "77/102");
        addStamp(arbitrary);
        addShadowlessLayout(arbitrary);
        arbitrary.model = "Pokémon Base Set Arbitrary specimen 77/102";
        CollectibleCardIdentityPolicy.normalizeConfirmedIdentity(arbitrary, null);
        require(arbitrary.model.contains("1st Edition Shadowless"),
                "combined classification must not depend on a known card name");

        Models.Identification shadowedMachamp = baseSet("Machamp", "8/102");
        addStamp(shadowedMachamp);
        shadowedMachamp.photoIdentityFields.add("illustration_frame_drop_shadow=present");
        shadowedMachamp.photoIdentityFields.add("nintendo_copyright_99=absent");
        shadowedMachamp.photoIdentityFields.add("copyright_layout=unlimited");
        shadowedMachamp.model = "Pokémon Base Set Machamp Holo 8/102";
        CollectibleCardIdentityPolicy.normalizeConfirmedIdentity(shadowedMachamp, null);
        require(shadowedMachamp.model.contains("1st Edition Shadowed")
                        && !shadowedMachamp.model.contains("Shadowless"),
                "a stamped shadowed Machamp must not be mislabeled Shadowless");

        Models.Identification stampOnly = baseSet("Any card", "1/102");
        addStamp(stampOnly);
        stampOnly.model = "Pokémon Base Set Any card 1/102";
        CollectibleCardIdentityPolicy.normalizeConfirmedIdentity(stampOnly, null);
        require(stampOnly.model.endsWith("1st Edition")
                        && !stampOnly.model.contains("Shadowless")
                        && !stampOnly.model.contains("Shadowed"),
                "an unresolved layout must remain unresolved instead of being guessed");

        System.out.println("V098UniversalBaseSetAxesRegressionTest: PASS");
    }

    private static void addStamp(Models.Identification id) {
        id.photoIdentityFields.add("first_edition_stamp=present");
        id.photoIdentityFields.add("first_edition_stamp_area_clear=true");
        id.photoIdentityFields.add("first_edition_stamp_position=left_below_artwork");
    }

    private static void addShadowlessLayout(Models.Identification id) {
        id.photoIdentityFields.add("illustration_frame_drop_shadow=absent");
        id.photoIdentityFields.add("nintendo_copyright_99=present");
        id.photoIdentityFields.add("copyright_layout=shadowless");
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
