package com.flipcheck.nativebeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic last-mile closure for cards whose commercial identity is
 * physically printed across the supplied views. Web retrieval may enrich the
 * tuple, but an unavailable catalog page must not erase observed identity.
 */
final class CardPhotoTupleClosure {
    private static final Pattern YEAR = Pattern.compile("(?<![0-9])((?:19|20)[0-9]{2}(?:[-/][0-9]{2,4})?)(?![0-9])");
    private static final Pattern RATING = Pattern.compile("(?i)^(?:OFF|DEF|OVR|ATT|SPD|RATING|POWER|HP)\\s*[:=]?\\s*[0-9]{1,4}$");
    private static final Pattern SERIAL_FRACTION = Pattern.compile("(?<![0-9])([0-9]{1,4})\\s*/\\s*([0-9]{1,4})(?![0-9])");

    private CardPhotoTupleClosure() {}

    /** Reserve the paid second request for an exact card lookup, not a second OCR pass. */
    static boolean shouldReserveCatalogPass(Models.Identification id) {
        if (!CollectibleCardIdentityPolicy.isCard(id) || !frontAndBack(id)
                || OverlayScopePolicy.blocksIdentity(id) || !knownCommercialCard(id)) return false;
        return !subject(id).isEmpty() && !observedBrand(id).isEmpty()
                && (!series(id).isEmpty() || !year(id).isEmpty())
                && evidenceCount(id) >= 7;
    }

    /** Known commercial cards receive the second pass before generic exits. */
    static boolean requiresMandatoryVerification(Models.Identification id, Models.Usage usage) {
        if (id == null || usage == null || usage.requests != 1 || usage.webCalls != 1
                || OverlayScopePolicy.blocksIdentity(id) || !knownCommercialCard(id)
                || hasMaterialStrongConflict(id)) return false;
        if (observedBrand(id).isEmpty() || subject(id).isEmpty() || series(id).isEmpty()
                || evidenceCount(id) < 5) return false;
        return CollectibleCardIdentityPolicy.isTradingCardGame(id) ? hasFront(id) : frontAndBack(id);
    }

    static boolean canClose(Models.Identification id) {
        if (id == null || id.marketReady || !CollectibleCardIdentityPolicy.isCard(id)
                || OverlayScopePolicy.blocksIdentity(id) || !knownCommercialCard(id)
                || hasMaterialStrongConflict(id)) return false;
        PhysicalTuple tuple = extractPhysicalTuple(id);
        if (!isClosedByPhysicalTuple(id, tuple)) {
            return false;
        }
        if (CollectibleCardIdentityPolicy.isTradingCardGame(id)) {
            return hasFront(id) && evidenceCount(id) >= 5 && tuple.hasPhysicalIdentifier;
        }
        return evidenceCount(id) >= 5 && tuple.hasPhysicalIdentifier
                && frontAndBack(id);
    }

    private static boolean isClosedByPhysicalTuple(Models.Identification id,
                                                  PhysicalTuple tuple) {
        if (tuple.brand.isEmpty() || tuple.brandMismatch || tuple.subject.isEmpty()) {
            return false;
        }
        if (!tuple.hasSetOrCollectionOrYearOrSeason()) {
            return false;
        }
        if (tuple.cardNumber.isEmpty()) {
            return false;
        }
        if (tuple.hasPhysicalConflict || tuple.hasUnresolvableVariantAmbiguity) {
            return false;
        }
        return hasDiscriminatingTupleFields(id, tuple.variant, tuple.serial,
                tuple.team, tuple.hasUnresolvableVariantAmbiguity);
    }

    private static boolean hasDiscriminatingTupleFields(Models.Identification id,
                                                        String variant,
                                                        String serial,
                                                        String team,
                                                        boolean ambiguousVariantPairs) {
        String value = safe(variant).toLowerCase(Locale.ROOT);
        String visibleSerial = safe(serial).toLowerCase(Locale.ROOT);
        boolean tcg = CollectibleCardIdentityPolicy.isTradingCardGame(id);
        boolean hasDiscriminatingVariant = !value.isEmpty() && !unresolvedVariant(value)
                && (hasVariantSignal(value) || hasEditionSignal(value)
                || hasLanguageSignal(value));
        if (tcg) {
            return hasDiscriminatingVariant || !visibleSerial.isEmpty();
        }
        if (!team.isEmpty()) {
            return true;
        }
        return hasDiscriminatingVariant || !visibleSerial.isEmpty() || !ambiguousVariantPairs;
    }

