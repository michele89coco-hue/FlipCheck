package com.flipcheck.nativebeta;

import java.util.List;
import java.util.Locale;

/**
 * Treats the printed surface of a sealed retail product as the physical
 * identity-bearing object, without weakening the rules for loose products or
 * unrelated packaging visible near them.
 */
final class SealedProductIdentityPolicy {
    private SealedProductIdentityPolicy() {
    }

    static void consolidateObservation(Models.Identification id, Models.LocalScan local) {
        if (!isSealedRetailProduct(id) || id == null || local == null
                || !id.photoIdentityPhysicalBinding
                || id.photoIdentityOverlayOrWatermark
                || id.photoIdentityConfidence < 85) {
            return;
        }
        removeSeasonMasqueradingAsSerial(id);
        String manufacturer = identityField(id.photoIdentityFields,
                "manufacturer", "brand", "maker", "publisher");
        boolean literalManufacturer = literalInLocalOcr(local, manufacturer);
        boolean completeCompositeFallback = id.photoIdentityComplete
                && id.photoIdentityConfidence >= 95
                && completePrintedTuple(id.photoIdentityFields)
                && corroboratedTupleFields(local, id.photoIdentityFields) >= 1
                && !conflictingObservedBrand(id, manufacturer);
        if (manufacturer.isEmpty() || (!literalManufacturer && !completeCompositeFallback)) {
            return;
        }
        id.brand = manufacturer;
        id.brandEvidence = "physical_package_identity";
        id.brandRoleConfidence = Math.max(id.brandRoleConfidence, 94);
        addOnce(id.brandLabels, manufacturer);
        addOnce(id.searchableLabels, manufacturer);

        for (String field : id.photoIdentityFields) {
            String value = fieldValue(field);
            if (!value.isEmpty() && literalInLocalOcr(local, value)
                    && SearchEvidenceFilter.isSearchableLiteral(value)) {
                addOnce(id.searchableLabels, value);
            }
        }
        if (completePrintedTuple(id.photoIdentityFields)) {
            id.photoIdentityComplete = true;
            id.photoIdentityKind = "composite_markings";
            id.photoIdentityConfidence = Math.max(id.photoIdentityConfidence, 92);
        }
    }

    static boolean isSealedRetailProduct(Models.Identification id) {
        if (id == null) {
            return false;
        }
        String raw = (safe(id.categoryKey) + " " + safe(id.category) + " "
                + safe(id.photoIdentityName) + " " + id.photoIdentityFields)
                .toLowerCase(Locale.ROOT);
        String x = raw.replaceAll("[^a-z0-9]+", " ").trim()
                .replaceAll("\\s+", " ");
        boolean cardBox = x.contains("trading card box") || x.contains("card box")
                || x.contains("hobby box")
                || x.contains("blaster box") || x.contains("retail box");
        boolean sealedProduct = x.contains("sealed product")
                || x.contains("sealed retail") || x.contains("factory sealed")
                || x.contains("format hobby box") || x.contains("format retail box")
                || x.contains("format blaster box") || x.contains("format sealed box")
                || (x.contains("sealed") && (x.contains("trading card")
                || x.contains("card product") || x.contains("card retail")));
        return cardBox || sealedProduct;
    }

    static boolean canConfirmCommercialSku(Models.Identification id,
                                           Models.CandidateScore c) {
        if (id == null || c == null || !isSealedRetailProduct(id)
                || c.hardRejected || UniversalConsistencyGate.strongCandidateConflict(c)
                || !id.photoIdentityPhysicalBinding || id.photoIdentityOverlayOrWatermark
                || id.photoIdentityConfidence < 88
                || !factTrue(c, "source_grounded")
                || !factTrue(c, "same_entity_role")
                || factTrue(c, "relationship_only")
                || !factTrue(c, "disproof_passed")) {
            return false;
        }
        boolean printedTuple = completePrintedTuple(id.photoIdentityFields);
        boolean groundedFrontTuple = groundedFrontTuple(id, c);
        boolean sourceNamedSku = c.probableReferenceConfidence >= 75
                && !safe(c.probableReference).isEmpty();
        boolean exactCandidate = factTrue(c, "exact_reference_complete")
                && !safe(c.model).isEmpty();
        return (printedTuple || id.photoIdentityComplete || groundedFrontTuple)
                && (sourceNamedSku || exactCandidate)
                && c.textScore >= 78 && c.webScore >= 70;
    }

