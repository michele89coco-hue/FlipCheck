package com.flipcheck.nativebeta;

import java.io.Serializable;

/** Immutable epistemic unit consumed by UniversalIdentityEngineV2. */
final class EvidenceAtom implements Serializable {
    private static final long serialVersionUID = 1L;

    enum EpistemicLevel { OBSERVED, INFERRED, RETRIEVED }
    enum Modality {
        LOCAL_OCR, PRIMARY_VISION, FOCUSED_VISION, LOGO_DETECTION, BARCODE_SCAN,
        WEB_CATALOG, WEB_PRODUCT_PAGE, MARKETPLACE, USER_HINT
    }

    final String id;
    final String field;
    final String rawValue;
    final String normalizedValue;
    final EpistemicLevel epistemicLevel;
    final Modality modality;
    final String source;
    final int imageIndex;
    final String side;
    final String boundingBox;
    final String cropId;
    final String semanticScope;
    final int confidence;
    final int qualityScore;
    final String extractorVersion;
    final String pipelineStage;
    final String sourceUrl;
    final String parentEvidenceId;

    EvidenceAtom(String id, String field, String rawValue, String normalizedValue,
                 EpistemicLevel epistemicLevel, Modality modality, String source,
                 int imageIndex, String side, String boundingBox, String cropId,
                 String semanticScope, int confidence, int qualityScore,
                 String extractorVersion, String pipelineStage, String sourceUrl,
                 String parentEvidenceId) {
        this.id=safe(id); this.field=safe(field); this.rawValue=safe(rawValue);
        this.normalizedValue=safe(normalizedValue); this.epistemicLevel=epistemicLevel;
        this.modality=modality; this.source=safe(source); this.imageIndex=imageIndex;
        this.side=safe(side); this.boundingBox=safe(boundingBox); this.cropId=safe(cropId);
        this.semanticScope=safe(semanticScope); this.confidence=clamp(confidence);
        this.qualityScore=clamp(qualityScore); this.extractorVersion=safe(extractorVersion);
        this.pipelineStage=safe(pipelineStage); this.sourceUrl=safe(sourceUrl);
        this.parentEvidenceId=safe(parentEvidenceId);
    }

    boolean localized() {
        return imageIndex >= 0 && !rawValue.isEmpty()
                && (!boundingBox.isEmpty() || !cropId.isEmpty());
    }

    boolean reliable() {
        return confidence >= 72 && qualityScore >= 60;
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
