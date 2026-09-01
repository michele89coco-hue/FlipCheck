package com.flipcheck.nativebeta;

import androidx.core.os.EnvironmentCompat;
import com.flipcheck.nativebeta.Models;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class CategoryFactPolicy {
    private CategoryFactPolicy() {
    }

    static void apply(Models.Identification id) {
        String s;
        int eq;
        if (id == null) {
            return;
        }
        List<String> clean = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : id.visualFacts) {
            if (raw != null && (eq = (s = raw.trim()).indexOf(61)) > 0 && eq < s.length() - 1) {
                String key = norm(s.substring(0, eq));
                String value = s.substring(eq + 1).trim();
                if (!key.isEmpty() && !missing(value)) {
                    String k = key + '=' + canon(value);
                    if (seen.add(k)) {
                        clean.add(key + '=' + value);
                    }
                }
            }
        }
        id.visualFacts.clear();
        id.visualFacts.addAll(clean);
    }

    private static String norm(String key) {
        String x = key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (x.equals("model") || x.equals("model_number")) {
            return "model_code";
        }
        if (x.equals("pn") || x.equals("p/n") || x.equals("part") || x.equals("part_no")) {
            return "part_number";
        }
        if (!x.equals("serial") && !x.equals("serial_no")) {
            return x.replaceAll("[^a-z0-9_]", "");
        }
        return "serial_number";
    }

    private static boolean missing(String value) {
        String x = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return x.isEmpty() || x.equals(EnvironmentCompat.MEDIA_UNKNOWN) || x.equals("null") || x.equals("none") || x.equals("n/a") || x.equals("na") || x.equals("not_applicable") || x.equals("non_applicable") || x.equals("not_observed") || x.equals("not_visible") || x.equals("unclear") || x.equals("non_visibile") || x.equals("non_leggibile");
    }

    private static String canon(String v) {
        return v == null ? "" : v.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
