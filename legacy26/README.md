# FlipCheck v0.26.1 — targeted fixes

Base: the user's `FlipCheck_Beta_v0.26.apk`, SHA256 `6803ab0bc086c48d26672311b519024e489f3010894a86b8234b301abd569d3c`.
`baseline/index.html` is the exact original asset. The complete inline recognition,
web resolution, source scoring, confidence, resize and market engine script remains
byte-identical in the running page, verified by an automated test. Small additional
scripts provide multi-selection and separate Pokémon printing checks.

Changes:
- Select up to three photos together; stable selection order, duplicate detection,
  cancellation preservation, failed replacement preservation and upload/API locking.
- Ask for stamp, artwork shadow, copyright and slab text in the existing Vision
  response. No additional Vision/Web stage. The original automatic optional Web
  resolver remains. Allow 2150 output tokens instead of 1800 for the added fields;
  token usage can increase, and no speed or accuracy gain is claimed.
- First Edition and Shadowless are independent. English original Base Set border
  rules do not generalize to other languages or sets. Clear copyright provides a
  second cue; obscured areas remain unknown. Trainers/Energies need appropriate
  stamp locations and do not use the Pokémon artwork-border rule alone.
- Retain the core identity/confidence, but keep ambiguous printing out of an
  automatic market query. A manual corrected description remains possible.
- Show slab wording as a declaration, not as an observed card stamp. Contradictions
  between visible card printing and label remain explicit.
- Export current analysis JSON without API key or image data for phone testing.

Rules checked against CGC's guide:
https://www.cgccards.com/news/article/10262/pokemon-first-editions/

The original upload only contains a small Android DEX host, no Java project. This
module reconstructs that host around the original HTML and adds Android multiple-URI
picker handling plus JSON export. Package `com.flipcheck.beta.legacy26fix`, label
`FlipCheck 26 Fix`, versionCode158, versionName0.26.1-targeted. It installs alongside
the old v26 and native builds. The signing key available for the project differs
from the original v26 key, so this is deliberately a separate package.

The new supplied `kobe diagnotica.json` records native build157, not v26: SkyBox is
reclassified as rightsHolder because its location mentions copyright plus logo;
Metal Universe with role `product-line logo` remains inferred; Vision reads 18 at
86 confidence instead of the reference photo's 81. This experiment returns to v26
as requested and does not patch the native157 path or encode Kobe identifiers.

Validation: node unit tests and Chromium UI tests with clearly synthetic API
responses, all outgoing API requests intercepted. These test software behavior,
not recognition accuracy on real cards. CI assembles and verifies the signed APK.
No paid live API scan or phone installation is part of these automated checks.


## Build 171: targeted continuation and multi-photo fixes

- Continue with the estimated cost of the next API request within the existing 0.03 budget; do not reserve a mandatory second image comparison before a text search. Failed Google calls keep their unknown-billing reserve and a sanitized failure reason.
- Defer uncertain Pokémon printing recovery until an inferred catalogue family has been resolved. Re-evaluate only uncertain printing fields with that verified family; preserve certain stamps.
- Reuse completed image matches to validate a missing quantity from a source naming the same model and season. Conflicting amounts, different entries, missing image evidence and uncertain card parallels remain blocking.
- Follow the product link attached to a matching Google image where available. Do not attach a whole collection's text to one product; remove duplicate image references and excess page whitespace.
- Send explicit native batch/single picker modes. The main button and empty slots support three photos; a separate File selector also supports multiple photos. Cancellation preserves the loaded images.
- Regression fixtures from build 170 contain diagnostics only. Browser responses and images are synthetic; these tests do not establish live recognition accuracy. No live AI requests are part of the release checks.

## Build 174: evidence fusion and focused rereading

- Preserve separately cited facts across actual image comparisons; compose a card identity from series, subject and number/year without requiring an invented full-title quote or every brand/year field. Keep core identity separate from unresolved physical printing.
- Do not merge anonymous candidates from different references, transfer bare slab/listing numbers to card numbers, or accept a parallel while its own reading requests confirmation.
- Use bundled local OCR on original object images when assistance is needed. Retain text coordinates to select uncertain details; fall back to the full original object when localization is uncertain. OCR alone does not convert a guess into a confirmed observation.
- Render PDF pages individually at up to 1536 x 2048; retain page attribution and document context separately. Preserve image captions and up to two product images per HTML source. Avoid power-of-two over-reduction in the OCR decoder.
- Use a compact, single-candidate visual review for a specific unresolved field. Reuse previous evidence, preserve the global 0.03 budget and log skipped recoveries. Existing successful photo-only identification remains available.
- Regression checks use the recorded build173 responses plus synthetic browser/native images, without paid live recognition. APK accuracy still requires user testing with the original photographs.

## Build 175: identity recovery from build174 diagnostics

- Recognize catalogue aliases of the original English Base Set, including Pokemon Game Base Set. An unread artwork border must trigger the existing focused printing read. Physical stamp, border and copyright remain independent; a proved printing removes incompatible catalogue assertions from displayed facts while retaining their diagnostic history.
- Compose collectible panel identities even when the initial kind is object. Keep subject/publication/year/issue provenance. Anonymous image matches remain discovery leads until an attributed catalogue entry supports identity.
- Revisit a box presentation dispute once within the budget. This schedules a real comparison, never turns a rejected candidate into a match by itself. Preserve separately corroborated brand/year facts; incompatible case quantities, identifiers and seasons remain blocked.
- Retain small printed symbols in the initial observation schema. A guessed brand can guide object search as an explicit hypothesis, never as OCR. Remotes with useful control labels use text search first and unrelated reference images are filtered before paid comparison.
- Preserve a concrete target detail request when source retrieval fails. Clear obsolete variant/core state and redraw the final synchronized result.
- Public regression cases are synthetic. Uploaded build174 diagnostics remain outside git and can be replayed locally with FLIPCHECK_RECORDED_FIXTURE. These checks and mocked browser wiring do not call paid APIs or prove accuracy on real photos.

## Build 179: photographic core and retained evidence

- A clearly photographed card number, product season, printed series and subject establish a photo-origin core. Unrelated catalogue pictures cannot erase it. The commercial parallel and Pokemon printing remain separate checks; number/year alone, statistics, copyright and serials do not establish this tuple.
- Compact prompts retain a separate original source text for citation/quantity validation. Written quantities such as "one guaranteed autograph per box" normalize with their units; conflicting amounts per unit still block.
- Scope printing ambiguity separately from the card entry. Route a remaining border/stamp/copyright question to the original image; complete identity only after the actual focused printing response confirms the required details.
- Repair missing or invalid catalogue fields with one bounded text-only extraction from existing sources. Reuse the recorded image comparisons without inventing new ones. Preserve rejected fields and repair provenance in diagnostics.
- A printed card tuple uses text search first. Search queries include unresolved observed appearance; unrelated product thumbnails are excluded. Never replace an unresolved photographed parallel with a resolver's unverified Base label.
- Budget planning preserves a minimal decisive comparison within the existing 0.03 ceiling. If the core is established, comparison can select the side showing the parallel and retain the other side's previously read facts as context.
- Original-resolution uploads, detail crops and the native background session from build178 remain in place. Tests are synthetic/offline and Android lifecycle tests use no live recognition services. User photos and diagnostic exports are not committed.
