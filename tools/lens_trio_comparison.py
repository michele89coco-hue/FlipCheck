#!/usr/bin/env python3
"""Run the three user-supplied original images through one Lens provider."""
import hashlib
import importlib
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FIXTURE_PATH = "tools/regression/fixtures/lens-trio-20260909"
CASES = [("image_a", "image-a.jpg"), ("image_b", "image-b.jpg"), ("image_c", "image-c.jpg")]
EXPECTED = {
    "image-a.jpg": "382e2f2258a6718e18483e606ab7980b47ef726ed740db6ef5481bf7c3882762",
    "image-b.jpg": "fdd5e9bd13d4ebd28f6e4b44faa8a05206ad4499991817e050a099770d1b3744",
    "image-c.jpg": "0a0a3531ad68952d08b15fa2446026c64cc1d86ffff46f0eb185c2a776186bf2"
}

def compact(value):
    if isinstance(value, str) and value.startswith("data:image/"):
        return "[embedded image omitted from logs]"
    if isinstance(value, list):
        return [compact(item) for item in value]
    if isinstance(value, dict):
        return {key: compact(item) for key, item in value.items()}
    return value

def main():
    provider = sys.argv[1] if len(sys.argv) == 2 else ""
    if provider not in ("searchapi", "oxylabs"):
        raise SystemExit("Expected provider: searchapi or oxylabs")
    revision = os.environ.get("GITHUB_SHA", "")
    if len(revision) != 40 or any(c not in "0123456789abcdef" for c in revision):
        raise SystemExit("A concrete GitHub commit SHA is required")
    for filename, expected in EXPECTED.items():
        actual = hashlib.sha256((ROOT / FIXTURE_PATH / filename).read_bytes()).hexdigest()
        if actual != expected:
            raise SystemExit("Original fixture checksum mismatch: " + filename)

    probe = importlib.import_module(provider + "_lens_probe")
    probe.CASES = list(CASES)
    probe.FIXTURE_COMMIT = revision
    probe.IMAGE_ROOT = ("https://raw.githubusercontent.com/michele89coco-hue/FlipCheck/"
                        + revision + "/" + FIXTURE_PATH + "/")
    attempt = 1

    def output(marker, value):
        safe = compact(probe.sanitize(value))
        if isinstance(safe, dict):
            safe = {"attempt": attempt, **safe}
        print(marker + " " + json.dumps(safe), flush=True)

    probe.output = output
    destination = ROOT / "trio-lens-results" / provider
    destination.mkdir(parents=True, exist_ok=True)
    os.chdir(destination)
    output("TRIO_INPUTS", {
        "provider": provider, "sha256": EXPECTED,
        "original_bytes": True, "text_hint": None,
        "maximum_initial_searches": len(CASES),
        "maximum_provider_613_retries": len(CASES) if provider == "oxylabs" else 0,
    })
    status = probe.main()
    report_path = destination / (provider + "-results") / "report.json"
    def review(path):
        if not path.exists():
            return
        report = json.loads(path.read_text())
        for case in report.get("cases", []):
            selected = {key: value for key, value in case.items()
                        if key not in ("parsed_content", "visual_matches", "exact_matches")}
            selected["visual_matches"] = [
                {key: value for key, value in match.items()
                 if key in ("position", "title", "link", "source", "snippet")}
                for match in case.get("visual_matches", [])[:20]
            ]
            selected["exact_matches"] = [
                {key: value for key, value in match.items()
                 if key in ("position", "title", "link", "source", "snippet")}
                for match in case.get("exact_matches", [])[:20]
            ]
            output("TRIO_REVIEW", {"provider": provider, **selected})
    review(report_path)
    if provider == "oxylabs" and report_path.exists():
        initial = json.loads(report_path.read_text())
        failed = {case["id"] for case in initial["cases"]
                  if case.get("http_status") == 200
                  and 613 in case.get("page_statuses", [])
                  and not case.get("visual_match_count")}
        if failed:
            attempt = 2
            probe.CASES = [case for case in CASES if case[0] in failed]
            retry_dir = destination / "retry"
            retry_dir.mkdir(exist_ok=True)
            os.chdir(retry_dir)
            output("OXYLABS_RETRY", {"case_ids": sorted(failed), "reason": "provider_status_613"})
            probe.main()
            retry_path = retry_dir / "oxylabs-results" / "report.json"
            review(retry_path)
            recovered = set()
            if retry_path.exists():
                retry = json.loads(retry_path.read_text())
                recovered = {case["id"] for case in retry["cases"]
                             if case["http_status"] == 200
                             and case["visual_match_count"] > 0
                             and case["page_statuses"]
                             and all(code == 200 for code in case["page_statuses"])}
            initially_ok = {case["id"] for case in initial["cases"]
                            if case["http_status"] == 200
                            and case["visual_match_count"] > 0
                            and case["page_statuses"]
                            and all(code == 200 for code in case["page_statuses"])}
            status = 0 if len(initially_ok | recovered) == len(CASES) else 1
    return status

if __name__ == "__main__":
    sys.exit(main())
