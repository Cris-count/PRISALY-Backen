"""Preserve only sanitized test output; no JVM properties, environment dump or credentials."""
import json
import os
import pathlib
import re
import shutil
import sys
import xml.etree.ElementTree as ET

out = pathlib.Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)

def scrub(text):
    text = re.sub(r"jdbc:postgresql://[^\s\"'<>]+", "[JDBC REDACTED]", text)
    text = re.sub(r"(?i)(password|accessToken|authorization)(\s*[=:]\s*)[^\s,\"<>]+", r"\1\2[REDACTED]", text)
    return text

raw = out / "maven-raw.log"
if raw.exists():
    (out / "maven.txt").write_text(scrub(raw.read_text(errors="replace")), encoding="utf-8")
    raw.unlink()
suites = []
for module in ("m1", "m2", "m3", "m4", "m5", "m6", "m7", "app"):
    for phase in ("surefire", "failsafe"):
        for source in pathlib.Path(module, "target", phase + "-reports").glob("TEST-*.xml"):
            tree = ET.parse(source)
            root = tree.getroot()
            for props in list(root.findall("properties")):
                root.remove(props)
            dest = out / (module + "-" + source.name)
            dest.write_text(scrub(ET.tostring(root, encoding="unicode")), encoding="utf-8")
            suites.append(dict(module=module, phase=phase, suite=root.get("name"),
                               **{k: int(root.get(k, 0)) for k in ("tests", "failures", "errors", "skipped")}))
references = pathlib.Path("app/target/m4-schema-canonical")
if references.exists():
    shutil.copytree(references, out / "references", dirs_exist_ok=True)
(out / "summary.json").write_text(json.dumps({"sha": os.environ.get("CANDIDATE_SHA"), "suites": suites}, indent=2) + "\n", encoding="utf-8")
expected = os.environ.get("EXPECTED_SUITE")
if not any(s["suite"] == expected and s["tests"] > 0 for s in suites):
    sys.exit("Missing required PostgreSQL suite")
if any(s["failures"] or s["errors"] or s["skipped"] for s in suites):
    sys.exit("Campaign reports contain failures, errors or omissions")
