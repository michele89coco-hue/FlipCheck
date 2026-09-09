# Recognition 202 — universal regions and catalogue structure

Version 0.29.9-universal-regions, versionCode 202. Parent 9996343 (201).

- Read Pokemon whole-card, upper, lower and broad artwork panels before search in one additional composite Vision request. Cache original-resolution crops and observations in the existing per-scan workspace. Reuse cached whole/artwork panels for finish rereads. Region coordinates are relative to the photographed card; output evidence remains mapped to the original photo. Crops follow detected bounding boxes and decoded image orientation; no new perspective-rectification model is claimed.
- Reject attack multipliers as Pokemon collector numbers. Normalize Deutsch and focused number prefixes. Preserve uncertain facts rather than fabricate defaults.
- Query includes available international-name alternative, full number, printed year, rarity and sports insert. First discovery has no site whitelist; final evidence still passes source/quotation checks. Query aliases are not photographic confirmation.
- Normalize generic Prizms/base duplicates only with an attributed same-card anchor. Use a photographed insert when catalog sections corroborate it. Unknown metadata cannot merge two different known inserts. Parse plain section headings and semicolon parallel lists without leaking variants across sections.
- Keep physical specimen serials even while base identity is pending. Titles retain documented sports product/insert, RC and serial. Modern rarity labels no longer acquire generic Rarity Symbol text. Historical printing applicability uses set/language/period; incompatible explicit First Edition observations receive targeted verification.
- Reread uncertain TCG codes with broader context. Reuse attributed official variant images for the base-card comparison before paid discovery. Incomplete or conflicting visual evidence still cannot confirm a print.
- Skip an unaffordable web request without aborting subsequent affordable visual recovery. Log skipped queries separately from actually executed queries. Default $0.03 cap and conservative two-search reservation remain unchanged.
- Preserve signed package identity and diagnostic export after failures.

## Verification scope

419 deterministic policy checks, plus browser and Android gates in CI. Tests use recorded OCR/Vision/catalogue packets and synthetic image carriers. No paid AI calls, no claimed live OCR accuracy. The six build-201 logs are retained as sanitized regression fixtures.

Recorded Boniface and Yamal close with existing evidence; Doncic base closes and its exact Green print requires the existing front/back serial-absence check. New crop/comparison responses in browser tests are explicitly simulated. Glurak's old attack reading is rejected; Mewtwo's catalog/visual recovery and Luffy's newly enabled comparison must still be assessed on device. Historical incorrect CD Promo/Expedition closure remains rejected.
