# DC-M4-POSTGRES-02 — PostgreSQL remoto aprobado

7 de noviembre de 2026. Nicolás / apoyo técnico M4; coordinación comunicada a Cristian / CI-APP.

**Resultado: 114 pruebas PostgreSQL ejecutadas y aprobadas, 0 fallos, 0 errores, 0 omisiones. Maven exit 0. No se reprodujeron defectos productivos M4 en la selección.**

- Ejecución: https://github.com/Cris-count/PRISALY-Backen/actions/runs/42407696966
- Job: https://github.com/Cris-count/PRISALY-Backen/actions/runs/42407696966/job/128508862568
- **SHA del código probado: d8eb62d0e4873ea28f426ed3d59392a5cd1ae95c.**
- Commit del ejecutor: 43b74582438c7719b4a6314dbf8c1318132a1cb0, rama temporal ci/m4-postgres-02. El SHA que muestra el encabezado del workflow identifica el ejecutor, no sustituye al checkout candidato fijado expresamente.
- PR borrador de campaña: https://github.com/Cris-count/PRISALY-Backen/pull/51 — sin merge. No modifica CI estándar ni código productivo; no propone hacer permanente esta campaña.
- Runner Linux X64, ubuntu24, imagen 20261101.360.1, Java Temurin 21, Docker operativo y PostgreSQL 16.15 mediante Testcontainers sin reutilización ni conexión a bases compartidas.
- Paso PostgreSQL: 19:45:07–19:46:40 UTC (14:45:07–14:46:40 Colombia), primer intento. Job y conservación de artefactos completados satisfactoriamente.

## Resultados por suite

| Suite | Ejecutadas/aprobadas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| Bd0052BaselineIT, solo métodos canónicos | 3 | 0 | 0 | 0 |
| M4M1ContratosPostgreSqlIT | 6 | 0 | 0 | 0 |
| ProgramacionParticipacionPostgreSqlIT | 22 | 0 | 0 | 0 |
| SesionAcademicaPostgreSqlIT | 32 | 0 | 0 | 0 |
| ParticipacionesM4DesdeM5IT | 8 | 0 | 0 | 0 |
| InicioIntentoIT | 15 | 0 | 0 | 0 |
| HistorialM4M5IT | 23 | 0 | 0 | 0 |
| ParticipacionesPortWiringIT | 1 | 0 | 0 | 0 |
| NotificacionPortWiringIT | 1 | 0 | 0 | 0 |
| ConsultaIntentoEvaluacionM5M6IT | 3 | 0 | 0 | 0 |
| **Total PostgreSQL** | **114** | **0** | **0** | **0** |

El ejecutor exigió estos conteos exactos, un XML por suite, el número de testcase esperado, cero omisiones y los tres métodos baseline exactos. La prueba diagnosticoV010CreaDosTablasParalelasYEludeTriggerOficial no fue seleccionada ni ejecutada. Está excluida deliberadamente, no contada como una omisión JUnit.

Las antiguas 18 omisiones se desglosan en 4+6+8 registros deshabilitados; con parámetros habilitados son 32 casos. Esta campaña ejecutó los 31 canónicos correspondientes y excluyó el único diagnóstico V010, además de ampliar las demás integraciones. No se suman esas cifras históricas a los 114 nuevos.

## Migraciones e integridad

V001–V009 aplicadas y verificadas sobre contenedores efímeros. migrations.log conserva, entre otras, confirmaciones Flyway `Successfully applied 9 migrations ... now at version v009`. El manifiesto SHA-256 identifica los nueve archivos SQL. Se comparó su contenido con el checkout local: coincide al normalizar CRLF de Windows a LF del runner. No se editó ninguna migración ni se ejecutó V010 en el recorrido canónico.

Limpieza limitada al interior de PostgreSQLContainer desechables conforme a la orden; no se usó compose, volumen prisaly_pg_data, Flyway repair ni una base compartida.

