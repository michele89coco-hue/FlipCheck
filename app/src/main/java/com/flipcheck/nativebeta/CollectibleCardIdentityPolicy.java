package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Integrity rules for cards, without changing the universal retrieval ladder. */
final class CollectibleCardIdentityPolicy {
    private static final Pattern THREE_PART_DATE = Pattern.compile(
            "(?<![0-9])([0-9]{1,2})[./-]([0-9]{1,2})[./-]([0-9]{2,4})(?![0-9])");
    private static final Pattern FRACTION = Pattern.compile(
            "(?<![0-9])([0-9]{1,5})\\s*/\\s*([0-9]{1,5})(?![0-9])");
    private static final Pattern ISOLATED_FRACTION = Pattern.compile(
            "^\\s*([0-9]{1,4})\\s*/\\s*([0-9]{1,4})\\s*$");
    private static final Pattern LOCATED_PHYSICAL_FRACTION = Pattern.compile(
            "(?i)^\\s*(?:physical_serial_marking|physical_print_run|card_surface_serial)"
                    + "\\s*[=:]\\s*([0-9]{1,4})\\s*/\\s*([0-9]{1,4})\\s*$");
    private static final Pattern PRINT_RUN_SUFFIX = Pattern.compile(
            "(?<![0-9])(?:[0-9]{1,5}\\s*)?/\\s*[0-9]{1,5}(?![0-9])");
    private static final Pattern CARD_CODE = Pattern.compile(
            "(?i)(?:\\b(?:card|no\\.?|number|#)\\s*[:#.-]?\\s*)?\\b([A-Z]{1,5}[0-9]{1,4}[A-Z]?)\\b");
    private static final Pattern BARE_YEAR = Pattern.compile(
            "(?i)^(?:year|date)?\\s*[=:]?\\s*((?:19|20)[0-9]{2}(?:[-/]?[0-9]{2,4})?)$");

    private CollectibleCardIdentityPolicy() {
    }

    static boolean isCard(Models.Identification id) {
        if (id == null) {
            return false;
        }
        if (SealedProductIdentityPolicy.isSealedRetailProduct(id)) {
            return false;
        }
        String value = (safe(id.categoryKey) + " " + safe(id.category)).toLowerCase(Locale.ROOT);
        return value.contains("trading_card") || value.contains("trading card")
                || value.contains("collectible_card") || value.contains("collectible card")
                || value.contains("sports card") || value.contains("figurina");
    }

    /** TCG collector numbers such as Pokemon 10/102 are checklist positions, not print runs. */
    static boolean isTradingCardGame(Models.Identification id) {
        if (!isCard(id)) {
            return false;
        }
        String categoryDomain = (safe(id.categoryKey) + " " + safe(id.category))
                .toLowerCase(Locale.ROOT);
        // Sports products such as Panini Adrenalyn XL may literally print
        // "TRADING CARD GAME". Player/team/sport classification outranks that
        // marketing phrase; otherwise the final gate wrongly demands a TCG
        // collector fraction and reopens an already exact sports identity.
        if (categoryDomain.contains("sports") || categoryDomain.contains("basketball")
                || categoryDomain.contains("football") || categoryDomain.contains("baseball")
                || categoryDomain.contains("soccer") || categoryDomain.contains("hockey")) {
            return false;
        }
        String value = (safe(id.categoryKey) + " " + safe(id.category) + " "
                + safe(id.brand) + " " + safe(id.photoIdentityName) + " "
                + id.visibleLabels + " " + id.photoIdentityFields)
                .toLowerCase(Locale.ROOT);
        String physical = (id.visualFacts + " " + id.photoIdentityFields + " "
                + id.visibleLabels).toLowerCase(Locale.ROOT);
        if ((physical.contains("player=") || physical.contains("athlete=")
                || physical.contains("team="))
                && (physical.contains(" nba") || physical.contains("nfl")
                || physical.contains("mlb") || physical.contains("nhl")
                || physical.contains("basketball") || physical.contains("football")
                || physical.contains("soccer") || physical.contains("baseball"))) {
            return false;
        }
        return value.contains("pokemon") || value.contains("pokémon")
                || value.contains("trading card game") || value.contains(" tcg")
                || value.contains("magic the gathering") || value.contains("mtg")
                || value.contains("yu-gi-oh") || value.contains("yugioh")
                || value.contains("lorcana") || value.contains("one piece card game");
    }

    static void sanitizeObservation(Models.Identification id, Models.LocalScan local) {
        if (!isCard(id)) {
            return;
        }
        normalizeTradingCardBrand(id);
        removeLikelyGameplayNumber(id, local);
        String localText = local == null ? "" : safe(local.joinedText());
        Set<String> dateFractions = dateFractions(localText);
        List<String> narrativeYears = narrativeYears(localText);
        String collectorNumber = observedTcgCollectorNumber(id, local);
        if (!collectorNumber.isEmpty()) {
            id.photoIdentityFields.removeIf(raw -> {
                String lower = safe(raw).toLowerCase(Locale.ROOT);
                boolean conflictingPlainNumber = lower.startsWith("card_number=")
                        && fraction(fieldValue(raw)).isEmpty();
                return conflictingPlainNumber || (lower.startsWith("serial=")
                        || lower.startsWith("serial_fraction="))
                        && collectorNumber.equals(fraction(fieldValue(raw)));
            });
            addOnce(id.photoIdentityFields, "card_number=" + collectorNumber);
        }
        String physicalSerial = observedPhysicalSerial(id, local, dateFractions,
                collectorNumber);
        if (!physicalSerial.isEmpty()) {
            id.photoIdentityFields.removeIf(raw -> {
                String x = safe(raw).toLowerCase(Locale.ROOT);
                return x.startsWith("serial=") || x.startsWith("serial_fraction=")
                        || isSerialBindingOnly(raw);
            });
            addOnce(id.photoIdentityFields, "serial=" + physicalSerial);
            addOnce(id.photoIdentityFields, "serial_binding=physical_card_surface");
            removeSerialConfoundedCardNumbers(id, local, physicalSerial);
            if (!containsToken(id.photoIdentityName, physicalSerial)) {
                id.photoIdentityName = safe(id.photoIdentityName) + " serial " + physicalSerial;
            }
        }
        boolean trustedSerial = hasPhysicalSerialBinding(id.photoIdentityFields)
                && hasPhysicalSerialValue(id)
                && !id.photoIdentityOverlayOrWatermark;
        List<String> cleaned = new ArrayList<>();
        for (String raw : id.photoIdentityFields) {
            String value = fieldValue(raw);
            String compactFraction = fraction(value);
            if (!compactFraction.isEmpty()
                    && ((!trustedSerial && !compactFraction.equals(collectorNumber))
                    || dateFractions.contains(compactFraction))) {
                continue;
            }
            if (isNarrativeOnlyYear(raw, value, narrativeYears)) {
                continue;
            }
            addOnce(cleaned, raw);
        }
        id.photoIdentityFields.clear();
        id.photoIdentityFields.addAll(cleaned);
        if (!trustedSerial) {
            id.photoIdentityFields.removeIf(raw -> isSerialBindingOnly(raw));
        }
        String codeFraction = fraction(id.photoIdentityCode);
        if (!codeFraction.isEmpty()
                && (!trustedSerial || dateFractions.contains(codeFraction))) {
            id.photoIdentityCode = "";
        }
        if (!trustedSerial && collectorNumber.isEmpty()) {
            for (String raw : new ArrayList<>(id.visibleLabels)) {
                if (!fraction(raw).isEmpty()) {
                    id.visibleLabels.remove(raw);
                    id.searchableLabels.remove(raw);
                }
            }
            id.photoIdentityName = removeFractions(id.photoIdentityName);
            id.photoIdentityName = removeDanglingSerial(id.photoIdentityName);
        }
        for (String falseFraction : dateFractions) {
            id.photoIdentityName = removeToken(id.photoIdentityName, falseFraction);
        }
        for (String year : narrativeYears) {
            if (!containsQualifiedSetYear(id.photoIdentityFields, year)) {
                id.photoIdentityName = removeToken(id.photoIdentityName, year);
            }
        }
        String cardNumber = observedCardNumber(id, local);
        if (!cardNumber.isEmpty()) {
            addOnce(id.photoIdentityFields, "card_number=" + cardNumber);
        }
        int stable = stableFieldCount(id);
        if (id.photoIdentityPhysicalBinding && !id.photoIdentityOverlayOrWatermark
                && observationViewsSufficient(id) && stable >= 4 && !cardNumber.isEmpty()) {
            id.photoIdentityComplete = true;
            id.photoIdentityKind = "composite_markings";
            id.photoIdentityConfidence = Math.max(id.photoIdentityConfidence, 94);
        }
    }