    static void confirmCommercialSku(Models.Identification id,
                                     Models.CandidateScore c) {
        if (id == null || c == null) {
            return;
        }
        if (!safe(c.brand).isEmpty()) {
            id.brand = c.brand;
        }
        String commercialName = bestCommercialName(id, c);
        commercialName = stripLeadingBrand(commercialName, id.brand);
        id.family = stripLeadingBrand(!safe(c.family).isEmpty()
                ? c.family : commercialName, id.brand);
        id.model = commercialName;
        id.marketReady = true;
        id.disproofPassed = true;
        id.modelProof = "physical_sealed_product_tuple";
        id.modelConfidence = Math.max(90, Math.min(97,
                Math.max(id.photoIdentityConfidence, c.probableReferenceConfidence)));
        id.categoryConfidence = Math.max(id.categoryConfidence, 96);
        id.familyConfidence = Math.max(id.familyConfidence, 94);
        id.nextPhotoRequest = "";
        id.nextPhotoReason = "";
        id.verificationSummary = "Prodotto sigillato identificato tramite marca, stagione, "
                + "linea e formato stampati sulla confezione, coerenti con la fonte.";
        id.decisionReason = "CONFIRMED v0.92: identità commerciale completa del prodotto sigillato.";
        addOnce(c.candidateFacts, "confirmed_by_sealed_commercial_tuple=true");
        UniversalIdentityClosure.apply(id, "legacy_sealed_candidate_gate_delegate");
    }

    static boolean hasBoundManufacturer(Models.Identification id) {
        return isSealedRetailProduct(id) && id.photoIdentityPhysicalBinding
                && !id.photoIdentityOverlayOrWatermark
                && "physical_package_identity".equalsIgnoreCase(safe(id.brandEvidence))
                && id.brandRoleConfidence >= 90 && !safe(id.brand).isEmpty();
    }

    /** Keeps a complete printed tuple useful even when web retrieval returns no candidate. */
    static void applyPhotoTupleFallback(Models.Identification id) {
        if (id == null || !hasBoundManufacturer(id)
                || !completePrintedTuple(id.photoIdentityFields)) {
            return;
        }
        String family = joinUnique(
                identityField(id.photoIdentityFields, "season", "season/year", "season_year",
                        "release", "year", "edition"),
                identityField(id.photoIdentityFields, "product_line", "product line", "set", "collection"),
                identityField(id.photoIdentityFields, "series"),
                identityField(id.photoIdentityFields, "sport", "category"),
                identityField(id.photoIdentityFields, "format", "box_format", "product_format"),
                identityField(id.photoIdentityFields,
                        "configuration", "pack_configuration", "box_configuration"));
        if (!family.isEmpty()) {
            id.family = family;
            id.familyConfidence = Math.max(id.familyConfidence,
                    Math.min(88, id.photoIdentityConfidence));
        }
    }

    /**
     * A factory-sealed box can identify itself without a surviving web
     * candidate when the front supplies the complete commercial tuple. This
     * does not apply to loose objects, accessories or generic packaging.
     */
    static boolean canConfirmPhotoTupleWithoutCandidate(Models.Identification id) {
        if (id == null || !isSealedRetailProduct(id) || !hasBoundManufacturer(id)
                || !id.photoIdentityPhysicalBinding || id.photoIdentityOverlayOrWatermark
                || !id.photoIdentityComplete || id.photoIdentityConfidence < 95
                || !completePrintedTuple(id.photoIdentityFields)) {
            return false;
        }
        String name = canon(joinUnique(id.photoIdentityName,
                printedCommercialName(id.photoIdentityFields)));
        boolean commercialFormat = name.contains("HOBBY BOX")
                || name.contains("BLASTER BOX") || name.contains("RETAIL BOX")
                || name.contains("SEALED BOX")
                || (name.contains("BOX") && !identityField(id.photoIdentityFields,
                "configuration", "pack_configuration", "box_configuration").isEmpty());
        return commercialFormat && id.localScan != null
                && corroboratedTupleFields(id.localScan, id.photoIdentityFields) >= 1;
    }