HistorialM4M5IT 23/23 cubrió carreras de inicio/edición, dos inicios, rollback y fallo entre Participacion/Intento sin huérfanos, recuperación tras timeout y protección de historial. Persistencia M4 verificó RF-09, F3-03, referencias, FK antes de notificación, duplicados, locks y rollback. Wiring app acreditó proveedor de historial único con el adaptador esperado; el consumidor del PR #48 pasó sus tres casos PostgreSQL. Los límites de fixtures/identidad y dobles académicos de cada suite se conservan: no son habilitación de políticas productivas.

## Evidencia conservada

Artefacto **m4-postgres-42407696966-1**, ID 13588371407, 88 814 bytes. Publicado en el run con retención de 7 días. Contiene maven.log, command.json, manifest.json, summary.json, migrations.log, docker-info.txt, postgres-image.json y XML Failsafe (propiedades JVM eliminadas), con conservación incluso ante fallo.

Descargado localmente en `m4/target/DC-M4-POSTGRES-02/remote-artifact.zip` y extraído en su carpeta artifact. Copias persistentes del resumen, manifiesto, comando, migraciones e información del runner en `docs/evidencias/DC-M4-POSTGRES-02/`. El resumen descargado fue verificado contra 114/0/0/0 y problems vacío. No se atribuye el CI estándar paralelo ni pruebas unitarias a este total PostgreSQL.

La campaña temporal está en .github/workflows/m4-postgres-once.yml y .github/scripts/m4_postgres_campaign.py en el PR #51. Se dispara únicamente desde la rama de campaña y sus paths; no altera backend-ci.yml ni requisitos de rama. Evaluar conservación/costos antes de cualquier merge; no hay merge automático.

## Criterio de cierre

El bloqueo de ejecución PostgreSQL M4 queda resuelto para el SHA identificado. Dirección Central dispone de evidencia para cerrar esta validación técnica. No se necesita un PR productivo correctivo M4 derivado de esta corrida.

No acredita PM4-21 por Postman con historial legítimo ni un flujo estudiantil productivo, no prueba llegada de correo al buzón, no audita V010 en bases compartidas y no ratifica DD-02, DD-03, DD-04, D07, D-06 ni otras políticas académicas. Esas restricciones permanecen intactas.

## Registro adicional — campaña comunicada por Ingrid (DC-MACRO-04)

Ingrid comunica una ejecución independiente sobre **d8eb62d0e4873ea28f426ed3d59392a5cd1ae95c**: BUILD SUCCESS, **15 suites, 156/156 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas**, PostgreSQL 16.15/Testcontainers, migraciones V001–V009, tres métodos baseline canónicos y diagnóstico V010 excluido. Informa checkout aislado sin cambios productivos, staging, commit, push, merge ni uso de bases compartidas.

Procedencia comunicada: `C:\Users\Heiner\Desktop\PRISALY\DC-MACRO-04\`, con informe, log Maven, 15 XML, registro del bloqueo inicial Docker y manifiesto SHA-256. **Evidencia reportada por Ingrid, no inspeccionada desde este equipo**; la ruta corresponde al equipo de Heiner y no acredita acceso local de Nicolás a sus archivos.

Se registra como resultado independiente de la corrida GitHub Actions anterior. **156 es el total de su campaña, no un conteo verificado aquí de 156 casos PostgreSQL.** No sumar 156 y 114 ni tratarlos como cobertura distinta acumulable. El desglose unitarias/PostgreSQL de esa ejecución queda sujeto a sus XML; el conteo remoto directamente verificado en este informe continúa siendo 114 PostgreSQL.

Ingrid reporta integridad persistente Convocatoria–Estudiante–Sesion–Caso y lectura del PR #48 en transacción independiente read-only, con pruebas D-06 favorables. **No ejecutó concurrencia del lector frente a la reserva D-06**: esa interacción no queda certificada por esta comunicación. Tampoco se extienden resultados a SHA posteriores. DD-02, D07 y PM4-21 siguen bloqueados.
