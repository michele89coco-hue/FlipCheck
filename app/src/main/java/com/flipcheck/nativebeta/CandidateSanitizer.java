package com.flipcheck.nativebeta;

import java.util.Locale;

final class CandidateSanitizer {
    private CandidateSanitizer() {
    }

    static boolean imageArtifact(String raw) {
        String u = safe(raw).toUpperCase(Locale.ROOT);
        if (u.isEmpty() || u.matches(".*\\.(JPG|JPEG|PNG|WEBP|GIF|AVIF|BMP|SVG)(?:[?#].*)?$") || u.matches("\\d{2,5}X\\d{2,5}(?:\\.(JPG|JPEG|PNG|WEBP|GIF|AVIF))?")) {
            return true;
        }
        return u.matches("(?:IMG|IMAGE|PHOTO|THUMB|THUMBNAIL|ASSET)[-_]?\\d+.*");
    }

    static boolean infrastructureToken(String raw) {
        String u = safe(raw).toUpperCase(Locale.ROOT);
        if (u.isEmpty() || u.matches(".*(?:HTTPS?://|WWW\\.).*") || u.matches(".*\\.(COM|NET|ORG|IO|CO|DEV|APP)(?:[/?:#._-].*)?") || u.contains("CDN") || u.contains("CACHE") || u.contains("GOOGLEUSERCONTENT") || u.contains("GGPHT") || u.contains("CLOUD") || u.contains("USEAST") || u.contains("USWEST") || u.contains("EUWEST") || u.contains("EUCENTRAL") || u.contains("APNORTHEAST") || u.contains("APSOUTHEAST")) {
            return true;
        }
        return u.matches("[A-F0-9]{20,}");
    }

    static boolean plausibleProductCode(String raw) throws NumberFormatException {
        String s = safe(raw).toUpperCase(Locale.ROOT);
        if (s.length() < 3 || s.length() > 40 || imageArtifact(s) || infrastructureToken(s)) {
            return false;
        }
        if (s.matches("\\d{4}")) {
            try {
                int y = Integer.parseInt(s);
                if (y >= 1900 && y <= 2099) {
                    return false;
                }
            } catch (Exception e) {
            }
        }
        if (s.matches("\\d{4,12}")) {
            return true;
        }
        String c = compact(s);
        return c.length() >= 4 && c.length() <= 28 && c.matches(".*[A-Z].*") && c.matches(".*\\d.*");
    }

    static boolean acceptableProductCandidate(String raw) {
        return plausibleProductCode(raw) || (!imageArtifact(raw) && !infrastructureToken(raw) && safe(raw).length() >= 4 && safe(raw).length() <= 64);
    }

    static boolean hostTvModel(String raw) {
        return false;
    }

    static boolean strongRemoteCode(String raw) {
        return plausibleProductCode(raw);
    }

    static boolean acceptableRemoteCandidate(String raw) {
        return acceptableProductCandidate(raw);
    }

    static String compact(String s) {
        return safe(s).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
