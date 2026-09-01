package com.flipcheck.nativebeta;

import androidx.core.os.EnvironmentCompat;
import com.flipcheck.nativebeta.Models;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class UniversalSearchPlan {
    private UniversalSearchPlan() {
    }

    static String structureSeed(Models.Identification id) {
        if (id == null) {
            return "physical product";
        }
        List<String> parts = new ArrayList<>();
        add(parts, clip(id.visualFingerprint, 420));
        Iterator<String> it = id.spatialSignature.iterator();
        while (it.hasNext()) {
            add(parts, clip(it.next(), 130));
            if (parts.size() >= 6) {
                break;
            }
        }
        for (String x : id.visualFacts) {
            if (usefulFact(x)) {
                add(parts, clip(x, 130));
            }
            if (parts.size() >= 11) {
                break;
            }
        }
        add(parts, cleanCategory(id.category));
        add(parts, cleanCategory(id.categoryKey).replace('_', ' '));
        String out = join(parts, " | ");
        return out.isEmpty() ? "physical product" : clip(out, 1200);
    }

    static String literalTextSeed(Models.Identification id) {
        if (id == null) {
            return "none";
        }
        List<String> xs = new ArrayList<>();
        List<String> source = id.searchableLabels.isEmpty() ? id.visibleLabels : id.searchableLabels;
        for (String raw : source) {
            String x = safe(raw);
            if (usefulLiteral(x) && SearchEvidenceFilter.isSearchableLiteral(x)
                    && !containsIgnoreCase(xs, x)) {
                xs.add("\"" + x.replace("\"", "") + "\"");
                if (xs.size() >= 5) {
                    break;
                }
            }
        }
        return xs.isEmpty() ? "none" : join(xs, " ");
    }

    static List<String> hypothesisLanes(Models.Identification id) {
        List<String> out = new ArrayList<>();
        if (id == null) {
            return out;
        }
        for (String raw : id.visionCandidates) {
            String x = stripLiteralTokens(id, safe(raw)).replaceAll("\\s+", " ").trim();
            if (x.length() >= 4) {
                boolean duplicate = false;
                Iterator<String> it = out.iterator();
                while (true) {
                    if (!it.hasNext()) {
                        break;
                    }
                    String old = it.next();
                    if (tokenOverlap(old, x) >= 0.7d) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    out.add(clip(x, 180));
                }
                if (out.size() >= 3) {
                    break;
                }
            }
        }
        return out;
    }

    static String hypothesisSeed(Models.Identification id) {
        List<String> xs = hypothesisLanes(id);
        return xs.isEmpty() ? "none" : join(xs, " || ");
    }

    static String laneBlock(Models.Identification id) {
        List<String> xs = hypothesisLanes(id);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < xs.size(); i++) {
            if (b.length() > 0) {
                b.append(" | ");
            }
            b.append("LANE_").append(i + 1).append('=').append(xs.get(i));
        }
        int i2 = b.length();
        return i2 == 0 ? "LANES=none" : b.toString();
    }

    static String policyBlock(Models.Identification id) {
        return BrandAnchorPolicy.promptBlock(id) + " | " + BrandEntityPolicy.promptBlock(id) + " | STRUCTURE_SEED=" + structureSeed(id) + " | LITERAL_TEXT_SEED=" + literalTextSeed(id) + " | " + laneBlock(id) + " | DOMAIN_LOCK_ALLOWED=false_until_concrete_same_brand_candidate_exists";
    }

    private static String stripLiteralTokens(Models.Identification id, String raw) {
        String x = raw;
        if (id == null) {
            return x;
        }
        for (String label : id.visibleLabels) {
            String l = safe(label);
            if (l.length() >= 3) {
                x = x.replaceAll("(?i)\\b" + Pattern.quote(l) + "\\b", " ");
            }
        }
        if (id.brand != null && !id.brand.trim().isEmpty()) {
            return x.replaceAll("(?i)\\b" + Pattern.quote(id.brand.trim()) + "\\b", " ");
        }
        return x;
    }

    private static double tokenOverlap(String a, String b) {
        Set<String> aa = tokens(a);
        Set<String> bb = tokens(b);
        if (aa.isEmpty() || bb.isEmpty()) {
            return 0.0d;
        }
        int inter = 0;
        for (String x : aa) {
            if (bb.contains(x)) {
                inter++;
            }
        }
        Set<String> u = new HashSet<>(aa);
        u.addAll(bb);
        if (u.isEmpty()) {
            return 0.0d;
        }
        return inter / u.size();
    }

    private static Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        for (String x : safe(s).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (x.length() >= 3 && !x.equals("the") && !x.equals("with") && !x.equals("and") && !x.equals("for")) {
                out.add(x);
            }
        }
        return out;
    }

    private static boolean usefulFact(String raw) {
        String x = safe(raw).toLowerCase(Locale.ROOT);
        return (x.isEmpty() || x.startsWith("brand=") || x.startsWith("manufacturer=") || x.startsWith("model=") || x.startsWith("model_code=") || x.startsWith("part_number=") || x.startsWith("sku=") || x.startsWith("serial=") || x.contains(EnvironmentCompat.MEDIA_UNKNOWN) || x.contains("not_visible") || x.contains("not observed")) ? false : true;
    }

    private static boolean usefulLiteral(String x) {
        if (x.length() < 2 || x.length() > 70 || x.startsWith("{") || x.startsWith("[") || x.contains("\"location\"") || x.contains("\"type\"")) {
            return false;
        }
        String u = x.toUpperCase(Locale.ROOT);
        return !u.matches("(OK|ON|OFF|YES|NO|UP|DOWN|LEFT|RIGHT|MENU|BACK|HOME|POWER|START|STOP|RESET|SET|MODE|ENTER|CLEAR|AUTO)")
                && !SearchEvidenceFilter.isTransientDisplay(x)
                && !SearchEvidenceFilter.isControlLabel(x);
    }

    private static String cleanCategory(String s) {
        String x = safe(s);
        return (x.equalsIgnoreCase("other") || x.equalsIgnoreCase(EnvironmentCompat.MEDIA_UNKNOWN)) ? "" : x;
    }

    private static boolean containsIgnoreCase(List<String> xs, String v) {
        for (String x : xs) {
            if (x.replace("\"", "").equalsIgnoreCase(v)) {
                return true;
            }
        }
        return false;
    }

    private static void add(List<String> xs, String x) {
        String x2 = safe(x);
        if (x2.isEmpty() || containsIgnoreCase(xs, x2)) {
            return;
        }
        xs.add(x2);
    }

    private static String join(List<String> xs, String sep) {
        StringBuilder b = new StringBuilder();
        for (String x : xs) {
            if (b.length() > 0) {
                b.append(sep);
            }
            b.append(x);
        }
        return b.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String clip(String s, int n) {
        String s2 = safe(s);
        return s2.length() <= n ? s2 : s2.substring(0, n);
    }
}
