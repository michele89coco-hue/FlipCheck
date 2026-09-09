#!/usr/bin/env python3
"""Bounded, image-only SearchApi Lens experiment. No Gemini calls or APK changes."""
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ENDPOINT = "https://www.searchapi.io/api/v1/"
FIXTURE_COMMIT = "57fcdfb41b68b4388de3874d2d9b92dc74a336e7"
IMAGE_ROOT = (
    "https://raw.githubusercontent.com/michele89coco-hue/FlipCheck/"
    + FIXTURE_COMMIT + "/tools/regression/fixtures/v132-real-images/"
)
CASES = [
    ("kobe", "kobe-front.jpg"),
    ("vileplume", "vileplume-front.jpg"),
    ("topps", "topps-front.jpg"),
    ("philips", "philips-front.jpg"),
]
SECRET = os.environ.get("SEARCHAPI_API_KEY", "").strip()


def sanitize(value):
    if isinstance(value, str):
        return value.replace(SECRET, "[REDACTED]") if SECRET else value
    if isinstance(value, list):
        return [sanitize(v) for v in value]
    if isinstance(value, dict):
        return {
            str(k): sanitize(v) for k, v in value.items()
            if not any(x in str(k).lower() for x in ("api_key", "authorization", "token"))
        }
    return value


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward the API credential to a redirected destination.
        return None


def request_json(route, params=None):
    url = ENDPOINT + route
    if params:
        url += "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(
        url,
        headers={"Authorization": "Bearer " + SECRET, "Accept": "application/json"},
    )
    opener = urllib.request.build_opener(NoRedirect())
    try:
        with opener.open(req, timeout=90) as response:
            data = response.read(5_000_001)
            if len(data) > 5_000_000:
                return 0, {"error": "Response exceeds five megabytes"}
            return response.status, json.loads(data)
    except urllib.error.HTTPError as exc:
        # Do not print HTTP bodies, request URLs or headers on failures.
        return exc.code, {"error": "HTTP " + str(exc.code)}
    except (urllib.error.URLError, TimeoutError, ValueError, OSError) as exc:
        return 0, {"error": type(exc).__name__}


def account_snapshot(data):
    account = data.get("account") or {}
    return {
        k: account.get(k)
        for k in ("current_month_usage", "monthly_allowance", "remaining_credits")
        if isinstance(account.get(k), (int, float))
    }


def pick_match(match):
    picked = {
        k: match[k]
        for k in ("position", "title", "link", "source", "thumbnail", "price",
                  "extracted_price", "currency", "snippet")
        if k in match
    }
    if isinstance(match.get("image"), dict):
        picked["image"] = {
            k: match["image"][k] for k in ("link", "width", "height") if k in match["image"]
        }
    return picked


def output(marker, data):
    print(marker + " " + json.dumps(sanitize(data), ensure_ascii=True), flush=True)


def main():
    if not SECRET:
        print("SEARCHAPI_BLOCKED repository secret SEARCHAPI_API_KEY is missing", flush=True)
        return 2
    status, before_data = request_json("me")
    if status != 200:
        output("SEARCHAPI_BLOCKED", {"stage": "authentication", "http_status": status})
        return 2
    before = account_snapshot(before_data)
    output("SEARCHAPI_ACCOUNT_BEFORE", before)
    remaining = before.get("remaining_credits")
    if not isinstance(remaining, (int, float)) or remaining < len(CASES):
        output("SEARCHAPI_BLOCKED", {"reason": "Cannot verify four available credits"})
        return 2
    report = {
        "provider": "SearchApi", "engine": "google_lens", "fixture_commit": FIXTURE_COMMIT,
        "mode": "image_only_no_text_hint", "maximum_search_requests": len(CASES),
        "account_before": before, "cases": [],
        "interpretation": "Retrieval candidates only; no identity or variant is automatically confirmed.",
    }
    destination = Path("searchapi-results/report.json")
    destination.parent.mkdir(parents=True, exist_ok=True)
    for case_id, filename in CASES:
        params = {"engine": "google_lens", "url": IMAGE_ROOT + filename,
                  "search_type": "all", "country": "it", "hl": "en"}
        started = time.monotonic()
        status, data = request_json("search", params)
        metadata = data.get("search_metadata") or {}
        case = {
            "id": case_id, "image_url": params["url"], "search_type": "all",
            "http_status": status, "duration_seconds": round(time.monotonic() - started, 2),
            "provider_status": metadata.get("status"),
            "visual_match_count": len(data.get("visual_matches") or []),
            "exact_match_count": len(data.get("exact_matches") or []),
            "visual_matches": [pick_match(m) for m in (data.get("visual_matches") or [])[:20]],
            "exact_matches": [pick_match(m) for m in (data.get("exact_matches") or [])[:10]],
            "related_searches": [
                {k: m[k] for k in ("title", "link") if k in m}
                for m in (data.get("related_searches") or [])[:5]
            ],
        }
        if data.get("error"):
            case["error"] = str(data["error"])[:200]
        report["cases"].append(case)
        destination.write_text(json.dumps(sanitize(report), indent=2), encoding="utf-8")
        output("SEARCHAPI_CASE", {**case, "visual_matches": case["visual_matches"][:8]})
        if status in (401, 403, 429):
            break  # No automatic retries or further credit use after account/rate errors.
    status, after_data = request_json("me")
    after = account_snapshot(after_data) if status == 200 else {}
    report["account_after"] = after
    after_remaining = after.get("remaining_credits")
    report["observed_credit_delta"] = remaining - after_remaining if isinstance(after_remaining, (int, float)) else None
    destination.write_text(json.dumps(sanitize(report), indent=2), encoding="utf-8")
    output("SEARCHAPI_ACCOUNT_AFTER", after)
    output("SEARCHAPI_SUMMARY", {
        "search_requests": len(report["cases"]),
        "successful_http_responses": sum(c["http_status"] == 200 and not c.get("error") for c in report["cases"]),
        "cases_with_visual_candidates": sum(c["visual_match_count"] > 0 for c in report["cases"]),
        "observed_credit_delta": report["observed_credit_delta"],
    })
    return 0 if len(report["cases"]) == len(CASES) and all(c["http_status"] == 200 and not c.get("error") for c in report["cases"]) else 1


if __name__ == "__main__":
    sys.exit(main())