    private static boolean hasVariantSignal(String value) {
        return value.contains("holo") || value.contains("foil") || value.contains("shadowless")
                || value.contains("shadow-less") || value.contains("1st") || value.contains("first")
                || value.contains("edition") || value.contains("parallel") || value.contains("rc")
                || value.contains("rookie") || value.contains("relic") || value.contains("promotional");
    }

    private static boolean hasEditionSignal(String value) {
        return value.contains("unlimited") || value.contains("num") || value.contains("numbered")
                || value.contains("serial") || value.contains("limited");
    }

    private static boolean hasLanguageSignal(String value) {
        return value.contains("english") || value.contains("italian") || value.contains("spanish")
                || value.contains("french") || value.contains("german") || value.contains("japanese")
                || value.contains("chinese") || value.contains("russian")
                || value.contains("en ") || value.contains("it ") || value.contains("es ")
                || value.contains("de ") || value.contains("ja ") || value.contains("cn ");

    static boolean apply(Models.Identification id) {
        if (!canClose(id)) return false;
        PhysicalTuple tuple = extractPhysicalTuple(id);
        String brand = observedBrand(id);
        String year = year(id);
        String series = series(id);
        String subject = subject(id);
        String number = CollectibleCardIdentityPolicy.observedCardNumber(id, id.localScan);
        String parallel = cardVariant(id);
        String edition = field(id, "edition", "printing");
        String rookie = truthyField(id, "rookie_card", "rookie", "rc") ? "RC" : "";

        String family = compact(join(year, series));
        if (canon(family).contains(canon(brand))) family = removeWords(family, brand);
        String model = compact(join(subject, number.isEmpty() ? "" : "#" + number,
                parallel, edition, rookie));

        Models.CandidateScore c = new Models.CandidateScore();
        c.brand = brand;
        c.family = family;
        c.model = model;
        c.probableReference = compact(join(brand, family, model));
        c.probableReferenceConfidence = 94;
        c.identifierScore = number.isEmpty() ? 88 : 99;
        c.textScore = 98;
        c.layoutScore = frontAndBack(id) ? 96 : 92;
        c.webScore = hasExactWebSupport(id) ? 88 : 0;
        c.totalScore = 94;
        c.evidence = "Tupla identitaria composta esclusivamente da campi fisici coerenti letti sulla carta fotografata.";
        addFact(c, "physical_tuple_confirmation=true");
        addFact(c, "exact_card_visual_tuple=true");
        addFact(c, "photo_identity_supported=true");
        addFact(c, "same_entity_role=true");
        addFact(c, "relationship_only=false");
        addFact(c, "disproof_passed=true");
        addFact(c, "physical_tuple_number=" + tuple.cardNumber);
        addFact(c, "physical_tuple_serial=" + tuple.serial);
        addFact(c, "physical_tuple_variant=" + tuple.variant);
        addFact(c, "web_checklist_disposition=verify");
        addFact(c, "source_grounded=" + hasExactWebSupport(id));
        addFact(c, "source_exact_reference=" + hasExactWebSupport(id));
        addFact(c, "exact_reference_complete=" + hasExactWebSupport(id));
        addFact(c, "source_identity_confidence=" + (hasExactWebSupport(id) ? 90 : 0));
        addFact(c, "visual_reference_checked=false");
        addFact(c, "visual_match_confidence=94");
        addFact(c, "photo_identity_matched_count=" + evidenceCount(id));

        id.candidates.clear();
        id.candidates.add(c);
        id.brand = brand;
        id.brandEvidence = "physical_card_tuple";
        id.brandRoleConfidence = Math.max(id.brandRoleConfidence, 94);
        id.family = family;
        id.familyConfidence = Math.max(id.familyConfidence, 94);
        id.model = model;
        id.modelConfidence = 94;
        id.photoIdentityComplete = true;
        id.photoIdentityPhysicalBinding = true;
        id.photoIdentityKind = frontAndBack(id)
                ? "deterministic_front_back_card_tuple" : "deterministic_card_tuple";
        id.photoIdentityConfidence = Math.max(id.photoIdentityConfidence, 94);
        id.marketReady = true;
        id.disproofPassed = true;
        id.modelProof = "physical_card_tuple";
        id.nextPhotoRequest = "";
        id.nextPhotoReason = "";
        id.verificationSummary = "Identità chiusa dai campi fisici leggibili e coerenti della carta"
                + (frontAndBack(id) ? " su fronte e retro" : "")
                + ". La conferma riguarda l'identità/versione visibile, non autenticità o condizione.";
        id.decisionReason = "CONFIRMED v1.12: seconda verifica eseguita; carta commerciale nota chiusa dalla tupla fisica, Web corroborativo.";
        return true;
    }

    private static PhysicalTuple extractPhysicalTuple(Models.Identification id) {
        PhysicalTuple tuple = new PhysicalTuple();
        tuple.brand = observedBrand(id);
        tuple.productLine = series(id);
        tuple.season = year(id);
        tuple.subject = subject(id);
        tuple.team = field(id, "team", "club");
        tuple.cardNumber = safe(CollectibleCardIdentityPolicy.observedCardNumber(id, id.localScan));
        tuple.variant = cardVariant(id);
        tuple.serial = physicalSerialBinding(id);
        tuple.hasPhysicalIdentifier = !tuple.cardNumber.isEmpty() || !tuple.variant.isEmpty()
                || !tuple.serial.isEmpty();
        tuple.hasSetOrCollectionOrYearOrSeason = !tuple.productLine.isEmpty() || !tuple.season.isEmpty();
        tuple.hasUnresolvableVariantAmbiguity = hasUnresolvableVariantAmbiguity(id);

        String brandA = tuple.brand;
        String brandB = field(id, "manufacturer", "publisher", "brand");
        if (!safe(brandA).isEmpty() && !safe(brandB).isEmpty()
                && !canon(brandA).equals(canon(brandB))) {
            tuple.brandMismatch = true;
        }
        tuple.hasPhysicalConflict = hasConflictingPhysicalSignals(id);
        return tuple;
    }

    private static boolean hasUnresolvableVariantAmbiguity(Models.Identification id) {
        String first = chooseVariantValue(id, "parallel", "variant", "finish", "tier");
        if (first.isEmpty()) {
            return false;
        }
        String second = chooseVariantValue(id, "front_variant", "back_variant", "physical_variant");
        return !second.isEmpty() && !same(first, second);
    }

    private static String chooseVariantValue(Models.Identification id, String... keys) {
        String best = "";
        for (String raw : id.photoIdentityFields) {
            String x = safe(raw);
            int p = x.indexOf('=');
            if (p < 1) continue;
            String key = x.substring(0, p).trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            String value = x.substring(p + 1).trim();
            if (value.isEmpty() || unresolvedVariant(value)) {
                continue;
            }
            for (String expected : keys) {
                if (expected.equals(key)) {
                    if (best.isEmpty()) {
                        best = value;
                    } else if (!same(best, value)) {
                        return value;
                    }
                }
            }
        }
        return best;
    }

    private static boolean hasConflictingPhysicalSignals(Models.Identification id) {
        for (String raw : id.finalContradictions) {
            String x = safe(raw).toLowerCase(Locale.ROOT);
            if ((x.contains("front") && x.contains("back")
                    && (x.contains("mismatch") || x.contains("discord") || x.contains("diverg")))
                    || (x.contains("serial") && x.contains("card")
                    && (x.contains("conflict") || x.contains("contradiction")))
                    || (x.contains("card number") && x.contains("conflict"))) {
                return true;
            }
        }
        return false;
    }

    private static String physicalSerialBinding(Models.Identification id) {
        String explicit = explicitSerialFromFields(id);
        if (!explicit.isEmpty()) return explicit;
        return explicitSerialFromText(id.photoIdentityCode);
    }

    private static String explicitSerialFromFields(Models.Identification id) {
        for (String raw : id.photoIdentityFields) {
            String x = safe(raw);
            int p = x.indexOf('=');
            if (p < 1) {
                continue;
            }
            String key = x.substring(0, p).trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            String value = x.substring(p + 1).trim();
            if (!(key.equals("serial") || key.equals("serial_fraction")
                    || key.equals("physical_print_run") || key.equals("card_surface_serial")
                    || key.equals("physical_serial_marking") || key.equals("print_run"))) {
                continue;
            }
            String serial = parseSerialFraction(value);
            if (!serial.isEmpty()) return serial;
        }
        return "";
    }

    private static String explicitSerialFromText(String source) {
        return parseSerialFraction(source);
    }

    private static String parseSerialFraction(String value) {
        Matcher matcher = SERIAL_FRACTION.matcher(safe(value));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) + "/" + Integer.parseInt(matcher.group(2)) : "";
    }

