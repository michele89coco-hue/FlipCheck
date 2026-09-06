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
