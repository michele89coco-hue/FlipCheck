# Build 236 — identity-based sold comparisons

## Why
The recorded Anthony Hill Jr. scan has concordant physical Superfractor text and surface evidence, but the recognition candidate says Tie dye. The physical parallel now wins when the provider supports the same core identity and the printed identifiers agree. A Topps/Bowman Superfractor derives 1/1 from its parallel definition, explicitly distinct from a serial read on the image. Conflicting or unreadable physical keys still prevent closure. Existing subject and season protections remain.

## Market sources checked, 11 September 2026
- Pokémon: https://www.pricecharting.com/game/pokemon-base-set/machamp-shadowless-8
- Sports: https://www.sportscardspro.com/game/basketball-cards-2018-panini-prizm/luka-doncic-green-prizm-280
- Discovery: https://www.sportscardspro.com/search-products?q=luka+doncic+green+prizm&type=prices
- API limitation: https://www.pricecharting.com/api-documentation and https://www.sportscardspro.com/api-documentation

Both product pages separate headline guide values from dated sold rows and grading groups. Broad Dončić searches return many years and parallels. The 2018 index corresponds to season 2018-19. Individual rows can be misclassified: the Green Prizm page includes a numbered /5 sale; the Machamp page includes a Trainer Deck A title. Therefore matching a product page is insufficient to accept every row. Native pricing APIs do not supply individual historical sales; this implementation uses OpenAI Responses web search restricted to the appropriate site.

Queries include subject, season, set, collector number, parallel, language, printing, autograph and serial/print run. Internal search URLs are generated; product slugs are discovered rather than guessed. Pokémon localized names can aid discovery but do not change the language constraint for comparable sales.

## Behavior
- Ximilar requests recognition without the optional paid price statistics. Its estimated budget reservation is reduced accordingly; this is an estimate, not a statement of the user's actual plan charge.
- An explicit “Cerca vendite comparabili” action retrieves dated sold rows. The shared budget and cancellation still apply. Refresh is another explicit request, not an automatic retry.
- Require a retrieved, cited product page on the selected domain, sale URL, short evidence quote, explicit price/currency and date within the last year.
- Exclude incompatible identity, language, printing, parallel, autograph, print run, duplicate sale IDs, asking prices and unsupported records. Unknown language does not become English just because the website is English.
- Raw median requires at least three accepted rows, one currency, and matching condition when the user's condition is known. Otherwise label the raw figure as an indication with unspecified condition. Three sales are a minimum display gate, not a statistical guarantee.
- Slab median requires the same grader and grade. PSA 5–10 comparisons are separate and require their own observed sales; generic “Grade 8” never means PSA 8 automatically.
- 1/1 cards get no generic median, guide substitution or inferred grading values. Exact previous sales may appear as history. An inaccessible site, missing product page, insufficient sample and no matching sales have distinct states.
- Site guide quotations remain secondary and appear only alongside accepted identity-compatible sales. They never become the value of the photographed copy.

## Validation scope
The regression suite replays the uploaded Hill diagnostic without credentials or images and covers wrong identity/serial, sold filtering, language/currency, source provenance, raw/slab separation and 1/1 absence of comps. Browser tests mock the external API boundary and exercise rendering, budget rejection and explicit refresh. Android instrumentation checks the recognition-only payload and existing native lifecycle. Public sample pages were inspected live; no paid live OpenAI market extraction or new recognition accuracy claim is made by these tests.
