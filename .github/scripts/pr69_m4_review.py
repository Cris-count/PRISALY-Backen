"""Reuse the candidate's sanitizer and verify the focused review without modifying candidate files."""
import json
import pathlib
import subprocess
import sys

out = pathlib.Path(sys.argv[1])
status = subprocess.run([sys.executable, ".github/scripts/m4_schema_evidence.py", str(out)], check=False).returncode
summary_file = out / "summary.json"
if not summary_file.exists():
    sys.exit("No sanitized test evidence generated")
summary = json.loads(summary_file.read_text())
required = {
    "ReferenciasCanonicasTest", "ValorRespuestaContratoTest", "MapeoIntentoFilasCorruptasTest",
    "ErroresTecnicosM5Test", "RespuestaHttpErroresTecnicosTest", "ModelosLimitesEInmutabilidadTest",
    "M4CoordinacionInicioTest", "FallosTecnicosM5IT", "RevisionMacro02PersistenciaIT",
    "RobustezPersistenciaM5IT", "ConcurrenciaLectorM5M6ReservaD06IT", "ConsultaIntentoEvaluacionM5M6IT",
    "HistorialM4M5IT", "ParticipacionesPortWiringIT",
}
present = {s["suite"].rsplit(".", 1)[-1] for s in summary["suites"] if s["tests"] > 0}
summary["required_suites_missing"] = sorted(required - present)
summary["product_sha_file"] = (out / "product-sha.txt").read_text().strip()
summary["unit_tests"] = sum(s["tests"] for s in summary["suites"] if s["phase"] == "surefire")
summary["postgres_tests"] = sum(s["tests"] for s in summary["suites"] if s["phase"] == "failsafe")
summary_file.write_text(json.dumps(summary, indent=2) + "\n")
for file in out.iterdir():
    if file.is_file():
        file.write_text(file.read_text().replace("hunter2", "[FIXTURE SECRET REDACTED]"))
if status or required - present or summary["product_sha_file"] != "b97ba887629b6229fdd9676877e31049be4c3461":
    sys.exit("Incomplete or failed review; inspect sanitized summary and XML")
