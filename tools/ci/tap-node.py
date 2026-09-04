#!/usr/bin/env python3
"""Print the center of the first UIAutomator node matching a regex."""
import re
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 3:
    raise SystemExit("usage: tap-node.py WINDOW.XML REGEX")

pattern = re.compile(sys.argv[2], re.IGNORECASE)
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter("node"):
    # Match every semantic attribute independently. Concatenating them first
    # makes exact expressions such as ^flipcheck-api-key$ impossible because
    # unrelated text/resource-id values are appended to the description.
    if not any(
        pattern.search(node.attrib.get(key, ""))
        for key in ("content-desc", "text", "resource-id")
    ):
        continue
    bounds = node.attrib.get("bounds", "")
    values = [int(value) for value in re.findall(r"\d+", bounds)]
    if len(values) == 4 and values[2] > values[0] and values[3] > values[1]:
        print((values[0] + values[2]) // 2, (values[1] + values[3]) // 2)
        raise SystemExit(0)
raise SystemExit(1)
