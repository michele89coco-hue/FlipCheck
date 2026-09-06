# FlipCheck 0.26.5 — build 162

Fixes the latency and unresolved identity regressions reported with build 161. Keeps the v0.26 baseline engine, Android insets/multi-selection fixes, 2560-pixel photo preparation and original-pixel crops.

- Close coherent, sufficiently specific photo identities without web. Existing confidence gates still apply. Inferred parallel names are displayed separately and excluded from the confirmed comparison query; slab grading/condition do not reopen an otherwise complete card identity.
- Re-read at most two useful unreadable details. Skip crops on already clear identifiers, including a catalogue disagreement that requires lookup rather than another reading. Detail Vision returns short text corrections with a 1024-pixel overview and original-pixel crop, instead of another full identity and every full-resolution photo. Both Vision calls explicitly use low reasoning effort.
- Actual image count bounds JSON photo indices; label each original image explicitly. Invalid returned indices remain visible in diagnostics but are discarded as evidence.
- Web is conditional. One lookup by number/series/model; a second only for an unresolved candidate supported by a returned source and a different query. Empty results and network errors stop without automatic retries. Web cannot prove a physical edition/finish.
- Accept relevant checklist rows linking subject, set and collector number. Preserve clear physical conflicts, but do not treat missing source language or a corrected inferred set as a contradiction. Product seasons and e-reader auxiliary codes are not model codes. Sealed-box configuration still needs a physical/source link.
- Diagnostic wall time includes failed-request waits; API usage remains the usage actually returned, with failed-request billing explicitly unknown.

## Scope and provenance of routing reference

`tcg-reference.js` contains the same factual subset previously prepared for the project from https://github.com/PokemonTCG/pokemon-tcg-data at commit `8b4e387930ead7be6595b4d4c59b7ba7a3a79f08`: 15 early sets, 1,709 card rows, with set/number/name/HP and English attacks. Only number/name/HP and printed total are used here. It detects a conflict in a supported set to request web; it never replaces the observed identity from the local catalogue. Unknown sets/names are not classified as conflicts. This is a bounded sanity check, not a universal identity database or a guarantee of recognition.

## Verification

`tests/fixtures/build161-regressions.json` is a diagnostic replay subset from the supplied Politoed, Vileplume, Boniface and Topps box logs. Tests replay the existing JSON/model responses; they do not recognize photographs anew or measure live API latency. Browser requests are intercepted. No paid recognition requests are made by the test suite.

Expected replay behavior: Vileplume and Boniface core use one Vision/no web; Politoed uses one Vision/one web and resolves Skyridge H23/H32; the supplied Mega-box response remains unresolved without the configuration link. Boniface's inferred Green Prizm name remains unverified. No claim of 100% recognition accuracy.

GitHub Actions also builds/signs the APK and exercises Android 16 navigation and the photo picker. Package and signing certificate are retained for an in-place update of FlipCheck 26 Fix.