    static void confirmPhotoTupleWithoutCandidate(Models.Identification id) {
        if (!canConfirmPhotoTupleWithoutCandidate(id)) {
            return;
        }
        String commercialName = printedCommercialName(id.photoIdentityFields);
        id.family = joinUnique(
                identityField(id.photoIdentityFields, "product_line", "product line", "set", "collection"),
                identityField(id.photoIdentityFields, "sport", "category"));
        id.model = stripLeadingBrand(commercialName, id.brand);
        id.marketReady = true;
        id.disproofPassed = true;
        id.modelProof = "physical_sealed_product_tuple";
        id.modelConfidence = Math.max(94, Math.min(97, id.photoIdentityConfidence));
        id.categoryConfidence = Math.max(id.categoryConfidence, 96);
        id.familyConfidence = Math.max(id.familyConfidence, 94);
        id.nextPhotoRequest = "";
        id.nextPhotoReason = "";
        id.verificationSummary = "Prodotto sigillato identificato dalla tupla commerciale completa "
                + "stampata sul fronte della confezione.";
        id.decisionReason = "CONFIRMED v0.97: tupla fisica completa del prodotto sigillato; barcode facoltativo.";
        UniversalIdentityClosure.apply(id, "legacy_sealed_tuple_gate_delegate");
    }

    static boolean hasPhotoTupleFamily(Models.Identification id) {
        return id != null && hasBoundManufacturer(id)
                && completePrintedTuple(id.photoIdentityFields)
                && !safe(id.family).isEmpty();
    }

    private static boolean completePrintedTuple(List<String> fields) {
        boolean manufacturer = !identityField(fields,
                "manufacturer", "brand", "maker", "publisher").isEmpty();
        boolean line = !identityField(fields,
                "product_line", "product line", "set", "collection").isEmpty();
        boolean format = !identityField(fields, "format", "box_format", "product_format").isEmpty();
        // Some sealed boxes identify their commercial configuration directly
        // (for example "1 Autograph Per Box") without repeating a separate
        // Hobby/Blaster token in the structured response.  That printed box
        // configuration is a SKU discriminator, not a generic object label.
        boolean boxConfiguration = !identityField(fields,
                "configuration", "pack_configuration", "box_configuration").isEmpty();
        boolean season = !identityField(fields,
                "season", "season/year", "season_year", "release", "year", "edition").isEmpty();
        return manufacturer && line && (format || boxConfiguration) && season;
    }

    /**
     * Some otherwise correct Vision responses omit photo_identity.complete or
     * a redundant format field. Recover only when the front itself carries a
     * brand, season and distinctive product-line phrase and a grounded source
     * names one high-confidence sealed SKU. This is deliberately narrower than
     * accepting a web title alone.
     */
    private static boolean groundedFrontTuple(Models.Identification id,
                                               Models.CandidateScore c) {
        if (id == null || c == null || c.probableReferenceConfidence < 90) {
            return false;
        }
        String observed = canon(joinUnique(safe(id.photoIdentityName),
                joinList(id.visibleLabels), joinList(id.photoIdentityFields),
                id.localScan == null ? "" : id.localScan.joinedText()));
        String candidateText = canon(joinUnique(c.probableReference, c.model, c.family));
        String brand = !safe(id.brand).isEmpty() ? id.brand : c.brand;
        String season = firstSeason(observed);
        if (season.isEmpty()) {
            season = firstSeason(candidateText);
        }
        boolean brandObserved = !canon(brand).isEmpty()
                && observed.contains(canon(brand));
        boolean seasonObserved = !season.isEmpty() && observed.contains(canon(season));
        String distinctiveLine = distinctiveLine(c.family, brand, season);
        boolean lineObserved = !distinctiveLine.isEmpty()
                && observed.contains(distinctiveLine);
        boolean sealedSkuNamed = candidateText.contains("HOBBY BOX")
                || candidateText.contains("BLASTER BOX")
                || candidateText.contains("RETAIL BOX")
                || candidateText.contains("SEALED BOX");
        return brandObserved && seasonObserved && lineObserved && sealedSkuNamed;
    }

