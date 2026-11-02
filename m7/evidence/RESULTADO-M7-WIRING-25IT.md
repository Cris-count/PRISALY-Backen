# Evidencia — fix wiring M7 (25 IT PostgreSQL)

**Base auditoría:** `1960eff`  
**Rama:** `feature/m1-m7-estabilizacion`

## Cambio

- `M7Configuration` ya no exige `StudentAccessValidationService` (issuance ensamblado en app).
- DD-02 productivo apagado: `prisaly.m1.student-access.enabled=false`, `/validar` → 501, denying intacto, worker SMTP off.
- `CaseAuthorizationPort` en app fail-closed (sin `CREAR_CASO` como atajo de publicar/modificar).

## Unitarias (M1/M7)

```text
mvnw -pl m1,m7 -am "-Dtest=DenyingStudentAccessServiceTest,EstudianteAccessValidarTest,M7WorkerDisabledTest,M4FallbackSinM7Test,StudentAccessDeliveryM1M7Test" test "-Dsurefire.failIfNoSpecifiedTests=false"
```

Resultado: BUILD SUCCESS (M1 5 tests, M7 4 tests filtrados).

## 25 IT PostgreSQL

```text
mvnw -B -pl m7 -am verify -Pdocker-it "-Dit.test=M7ConvocatoriaPostgreSqlIT,M7EnvioWorkerPostgreSqlIT" "-Dfailsafe.failIfNoSpecifiedTests=false"
```

| Suite | Resultado |
| --- | --- |
| `M7ConvocatoriaPostgreSqlIT` | 14/14 |
| `M7EnvioWorkerPostgreSqlIT` | 11/11 |
| **Total** | **25/25** BUILD SUCCESS |

Log local: `m7-postgres-25-it.log` (raíz Backend; no versionado si está en `.gitignore`).
