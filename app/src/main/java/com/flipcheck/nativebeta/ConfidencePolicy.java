package com.flipcheck.nativebeta;

import androidx.core.os.EnvironmentCompat;
import com.flipcheck.nativebeta.Models;
import com.flipcheck.nativebeta.UniversalRecognitionLadder;
import java.util.Locale;

final class ConfidencePolicy {
    private ConfidencePolicy() {
    }

    static int identity(Models.Identification id) {
        if (id == null) {
            return 0;
        }
        if(id.mainIdentityConfidence>0){int main=clamp(id.mainIdentityConfidence);return "CONFLICTED".equals(id.identityStatus)?Math.min(main,79):main;}
        if (id.marketReady) {
            return clamp(id.modelConfidence);
        }
        UniversalRecognitionLadder.State s = UniversalRecognitionLadder.assess(id);
        if (s.level < 4) {
            return 0;
        }
        int base = clamp(s.confidence);
        if (!id.disproofPassed) {
            base = Math.min(base, 88);
        }
        if (base < 45) {
            return 0;
        }
        return base;
    }

    static boolean isSpecific(Models.CandidateScore c, Models.Identification id) {
        if (c == null) {
            return false;
        }
        if (nonEmpty(c.model) && looksSpecific(c.model, id)) {
            return true;
        }
        String x = c.displayName();
        return containsCodeLike(x) && looksSpecific(x, id);
    }

    static boolean looksSpecific(String value, Models.Identification id) {
        if (!nonEmpty(value)) {
            return false;
        }
        String x = canon(value);
        if (x.isEmpty()) {
            return false;
        }
        String cat = canon(id == null ? "" : id.category);
        if (!cat.isEmpty() && (x.equals(cat) || (x.contains(cat) && x.length() < cat.length() + 10))) {
            return false;
        }
        String low = value.trim().toLowerCase(Locale.ROOT);
        return (low.equals(EnvironmentCompat.MEDIA_UNKNOWN) || low.equals("generic") || low.equals("object") || low.equals("product") || low.equals("item")) ? false : true;
    }

    private static boolean containsCodeLike(String s) {
        return s != null && s.matches(".*(?:[A-Za-z]{1,8}[-_/]?[0-9]{2,}|[0-9]{3,}[-_/][A-Za-z0-9]{2,}).*");
    }

    private static int clamp(int x) {
        return Math.max(0, Math.min(100, x));
    }

    private static boolean nonEmpty(String s) {
        return (s == null || s.trim().isEmpty()) ? false : true;
    }

    private static String canon(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