    /** Re-evaluates completeness after the exact catalog candidate supplies family context. */
    static void prepareForCandidateConfirmation(Models.Identification id,
                                                Models.CandidateScore candidate) {
        if (!isCard(id) || candidate == null || candidate.hardRejected
                || !id.photoIdentityPhysicalBinding || id.photoIdentityOverlayOrWatermark
                || !factTrue(candidate, "source_grounded")
                || !factTrue(candidate, "same_entity_role")
                || factTrue(candidate, "relationship_only")
                || !factTrue(candidate, "disproof_passed")) {
            return;
        }
        String observed = observedCardNumber(id, id.localScan);
        String proposed = candidateCardNumber(id, candidate);
        boolean exact = factTrue(candidate, "source_exact_reference")
                || factTrue(candidate, "exact_reference_complete");
        if (observed.isEmpty() || !observed.equalsIgnoreCase(proposed) || !exact) return;
        if (!safe(id.brand).isEmpty()) addOnce(id.photoIdentityFields, "manufacturer=" + id.brand);
        else if (!safe(candidate.brand).isEmpty()) addOnce(id.photoIdentityFields,
                "manufacturer=" + candidate.brand);
        if (!safe(candidate.family).isEmpty()) addOnce(id.photoIdentityFields,
                "set=" + candidate.family);
        addOnce(id.photoIdentityFields, "card_number=" + observed);
        if (identityViewsSufficient(id) && stableFieldCount(id) >= 4) {
            id.photoIdentityComplete = true;
            id.photoIdentityKind = "composite_markings";
            id.photoIdentityConfidence = Math.max(id.photoIdentityConfidence, 94);
            if (safe(id.photoIdentityName).isEmpty()) {
                id.photoIdentityName = safe(candidate.probableReference).isEmpty()
                        ? candidate.displayName() : candidate.probableReference;
            }
        }
    }

    private static boolean observationViewsSufficient(Models.Identification id) {
        if (!isTradingCardGame(id)) return complementaryViews(id);
        return hasFrontView(id);
    }

    static void applyCandidateGate(Models.Identification id, Models.CandidateScore candidate) {
        if (!isCard(id) || candidate == null) {
            return;
        }
        String physicalSerial = physicalSerialValue(id);
        if (physicalSerial.isEmpty()) {
            if (!isTradingCardGame(id)) {
                candidate.probableReference = removeFractions(candidate.probableReference);
                candidate.model = removeFractions(candidate.model);
            }
            candidate.probableReference = removeDanglingSerial(candidate.probableReference);
            candidate.model = removeDanglingSerial(candidate.model);
            candidate.candidateFacts.removeIf(raw -> {
                String x = safe(raw).toLowerCase(Locale.ROOT);
                return (x.startsWith("serial=") || x.startsWith("serial_fraction=")
                        || x.startsWith("print_run=")) && !fraction(x).isEmpty();
            });
        } else {
            candidate.probableReference = removeOtherFractions(
                    candidate.probableReference, physicalSerial);
            candidate.model = removeOtherFractions(candidate.model, physicalSerial);
            candidate.candidateFacts.removeIf(raw -> {
                String x = safe(raw).toLowerCase(Locale.ROOT);
                String found = fraction(x);
                boolean serialFact = x.startsWith("serial=")
                        || x.startsWith("serial_fraction=")
                        || x.startsWith("print_run=");
                if (!serialFact) {
                    return false;
                }
                if (!found.isEmpty()) {
                    return !found.equals(physicalSerial);
                }
                return PRINT_RUN_SUFFIX.matcher(x).find();
            });
            candidate.contradictions.removeIf(raw ->
                    serialOmissionOnly(raw, physicalSerial));
            candidate.hardViolations.removeIf(raw ->
                    serialOmissionOnly(raw, physicalSerial));
            addOnce(candidate.candidateFacts, "physical_serial=" + physicalSerial);
            addOnce(candidate.candidateFacts, "physical_serial_binding=true");
        }
        String observed = observedCardNumber(id, id.localScan);
        String proposed = candidateCardNumber(id, candidate);
        if (!observed.isEmpty() && !proposed.isEmpty()
                && samePrefix(observed, proposed) && !observed.equalsIgnoreCase(proposed)) {
            candidate.hardRejected = true;
            addOnce(candidate.hardViolations,
                    "physical card number " + observed + " conflicts with candidate " + proposed);
            addOnce(candidate.contradictions,
                    "STRONG: photographed card number " + observed
                            + " does not match candidate " + proposed);
        } else if (!observed.isEmpty() && observed.equalsIgnoreCase(proposed)) {
            addOnce(candidate.candidateFacts, "physical_card_number_match=true");
            addOnce(candidate.candidateFacts, "physical_card_number=" + observed);
        }
    }

