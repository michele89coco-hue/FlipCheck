# FlipCheck v0.26.4 — build 161

Base: the uploaded v0.26.2 APK, source d6649b2 (build 159 plus verification notes). The original inline engine remains byte-identical. Android navigation/IME padding and multi-photo picker are retained. No v160 local Pokémon catalogue or generic raw-result candidate extraction is in this branch.

## Changes

- Full images use JPEG quality 0.94 and a 2560-pixel longest-side limit, preserving aspect ratio and never enlarging small inputs. Decoded originals are retained as Files, not as long-lived full-resolution bitmaps.
- The first Vision response locates at most three precise text regions and types literal identifiers as collector number, copy serial, model, part number, barcode or slab certificate. It also reports clear/uncertain legibility.
- Regions use normalized coordinates of the whole oriented input. The app maps them onto the original file, adds a 1.5% margin and extracts lossless PNG crops (1600px maximum, no upscaling). Invalid/out-of-range/oversized regions are skipped. Originals remain in the second Vision context and crop labels map to original photo indices.
- At most one detail reread. No crops means no second Vision. A failed detail pass preserves the first reading and records the failure.
- Identification performs one required web search on brand, series, typed codes and distinguishing printed text. A second, different query only runs after an unresolved/conflicting first result. Each response is capped at one tool call. The existing market search remains separate and user-triggered.
- Web candidates require relevant tool-returned URLs and quoted evidence linking the model and physical clues. When returned excerpts exist the quote must match them; otherwise the evidence is explicitly tagged cited_quote, not a verified fetched excerpt. No substring-based inference that a domain is official. Generic Pokédex pages, compatibility-only matches and bare numeric collisions do not confirm a product.
- Standalone collector numbers such as H23 are supported. Canonical web identity replaces the stale title, but original observations are preserved separately. Clear conflicting numbers remain unresolved; uncertain numbers can be corrected through other physical clues and an exact source. Copy serials/certificates are not collector numbers.
- Physical Pokémon printing evidence still comes from photos, not web. Clean up dangling negations when rendering an absent edition stamp.
- Diagnostics include original/sent dimensions, encodings, crop rectangles, first/detail readings, phase errors, query changes and per-request usage. Image payloads and API keys are excluded. Each actual request is counted once. Each request has a 60-second client timeout; authentication/quota errors stop additional lookups.

## Validation and limits

Node policy regressions cover stale collector numbers, partial text, source relevance, false Blaster confirmation, ambiguous products, code/part compatibility, serial typing, query changes and unchanged v26 printing behavior. Chromium integration exercises large-image preparation, native-coordinate crops, batch selection/order, bounded second searches, failure recovery and telemetry. Android 16 instrumentation checks bottom navigation, keyboard and picker behavior. These are offline/mocked tests, not proof of recognition accuracy on live photos.

Model coordinates and transcriptions can still be wrong. Cropping cannot reconstruct detail missing from a screenshot. Higher image resolution and a second Vision/web request can increase latency and billed tokens. No claim of 100% accuracy or fixed per-scan cost is made.

Implementation references: https://developers.openai.com/api/docs/guides/images-vision and https://developers.openai.com/api/docs/guides/tools-web-search (consulted 2026-09-06). Keep original detail for coordinate-sensitive small text and enforce the local image/call budgets.
