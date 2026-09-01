package com.flipcheck.nativebeta;

import android.net.Uri;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

final class Models {
    private Models() {
    }

    static final class Identifier implements Serializable {
        private static final long serialVersionUID = 1L;
        final int imageIndex;
        final String label;
        final String origin;
        final String value;

        Identifier(String label, String value, int imageIndex, String origin) {
            this.label = label == null ? "" : label;
            this.value = value == null ? "" : value;
            this.imageIndex = imageIndex;
            this.origin = origin != null ? origin : "";
        }
    }

    static final class LocalScan implements Serializable {
        private static final long serialVersionUID = 1L;
        long durationMs;
        final List<Identifier> identifiers = new ArrayList();
        final List<String> barcodes = new ArrayList();
        final List<String> textByImage = new ArrayList();

        LocalScan() {
        }

        String joinedText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < this.textByImage.size(); i++) {
                String t = this.textByImage.get(i);
                if (t != null && !t.trim().isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append("\n--- FOTO ").append(i + 1).append(" ---\n");
                    }
                    sb.append(t.trim());
                }
            }
            return sb.toString();
        }
    }

    static final class Source implements Serializable {
        private static final long serialVersionUID = 1L;
        int relevance;
        boolean strong;
        String title = "";
        String url = "";
        String snippet = "";

        Source() {
        }

        String domain() {
            try {
                Uri u = Uri.parse(this.url);
                String h = u.getHost();
                return h == null ? this.url : h.replaceFirst("^www\\.", "");
            } catch (Exception e) {
                return this.url;
            }
        }
    }

    static final class CandidateScore implements Serializable {
        private static final long serialVersionUID = 1L;
        int hardMatchWeight;
        boolean hardRejected;
        int identifierScore;
        int layoutScore;
        int textScore;
        int totalScore;
        int webScore;
        int probableReferenceConfidence;
        String brand = "";
        String family = "";
        String model = "";
        String probableReference = "";
        String evidence = "";
        final List<String> candidateFacts = new ArrayList();
        final List<String> contradictions = new ArrayList();
        final List<String> hardMatches = new ArrayList();
        final List<String> hardViolations = new ArrayList();

        CandidateScore() {
        }

        String displayName() {
            StringBuilder b = new StringBuilder();
            String joinedLater = (this.family + " " + this.model).toUpperCase();
            if (!this.brand.trim().isEmpty()
                    && !containsWords(joinedLater, this.brand)) {
                b.append(this.brand.trim());
            }
            if (!this.family.trim().isEmpty()
                    && !containsWords(this.model, this.family)) {
                if (b.length() > 0) {
                    b.append(' ');
                }
                b.append(this.family.trim());
            }
            if (!this.model.trim().isEmpty()) {
                if (b.length() > 0) {
                    b.append(' ');
                }
                b.append(this.model.trim());
            }
            return b.length() == 0 ? "Candidato senza nome" : b.toString();
        }

        private static boolean containsWords(String haystack, String needle) {
            String h = canonWords(haystack);
            String n = canonWords(needle);
            if (n.isEmpty()) {
                return false;
            }
            String[] tokens = n.split(" ");
            String padded = " " + h + " ";
            int total = 0;
            int found = 0;
            for (String token : tokens) {
                if (token.length() < 2) {
                    continue;
                }
                total++;
                if (padded.contains(" " + token + " ")) {
                    found++;
                }
            }
            return total > 0 && found == total;
        }

        private static String canonWords(String value) {
            return value == null ? "" : value.toUpperCase(java.util.Locale.ROOT)
                    .replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " ");
        }
    }

    static final class Identification implements Serializable {
        private static final long serialVersionUID = 1L;
        int brandEntityConfidence;
        int brandRoleConfidence;
        int categoryConfidence;
        boolean disproofPassed;
        int familyConfidence;
        LocalScan localScan;
        boolean marketReady;
        int modelConfidence;
        int priceConfidence;
        int tournamentMargin;
        int visionIdentityConfidence;
        int photoIdentityConfidence;
        String title = "";
        String category = "";
        String categoryKey = "other";
        String brand = "";
        String brandEvidence = "unknown";
        String brandRoleReason = "";
        String brandEntity = "";
        String brandOfficialDomain = "";
        String brandOfficialUrl = "";
        final List<String> brandProductClasses = new ArrayList();
        String family = "";
        String model = "";
        String primaryIdentifier = "";
        String searchQuery = "";
        String verificationSummary = "";
        String nextPhotoRequest = "";
        String nextPhotoReason = "";
        String visualFingerprint = "";
        String modelProof = "";
        String decisionReason = "";
        String searchSuggestionsHtml = "";
        String visionIdentityReason = "";
        String photoIdentityName = "";
        String photoIdentityCode = "";
        String photoIdentityKind = "none";
        boolean photoIdentityComplete;
        boolean photoIdentityPhysicalBinding;
        boolean photoIdentityOverlayOrWatermark;
        boolean photoProtocolReady = true;
        final List<String> identifierVariants = new ArrayList();
        final List<String> distinctiveTerms = new ArrayList();
        final List<String> visualFacts = new ArrayList();
        final List<String> visibleLabels = new ArrayList();
        final List<String> searchableLabels = new ArrayList();
        final List<String> softOcrLabels = new ArrayList();
        final List<String> brandLabels = new ArrayList();
        final List<String> identifierLabels = new ArrayList();
        final List<String> descriptorLabels = new ArrayList();
        final List<String> controlLabels = new ArrayList();
        final List<String> transientLabels = new ArrayList();
        final List<String> spatialSignature = new ArrayList();
        final List<String> visionCandidates = new ArrayList();
        final List<String> matchedVisualFacts = new ArrayList();
        final List<String> matchedLayoutTokens = new ArrayList();
        final List<String> finalContradictions = new ArrayList();
        final List<String> photoViews = new ArrayList();
        final List<String> requiredShots = new ArrayList();
        final List<String> missingShots = new ArrayList();
        final List<String> webQueries = new ArrayList();
        final List<String> webStages = new ArrayList();
        final List<CandidateScore> candidates = new ArrayList();
        final List<CandidateScore> rejectedCandidates = new ArrayList();
        final List<Source> sources = new ArrayList();
        final List<String> observedEvidence = new ArrayList();
        final List<String> inferredEvidence = new ArrayList();
        final List<String> verifiedEvidence = new ArrayList();
        final List<String> hardConstraints = new ArrayList();
        final List<String> userConfirmedFacts = new ArrayList();
        final List<String> photoIdentityFields = new ArrayList();

        Identification() {
        }
    }

    static final class Usage implements Serializable {
        private static final long serialVersionUID = 1L;
        long apiMs;
        long cachedTokens;
        double costUsd;
        long inputTokens;
        long outputTokens;
        int requests;
        int visionCalls;
        int webCalls;

        Usage() {
        }

        void add(Usage o) {
            if (o == null) {
                return;
            }
            this.requests += o.requests;
            this.visionCalls += o.visionCalls;
            this.webCalls += o.webCalls;
            this.inputTokens += o.inputTokens;
            this.outputTokens += o.outputTokens;
            this.cachedTokens += o.cachedTokens;
            this.apiMs += o.apiMs;
            this.costUsd += o.costUsd;
        }
    }
}
