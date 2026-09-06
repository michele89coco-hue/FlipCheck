# v0.26.3 — targeted recognition fixes (build 160)

Base: v0.26.2, commit fbb4f1ead7cc65afe01f9aae7446c13161a32f10.
The original inline v26 engine remains byte-identical. Android bar, keyboard,
multi-photo, resize, models and market engine behavior are retained.

## Problems reproduced from build 159

- Both remote and Topps box diagnostics have one web call, a null parsed resolver
  result and exactly 950 output tokens, the old output cap. Those exports do not
  include the API completion status, so truncation is strongly indicated rather
  than proved retrospectively.
- Politoed was incorrectly accepted as Neo Genesis 12/111 (Pichu's number), with
  110 PV and Italian attack text. The edition check subsequently blocked market
  readiness, but the wrong core identity remained visible.
- The remote requested the rear and battery compartment again despite explicitly
  reporting readable identifiers from both areas.

## Changes

The existing resolver gets a 2600-token output ceiling, two candidates maximum,
and compact-output instructions. This is a ceiling, not a fixed token charge.
There is still at most one automatic web search per identification. The schema
now permits the identifier-grounded specificity values already used by the v26
resolver prompt and scoring code. No automatic retry or new paid request.
Actual excerpts inside `action.sources`/`sources` are now accepted alongside the
existing `results` forms. URL/title-only metadata is excluded from that evidence
path. Diagnostics record both extracted-result and source counts.

Diagnostics now record attempted requests, completion status, incomplete reason,
parse failure, token limit and elapsed time, including network/HTTP failures.
They retain the original Vision reading. A new failed attempt cannot export the
previous identity. Technical resolver failure is shown as an interrupted web
verification, not a request to photograph the same item again.

An offline factual checklist checks Pokémon subject, expansion, collector number,
denominator and explicit HP/PV. English attack names are checked when the reading
explicitly labels them as attacks; localized attack translations are not guessed.
A known contradiction forces the existing one-search path. Disputed numbers remain
in the diagnostic/prompt but are not exact discovery keys. Alternatives from the
checklist are suggestions, never automatic visual identifications. After web
resolution, the tuple is checked against the original observed subject and HP.
Only on this recovery path, an exact model backed by a genuinely relevant source,
two rare terms and a consistent numbered checklist entry with observed HP can
receive an 8-point corroboration adjustment (cap 96). A local suggestion alone or
a source with a contradiction cannot obtain this adjustment. Scores are internal
heuristics, not measured probabilities of visual accuracy.

For indexed Western sets without a first-edition issue (Base Set 2, Legendary
Collection and the three e-card sets), an unreadable stamp is not a missing
discriminator after a consistent numbered identity. A claimed visible stamp on
those sets is treated as a contradiction. Shadowless/first-edition observations
on the working Base/Jungle cases retain their independent v0.26.1 rules.

For objects, a generic rear/battery request is removed only when the original
reading explicitly says the corresponding area has readable alphanumeric codes.
Requests for an unreadable identified code or an unphotographed area are retained.
This does not promote an unresolved commercial model.

## Checklist provenance and limits

Source: https://github.com/PokemonTCG/pokemon-tcg-data
Pinned snapshot: 8b4e387930ead7be6595b4d4c59b7ba7a3a79f08.
Files: `sets/en.json` and `cards/en/{set-id}.json`.
15 sets, 1709 card records: base1–base6, gym1–gym2, neo1–neo4, ecard1–ecard3.
`tcg-catalog.js` retains only set names/counts and card numbers, names, HP and attack
names. No images, rules text, pricing or third-party executable code are included.
`tools/compact-catalog.py` regenerates it from the pinned downloaded JSON files.
The community checklist is not an authenticity service or a universal catalogue.
The deterministic check is scoped to English/Italian Pokémon with recognized
names/sets. Unknown sets, unsupported translated names and other categories keep
their previous route; they are not rejected for missing catalogue coverage.

## Validation

The fixtures contain original build159 Vision outputs supplied by the user, with
session/photo-event telemetry omitted. Policy checks replay these actual readings.
Browser tests use fabricated API/source responses and intercept external requests,
including completion, truncation and HTTP error cases. The Android16 emulator
checks asset startup, tabs, keyboard and three image URI loading. None of these
tests measures new model accuracy on the physical photos. No live API calls.
