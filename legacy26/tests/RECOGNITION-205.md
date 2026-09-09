# Build 205 — SearchApi Lens first

Branch: `codex/pokemon-number-identity`, based on released build 204.

## Path

Inside the existing scan lock/background lifetime: one image-only server Lens attempt,
on-device OCR on uploaded photos, physical reading/targeted detail recovery, up to
20 candidate evaluations, retrieved catalogue pages, existing targeted web fallback,
then the existing evidence reducer. Slab label/certificate closure remains separate.
No Lens title is ingested as a photo observation. Clear physical contradictions reject
candidates; incomplete or uncertain evidence stays incomplete. A failed Lens request
does not abort OCR/catalogue/web. The configured aggregate scan budget is unchanged.

The first physical Vision reading still handles domain, object boundaries, language
and visual details that unclassified local OCR cannot establish. It receives OCR as
uncertain text, never as instructions or guaranteed evidence. Later detail requests
are driven by missing fields. Catalogue extraction from downloaded Lens pages is text
only and makes no web-tool request. Native reference requests use the existing public
address restrictions; service authentication never follows redirects or enters caches.

Additional corrections: preserve Pokémon V/ex suffixes in translations; retain
Box Topper/subset in final display and query; include full-card context for Machamp
shadow rereads; generic product names alone cannot establish an exact model.

## Recorded evidence

`fixtures/lens-205-recorded.json.gz` contains the 10 real SearchApi response sets
from experimental runs 34351482948, 34353512936, 34354175880 and 34355068789.
The fixture excludes original photo URLs and contains public result links/images.
No new user photograph is committed for this integration.

The selection suite covers Glurak 9/12 versus 146/144; Italian Holo Dragonite 9/165
versus 43/165, Reverse and Japanese; English Luffy P-110 / 4th Anniversary; Machamp
8/102 with physically unresolved shadow; Vileplume 15/64 Holo First Edition versus
31/64 and Unlimited; Kobe #81 / 1997-98 and statistic provenance; Rekord Journal 1958
as a Pelé/Garrincha candidate without authenticity claims; Topps product versus box
format; Philips without a verified model; and Orbit without verified 94026.

Those tests combine recorded retrieval output with explicit expected/recorded photo
facts. They measure candidate selection and closure policy, **not 10 fresh correct
identifications**. Existing photographic fixtures for Kobe, Vileplume, Topps and Philips
are retained. The six remaining originals were retrieved read-only from the experimental
commit and visually inspected; Dragonite’s tiny footer is not claimed as a fresh clear OCR
reading. The Glurak browser closure test uses recorded build204 physical readings and
a recorded page containing the same literal German sale quote. Browser tests use synthetic image carriers with recorded observation
packets and intercepted native/API requests. Native Android tests cover actual local
OCR fixtures, upload/background/cancellation/navigation and result propagation.

Server tests cover the verified HTTP contract, no text hint, candidate cap, credential
isolation, no redirects, expiry/deletion, deduplication, errors, exhausted credits,
empty responses and unknown-billing timeouts. Browser tests exercise failure fallback,
three uploaded photos and persisted incomplete outcomes. No new paid API tests.

## Deployment boundary

The APK contains the client and an endpoint/access configuration screen. The repository
contains the deployable server, but the GitHub secret alone does not make it reachable.
Until a real HTTPS service is provisioned and connected, diagnostics explicitly report
`not_configured` and the app continues through the existing recognition path. This must
be disclosed with the APK; it is not a live Lens validation.
