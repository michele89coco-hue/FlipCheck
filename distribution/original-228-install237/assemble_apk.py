"""Reassemble the archived signed APK and verify every byte against its manifest."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parent
manifest = json.loads((ROOT / "manifest.json").read_text())
target = ROOT / manifest["apk"]
temporary = target.with_suffix(".assembling")
digest = hashlib.sha256()
size = 0
try:
    with temporary.open("wb") as output:
        for part in manifest["parts"]:
            path = (ROOT / part["file"]).resolve()
            if not path.is_relative_to(ROOT):
                raise ValueError("Part path escapes the archive directory")
            data = path.read_bytes()
            if len(data) != part["bytes"] or hashlib.sha256(data).hexdigest() != part["sha256"]:
                raise ValueError("Corrupt APK part: " + part["file"])
            output.write(data)
            digest.update(data)
            size += len(data)
    if size != manifest["bytes"] or digest.hexdigest() != manifest["sha256"]:
        raise ValueError("Reassembled APK does not match the signed original")
    temporary.replace(target)
finally:
    temporary.unlink(missing_ok=True)
print(json.dumps({"apk": str(target), "bytes": size, "sha256": digest.hexdigest(), "status": "PASS"}))
