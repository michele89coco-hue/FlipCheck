package com.flipcheck.nativebeta;

import java.util.Locale;

/** Distinguishes phone/gallery chrome from an overlay that hides the object itself. */
final class OverlayScopePolicy {
    private OverlayScopePolicy() {}

    static void normalize(Models.Identification id) {
        if (id == null || !id.photoIdentityOverlayOrWatermark
                || UniversalIdentityClosure.externalWatermarkObscuresIdentity(id)) return;
        // Vision may use overlay=true for reflections, printed/composite card graphics
        // or gallery chrome.  It is only a warning unless an explicitly external
        // watermark also obscures identity-bearing data.
        id.photoIdentityOverlayOrWatermark = false;
        addWarning(id, "non_blocking_overlay_warning=true");
        if (id.photoIdentityPhysicalBinding && id.photoIdentityConfidence >= 88
                && !safe(id.photoIdentityName).isEmpty()
                && id.photoIdentityFields.size() >= 4) {
            id.photoIdentityComplete = true;
        }
    }

    static boolean blocksIdentity(Models.Identification id) {
        return UniversalIdentityClosure.externalWatermarkObscuresIdentity(id);
    }

    private static void addWarning(Models.Identification id, String value) {
        for (String old : id.observedEvidence) if (value.equals(old)) return;
        id.observedEvidence.add(value);
    }

    private static boolean peripheralOnly(Models.Identification id) {
        String x = (id.photoIdentityFields + " " + id.visualFacts + " "
                + safe(id.visionIdentityReason)).toLowerCase(Locale.ROOT);
        boolean ui = x.contains("status bar") || x.contains("phone status")
                || x.contains("gallery interface") || x.contains("gallery ui")
                || x.contains("camera interface") || x.contains("navigation bar")
                || x.contains("interfaccia galleria") || x.contains("barra di stato");
        boolean coversObject = x.contains("obscur") || x.contains("covering the object")
                || x.contains("covers the object") || x.contains("overlaps the object")
                || x.contains("copre l'oggetto") || x.contains("sovrappost");
        return ui && !coversObject && id.photoIdentityPhysicalBinding
                && id.photoIdentityFields.size() >= 3;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