    static int supportedScore(Models.Identification id, Models.CandidateScore candidate) {
        if (!baseSupported(id, candidate)) {
            return 0;
        }
        int score = Math.round(id.photoIdentityConfidence * 0.35f
                + candidate.textScore * 0.30f + candidate.webScore * 0.35f);
        return Math.max(86, Math.min(96, score));
    }

    static boolean canConfirm(Models.Identification id, Models.CandidateScore candidate) {
        return baseSupported(id, candidate) && supportedScore(id, candidate) >= 86
                && factInt(candidate, "source_identity_confidence") >= 78
                && !variantUnresolved(id, candidate);
    }

    static boolean variantUnresolved(Models.Identification id,
                                     Models.CandidateScore candidate) {
        StringBuilder text = new StringBuilder(join(id.visionIdentityReason,
                id.verificationSummary, id.decisionReason, id.visionCandidates.toString()));
        if (candidate != null) {
            text.append(' ').append(candidate.evidence).append(' ')
                    .append(candidate.contradictions).append(' ')
                    .append(candidate.hardViolations);
        }
        String x = text.toString().toLowerCase(Locale.ROOT);
        boolean explicitlyUnresolved = x.contains("variant unresolved") || x.contains("variant is unresolved")
                || x.contains("parallel unresolved")
                || x.contains("parallel name unresolved")
                || x.contains("exact parallel name") && x.contains("unresolved")
                || x.contains("does not reliably distinguish")
                || x.contains("does not distinguish")
                || x.contains("cannot distinguish")
                || x.contains("possible unlimited or shadowless")
                || x.contains("unlimited or shadowless")
                || x.contains("shadowless from unlimited");
        if (!isTradingCardGame(id)) {
            return explicitlyUnresolved;
        }
        if (isPokemonBaseSet(id)) {
            return explicitlyUnresolved || printingVariant(id, candidate).isEmpty();
        }
        return explicitlyUnresolved;
    }

    static void confirm(Models.Identification id, Models.CandidateScore candidate) {
        PhotoIdentityPolicy.confirm(id, candidate);
        normalizeConfirmedIdentity(id, candidate);
        id.modelConfidence = Math.max(id.modelConfidence,
                Math.min(97, Math.max(91, candidate.totalScore)));
        id.verificationSummary = "Identità della carta verificata tramite campi fisici stabili "
                + "(produttore/set, soggetto e numero carta) e fonte coerente. Date biografiche, "
                + "testo narrativo e copyright non sono stati usati come anno del set o seriale.";
        id.decisionReason = "CONFIRMED v0.85: identità catalogo della carta + numero fisico "
                + "coerente + fonte esatta + disproof superato.";
        String serial = physicalSerialValue(id);
        if (!serial.isEmpty()) {
            id.verificationSummary += " Tiratura fisica " + serial
                    + " verificata sulla carta; il nome commerciale esatto del parallel "
                    + "non viene inventato quando non è dimostrato dalla fonte.";
        }
    }

    /**
     * A readable card must not collapse back to only "sports card" or a set
     * name while an exact, same-entity catalog reference is already the best
     * grounded candidate. This exposes that reference as PROBABLE without
     * weakening the stricter confirmation gate.
     */
    static void exposeBestSpecificProbable(Models.Identification id,
                                           Models.CandidateScore candidate) {
        if (!isCard(id) || id.marketReady || candidate == null || candidate.hardRejected
                || !factTrue(candidate, "source_grounded")
                || !factTrue(candidate, "same_entity_role")
                || factTrue(candidate, "relationship_only")
                || UniversalConsistencyGate.strongCandidateConflict(candidate)) return;
        String reference = safe(candidate.probableReference);
        if (reference.isEmpty() && factTrue(candidate, "exact_reference_complete")) {
            reference = safe(candidate.displayName());
        }
        String observed = observedCardNumber(id, id.localScan);
        String proposed = candidateCardNumber(id, candidate);
        boolean numberCoherent = observed.isEmpty() || proposed.isEmpty()
                || observed.equalsIgnoreCase(proposed);
        if (reference.isEmpty() || !numberCoherent
                || candidate.probableReferenceConfidence < 70
                && !factTrue(candidate, "exact_reference_complete")) return;
        id.model = reference;
        id.modelConfidence = Math.max(id.modelConfidence,
                Math.min(84, Math.max(55, candidate.totalScore)));
        if (!candidate.family.isEmpty()) id.family = candidate.family;
        if (!candidate.brand.isEmpty() && id.brand.isEmpty()) id.brand = candidate.brand;
        id.verificationSummary = "Miglior identità specifica supportata: " + reference
                + ". È mostrata come probabile finché la verifica discriminante non chiude "
                + "tutti gli attributi fisici della variante.";
    }

    private static boolean baseSupported(Models.Identification id, Models.CandidateScore c) {
        if (!isCard(id) || c == null || c.hardRejected || !id.photoIdentityComplete
                || !id.photoIdentityPhysicalBinding || id.photoIdentityOverlayOrWatermark
                || stableFieldCount(id) < 4 || !identityViewsSufficient(id)
                || !factTrue(c, "source_grounded") || !factTrue(c, "disproof_passed")
                || factTrue(c, "relationship_only") || !factTrue(c, "same_entity_role")) {
            return false;
        }
        String observed = observedCardNumber(id, id.localScan);
        String proposed = candidateCardNumber(id, c);
        boolean numberMatches = !observed.isEmpty() && observed.equalsIgnoreCase(proposed);
        boolean exactVisualTuple = factTrue(c, "exact_card_visual_tuple")
                && factTrue(c, "visual_reference_checked")
                && factInt(c, "visual_match_confidence") >= 92;
        boolean exactSource = factTrue(c, "source_exact_reference")
                || factTrue(c, "exact_reference_complete");
        return (numberMatches || exactVisualTuple)
                && exactSource && c.textScore >= 82 && c.webScore >= 55;
    }