    private static String bestCommercialName(Models.Identification id,
                                             Models.CandidateScore c) {
        String printed = printedCommercialName(id.photoIdentityFields);
        if (!printed.isEmpty()) {
            return printed;
        }
        String photo = collapseRepeatedBrand(safe(id.photoIdentityName), id.brand);
        String model = collapseRepeatedBrand(safe(c.model), id.brand);
        String probable = collapseRepeatedBrand(safe(c.probableReference), id.brand);
        String candidate = commercialSpecificity(probable) > commercialSpecificity(model)
                ? probable : model;
        String photoCanon = canon(photo);
        boolean photoNamesFormat = photoCanon.contains("HOBBY BOX")
                || photoCanon.contains("BLASTER BOX") || photoCanon.contains("RETAIL BOX");
        return photoNamesFormat && commercialSpecificity(photo) >= commercialSpecificity(candidate)
                ? photo : candidate;
    }

    private static String printedCommercialName(List<String> fields) {
        String season = identityField(fields, "season", "season/year", "season_year",
                "release", "year", "edition");
        String line = identityField(fields, "product_line", "product line", "set", "collection");
        String series = identityField(fields, "series");
        String sport = identityField(fields, "sport", "category");
        String format = identityField(fields, "format", "box_format", "product_format");
        String configuration = identityField(fields,
                "configuration", "pack_configuration", "box_configuration");
        if (season.isEmpty() || line.isEmpty()
                || (format.isEmpty() && configuration.isEmpty())) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        appendCommercialPart(out, season);
        appendCommercialPart(out, line);
        if (!(stemCanon(line).contains("UPDATE") && stemCanon(series).contains("UPDATE"))) {
            appendCommercialPart(out, series);
        }
        appendCommercialPart(out, sport);
        appendCommercialPart(out, format);
        appendCommercialPart(out, configuration);
        return out.toString().trim();
    }

    private static void appendCommercialPart(StringBuilder out, String value) {
        String part = safe(value);
        if (part.isEmpty()) {
            return;
        }
        String existing = stemCanon(out.toString());
        String wanted = stemCanon(part);
        if (!wanted.isEmpty() && existing.contains(wanted)) {
            return;
        }
        if (out.length() > 0) {
            out.append(' ');
        }
        out.append(part);
    }

    private static int commercialSpecificity(String value) {
        String x = canon(value);
        int score = x.length();
        if (firstSeason(x).length() > 0) score += 30;
        if (x.contains("HOBBY BOX") || x.contains("BLASTER BOX")
                || x.contains("RETAIL BOX")) score += 30;
        return score;
    }

    private static String stemCanon(String value) {
        return canon(value).replace("UPDATES", "UPDATE")
                .replace("CARDS", "CARD");
    }

    private static String collapseRepeatedBrand(String value, String brand) {
        String out = safe(value);
        String maker = safe(brand);
        if (maker.isEmpty()) {
            return out;
        }
        String[] tokens = out.split("\\s+");
        StringBuilder clean = new StringBuilder();
        boolean seen = false;
        for (String token : tokens) {
            if (token.equalsIgnoreCase(maker)) {
                if (seen) {
                    continue;
                }
                seen = true;
            }
            if (clean.length() > 0) {
                clean.append(' ');
            }
            clean.append(token);
        }
        return clean.toString().trim();
    }

    private static String distinctiveLine(String family, String brand, String season) {
        String line = canon(family);
        line = removeCanonPhrase(line, canon(brand));
        line = removeCanonPhrase(line, canon(season));
        line = removeCanonPhrase(line, "BASKETBALL");
        line = removeCanonPhrase(line, "FOOTBALL");
        line = removeCanonPhrase(line, "SOCCER");
        line = removeCanonPhrase(line, "BASEBALL");
        line = removeCanonPhrase(line, "TRADING CARDS");
        return line.trim().replaceAll("\\s+", " ");
    }

    private static String removeCanonPhrase(String source, String phrase) {
        if (phrase.isEmpty()) {
            return source;
        }
        return (" " + source + " ").replace(" " + phrase + " ", " ")
                .trim().replaceAll("\\s+", " ");
    }

