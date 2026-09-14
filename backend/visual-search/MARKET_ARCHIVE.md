# FlipCheck market archive — prepared integration

This change adds server endpoints for exact-card alias plans, immediate market
attempt persistence, diagnostic retrieval, and a seven-day cache. It does not
call Gemini/OpenAI, replace the Android pipeline, or deploy any Render resource.
The current Android APK still saves locally. An Android client integration and
the agreed Gemini identity / two OpenAI market calls are separate pending work.

## Runtime configuration

Reuse `flipcheck-lens` in Frankfurt (`srv-dagmg50u01pc738hokj0`). Add managed
PostgreSQL in the same confirmed workspace/region, and set the **internal**
connection string as `FLIPCHECK_DATABASE_URL`. Schema creation runs at startup.
No SQLite or memory fallback is used in production. When the database is absent,
save/read/lookup return 503 and `saved: false`; query planning still works.

The existing `FLIPCHECK_ACCESS_TOKEN` authorizes private test-client requests.
Generate a distinct `FLIPCHECK_MARKET_REVIEW_TOKEN` for operator/backend review;
never embed the review credential in the APK. Existing Lens routes remain intact.
This shared test token is not per-user authentication: add tenant/user authorization
before a public multi-user release.

Proposed resource, pending cost approval: PostgreSQL `basic_256mb`, 1 GB,
Frankfurt, named `flipcheck-market`. The existing free web service can connect
internally; its cold-start behavior is unchanged. Render's free database expires
after 30 days, so it is not the proposed durable archive.

## HTTP contract

All requests use JSON, HTTPS and `Authorization: Bearer <client access token>`.
Only `/approve` uses the separate review token. POST bodies are limited to 256 KiB.

| Endpoint | Purpose |
| --- | --- |
| GET `/v1/market/config` | Reports whether persistent storage is configured |
| POST `/v1/market/plan` | Primary short query plus at most two alias alternatives |
| POST `/v1/market/save` | Saves a started/completed/empty/partial/error market attempt |
| POST `/v1/market/read` | Reads the complete sanitized report by `attempt_id` |
| POST `/v1/market/lookup` | Gets an approved fresh result or requests a refresh |
| POST `/v1/market/approve` | Approves a specific revision after source/identity review |

Example planning/lookup input:

```json
{
  "identity": {
    "category": "Sports", "subject_en": "Luka Doncic", "year": "2018-19",
    "set_en": "Panini Prizm", "card_number": "280", "variant_en": "Green",
    "language": "en", "format": "raw"
  },
  "context": {"currency": "EUR", "market": "ebay.it", "component": "values"},
  "aliases": [{"field": "subject_en", "value": "Luka Dončić"}]
}
```

Use `component: history` for the separate sold-history response/cache. History
and current values do not overwrite one another. Alias fields are limited to
`subject_en` and `set_en`: use only confirmed equivalent catalogue names supplied
by the identity stage/local dictionary. Aliases never substitute a different
variant, language, grader or grade. They do not change the canonical cache key.
Reconcile a recognized synonym to canonical identity before lookup; the server
does not guess that two independently submitted identities represent one card.

Optional identity fields: `edition`, `serial_tier`, `autograph`, `memorabilia`,
`grading_company`, `grade`, `raw_condition`. Unknown identity fields must be
resolved upstream rather than guessed. All names in the canonical identity are
English; `language` always describes the physical printed card. Slab identities
from local OCR use exactly the same contract without an extra identification call.
`serial_tier: 2/25` normalizes to `/25`; collector number `9/165` remains intact.
Certificate IDs never enter the query or shared cache key.

For `/save`, add `attempt_id` (8–100 safe alphanumeric/underscore/hyphen chars),
monotonically increasing integer `revision`, and `status`. Save revision 0 with
`status: started` **before** starting a market provider request. Save each completed
stage immediately, including empty responses and error codes. Retries reuse the
same attempt/revision; changed content requires a higher revision. A new refresh
uses a new attempt ID. Alias plans are fixed within each attempt.

Structured result example (use `values` for estimates/asking prices and `sales`
for sold transactions):

```json
{
  "values": [{"kind": "market_estimate", "amount": 100, "currency": "EUR",
              "url": "https://source.example/card", "grading_company": "", "grade": ""}],
  "sales": [{"kind": "sold", "amount": 95, "currency": "EUR",
             "url": "https://source.example/sale", "sold_date": "2026-09-01",
             "grading_company": "", "grade": ""}]
}
```

Prices above are **contract examples, not real valuations**. Record real query
attempts as `audit: {provider, queries_used: [{query, status, exact_matches}]}`.
The query must belong to the saved plan. Error codes are short machine-readable
codes; raw exception text is not stored. Do not upload full provider prompts,
images, personal information, credentials, or Google grounded response bodies.
Only explicitly whitelisted structured market fields are retained. Source URLs
with credential query parameters are rejected.

The caller controls fallback execution and budget: use the first query, then
aliases only if exact matches are insufficient. This endpoint does not execute
searches or authorize additional paid calls. Persist both unsuccessful and
successful attempts to determine which alias actually recovered results.

## Cache review and safety

Client-supplied `ok` is not proof of correct identity or a real sale. It stays in
diagnostics until server/operator review confirms exact card, language, condition,
source date and actual price. Then POST `{attempt_id, revision}` to `/approve`
using the review credential. In the future server-side provider pipeline, the
same approval gate can follow source validation automatically. Do not add a model
`verified: true` shortcut. Attribution/source usage rights still apply.

Approval requires a nonempty `ok` report and an exact raw/slab value or sale in
the requested currency and component. Asking prices cannot be sold rows or the
sole basis for cache approval. Graded values may be retained separately, but PSA
10 is never returned as an approved raw valuation. Updating a reviewed report
revokes its approval. Duplicate/out-of-order delivery does not renew cache age.

Cache expires seven days after the report update; a miss requests on-demand
refresh without scheduling any paid background searches. Expired reports remain
in the archive for diagnostics. Failed new attempts do not replace prior fresh,
approved attempts. No automatic retention deletion is enabled at this stage;
monitor database usage before broad release.

## Validation and activation

Local: `python -m unittest test_market_archive test_lens` from this directory.
GitHub workflow `market-archive-tests.yml` also repeats archive tests against an
isolated PostgreSQL 16 service, with no paid API calls. Test fixtures exercise
HTTP authentication, save/read/review/lookup, restart persistence, currency/
language/variant/grade isolation, revision conflicts, duplicate concurrency,
history/value separation and TTL expiry.

After approving database cost: provision PostgreSQL, set connection/review
credentials, deploy the reviewed commit, and verify write/read through a service
restart. Then connect the Android outbox and cache lookup to the agreed market
pipeline. Until that live check succeeds, report remote persistence as inactive.
