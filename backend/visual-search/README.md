# FlipCheck Lens service — build 205

The Android APK calls this service; `SEARCHAPI_API_KEY` is never an Android build input.
The contract follows `tools/searchapi_lens_probe.py` on branch
`experiment/searchapi-lens-20260909` (script blob `77117af26ca71b658ead3e0a33ae22426b94761f`) and the
[SearchApi Google Lens documentation](https://www.searchapi.io/docs/google-lens):
GET `/api/v1/search`, bearer authentication, `engine=google_lens`, `url`,
`search_type=all`, `country=it`, `hl=en`. No text hint, no retry, no Oxylabs.

## Deploy

Use a container host with a public HTTPS domain. Terminate TLS at the host's proxy;
forward to container port 8080. Keep one replica: photographs and request deduplication
live in that process's bounded memory. Do not enable proxy caching or request/response
body logging; disable access logs for `/v1/lens-image/*`.

Set these **server runtime** environment variables in the host's secret settings:

| Variable | Meaning |
| --- | --- |
| `SEARCHAPI_API_KEY` | Existing SearchApi credential, provisioned to the server; GitHub secrets are not automatically hosting secrets |
| `PUBLIC_ORIGIN` | Actual public HTTPS origin, without a path |
| `FLIPCHECK_ACCESS_TOKEN` | Random app access token, at least 32 URL-safe characters; separate from provider key |
| `SEARCHAPI_UNIT_USD` | Per-search budget reservation based on the account plan; required, never assumed free |

Build with `docker build -t flipcheck-lens backend/visual-search` and run with the
runtime variables. The Dockerfile runs as an unprivileged user. The container can use
a read-only filesystem. No photo volume or database is required. Configure the host
to restart the process on failure and provide at least 256 MiB RAM.

Enter the public origin and app access token in the APK's Google Lens settings.
The token remains in memory during the app session; only the endpoint is persisted.
GET `/v1/lens/config` requires the app token and reports configuration readiness
without consuming a Lens credit. A missing/invalid service leaves OCR/catalogue/web
fallback available. Do not put the SearchApi key into the app access field.

## Image lifecycle and limits

The client uploads one re-encoded original photo (maximum 2048 px, no EXIF). The
server accepts up to 7 MiB, holds bytes only in RAM and issues a random 256-bit URL.
The URL is accessible to SearchApi for at most 90 seconds, without app authentication.
The server deletes it in `finally` on success, error or timeout; a timer enforces TTL
independently. URLs return `no-store` and `noindex`. A process restart deletes all photos.
This controls storage on FlipCheck's server; it does not claim deletion from provider systems.

At most 20 unique candidates are returned. Candidate rank is never identity proof.
Requests are deduplicated by scan ID plus image digest for 10 minutes; different
images cannot reuse a scan ID. Rate limit: 10 new scans/minute; 12 live images maximum.
An account snapshot is cached for five minutes and credits are reserved locally;
other clients may change the actual provider balance. Unknown billing after timeouts
retains the configured cost reservation. Diagnostics identify estimates separately.

## Validation

`python3 -m unittest discover -s backend/visual-search -p 'test*.py'`

Tests use injected recorded/synthetic transport responses and a local HTTP server.
No live SearchApi calls or credentials are needed. Production activation still
requires deploying this container, provisioning the runtime secrets, and checking
the public endpoint. Building the APK does not deploy the server.
