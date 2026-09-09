#!/usr/bin/env python3
"""Test a pixel-preserving front/back contact sheet with one request per provider."""
import hashlib
import importlib
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FIXTURE_PATH = "tools/regression/fixtures/lens-collage-20260909"
CASES = [("image_a", "image-a.png")]
EXPECTED = {
    "image-a.png": "0e05823d2fd85dc8e18fda814587caa998d30b4c508e263fd4fe44a51bdcd108"
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
    destination = ROOT / "collage-lens-results" / provider
    destination.mkdir(parents=True, exist_ok=True)
    os.chdir(destination)
    output("COLLAGE_INPUTS", {
        "provider": provider, "sha256": EXPECTED,
        "input_kind": "front_back_contact_sheet", "source_pixels_preserved": True,
        "resized": False, "front_left_back_right": True, "text_hint": None,
        "maximum_initial_searches": len(CASES),
        "maximum_provider_613_retries": 0,
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
            output("COLLAGE_REVIEW", {"provider": provider, **selected})
    review(report_path)
    return status

if __name__ == "__main__":
    sys.exit(main())
