package com.flipcheck.nativebeta;

import com.flipcheck.nativebeta.Models;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ObservationSanitizer {
    private ObservationSanitizer() {
    }

    static void apply(Models.Identification id) {
        if (id == null || id.visibleLabels.isEmpty()) {
            return;
        }
        List<String> raw = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Iterator<String> it = id.visibleLabels.iterator();
        while (it.hasNext()) {
            String s = it.next();
            String x = s == null ? "" : s.trim().replaceAll("\\s+", " ");
            String c = canon(x);
            if (c.length() >= 2 && c.length() <= 80 && seen.add(c)) {
                raw.add(x);
            }
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String x2 = raw.get(i);
            String cx = canon(x2);
            boolean fragment = false;
            if (cx.matches("[A-Z]{2,4}")) {
                int j = 0;
                while (true) {
                    if (j >= raw.size()) {
                        break;
                    }
                    if (i != j) {
                        String cy = canon(raw.get(j));
                        if (cy.length() >= cx.length() + 2 && cy.contains(cx)) {
                            fragment = true;
                            break;
                        }
                    }
                    j++;
                }
            }
            if (!fragment) {
                out.add(x2);
            }
        }
        id.visibleLabels.clear();
        id.visibleLabels.addAll(out);
    }

    private static String canon(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