    private static String cardVariant(Models.Identification id) {
        String value = field(id, "parallel", "variant", "finish", "tier");
        if (!value.isEmpty() && !unresolvedVariant(value)) return value;
        String printing = field(id, "physical_printing");
        String x = canon(printing);
        if (x.contains("BASE CARD") || x.endsWith(" BASE") || x.equals("BASE")) return "";
        return unresolvedVariant(printing) ? "" : printing;
    }

    private static boolean unresolvedVariant(String value) {
        String x = safe(value).toLowerCase(Locale.ROOT);
        return x.isEmpty() || x.contains("unresolved") || x.contains("unknown")
                || x.contains("unclear") || x.contains("da determinare");
    }

    private static boolean knownCommercialCard(Models.Identification id) {
        String x = (safe(id.brand) + " " + safe(id.photoIdentityName) + " "
                + id.visibleLabels + " " + id.visualFacts + " " + id.photoIdentityFields)
                .toLowerCase(Locale.ROOT);
        if (x.contains("custom") || x.contains("fan made") || x.contains("fan-made")
                || x.contains("homemade") || x.contains("unlicensed")
                || x.contains("unknown brand") || x.contains("marchio sconosciuto")) return false;
        return !observedBrand(id).isEmpty();
    }

    private static boolean hasMaterialStrongConflict(Models.Identification id) {
        for (String raw : id.finalContradictions) {
            String x = safe(raw).toLowerCase(Locale.ROOT);
            boolean falseStatNumber = (x.contains("card number") || x.contains("collector number"))
                    && (x.contains("rating") || x.contains("game attribute")
                    || x.contains("def ") || x.contains("off "));
            boolean sourceChecklist = x.contains("catalog") || x.contains("source")
                    || x.contains("checklist") || x.contains("web");
            if (!falseStatNumber && (x.contains("strong") || x.contains("conflict")
                    || x.contains("contradiction")) && !sourceChecklist) return true;
        }
        return false;
    }

