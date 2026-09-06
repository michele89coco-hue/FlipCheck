# FlipCheck 0.26.6 — build 163

Small fixes on build 162's fast path. No product-name lookup table or rule assigning a box format from an autograph count. The original v0.26 inline engine, photo resolution and Android host/navigation remain unchanged.

- Search queries retain physically read seasons, identifiers and discriminating text/specifications. A second query uses the candidate matching current photo facts rather than the first model-listed candidate. The query plan is included in diagnostics.
- Missing source evidence is distinct from an actual difference. New web responses supply a comparison state and a cited source value/URL; actual numerical/code conflicts remain independently checked. A family inferred from design is not a printed claim unless it has a physical location.
- Per-unit quantities and common electrical/dimensional/capacity units can be compared across wording and notation. Normalizing “autograph card” does not assign any box type. A possible content mention does not establish a stated quantity. Retail wording does not invalidate a source naming the exact model.
- When the first lookup reveals a physical-text/quantity conflict, one original-pixel crop can re-read the relevant line before another lookup. The re-reading receives photo text and crops, not the desired catalogue answer. At most two Vision requests and two web requests per identification; complete photo identities still use no web. Network failures stop without automatic retry.
- A validated catalogue set supplies the context for printing checks. Observed language, stamp, copyright and shadow stay photographic evidence. The market query retains the photographed language.

## Regression evidence and limits

`build162-regressions.json` contains subsets of the user's diagnostic responses, not photographs or API credentials. Replays verify that Vileplume and Politoed can resolve from the first already-recorded web response while preserving Kobe's no-web path. The user's box label is recorded as Hobby Box in the fixture only, never in production rules.

The reported box reading still says one autograph per three boxes, while the supplied Hobby source states one per box. That contradiction must be re-read, not silently removed to make a test pass. The browser recovery scenario uses an explicitly synthetic corrected reading to verify crop, merge and query orchestration. It does not establish what a new live Vision call will read from the user's photograph. No paid recognition requests are used by these checks.

Public source checked on 2026-09-06: https://launches.topps.com/en-US/launch/2025-26-topps-chromer-updates-basketball-hobby-box reports four cards per pack, twenty packs per box and one autograph per box. https://www.topps.com/products/2025-26-topps-chrome%C2%AE-updates-basketball-mega-box-1 separately describes possible autograph content; its generic marketing wording is not a box guarantee. These are research evidence, not runtime constants.
