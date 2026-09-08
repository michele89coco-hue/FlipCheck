# Build 191 — Identity finalization

Based on build 190, commit 5dd2743a440211ae076e29bf2d8ab8728dbf0048.

## Agreed behavior

- A slab is read once with the initial image request. Certificate lookup on the official grading company site is the preferred path. A readable complete label closes identification when that page cannot return a usable record. There is no paid title search, visual comparison or printing recheck for a slab.
- A retrieved page is a certificate verification only when it returns the requested certificate number and usable identity fields. Generic forms, URLs echoing a certificate, a different returned record and provider errors are not verification. Missing record fields retain their label provenance.
- Card manufacturer and grading company are distinct. Identity includes grading company and grade; unreadable grading data stays unknown. The certificate string retains leading zeros. The contained card's generic OCR conflicts cannot reopen an identity closed from its label.
- Card names, series, catalogue numbers, variant and dates are composed from resolved fields. A copyright year remains a copyright observation, not a manufactured catalogue release date. Superseded uncertainty is removed from the final title/variant. Product identity and a still unresolved box format have separate UI states.
- Pokémon 1st Edition and Shadowless rules remain set/language dependent. Japanese first edition is not globally disabled after 2000: verified releases after 2016 are excluded, 2016 stays eligible. No Rarity Symbol is checked only on compatible 1996 Japanese Base Pokémon/trainer cards, with a visible located rarity area; basic energy and promos cannot prove that printing through absence. R/RR and HOLO R are not converted into Reverse Holo.
- Local bundled OCR supports Japanese script selection and retains Latin by default. Dense small print has two additional inverted grayscale edge passes, capped at eight passes and the existing recovery time budget. Coordinates remain relative to the original photograph. No serial characters are invented or repaired by lookup.
- Existing market implementation is outside this change. Structured card/grading identity is retained for later comps work.

## Verification contract

Policy tests and production UI replays use no live model calls. Existing reference/transport fixtures are recorded responses with synthetic image carriers; they do not measure new Vision accuracy. Optional `FLIPCHECK_DIAGNOSTICS_DIR` replays the five user-supplied build190 exports locally without committing them. Current results: slab blocked→confirmed; Machamp title/year and stale Shadowless warning corrected; Politoed year/set/number and Holo retained; Boniface confirmed Green 2/5 preserved; Topps core confirmed with a focused format-photo request.

The Android gate tests the real Boniface photograph, requiring local OCR itself to return 2/5 at its original coordinates. It also tests Japanese text with the bundled model, structured certificate page extraction, background completion, navigation and photo selection. Build success alone is not evidence that this gate passed.