    static void normalizeConfirmedIdentity(Models.Identification id,
                                            Models.CandidateScore candidate) {
        if (id == null || !isCard(id)) {
            return;
        }
        String collector = observedCardNumber(id, id.localScan);
        String current = safe(id.model);
        if (current.equalsIgnoreCase(collector) || current.length() < 8) {
            String replacement = candidate == null ? "" : safe(candidate.probableReference);
            if (replacement.isEmpty() && candidate != null) {
                replacement = safe(candidate.displayName());
            }
            if (!replacement.isEmpty()) {
                id.model = replacement;
            }
        }
        if (isTradingCardGame(id)) {
            String printing = printingVariant(id, candidate);
            if (isPokemonBaseSet(id) && !printing.isEmpty()) {
                id.model = removePrintingVariantWords(id.model);
            }
            appendIdentityAxis(id, printing);
        } else {
            String subject = identityField(id.photoIdentityFields, "subject", "player", "athlete");
            if (!subject.isEmpty() && !containsToken(id.model, subject)) {
                String number = observedCardNumber(id, id.localScan);
                String parallel = physicalParallel(id);
                StringBuilder rebuilt = new StringBuilder(subject);
                if (!parallel.isEmpty()) rebuilt.append(' ').append(parallel);
                if (!number.isEmpty()) rebuilt.append(" #").append(number.replace("#", ""));
                id.model = rebuilt.toString().trim();
            }
        }
        appendIdentityAxis(id, physicalParallel(id));
        appendIdentityAxis(id, physicalFinish(id));
        appendIdentityAxis(id, physicalRookieMarker(id));
    }

    private static void appendIdentityAxis(Models.Identification id, String value) {
        String axis = safe(value);
        if (axis.isEmpty() || containsToken(id.model, axis)) {
            return;
        }
        id.model = safe(id.model) + " — " + axis;
    }

    private static String physicalParallel(Models.Identification id) {
        return identityField(id.photoIdentityFields, "parallel", "physical_parallel");
    }

    private static String physicalFinish(Models.Identification id) {
        String holo = identityField(id.photoIdentityFields,
                "holo", "holofoil", "holo_or_foil", "finish");
        String normalized = safe(holo).toLowerCase(Locale.ROOT);
        if (normalized.equals("present") || normalized.equals("true")
                || normalized.equals("holo") || normalized.equals("holofoil")
                || normalized.contains("holographic")) {
            return "Holo";
        }
        if (normalized.equals("absent") || normalized.equals("false")
                || normalized.equals("non-holo") || normalized.equals("non holo")) {
            return "non-holo";
        }
        // A named foil/parallel (for example green_prismatic_foil) is already
        // represented by the physical parallel and must not become a second,
        // awkward finish suffix.
        return "";
    }

    private static String physicalRookieMarker(Models.Identification id) {
        String rookie = identityField(id.photoIdentityFields,
                "rookie_card", "rookie", "rc");
        String normalized = safe(rookie).toLowerCase(Locale.ROOT);
        return normalized.equals("present") || normalized.equals("true")
                || normalized.equals("yes") || normalized.equals("rc") ? "RC" : "";
    }

    static boolean modelIsOnlyCollectorNumber(Models.Identification id) {
        if (id == null || !isTradingCardGame(id)) {
            return false;
        }
        String number = observedCardNumber(id, id.localScan);
        return !number.isEmpty() && safe(id.model).replace("#", "")
                .trim().equalsIgnoreCase(number);
    }

    private static boolean identityViewsSufficient(Models.Identification id) {
        if (!isTradingCardGame(id)) {
            return complementaryViews(id);
        }
        return hasFrontView(id) && (!isPokemonBaseSet(id)
                || !printingVariant(id, null).isEmpty());
    }

