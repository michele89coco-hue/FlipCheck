package com.flipcheck.nativebeta;

import androidx.core.os.EnvironmentCompat;
import com.flipcheck.nativebeta.Models;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PhotoProtocol {

    static final class Assessment {
        boolean ready;
        String categoryKey = "other";
        final List<String> required = new ArrayList();
        final List<String> missing = new ArrayList();
        String nextRequest = "";
        String reason = "";

        Assessment() {
        }
    }

    private PhotoProtocol() {
    }

    static Assessment assess(String rawCategoryKey, String rawCategory, List<String> photoViews, Models.LocalScan local, int imageCount) {
        Assessment a = new Assessment();
        a.categoryKey = normalize(rawCategoryKey);
        a.required.add("Una foto chiara dell'oggetto principale");
        a.ready = imageCount >= 1;
        if (!a.ready) {
            a.missing.add("object_photo");
            a.nextRequest = "Aggiungi una foto chiara dell'oggetto intero";
            a.reason = "Serve almeno una vista per avviare il riconoscimento.";
        }
        return a;
    }

    private static String normalize(String s) {
        String x = s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return (x.isEmpty() || x.equals(EnvironmentCompat.MEDIA_UNKNOWN)) ? "other" : x;
    }
}
