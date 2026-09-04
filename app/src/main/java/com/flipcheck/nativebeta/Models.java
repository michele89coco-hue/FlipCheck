package com.flipcheck.nativebeta;

import java.net.URI;
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
        /** catalog, market or other; assigned only after the source is actually used. */
        String sourceType = "other";

        Source() {
        }

        String domain() {
            try {
                URI u = URI.create(this.url);
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
        String mainSet = "";
        String subset = "";
        String designFamily = "";
        String subSeries = "";
        String model = "";
        String probableReference = "";
        String evidence = "";
        String canonicalKey = "";
        String categoryKey = "";
        String year = "";
        String subject = "";
        String cardNumber = "";
        String language = "";
        String edition = "";
        String printing = "";
        String parallel = "";
        String parallelFamily = "";
        String parallelColor = "";
        String printRun = "";
        String serialNumber = "";
        String finish = "";
        String format = "";
        String configuration = "";
        String materialVariantKey = "";
        String sourceUrl = "";
        String sourceAuthority = "";
        String team = "";
        String sport = "";
        String hpOrPv = "";
        String evolutionStage = "";
        String copyrightYear = "";
        String layoutSignature = "";
        String productType = "";
        String productCode = "";
        String packageCount = "";
        String cardsPerPack = "";
        String autographGuarantee = "";
        String memorabiliaGuarantee = "";
        String sealedStatus = "";
        final List<String> distinguishingTokens = new ArrayList();
        final List<String> attackNames = new ArrayList();
        boolean materiallyDistinctVariant;
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

    /** Append-only evidence record. Provenance fields are immutable after creation. */
    static final class EvidenceFact implements Serializable {
        private static final long serialVersionUID = 1L;
        final String key;
        final String value;
        final String origin;
        final String evidenceType;
        final int confidence;
        final int imageIndex;
        final String side;
        final String location;
        final String semanticRole;
        final String sourceUrl;
        final long createdAtMillis;
        final String timestampStage;

        EvidenceFact(String key,String value,String origin,String evidenceType,int confidence,
                     int imageIndex,String side,String location,String semanticRole,String sourceUrl){
            this.key=safe(key);this.value=safe(value);this.origin=safe(origin);
            this.evidenceType=safe(evidenceType);this.confidence=Math.max(0,Math.min(100,confidence));
            this.imageIndex=imageIndex;this.side=safe(side);this.location=safe(location);
            this.semanticRole=safe(semanticRole);this.sourceUrl=safe(sourceUrl);
            this.createdAtMillis=System.currentTimeMillis();
            this.timestampStage=safe(evidenceType);
        }
        private static String safe(String x){return x==null?"":x.trim();}
    }

    static final class MarketComparable implements Serializable {
        private static final long serialVersionUID = 1L;
        final String sourceUrl,title,itemState,condition,gradingCompany,grade,currency,date;
        final double price;
        final boolean sold,included;
        final String reason;
        MarketComparable(String sourceUrl,String title,String itemState,String condition,
                         String gradingCompany,String grade,String currency,double price,String date,
                         boolean sold,boolean included,String reason){
            this.sourceUrl=safe(sourceUrl);this.title=safe(title);this.itemState=safe(itemState);
            this.condition=safe(condition);this.gradingCompany=safe(gradingCompany);this.grade=safe(grade);
            this.currency=safe(currency);this.price=price;this.date=safe(date);this.sold=sold;
            this.included=included;this.reason=safe(reason);
        }
        private static String safe(String x){return x==null?"":x.trim();}
    }

    static final class Identification implements Serializable {
        private static final long serialVersionUID = 1L;
        int brandEntityConfidence;
        int brandRoleConfidence;
        int categoryConfidence;
        boolean disproofPassed;
        int familyConfidence;
        int mainIdentityConfidence;
        int coreIdentityConfidence;
        int exactIdentityConfidence;
        int identifierConfidence;
        int variantConfidence;
        int marketConfidence;
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
        boolean photoIdentityExternalWatermark;
        boolean photoIdentityIdentityObscured;
        boolean photoIdentityAmbiguous;
        int photoAlternativeCount;
        String discriminativeField = "";
        boolean discriminativeFieldVisible;
        /** Irreversible result of UniversalIdentityClosure for this analysis. */
        boolean identityConfirmed;
        String decision = "";
        String confirmedBrand = "";
        String confirmedFamily = "";
        String confirmedModel = "";
        boolean closureAttempt;
        boolean closureResult;
        String closureStage = "";
        String closureMissingFields = "";
        String physicalCardNumber = "";
        String cardNumberCandidate = "";
        String cardNumberVerificationStatus = "NOT_OBSERVED";
        boolean cardNumberVerified;
        String physicalCardNumberOrigin = "";
        String physicalSerial = "";
        String physicalSerialOrigin = "";
        String physicalCollectorNumber = "";
        String collectorNumberCandidate = "";
        String collectorNumberVerificationStatus = "NOT_OBSERVED";
        boolean collectorNumberVerified;
        String physicalParallel = "";
        String parallelColor = "";
        String finish = "";
        String language = "";
        String evolutionStage = "";
        String hpOrPv = "";
        String attackNames = "";
        String attackDamage = "";
        String graphicNumber = "";
        String cardType = "";
        String copyrightYear = "";
        String distinctivePrintedTokens = "";
        String productType = "";
        String sealedFormat = "";
        String productConfiguration = "";
        String queryProfile = "";
        String candidateCanonicalizationSummary = "";
        int canonicalCandidateCount;
        boolean rareVariantPhysicalProof;
        boolean physicallyGraded;
        String sourceConfirmedCatalogNumber = "";
        String sourceConfirmedCatalogNumberOrigin = "";
        String sourceConfirmedReleaseYear = "";
        String sourceConfirmedProductLine = "";
        String sourceConfirmedVariant = "";
        String sourceConfirmedMainSet = "";
        String sourceConfirmedSubset = "";
        String sourceConfirmedSubSeries = "";
        String sourceConfirmedParallelFamily = "";
        String sourceConfirmedParallelColor = "";
        String sourceConfirmedPrintRun = "";
        String sourceConfirmedFormat = "";
        String sourceConfirmedProductCode = "";
        String sourceReportedCatalogNumber = "";
        String sourceReportedReleaseYear = "";
        String sourceReportedProductLine = "";
        String sourceReportedVariant = "";
        String catalogCompatibilityStatus = "NOT_EVALUATED";
        String catalogMatchedFields = "";
        String catalogConflicts = "";
        boolean catalogVerified;
        String formatStatus = "FORMAT_NOT_OBSERVED";
        String disproofStatus = "NOT_EXECUTED";
        String exactResolutionReason = "";
        String catalogHierarchy = "";
        String secondWebResolutionReason = "";
        int exactWebResolutionAttempts;
        double estimatedAnalysisCostUsd;
        int webContributionScore;
        String sourceCatalogTitle = "";
        String priceSummary = "mercato non disponibile/non affidabile";
        String comparablesSummary = "comparabili non disponibili";
        String identityStatus = "UNRESOLVED";
        String closureBasis = "";
        String blockingReason = "";
        String missingDiscriminativeFields = "";
        String missingNonblockingFields = "";
        String requestedPhotoReason = "";
        String webStatus = "NOT_RUN";
        String marketStatus = "NOT_AVAILABLE";
        String marketDecisionStatus = "MARKET_UNAVAILABLE";
        /** Independent hierarchical result states; identity is no longer all-or-nothing. */
        String categoryStatus = "UNRESOLVED";
        String familyStatus = "UNRESOLVED";
        String coreIdentityStatus = "UNRESOLVED";
        String exactIdentityStatus = "UNRESOLVED";
        String identifierStatus = "NOT_OBSERVED";
        String variantStatus = "UNRESOLVED";
        String overallStatus = "INSUFFICIENT_EVIDENCE";
        String hierarchicalStatus = "INSUFFICIENT_EVIDENCE";
        /** Failure attribution and call accounting exposed in the technical panel. */
        String pipelineFailureDomain = "NONE";
        String visionFinishReason = "not_run";
        String visionResponseStatus = "NOT_RUN";
        int technicalRetryCount;
        int discriminativeVisionCount;
        int localOcrFactCount;
        String canonicalProfileVotes = "";
        String numberHypotheses = "";
        String numberConflicts = "";
        String postWebConflicts = "";
        String webFieldsAccepted = "";
        String webFieldsRejected = "";
        String queryFieldsIncluded = "";
        String queryFieldsExcluded = "";
        String excludedComparablesWithReason = "";
        String preWebInvariants = "NOT_RUN";
        String postWebInvariants = "NOT_RUN";
        String finalDecisionReason = "";
        String additionalVisionReason = "";
        transient NormalizedPhotoIdentity normalizedPhotoIdentity;
        String normalizationStage = "not_run";
        String canonicalProfile = "";
        String canonicalPhotoFields = "";
        String canonicalPhysicalFields = "";
        String aliasesConsumed = "";
        String factsRejectedWithReason = "";
        String semanticConflicts = "";
        String fingerprintComponents = "";
        int fingerprintScore;
        String closureInputSnapshot = "";
        String requestedPhotoProfile = "";
        String consistencyInvariants = "NOT_RUN";
        final List<String> consistencyInvariantErrors = new ArrayList();
        final List<String> consistencyInvariantWarnings = new ArrayList();
        FinalIdentityState finalState;
        boolean priceAvailable;
        boolean criticalCardDetailNeedsSecondVision;
        boolean criticalCardDetailVerified;
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
        final List<String> externalLabels = new ArrayList();
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
        final List<String> exactResolutionQueries = new ArrayList();
        final List<CandidateScore> candidates = new ArrayList();
        final List<CandidateScore> rejectedCandidates = new ArrayList();
        final List<Source> sources = new ArrayList();
        final List<String> observedEvidence = new ArrayList();
        final List<String> inferredEvidence = new ArrayList();
        final List<String> verifiedEvidence = new ArrayList();
        final List<String> hardConstraints = new ArrayList();
        final List<String> userConfirmedFacts = new ArrayList();
        final List<String> photoIdentityFields = new ArrayList();
        final List<String> featuredSubjects = new ArrayList();
        final List<EvidenceFact> evidenceLedger = new ArrayList();
        final List<MarketComparable> marketComparables = new ArrayList();

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