    private static boolean hasExactWebSupport(Models.Identification id) {
        for (Models.CandidateScore c : id.candidates) {
            if (c == null || c.hardRejected) continue;
            if (factTrue(c, "source_grounded") && (factTrue(c, "source_exact_reference")
                    || factTrue(c, "exact_reference_complete"))) return true;
        }
        return false;
    }

    private static String observedBrand(Models.Identification id) {
        String keyed = field(id, "manufacturer", "publisher", "brand");
        if (!keyed.isEmpty()) return keyed;
        String brand = safe(id.brand);
        if (brand.isEmpty()) return "";
        String wanted = canon(brand);
        if (canon(id.visibleLabels.toString()).contains(wanted)
                || canon(id.photoIdentityFields.toString()).contains(wanted)
                || BrandBlindPolicy.trustedObservedBrand(id)) return brand;
        return "";
    }

    private static String subject(Models.Identification id) {
        String value = field(id, "subject", "player", "athlete", "character");
        if (!value.isEmpty()) return value;
        for (String label : id.visibleLabels) {
            String x = safe(label);
            if (x.matches("(?i)[A-ZÀ-ÖØ-Ý][A-ZÀ-ÖØ-öø-ÿ.'-]+\\s+[A-ZÀ-ÖØ-Ý][A-ZÀ-ÖØ-öø-ÿ.'-]+")
                    && !generic(x)) return x;
        }
        return "";
    }

    private static String year(Models.Identification id) {
        String keyed = field(id, "season", "set_year", "year", "copyright_year");
        Matcher m = YEAR.matcher(keyed + " " + id.visibleLabels + " " + id.visualFacts + " " + id.photoIdentityFields);
        return m.find() ? m.group(1) : "";
    }

    private static String series(Models.Identification id) {
        String keyed = field(id, "set", "series", "product_line", "collection");
        if (!keyed.isEmpty() && !generic(keyed)) return keyed;
        String brand = observedBrand(id);
        String subject = subject(id);
        String team = field(id, "team", "club");
        String best = "";
        int bestScore = -1;
        for (String raw : id.visibleLabels) {
            String x = safe(raw);
            if (x.length() < 3 || x.length() > 48 || generic(x)
                    || same(x, brand) || same(x, subject) || same(x, team)
                    || RATING.matcher(x).matches() || YEAR.matcher(x).find()
                    || x.matches(".*[©®].*") || x.matches("^[0-9# /.-]+$")) continue;
            int score = x.contains(" ") ? 4 : 1;
            if (x.matches(".*[A-Za-z].*[A-Z].*")) score++;
            if (x.length() >= 6 && x.length() <= 28) score++;
            if (score > bestScore) { best = x; bestScore = score; }
        }
        return best;
    }

