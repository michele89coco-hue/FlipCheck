#!/usr/bin/env python3
"""Fail closed unless the exact v1.34 APK has complete installed live evidence."""

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    parser.add_argument("--live-report", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--unit-pass", action="store_true")
    parser.add_argument("--replay-pass", action="store_true")
    parser.add_argument("--apk-installation-pass", action="store_true")
    parser.add_argument("--apk-launch-pass", action="store_true")
    args = parser.parse_args()

    apk = Path(args.apk)
    report_path = Path(args.live_report)
    failures = []
    if not apk.is_file():
        failures.append("apk_missing")
    if not report_path.is_file():
        failures.append("live_report_missing")
        live = {}
    else:
        live = json.loads(report_path.read_text(encoding="utf-8"))

    apk_sha = sha256(apk) if apk.is_file() else ""
    checks = {
        "unitTests": args.unit_pass,
        "replayTests": args.replay_pass,
        "livePipelineTests": live.get("livePipelineTests") == "PASS",
        "apkInstallation": args.apk_installation_pass,
        "apkLaunch": args.apk_launch_pass and live.get("apkLaunch") == "PASS",
        "fourRealRegressions": live.get("fourRealRegressions") == "PASS",
        "invariants": live.get("invariants") == "PASS",
        "costReport": live.get("costReport") == "PRESENT",
        "versionCode": int(live.get("versionCode", 0)) == 143,
        "versionName": live.get("versionName") == "1.34-evidence-integrity",
        "sameApkSha256": bool(apk_sha) and live.get("apkSha256") == apk_sha,
        "liveNoMock": live.get("testMode") == "LIVE_API_NO_MOCK_NO_REPLAY",
    }
    for key, passed in checks.items():
        if not passed:
            failures.append(key)

    runs = live.get("runs", []) if isinstance(live.get("runs", []), list) else []
    counts = Counter((run.get("case"), run.get("status")) for run in runs)
    for case in ("topps", "kobe", "vileplume", "philips"):
        if counts[(case, "PASS")] != 3:
            failures.append(f"{case}_requires_3_live_passes")
    if len(runs) != 12:
        failures.append("expected_12_live_runs")
    for run in runs:
        if run.get("status") == "PASS" and float(run.get("costUsd", 999.0)) > 0.0250001:
            failures.append(f"cost_exceeded:{run.get('case')}:{run.get('mode')}")
        if run.get("status") == "PASS" and not run.get("views"):
            failures.append(f"views_missing:{run.get('case')}:{run.get('mode')}")

    result = {
        "release": "FlipCheck-v1.34.apk",
        "status": "PASS" if not failures else "FAIL",
        "checks": checks,
        "failures": sorted(set(failures)),
        "apkSha256": apk_sha,
        "sourceCommit": live.get("sourceCommit", ""),
        "device": live.get("device", ""),
        "androidVersion": live.get("androidVersion", ""),
        "liveRunCount": len(runs),
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if not failures else 1


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


if __name__ == "__main__":
    raise SystemExit(main())
