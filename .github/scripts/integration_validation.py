"""Full backend audit, pinned product tree, disposable Testcontainers only. No application edits."""
import json
import os
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

SHA = os.environ["PRISALY_CANDIDATE_SHA"]
if not re.fullmatch(r"[0-9a-f]{40}", SHA):
    raise SystemExit("Full candidate SHA required")
MODULES = ["m1", "m2", "m3", "m4", "m5", "m6", "m7", "app"]
BASELINE = "Bd0052BaselineIT#rf09RealDeBaseDatosRechazaBorrador+publicadoProgramaYBloqueaContenidoConTriggerReal+rollbackRevierteSesionYMarcaAcademicaEnMismaTransaccion"
EXCLUSION = "Bd0052BaselineIT#diagnosticoV010CreaDosTablasParalelasYEludeTriggerOficial"
mode, target = sys.argv[1:]
out = pathlib.Path(target)
public = out / "public"
public.mkdir(parents=True, exist_ok=True)

def save(name, data):
    (public / name).write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

def scrub(text):
    text = re.sub(r"jdbc:postgresql://[^\s\"'<>]+", "[JDBC REDACTED]", text)
    text = re.sub(r'(?i)("(?:password|accessToken|refreshToken|token|codigo|challengeId|secret|cookie)"\s*:\s*)"(?:\\.|[^"\\])*"', r'\1"[REDACTED]"', text)
    text = re.sub(r"(?im)^(\s*(?:authorization|cookie|set-cookie)\s*[:=]).*$", r"\1 [REDACTED]", text)
    text = re.sub(r"(?i)Bearer\s+[A-Za-z0-9._~+/=-]+", "Bearer [REDACTED]", text)
    text = re.sub(r"(?i)((?:password|secret|hmacKey)\s*[=:]\s*)[^\s,\"'<>]+", r"\1[REDACTED]", text)
    text = re.sub(r"\$2[aby]\$\d\d\$[./A-Za-z0-9]{53}", "[BCRYPT REDACTED]", text)
    return text.replace("hunter2", "[FIXTURE SECRET REDACTED]").replace("LocalBruno!234", "[FIXTURE SECRET REDACTED]")

if mode == "run":
    actual = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if actual != SHA:
        sys.exit("Unexpected product SHA; no execution")
    names = set()
    inventory = {}
    for module in MODULES:
        production = sorted(pathlib.Path(module, "src/main").rglob("*.java"))
        tests = sorted(pathlib.Path(module, "src/test").rglob("*.java"))
        inventory[module] = {"production_files": len(production), "test_files": len(tests),
                             "implementation_files": len([p for p in production if p.name != "package-info.java"])}
        for file in tests:
            if file.name.endswith(("IT.java", "ITCase.java")) or file.name == "PostgreSqlDockerSmokeTest.java":
                names.add(file.stem)
    names.discard("Bd0052BaselineIT")
    selected = sorted(names) + [BASELINE]
    args = ["sh", "./mvnw", "-B", "-fae", "-Pdocker-it", "verify", "-Dit.test=" + ",".join(selected),
            "-Dfailsafe.failIfNoSpecifiedTests=false", "-Dm4.baseline.migrations=" + str(pathlib.Path("app/src/main/resources/db/migration").resolve())]
    save("command.json", {"sha": actual, "args": args, "required_integration_suites": sorted(names | {"Bd0052BaselineIT"}),
                          "deliberately_excluded": [EXCLUSION], "reason": "Archived V010 diagnostic is not the canonical runtime; M4SchemaContratoPostgreSqlIT separately verifies V010 rejection."})
    save("inventory.json", inventory)
    code = 124
    try:
        with (out / "maven-raw.log").open("w", encoding="utf-8") as log:
            result = subprocess.run(args, stdout=log, stderr=subprocess.STDOUT, timeout=1800, check=False)
            code = result.returncode
    finally:
        save("execution.json", {"sha": actual, "maven_exit": code})
    sys.exit(code)
elif mode != "collect":
    sys.exit("Expected run or collect")

raw = out / "maven-raw.log"
if raw.exists():
    (public / "maven.txt").write_text(scrub(raw.read_text(errors="replace")), encoding="utf-8")
    raw.unlink()
suites, cases = [], []
for module in MODULES:
    for phase in ("surefire", "failsafe"):
        for file in pathlib.Path(module, "target", phase + "-reports").glob("TEST-*.xml"):
            tree = ET.parse(file)
            root = tree.getroot()
            for props in root.findall("properties"):
                root.remove(props)
            (public / (module + "-" + file.name)).write_text(scrub(ET.tostring(root, encoding="unicode")), encoding="utf-8")
            suites.append(dict(module=module, phase=phase, suite=root.get("name"),
                               **{k: int(root.get(k, 0)) for k in ("tests", "failures", "errors", "skipped")}))
            for case in root.findall("testcase"):
                status = "error" if case.find("error") is not None else "failed" if case.find("failure") is not None else "skipped" if case.find("skipped") is not None else "passed"
                cases.append({"module": module, "phase": phase, "suite": root.get("name"), "name": case.get("name"), "status": status})
command = json.loads((public / "command.json").read_text()) if (public / "command.json").exists() else {}
execution = json.loads((public / "execution.json").read_text()) if (public / "execution.json").exists() else {"maven_exit": None}
present = {s["suite"].rsplit(".", 1)[-1] for s in suites if s["phase"] == "failsafe" and s["tests"] > 0}
missing = sorted(set(command.get("required_integration_suites", [])) - present)
totals = {key: sum(s[key] for s in suites) for key in ("tests", "failures", "errors", "skipped")}
totals["passed"] = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
save("summary.json", {"sha": SHA, "execution": execution, "totals": totals, "missing_integration_suites": missing,
                      "deliberately_excluded": command.get("deliberately_excluded", []), "suites": suites})
save("cases.json", cases)
print(json.dumps({"sha": SHA, "totals": totals, "missing_suites": missing}))
if execution["maven_exit"] != 0 or missing or not suites or any(totals[k] for k in ("failures", "errors", "skipped")):
    sys.exit("Audit contains failed, missing or omitted checks; inspect evidence")