    private static String firstSeason(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?<![0-9])((?:19|20)[0-9]{2})[ /-]([0-9]{2,4})(?![0-9])")
                .matcher(safe(text));
        return matcher.find() ? matcher.group(1) + " " + matcher.group(2) : "";
    }

    private static String joinList(List<String> values) {
        return values == null ? "" : String.join(" ", values);
    }

    private static int corroboratedTupleFields(Models.LocalScan local, List<String> fields) {
        String observed = " " + canon(local == null ? "" : local.joinedText()) + " ";
        int count = 0;
        for (String raw : fields) {
            String lower = safe(raw).toLowerCase(Locale.ROOT);
            if (lower.startsWith("manufacturer=") || lower.startsWith("brand=")) {
                continue;
            }
            String[] tokens = canon(fieldValue(raw)).split(" ");
            for (String token : tokens) {
                if (token.length() >= 5 && observed.contains(" " + token + " ")) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static boolean conflictingObservedBrand(Models.Identification id,
                                                     String manufacturer) {
        String wanted = canon(manufacturer);
        if (!safe(id.brand).isEmpty() && !canon(id.brand).equals(wanted)
                && id.brandRoleConfidence >= 85) {
            return true;
        }
        for (String label : id.brandLabels) {
            if (!canon(label).isEmpty() && !canon(label).equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    private static String identityField(List<String> fields, String... keys) {
        if (fields == null) {
            return "";
        }
        for (String key : keys) {
            String expected = canonicalFieldKey(key);
            for (String raw : fields) {
                String x = safe(raw);
                int split = x.indexOf('=');
                if (split < 1) {
                    split = x.indexOf(':');
                }
                if (split > 0 && canonicalFieldKey(x.substring(0, split)).equals(expected)) {
                    return safe(x.substring(split + 1));
                }
            }
        }
        return "";
    }

    private static String canonicalFieldKey(String value) {
        String key = safe(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if ("season_year".equals(key)) {
            return "season";
        }
        return key;
    }

    private static void removeSeasonMasqueradingAsSerial(Models.Identification id) {
        if (id == null) {
            return;
        }
        id.photoIdentityFields.removeIf(raw -> {
            String key = canonicalFieldKey(safe(raw).split("[=:]", 2)[0]);
            return "serial".equals(key) && isSeasonValue(fieldValue(raw));
        });
        if (isSeasonValue(id.photoIdentityCode)) {
            id.photoIdentityCode = "";
        }
    }

    private static boolean isSeasonValue(String value) {
        return safe(value).matches("(?i)(?:19|20)[0-9]{2}\s*[-/]\s*[0-9]{2,4}");
    }

    private static String fieldValue(String raw) {
        String x = safe(raw);
        int p = x.indexOf('=');
        return p >= 0 && p + 1 < x.length() ? safe(x.substring(p + 1)) : "";
    }

    private static boolean literalInLocalOcr(Models.LocalScan local, String value) {
        String wanted = canon(value);
        String observed = canon(local == null ? "" : local.joinedText());
        return !wanted.isEmpty() && (" " + observed + " ").contains(" " + wanted + " ");
    }

    private static void addOnce(List<String> values, String value) {
        String x = safe(value);
        if (x.isEmpty()) {
            return;
        }
        for (String old : values) {
            if (old.equalsIgnoreCase(x)) {
                return;
            }
        }
        values.add(x);
    }

    private static String canon(String value) {
        return safe(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ")
                .trim().replaceAll("\\s+", " ");
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean factTrue(Models.CandidateScore c, String key) {
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        for (String raw : c.candidateFacts) {
            String x = safe(raw).toLowerCase(Locale.ROOT);
            if (x.startsWith(prefix)) {
                return "true".equals(x.substring(prefix.length()).trim());
            }
        }
        return false;
    }

    private static String stripLeadingBrand(String value, String brand) {
        String out = safe(value);
        String maker = safe(brand);
        while (!maker.isEmpty() && out.toLowerCase(Locale.ROOT)
                .startsWith(maker.toLowerCase(Locale.ROOT) + " ")) {
            out = safe(out.substring(maker.length()));
        }
        return out;
    }

    private static String joinUnique(String... values) {
        StringBuilder out = new StringBuilder();
        String seen = " ";
        for (String value : values) {
            String clean = safe(value);
            if (clean.isEmpty()) {
                continue;
            }
            String canonical = " " + canon(clean) + " ";
            if (seen.contains(canonical)) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(clean);
            seen += canon(clean) + " ";
        }
        return out.toString();
    }
}
