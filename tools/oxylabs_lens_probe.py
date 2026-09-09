#!/usr/bin/env python3
"""Compare Oxylabs Lens with the preceding SearchApi four-photo experiment."""
import base64
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ENDPOINT = "https://realtime.oxylabs.io/v1/queries"
FIXTURE_COMMIT = "57fcdfb41b68b4388de3874d2d9b92dc74a336e7"
IMAGE_ROOT = ("https://raw.githubusercontent.com/michele89coco-hue/FlipCheck/"
              + FIXTURE_COMMIT + "/tools/regression/fixtures/v132-real-images/")
CASES = [("kobe", "kobe-front.jpg"), ("vileplume", "vileplume-front.jpg"),
         ("topps", "topps-front.jpg"), ("philips", "philips-front.jpg")]
USERNAME = os.environ.get("OXYLABS_USERNAME", "").strip()
PASSWORD = os.environ.get("OXYLABS_PASSWORD", "").strip()
AUTH = base64.b64encode((USERNAME + ":" + PASSWORD).encode()).decode()
SECRETS = [s for s in (USERNAME, PASSWORD, AUTH,
           urllib.parse.quote(USERNAME, safe=""),
           urllib.parse.quote(PASSWORD, safe="")) if len(s) > 3]


def sanitize(value):
    if isinstance(value, str):
        for secret in SECRETS:
            value = value.replace(secret, "[REDACTED]")
        return value
    if isinstance(value, list):
        return [sanitize(v) for v in value]
    if isinstance(value, dict):
        return {str(k): sanitize(v) for k, v in value.items()
                if not any(x in str(k).lower()
                           for x in ("password", "username", "authorization", "token", "api_key"))}
    return value


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request_json(payload):
    req = urllib.request.Request(
        ENDPOINT, data=json.dumps(payload).encode(), method="POST",
        headers={"Authorization": "Basic " + AUTH,
                 "Accept": "application/json", "Content-Type": "application/json"})
    try:
        with urllib.request.build_opener(NoRedirect()).open(req, timeout=120) as response:
            data = response.read(6_000_001)
            if len(data) > 6_000_000:
                return 0, {"error": "Response exceeds six megabytes"}
            return response.status, json.loads(data)
    except urllib.error.HTTPError as exc:
        return exc.code, {"error": "HTTP " + str(exc.code)}
    except (urllib.error.URLError, TimeoutError, ValueError, OSError) as exc:
        return 0, {"error": type(exc).__name__}


def pick_match(match):
    fields = {"pos": "position", "url": "link", "title": "title",
              "domain": "source", "url_thumbnail": "thumbnail",
              "price": "price", "url_image": "image_url", "image_url": "image_url"}
    return {new: match[old] for old, new in fields.items() if old in match}


def parse_response(status, data):
    pages = data.get("results") if isinstance(data.get("results"), list) else []
    visual, exact, page_statuses, parse_statuses, parsed_content = [], [], [], [], []
    for page in pages:
        page_statuses.append(page.get("status_code"))
        content = page.get("content") or {}
        if isinstance(content, str):
            try:
                content = json.loads(content)
            except ValueError:
                content = {"format": "unparsed_text", "length": len(content)}
        if not isinstance(content, dict):
            continue
        parse_statuses.append(content.get("parse_status_code"))
        results = content.get("results") or {}
        if isinstance(results, dict):
            visual.extend(pick_match(m) for m in (results.get("organic") or [])
                          if isinstance(m, dict))
            exact.extend(pick_match(m) for m in (results.get("exact_match") or [])
                         if isinstance(m, dict))
        parsed_content.append(content)
    out = {"http_status": status, "page_statuses": page_statuses,
           "parse_statuses": parse_statuses,
           "visual_match_count": len(visual), "exact_match_count": len(exact),
           "visual_matches": visual, "exact_matches": exact,
           "parsed_content": parsed_content}
    if data.get("error"):
        out["error"] = str(data["error"])[:200]
    return out


def output(marker, value):
    print(marker + " " + json.dumps(sanitize(value), ensure_ascii=True), flush=True)


def main():
    missing = [n for n, value in (("OXYLABS_USERNAME", USERNAME),
                                 ("OXYLABS_PASSWORD", PASSWORD)) if not value]
    if missing:
        output("OXYLABS_BLOCKED", {"missing_repository_secrets": missing})
        return 2
    report = {"provider": "Oxylabs", "source": "google_lens",
              "fixture_commit": FIXTURE_COMMIT,
              "baseline_run": "https://github.com/michele89coco-hue/FlipCheck/actions/runs/34351482948",
              "maximum_search_requests": len(CASES),
              "mode": "image_only_no_text_hint", "locale": "en", "geo_location": "Italy",
              "javascript_rendering_requested": False,
              "interpretation": "Retrieval candidates only; no identity or variant is automatically confirmed.",
              "cases": []}
    destination = Path("oxylabs-results/report.json")
    destination.parent.mkdir(parents=True, exist_ok=True)
    output("OXYLABS_START", {"maximum_search_requests": len(CASES),
                            "fixture_commit": FIXTURE_COMMIT,
                            "locale": "en", "geo_location": "Italy"})
    for case_id, filename in CASES:
        payload = {"source": "google_lens", "query": IMAGE_ROOT + filename,
                   "parse": True, "geo_location": "Italy", "locale": "en"}
        started = time.monotonic()
        status, data = request_json(payload)
        case = {"id": case_id, "image_url": payload["query"],
                "duration_seconds": round(time.monotonic() - started, 2),
                **parse_response(status, data)}
        report["cases"].append(case)
        destination.write_text(json.dumps(sanitize(report), indent=2), encoding="utf-8")
        displayed = {k: v for k, v in case.items() if k != "parsed_content"}
        displayed["visual_matches"] = case["visual_matches"][:10]
        output("OXYLABS_CASE", displayed)
        if status in (401, 403, 429):
            break  # Stop immediately on authentication, permission or quota failure.
    success = sum(c["http_status"] == 200 and c["visual_match_count"] > 0
                  and all(s == 200 for s in c["page_statuses"])
                  for c in report["cases"])
    report["summary"] = {
        "search_requests": len(report["cases"]), "cases_with_visual_candidates": success,
        "total_search_seconds": round(sum(c["duration_seconds"] for c in report["cases"]), 2),
        "billed_units": None, "billing_note": "Usage/billing must be checked in the provider dashboard.",
    }
    destination.write_text(json.dumps(sanitize(report), indent=2), encoding="utf-8")
    output("OXYLABS_SUMMARY", report["summary"])
    return 0 if success == len(CASES) else 1


if __name__ == "__main__":
    sys.exit(main())
