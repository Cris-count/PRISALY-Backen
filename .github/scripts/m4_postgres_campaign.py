"""One-shot DC-M4-POSTGRES-02 campaign; no shared databases or diagnostic V010."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

TARGET = "d8eb62d0e4873ea28f426ed3d59392a5cd1ae95c"
EXPECTED = {
    "Bd0052BaselineIT": 3,
    "M4M1ContratosPostgreSqlIT": 6,
    "ProgramacionParticipacionPostgreSqlIT": 22,
    "SesionAcademicaPostgreSqlIT": 32,
    "ParticipacionesM4DesdeM5IT": 8,
    "InicioIntentoIT": 15,
    "HistorialM4M5IT": 23,
    "ParticipacionesPortWiringIT": 1,
    "NotificacionPortWiringIT": 1,
    "ConsultaIntentoEvaluacionM5M6IT": 3,
}
BASELINE_METHODS = [
    "rf09RealDeBaseDatosRechazaBorrador",
    "publicadoProgramaYBloqueaContenidoConTriggerReal",
    "rollbackRevierteSesionYMarcaAcademicaEnMismaTransaccion",
]


def main():
    source, output = map(lambda s: Path(s).resolve(), sys.argv[1:3])
    output.mkdir(parents=True, exist_ok=True)
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
    if sha != TARGET:
        raise RuntimeError(f"Unexpected candidate SHA: {sha}")
    baseline = source / "app/src/main/resources/db/migration"
    migrations = sorted(baseline.glob("*.sql"))
    # P5 (DC-P5-FASE-B-01): canonical manifest is V001-V009 plus V013.
    expected = {f"V{i:03}" for i in range(1, 10)} | {"V013"}
    if len(migrations) != 10 or {p.name.split("__")[0] for p in migrations} != expected:
        raise RuntimeError("Expected only canonical V001-V009 and V013")
    # Fresh checkout is mandatory: do not consume previous build/test evidence.
    if list(source.glob("*/target")):
        raise RuntimeError("Candidate must have no pre-existing target directories")
    manifest = {
        "sha": sha,
        "migrations": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in migrations},
        "expected_postgresql_cases": sum(EXPECTED.values()),
        "runner": {k: os.environ.get(k) for k in (
            "RUNNER_OS", "RUNNER_ARCH", "ImageOS", "ImageVersion", "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT"
        )},
    }
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2))
    suites = ["Bd0052BaselineIT#" + "+".join(BASELINE_METHODS)] + list(EXPECTED)[1:]
    command = [
        "sh", "./mvnw", "-B", "-fae", "-pl", "app", "-am", "verify", "-Pdocker-it",
        "-Dtest=SesionAcademicaDomainTest,M4MigrationResourcesTest,M4CoordinacionInicioTest,M4ProviderSecurityTest,ParticipacionesM4DesdeM5Test",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "-Dit.test=" + ",".join(suites), "-Dfailsafe.failIfNoSpecifiedTests=false",
        "-Dm4.baseline.migrations=" + str(baseline),
    ]
    (output / "command.json").write_text(json.dumps(command, indent=2))
    env = os.environ.copy()
    env["TESTCONTAINERS_REUSE_ENABLE"] = "false"
    with (output / "maven.log").open("w") as log:
        process = subprocess.Popen(command, cwd=source, env=env, text=True,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        for line in process.stdout:
            print(line, end="", flush=True)
            log.write(line)
        code = process.wait()
    results, problems = [], []
    totals = {k: 0 for k in ("tests", "failures", "errors", "skipped")}
    for name, count in EXPECTED.items():
        paths = list(source.glob(f"*/target/failsafe-reports/TEST-*.{name}.xml"))
        if len(paths) != 1:
            problems.append(f"{name}: expected exactly one report, got {len(paths)}")
            continue
        path = paths[0]
        root = ET.parse(path).getroot()
        stats = {k: int(root.get(k, 0)) for k in totals}
        cases = root.findall("testcase")
        if stats["tests"] != count or len(cases) != count:
            problems.append(f"{name}: expected {count} cases, got {stats['tests']}/{len(cases)}")
        if any(stats[k] for k in ("failures", "errors", "skipped")):
            problems.append(f"{name}: failures/errors/skips present")
        if name == "Bd0052BaselineIT" and {c.get("name") for c in cases} != set(BASELINE_METHODS):
            problems.append("Canonical baseline method selection differs")
        if any("diagnosticoV010" in (c.get("name") or "") for c in cases):
            problems.append("Forbidden diagnostic V010 was executed")
        for k in totals:
            totals[k] += stats[k]
        failures = [dict(suite=name, case=c.get("name"),
                         messages=[e.text or e.get("message", "") for e in c
                                   if e.tag in ("failure", "error", "skipped")])
                    for c in cases if any(c.find(t) is not None for t in ("failure", "error", "skipped"))]
        results.append(dict(suite=name, **stats, failures_detail=failures))
    # Preserve Failsafe evidence, removing JVM properties which may include credentials.
    for path in source.glob("*/target/failsafe-reports/*.xml"):
        tree = ET.parse(path)
        for parent in tree.iter():
            for child in list(parent):
                if child.tag == "properties":
                    parent.remove(child)
        dest = output / "failsafe" / path.relative_to(source)
        dest.parent.mkdir(parents=True, exist_ok=True)
        tree.write(dest, encoding="utf-8", xml_declaration=True)
    # Preserve crash diagnostics separately when the JVM did not produce normal XML.
    for path in source.glob("*/target/failsafe-reports/*dump*"):
        dest = output / "failsafe" / path.relative_to(source)
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(path.read_bytes())
    text = (output / "maven.log").read_text(errors="replace")
    migration_lines = [line for line in text.splitlines()
                       if re.search(r"Successfully (applied|validated)|Migrating schema|Current version", line)]
    (output / "migrations.log").write_text("\n".join(migration_lines))
    if code:
        problems.append(f"Maven exit code {code}")
    if totals["tests"] != sum(EXPECTED.values()):
        problems.append("Not all mandatory PostgreSQL cases executed")
    summary = dict(sha=sha, maven_exit=code, totals=totals, suites=results, problems=problems,
                   migrations="V001-V009 + V013 manifest; baseline cases passed" if not problems
                   else "See manifest, migrations.log and baseline report; not approved globally",
                   diagnostic_v010="excluded", shared_databases="not used")
    (output / "summary.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))
    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a") as f:
            f.write(f"## DC-M4-POSTGRES-02\n\nSHA: `{sha}`\n\n```json\n{json.dumps(totals, indent=2)}\n```\n")
            f.write("\n".join(f"- {p}" for p in problems) or "All mandatory PostgreSQL cases passed.")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