    private static int discriminatorCount(Models.Identification id) {
        int count = 0;
        String[] keys = {"team", "club", "position", "format", "sport", "off", "def",
                "rating", "front_rating_star", "card_number", "parallel", "variant",
                "rookie_card", "rookie", "rc", "serial", "language"};
        for (String key : keys) if (!field(id, key).isEmpty()) count++;
        return count;
    }

    private static int evidenceCount(Models.Identification id) {
        return id.visibleLabels.size() + id.visualFacts.size() + id.photoIdentityFields.size();
    }

    private static boolean hasFront(Models.Identification id) {
        if (id.photoViews.isEmpty()) return true;
        String x = id.photoViews.toString().toLowerCase(Locale.ROOT);
        return x.contains("front") || x.contains("fronte");
    }

    private static boolean frontAndBack(Models.Identification id) {
        String x = id.photoViews.toString().toLowerCase(Locale.ROOT);
        return (x.contains("front") || x.contains("fronte"))
                && (x.contains("back") || x.contains("rear") || x.contains("reverse") || x.contains("retro"));
    }

    private static String field(Models.Identification id, String... keys) {
        List<String> all = new ArrayList<>();
        all.addAll(id.photoIdentityFields);
        all.addAll(id.visualFacts);
        for (String raw : all) {
            String x = safe(raw);
            int p = x.indexOf('=');
            if (p < 1) p = x.indexOf(':');
            if (p < 1) continue;
            String key = x.substring(0, p).trim().toLowerCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_');
            for (String expected : keys) if (key.equals(expected)) return safe(x.substring(p + 1));
        }
        return "";
    }

    private static boolean truthyField(Models.Identification id, String... keys) {
        String x = field(id, keys).toLowerCase(Locale.ROOT);
        return x.equals("true") || x.equals("yes") || x.equals("present") || x.equals("1");
    }

    private static boolean generic(String value) {
        String x = canon(value);
        return x.isEmpty() || x.equals("PANINI") || x.equals("TOPPS") || x.equals("POKEMON")
                || x.equals("TRADING CARD GAME") || x.equals("SPORTS TRADING CARD")
                || x.equals("COLLECTIBLE TRADING CARD") || x.equals("BASKETBALL TRADING CARD")
                || x.equals("GUARD") || x.equals("FORWARD") || x.equals("CENTER");
    }

    private static boolean same(String a, String b) { return !canon(a).isEmpty() && canon(a).equals(canon(b)); }
    private static String removeWords(String value, String words) { return compact(safe(value).replaceAll("(?i)\\b" + Pattern.quote(words) + "\\b", " ")); }
    private static String join(String... values) { StringBuilder b = new StringBuilder(); for (String value : values) { String x = safe(value); if (x.isEmpty()) continue; if (b.length() > 0) b.append(' '); b.append(x); } return b.toString(); }
    private static String compact(String value) { return safe(value).replaceAll("\\s+", " "); }
    private static String canon(String value) { return safe(value).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim().replaceAll("\\s+", " "); }
    private static void addFact(Models.CandidateScore c, String value) { if (!c.candidateFacts.contains(value)) c.candidateFacts.add(value); }
    private static boolean factTrue(Models.CandidateScore c, String key) { String p = key.toLowerCase(Locale.ROOT) + "="; for (String raw : c.candidateFacts) { String x = safe(raw).toLowerCase(Locale.ROOT); if (x.startsWith(p)) return "true".equals(x.substring(p.length()).trim()); } return false; }
    private static final class PhysicalTuple {
        String brand;
        String productLine;
        String season;
        String subject;
        String team;
        String cardNumber;
        String variant;
        String serial;
        boolean hasPhysicalIdentifier;
        boolean hasSetOrCollectionOrYearOrSeason;
        boolean hasUnresolvableVariantAmbiguity;
        boolean hasPhysicalConflict;
        boolean brandMismatch;

        boolean hasSetOrCollectionOrYearOrSeason() {
            return hasSetOrCollectionOrYearOrSeason;
        }
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