    private static boolean hasFrontView(Models.Identification id) {
        if (id.photoViews.isEmpty()) {
            return true;
        }
        for (String view : id.photoViews) {
            String x = safe(view).toLowerCase(Locale.ROOT);
            if (x.contains("front") || x.contains("fronte")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPokemonBaseSet(Models.Identification id) {
        if (id == null) {
            return false;
        }
        String x = (safe(id.brand) + " " + safe(id.family) + " "
                + safe(id.photoIdentityName) + " " + id.photoIdentityFields
                + " " + id.visibleLabels).toLowerCase(Locale.ROOT);
        return (x.contains("pokemon") || x.contains("pokémon"))
                && x.contains("base set");
    }

    private static String printingVariant(Models.Identification id,
                                          Models.CandidateScore candidate) {
        List<String> values = new ArrayList<>();
        if (id != null) {
            values.addAll(id.photoIdentityFields);
            values.addAll(id.visualFacts);
            values.addAll(id.spatialSignature);
        }
        if (isPokemonBaseSet(id)) {
            return baseSetPrintingFromPhysicalCues(values);
        }
        if (candidate != null) {
            values.addAll(candidate.candidateFacts);
        }
        for (String raw : values) {
            String x = safe(raw).toLowerCase(Locale.ROOT);
            boolean physical = x.startsWith("printing=") || x.startsWith("edition=")
                    || x.startsWith("variant=") || x.startsWith("physical_printing=")
                    || x.startsWith("physical_variant=");
            if (physical && x.contains("shadowless")) {
                return "Shadowless";
            }
            if (physical && x.contains("unlimited")) {
                return "Unlimited";
            }
            if (physical && (x.contains("first edition") || x.contains("1st edition"))) {
                return "1st Edition";
            }
        }
        return "";
    }

    /**
     * A model-provided edition label is not evidence by itself. Base Set
     * printing is derived only from separately reported, physically visible
     * front-layout cues. This prevents a dark holo/background edge from being
     * mistaken for the Unlimited illustration-frame drop shadow.
     */
    private static String baseSetPrintingFromPhysicalCues(List<String> values) {
        int firstEditionPresent = 0;
        int firstEditionAbsent = 0;
        int firstEditionExpectedPosition = 0;
        int firstEditionOtherPosition = 0;
        int firstEditionAreaClear = 0;
        int frameShadowPresent = 0;
        int frameShadowAbsent = 0;
        int shadowlessLayout = 0;
        int unlimitedLayout = 0;
        int nintendoCopyright99Present = 0;
        int nintendoCopyright99Absent = 0;
        for (String raw : values) {
            String x = safe(raw).toLowerCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_');
            if (cue(x, "first_edition_stamp", "present")
                    || cue(x, "first_edition_stamp", "visible")
                    || cue(x, "first_edition_stamp", "true")) {
                firstEditionPresent++;
            }
            if (cue(x, "first_edition_stamp", "absent")
                    || cue(x, "first_edition_stamp", "none")
                    || cue(x, "first_edition_stamp", "false")) {
                firstEditionAbsent++;
            }
            if (cue(x, "first_edition_stamp_position", "left_below_artwork")
                    || cue(x, "first_edition_stamp_position", "expected")) {
                firstEditionExpectedPosition++;
            }
            if (cue(x, "first_edition_stamp_position", "other")) {
                firstEditionOtherPosition++;
            }
            if (cue(x, "first_edition_stamp_area_clear", "true")
                    || cue(x, "first_edition_stamp_area_clear", "yes")) {
                firstEditionAreaClear++;
            }
            if (cue(x, "illustration_frame_drop_shadow", "present")
                    || cue(x, "illustration_frame_shadow", "present")) {
                frameShadowPresent++;
            }
            if (cue(x, "illustration_frame_drop_shadow", "absent")
                    || cue(x, "illustration_frame_shadow", "absent")) {
                frameShadowAbsent++;
            }
            if ((x.startsWith("copyright_layout=") || x.startsWith("base_set_layout="))
                    && x.contains("shadowless")) {
                shadowlessLayout++;
            }
            if ((x.startsWith("copyright_layout=") || x.startsWith("base_set_layout="))
                    && x.contains("unlimited")) {
                unlimitedLayout++;
            }
            if (cue(x, "nintendo_copyright_99", "present")
                    || cue(x, "base_set_nintendo_copyright_99", "present")) {
                nintendoCopyright99Present++;
            }
            if (cue(x, "nintendo_copyright_99", "absent")
                    || cue(x, "base_set_nintendo_copyright_99", "absent")) {
                nintendoCopyright99Absent++;
            }
        }
        boolean stampConflict = (firstEditionPresent > 0 && firstEditionAbsent > 0)
                || (firstEditionPresent > 0 && firstEditionOtherPosition > 0)
                || (firstEditionExpectedPosition > 0 && firstEditionOtherPosition > 0);
        boolean shadowConflict = frameShadowPresent > 0 && frameShadowAbsent > 0;
        boolean layoutConflict = shadowlessLayout > 0 && unlimitedLayout > 0;
        boolean copyrightConflict = nintendoCopyright99Present > 0
                && nintendoCopyright99Absent > 0;
        if (stampConflict || layoutConflict || copyrightConflict) {
            return "";
        }
        // Edition stamp and print layout are independent physical axes. An
        // English Base Set card can therefore be 1st Edition Shadowless, while
        // some Machamp printings carry the stamp on a later shadowed layout.
        // No subject/card name participates in this decision.
        boolean localizedFirstEdition = firstEditionPresent > 0
                && firstEditionExpectedPosition > 0
                && firstEditionAbsent == 0 && firstEditionOtherPosition == 0;
        // Backward-compatible fallback for responses produced before the
        // position field existed: require the coherent shadowless frame cue.
        boolean legacyLocalizedFirstEdition = firstEditionPresent > 0
                && firstEditionExpectedPosition == 0
                && firstEditionOtherPosition == 0 && frameShadowAbsent > 0
                && unlimitedLayout == 0;
        boolean firstEdition = localizedFirstEdition || legacyLocalizedFirstEdition;
        boolean shadowlessPhysicalLayout = nintendoCopyright99Present > 0
                && nintendoCopyright99Absent == 0
                && (shadowlessLayout > 0 || frameShadowAbsent > 0);
        boolean shadowedPhysicalLayout = nintendoCopyright99Absent > 0
                && nintendoCopyright99Present == 0 && frameShadowPresent > 0
                && unlimitedLayout > 0;
        if (firstEdition) {
            if (shadowlessPhysicalLayout) {
                return "1st Edition Shadowless";
            }
            if (shadowedPhysicalLayout) {
                return "1st Edition Shadowed";
            }
            return "1st Edition";
        }
        // The discriminator is the extra "99" inside the Nintendo year
        // sequence, not the ordinary final Wizards ©1999 text. The latter is
        // present on multiple Base Set printings and previously produced false
        // Unlimited/Shadowless decisions. This physical cue outranks a dark
        // edge produced by a sleeve, glare or holographic artwork.
        if (firstEditionAbsent > 0 && firstEditionAreaClear > 0
                && nintendoCopyright99Present > 0
                && nintendoCopyright99Absent == 0) {
            return "Shadowless";
        }
        if (firstEditionAbsent > 0 && firstEditionAreaClear > 0
                && nintendoCopyright99Absent > 0
                && nintendoCopyright99Present == 0 && frameShadowPresent > 0
                && unlimitedLayout > 0) {
            return "Unlimited";
        }
        // Without the localized stamp or the Nintendo-sequence 99 cue, keep
        // the printing open. A frame-shadow/layout opinion alone is too easy
        // to corrupt with sleeves, glare and dark holo artwork.
        return "";
    }

    /** Illustrator credits and artist names can never become card brands. */
    private static void normalizeTradingCardBrand(Models.Identification id) {
        if (id == null || !isTradingCardGame(id)) {
            return;
        }
        String illustrator = identityField(id.photoIdentityFields,
                "illustrator", "artist", "illus");
        String manufacturer = identityField(id.photoIdentityFields,
                "manufacturer", "brand", "publisher");
        String all = (safe(id.photoIdentityName) + " " + id.visibleLabels + " "
                + id.photoIdentityFields).toLowerCase(Locale.ROOT);
        if ((all.contains("pokemon") || all.contains("pokémon"))
                && (manufacturer.isEmpty() || brandLooksLikeCredit(manufacturer, illustrator)
                || manufacturer.equalsIgnoreCase("Nintendo"))) {
            manufacturer = "Pokémon";
        }
        if (manufacturer.isEmpty() || brandLooksLikeCredit(manufacturer, illustrator)) {
            return;
        }
        if (safe(id.brand).isEmpty() || brandLooksLikeCredit(id.brand, illustrator)
                || (all.contains("pokemon") || all.contains("pokémon"))) {
            id.brand = manufacturer;
            id.brandEvidence = "visible_brand_text";
            id.brandRoleConfidence = Math.max(id.brandRoleConfidence, 95);
            id.brandLabels.removeIf(raw -> brandLooksLikeCredit(raw, illustrator));
            addOnce(id.brandLabels, manufacturer);
            addOnce(id.searchableLabels, manufacturer);
        }
    }

    private static boolean brandLooksLikeCredit(String value, String illustrator) {
        String x = safe(value).trim().toLowerCase(Locale.ROOT);
        String artist = safe(illustrator).trim().toLowerCase(Locale.ROOT);
        return x.startsWith("illus.") || x.startsWith("illus ")
                || x.startsWith("illustrated by") || x.startsWith("illustrator")
                || x.startsWith("artist") || (!artist.isEmpty() && x.equals(artist));
    }

    private static String identityField(List<String> fields, String... keys) {
        if (fields == null) {
            return "";
        }
        for (String raw : fields) {
            String clean = safe(raw).trim();
            int split = clean.indexOf('=');
            if (split < 1) {
                split = clean.indexOf(':');
            }
            if (split < 1) {
                continue;
            }
            String key = clean.substring(0, split).trim().toLowerCase(Locale.ROOT)
                    .replace(' ', '_');
            for (String expected : keys) {
                if (key.equals(expected.toLowerCase(Locale.ROOT).replace(' ', '_'))) {
                    return clean.substring(split + 1).trim();
                }
            }
        }
        return "";
    }

    private static boolean cue(String normalized, String key, String value) {
        return normalized.startsWith(key + "=")
                && normalized.substring(key.length() + 1).equals(value);
    }

    private static String removePrintingVariantWords(String source) {
        return safe(source)
                .replaceAll("(?i)(?:\\s*[—-]\\s*)?\\b(?:Shadowless|Shadowed|Unlimited|1st Edition|First Edition)\\b", " ")
                .replaceAll("\\s{2,}", " ").trim();
    }

    static String observedCardNumber(Models.Identification id, Models.LocalScan local) {
        if (id == null) {
            return "";
        }
        String tcgNumber = observedTcgCollectorNumber(id, local);
        if (!tcgNumber.isEmpty()) {
            return tcgNumber;
        }
        List<String> sources = new ArrayList<>();
        // Structured visual facts are the most reliable location for a TCG
        // checklist fraction. They must outrank unrelated narrative numbers
        // such as a Pokedex number, player uniform number or attack value.
        sources.addAll(id.visualFacts);
        sources.addAll(id.photoIdentityFields);
        sources.addAll(id.visibleLabels);
        sources.addAll(id.identifierLabels);
        if (local != null) {
            sources.addAll(local.textByImage);
        }
        for (String raw : sources) {
            String x = safe(raw).trim();
            Matcher explicit = Pattern.compile(
                    "(?i)(?:card_number|item_number|card|no\\.?|#)\\s*[=: #.-]*([A-Z]{0,5}[0-9]{1,4}[A-Z]?)")
                    .matcher(x);
            if (explicit.find()) {
                String value = explicit.group(1).toUpperCase(Locale.ROOT);
                if (!isTradingCardGame(id) && value.matches("[0-9]{1,3}")
                        && !explicitCardNumberVisible(id, local, value)) continue;
                if (!value.matches("(?:19|20)[0-9]{2}")) {
                    return value;
                }
            }
        }
        for (String raw : sources) {
            for (String line : safe(raw).split("[\\r\\n,;]+")) {
                String x = fieldValue(line).trim().toUpperCase(Locale.ROOT);
                Matcher matcher = CARD_CODE.matcher(x);
                if (matcher.matches()) {
                    String value = matcher.group(1).toUpperCase(Locale.ROOT);
                    if (!isTradingCardGame(id) && value.matches("[0-9]{1,3}")) continue;
                    if (!value.matches("(?:19|20)[0-9]{2}")) {
                        return value;
                    }
                }
            }
        }
        return "";
    }

    /** A short sports-game stat is never promoted to collector number without a physical No./# label. */
    private static void removeLikelyGameplayNumber(Models.Identification id,
                                                   Models.LocalScan local) {
        if (isTradingCardGame(id)) return;
        String all = (id.visibleLabels + " " + id.visualFacts + " "
                + id.photoIdentityFields + " " + (local == null ? "" : local.joinedText()))
                .toUpperCase(Locale.ROOT);
        boolean statsLayout = all.matches("(?s).*\\b(?:DEF|OFF|ATT|OVR|RATING|POWER)\\s*[:= ]*\\d{1,3}\\b.*");
        if (!statsLayout) return;
        java.util.function.Predicate<String> falseNumber = raw -> {
            String x = safe(raw);
            int p = x.indexOf('=');
            if (p < 1) p = x.indexOf(':');
            if (p < 1 || !x.substring(0, p).trim().equalsIgnoreCase("card_number")) return false;
            String value = x.substring(p + 1).trim().toUpperCase(Locale.ROOT);
            return value.matches("[0-9]{1,3}") && !explicitCardNumberVisible(id, local, value);
        };
        id.visualFacts.removeIf(falseNumber);
        id.photoIdentityFields.removeIf(falseNumber);
    }

    private static String candidateCardNumber(Models.Identification id,
                                              Models.CandidateScore c) {
        StringBuilder joined = new StringBuilder(join(c.brand, c.family, c.model,
                c.probableReference, c.evidence));
        for (String fact : c.candidateFacts) {
            joined.append(' ').append(fact);
        }
        String candidateText = joined.toString().toUpperCase(Locale.ROOT);
        if (isTradingCardGame(id)) {
            Matcher collector = FRACTION.matcher(candidateText);
            if (collector.find()) {
                return Integer.parseInt(collector.group(1)) + "/"
                        + Integer.parseInt(collector.group(2));
            }
        }
        Matcher explicit = Pattern.compile(
                "(?i)(?:CARD(?:_NUMBER)?|ITEM_NUMBER|NO\\.?|#)\\s*[=: #.-]*([A-Z]{0,5}[0-9]{1,4}[A-Z]?)")
                .matcher(candidateText);
        if (explicit.find()) {
            return explicit.group(1).toUpperCase(Locale.ROOT);
        }
        Matcher matcher = CARD_CODE.matcher(candidateText);
        String last = "";
        while (matcher.find()) {
            String value = matcher.group(1).toUpperCase(Locale.ROOT);
            if (!value.matches("(?:19|20)[0-9]{2}")) {
                last = value;
                if (value.matches("[A-Z]{1,5}[0-9]{1,4}[A-Z]?")) {
                    return value;
                }
            }
        }
        return last;
    }

    private static Set<String> dateFractions(String text) {
        Set<String> out = new HashSet<>();
        Matcher matcher = THREE_PART_DATE.matcher(safe(text));
        while (matcher.find()) {
            out.add(Integer.parseInt(matcher.group(1)) + "/"
                    + Integer.parseInt(matcher.group(2)));
        }
        return out;
    }

    /**
     * A short, isolated x/y token printed on a physical card is the specimen's
     * print run. Four-digit season ranges and prefixes of three-part dates are
     * deliberately excluded.
     */
    private static String observedPhysicalSerial(Models.Identification id,
                                                 Models.LocalScan local,
                                                 Set<String> dateFractions,
                                                 String collectorNumber) {
        if (id == null || !id.photoIdentityPhysicalBinding
                || id.photoIdentityOverlayOrWatermark) {
            return "";
        }
        // For TCGs, an isolated x/y is normally the collector number within the
        // set. A numbered TCG parallel needs explicit limited/serial context;
        // it must never be inferred from the collector number alone.
        if (isTradingCardGame(id)) {
            return explicitTcgSerial(id, collectorNumber);
        }
        List<String> sources = new ArrayList<>();
        sources.addAll(id.visibleLabels);
        sources.addAll(id.controlLabels);
        sources.addAll(id.transientLabels);
        if (local != null) {
            sources.addAll(local.textByImage);
        }
        for (String raw : sources) {
            for (String line : safe(raw).split("[\\r\\n,;]+")) {
                Matcher matcher = ISOLATED_FRACTION.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }
                int numerator = Integer.parseInt(matcher.group(1));
                int denominator = Integer.parseInt(matcher.group(2));
                String value = numerator + "/" + denominator;
                if (denominator <= 0 || numerator > denominator
                        || dateFractions.contains(value)
                        || isSeasonFraction(matcher.group(1), matcher.group(2))) {
                    continue;
                }
                return value;
            }
        }
        List<String> located = new ArrayList<>();
        located.addAll(id.visualFacts);
        located.addAll(id.spatialSignature);
        located.addAll(id.photoIdentityFields);
        for (String raw : located) {
            Matcher matcher = LOCATED_PHYSICAL_FRACTION.matcher(safe(raw));
            if (!matcher.matches()) {
                continue;
            }
            int numerator = Integer.parseInt(matcher.group(1));
            int denominator = Integer.parseInt(matcher.group(2));
            String value = numerator + "/" + denominator;
            if (denominator > 0 && numerator <= denominator
                    && !dateFractions.contains(value)
                    && !isSeasonFraction(matcher.group(1), matcher.group(2))) {
                return value;
            }
        }
        return "";
    }

    private static String explicitTcgSerial(Models.Identification id,
                                            String collectorNumber) {
        List<String> sources = new ArrayList<>();
        sources.addAll(id.visualFacts);
        sources.addAll(id.photoIdentityFields);
        for (String raw : sources) {
            String lower = safe(raw).toLowerCase(Locale.ROOT);
            boolean explicit = lower.startsWith("limited_serial_marking=")
                    || lower.startsWith("numbered_serial_marking=")
                    || lower.startsWith("physical_print_run=");
            String value = fraction(raw);
            if (explicit && !value.isEmpty() && !value.equals(collectorNumber)) {
                return value;
            }
        }
        return "";
    }

    private static String observedTcgCollectorNumber(Models.Identification id,
                                                     Models.LocalScan local) {
        if (!isTradingCardGame(id)) {
            return "";
        }
        List<String> sources = new ArrayList<>();
        sources.addAll(id.photoIdentityFields);
        sources.addAll(id.visibleLabels);
        sources.addAll(id.identifierLabels);
        if (local != null) {
            sources.addAll(local.textByImage);
        }
        for (String raw : sources) {
            String lower = safe(raw).toLowerCase(Locale.ROOT);
            if (lower.startsWith("card_number=")
                    || lower.startsWith("collector_number=")) {
                String value = fraction(fieldValue(raw));
                if (!value.isEmpty() && validFraction(value)) {
                    return value;
                }
            }
        }
        for (String raw : sources) {
            for (String line : safe(raw).split("[\\r\\n,;]+")) {
                Matcher matcher = ISOLATED_FRACTION.matcher(line);
                if (matcher.matches()) {
                    String value = Integer.parseInt(matcher.group(1)) + "/"
                            + Integer.parseInt(matcher.group(2));
                    if (validFraction(value)) {
                        return value;
                    }
                }
            }
        }
        return "";
    }

    private static boolean validFraction(String value) {
        Matcher matcher = ISOLATED_FRACTION.matcher(safe(value));
        if (!matcher.matches()) {
            return false;
        }
        int numerator = Integer.parseInt(matcher.group(1));
        int denominator = Integer.parseInt(matcher.group(2));
        return denominator > 0 && numerator > 0 && numerator <= denominator
                && !isSeasonFraction(matcher.group(1), matcher.group(2));
    }

    private static boolean isSeasonFraction(String left, String right) {
        if (left == null || right == null || left.length() != 4 || right.length() != 2) {
            return false;
        }
        int year = Integer.parseInt(left);
        return year >= 1900 && year <= 2099;
    }

    private static void removeSerialConfoundedCardNumbers(Models.Identification id,
                                                           Models.LocalScan local,
                                                           String serial) {
        Matcher serialParts = ISOLATED_FRACTION.matcher(serial);
        if (!serialParts.matches()) {
            return;
        }
        String numerator = serialParts.group(1);
        String denominator = serialParts.group(2);
        id.photoIdentityFields.removeIf(raw -> {
            String lower = safe(raw).toLowerCase(Locale.ROOT);
            if (!lower.startsWith("card_number=")) {
                return false;
            }
            String value = fieldValue(raw).toUpperCase(Locale.ROOT);
            Matcher digits = Pattern.compile("[A-Z]*([0-9]{1,4})[A-Z]*").matcher(value);
            if (!digits.matches()) {
                return false;
            }
            String number = digits.group(1);
            return (number.equals(numerator) || number.equals(denominator))
                    && !explicitCardNumberVisible(id, local, value);
        });
    }

    private static boolean explicitCardNumberVisible(Models.Identification id,
                                                      Models.LocalScan local,
                                                      String value) {
        StringBuilder text = new StringBuilder();
        if (local != null) {
            text.append(local.joinedText()).append('\n');
        }
        for (String label : id.visibleLabels) {
            text.append(label).append('\n');
        }
        Pattern explicit = Pattern.compile("(?i)(?:CARD(?:_NUMBER)?|ITEM|NO\\.?|#)"
                + "\\s*[=: #.-]*" + Pattern.quote(value) + "(?![A-Z0-9])");
        return explicit.matcher(text).find();
    }

    private static String physicalSerialValue(Models.Identification id) {
        if (id == null || !hasPhysicalSerialBinding(id.photoIdentityFields)) {
            return "";
        }
        for (String raw : id.photoIdentityFields) {
            String lower = safe(raw).toLowerCase(Locale.ROOT);
            if (lower.startsWith("serial=") || lower.startsWith("serial_fraction=")) {
                return fraction(fieldValue(raw));
            }
        }
        return "";
    }

    private static String removeOtherFractions(String source, String allowed) {
        Matcher matcher = PRINT_RUN_SUFFIX.matcher(safe(source));
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String found = fraction(matcher.group());
            matcher.appendReplacement(out, found.equals(allowed)
                    ? Matcher.quoteReplacement(matcher.group()) : " ");
        }
        matcher.appendTail(out);
        return out.toString().replaceAll("\\s{2,}", " ").trim();
    }

    private static boolean serialOmissionOnly(String raw, String physicalSerial) {
        String text = safe(raw);
        String lower = text.toLowerCase(Locale.ROOT);
        if (!(lower.contains("not established") || lower.contains("not listed")
                || lower.contains("does not repeat") || lower.contains("not repeated")
                || lower.contains("non confermat") || lower.contains("non riportat"))) {
            return false;
        }
        Matcher matcher = FRACTION.matcher(text);
        boolean sawPhysical = false;
        while (matcher.find()) {
            String found = Integer.parseInt(matcher.group(1)) + "/"
                    + Integer.parseInt(matcher.group(2));
            if (!found.equals(physicalSerial)) {
                return false;
            }
            sawPhysical = true;
        }
        return sawPhysical;
    }

    private static boolean containsToken(String source, String token) {
        return safe(source).matches("(?is).*?(?<![0-9])" + Pattern.quote(token)
                + "(?![0-9]).*");
    }

    private static List<String> narrativeYears(String text) {
        List<String> out = new ArrayList<>();
        String normalized = safe(text).replace('\n', ' ');
        Matcher matcher = Pattern.compile(
                "(?i)(?:born|birth|season|championship|copyright|©).{0,90}((?:19|20)[0-9]{2}(?:[-/][0-9]{2,4})?)")
                .matcher(normalized);
        while (matcher.find()) {
            addOnce(out, matcher.group(1));
        }
        Matcher reverse = Pattern.compile(
                "(?i)((?:19|20)[0-9]{2}(?:[-/][0-9]{2,4})?).{0,90}(?:season|championship|copyright|©)")
                .matcher(normalized);
        while (reverse.find()) {
            addOnce(out, reverse.group(1));
        }
        return out;
    }

    private static boolean isNarrativeOnlyYear(String raw, String value, List<String> years) {
        Matcher matcher = BARE_YEAR.matcher(safe(raw).trim());
        if (!matcher.matches()) {
            return false;
        }
        for (String year : years) {
            if (canon(year).equals(canon(value))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsQualifiedSetYear(List<String> fields, String year) {
        for (String raw : fields) {
            String x = safe(raw).toLowerCase(Locale.ROOT);
            if ((x.startsWith("set=") || x.startsWith("collection=")
                    || x.startsWith("release=") || x.startsWith("edition="))
                    && canon(x).contains(canon(year))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPhysicalSerialBinding(List<String> fields) {
        for (String raw : fields) {
            String x = canon(raw);
            if (x.contains("SERIALBINDINGPHYSICALCARDSURFACE")
                    || x.contains("SERIALSOURCECARDSURFACE")
                    || x.contains("SERIALPHYSICALLYBOUNDTRUE")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPhysicalSerialValue(Models.Identification id) {
        if (id == null) {
            return false;
        }
        for (String raw : id.photoIdentityFields) {
            String lower = safe(raw).trim().toLowerCase(Locale.ROOT);
            if ((lower.startsWith("serial=") || lower.startsWith("serial_fraction="))
                    && !fraction(fieldValue(raw)).isEmpty()) {
                return true;
            }
        }
        return !fraction(id.photoIdentityCode).isEmpty();
    }

    private static boolean isSerialBindingOnly(String raw) {
        String x = safe(raw).trim().toLowerCase(Locale.ROOT);
        return x.startsWith("serial_binding=") || x.startsWith("serial_source=")
                || x.startsWith("serial_physically_bound=");
    }

    private static String removeDanglingSerial(String source) {
        return safe(source).replaceAll("(?i)(?<![A-Z0-9])serial(?:e)?(?![A-Z0-9])", " ")
                .replaceAll("\\s+,", ",").replaceAll(",\\s*$", "")
                .replaceAll("\\s{2,}", " ").trim();
    }

    private static String removeFractions(String source) {
        String out = safe(source);
        Matcher matcher = PRINT_RUN_SUFFIX.matcher(out);
        return matcher.replaceAll(" ").replaceAll("\\s{2,}", " ").trim();
    }

    private static boolean complementaryViews(Models.Identification id) {
        String joined = id.photoViews.toString().toLowerCase(Locale.ROOT);
        return id.photoViews.size() >= 2 || (joined.contains("front")
                && (joined.contains("back") || joined.contains("rear")
                || joined.contains("reverse") || joined.contains("retro")));
    }

    private static int stableFieldCount(Models.Identification id) {
        Set<String> stable = new HashSet<>();
        for (String raw : id.photoIdentityFields) {
            String value = fieldValue(raw);
            boolean tcgCollectorNumber = isTradingCardGame(id)
                    && safe(raw).toLowerCase(Locale.ROOT).startsWith("card_number=")
                    && validFraction(value);
            if (value.length() < 2 || (!tcgCollectorNumber && FRACTION.matcher(value).find())
                    || BARE_YEAR.matcher(raw.trim()).matches()) {
                continue;
            }
            stable.add(canon(value));
        }
        return stable.size();
    }

    private static String fraction(String value) {
        Matcher matcher = FRACTION.matcher(safe(value));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) + "/"
                + Integer.parseInt(matcher.group(2)) : "";
    }

    private static boolean samePrefix(String a, String b) {
        return a.replaceAll("[0-9]", "").equalsIgnoreCase(b.replaceAll("[0-9]", ""));
    }

    private static String fieldValue(String raw) {
        String value = safe(raw).trim();
        int equals = value.indexOf('=');
        return equals >= 0 ? value.substring(equals + 1).trim() : value;
    }

    private static String removeToken(String source, String token) {
        return safe(source).replaceAll("(?i)(?<![A-Z0-9])" + Pattern.quote(token)
                + "(?![A-Z0-9])", " ").replaceAll("\\s{2,}", " ").trim();
    }

    private static boolean factTrue(Models.CandidateScore c, String key) {
        return "true".equalsIgnoreCase(fact(c, key));
    }

    private static int factInt(Models.CandidateScore c, String key) {
        try {
            return Integer.parseInt(fact(c, key));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String fact(Models.CandidateScore c, String key) {
        String prefix = key + "=";
        for (String raw : c.candidateFacts) {
            if (safe(raw).startsWith(prefix)) {
                return raw.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private static void addOnce(List<String> values, String value) {
        if (value != null && !value.trim().isEmpty() && !values.contains(value.trim())) {
            values.add(value.trim());
        }
    }

    private static String join(String... values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(value.trim());
            }
        }
        return out.toString();
    }

    private static String canon(String value) {
        return safe(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
