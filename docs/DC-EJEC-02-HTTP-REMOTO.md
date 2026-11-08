# DC-EJEC-02 — aceptación HTTP ejecutada en GitHub Actions

8 de noviembre de 2026. Nicolás / QA backend.

**Resultado verificado: 32 escenarios HTTP ejecutados y aprobados, 0 fallidos,
0 bloqueados, exit 0.** Se inspeccionaron las 32 solicitudes/respuestas saneadas
del artefacto, no solo el estado verde del job.

- Ejecución: https://github.com/Cris-count/PRISALY-Backen/actions/runs/42497720486
- Job: https://github.com/Cris-count/PRISALY-Backen/actions/runs/42497720486/job/128783565241
- PR de runner temporal: https://github.com/Cris-count/PRISALY-Backen/pull/59
- Código productivo probado: **bc8e378bd6dd50a3b1484b06c268932ba5607dd3**.
- Commit del ejecutor/workflow: **77452a3afa1f91409cc6fc88f5f5106870610be5**.
  El SHA mostrado en el encabezado del workflow no sustituye al checkout productivo fijado.
- Campaña: 16:01:25–16:02:17 UTC, **11:01:25–11:02:17 Colombia**.
- PostgreSQL real: **16.15 (Debian 16.15-1.pgdg13+2)**, contenedor desechable.
- JAR construido en runner, SHA-256:
  `c9e9b98ad9a7dec510fd4aac14ea5945b9be218b51e4319c1db0df3862d67b56`.

## Matriz de resultados

| Operaciones | Escenarios | Aprobados | Fallidos | Bloqueados |
|---|---|---:|---:|---:|
| Health real | HTTP-01 | 1 | 0 | 0 |
| Login, identidad personal y credenciales inválidas/inactivas | HTTP-02–10 | 9 | 0 | 0 |
| Borrador M3 y rechazos de acceso/payload | HTTP-11–14 | 4 | 0 | 0 |
| Sesiones: permisos, referencias, propiedad, ausencia y F3-03 | HTTP-15–23 | 9 | 0 | 0 |
| Convocatoria, listado y duplicado | HTTP-24–26 | 3 | 0 | 0 |
| DD-02 y acceso a intentos/respuestas M5 denegado | HTTP-27–30 | 4 | 0 | 0 |
| Logout y Bearer revocado | HTTP-31–32 | 2 | 0 | 0 |
| **Total HTTP** | **32** | **32** | **0** | **0** |

Detalle por escenario: [matrix.csv](evidencias/DC-EJEC-02-HTTP-REMOTO/matrix.csv).
Cada JSON HTTP-01…32 conserva método, ruta, petición saneada, código/cuerpo de
respuesta, esperado y aserciones. Todos los códigos y errores de dominio esperados
se contrastaron con esos archivos descargados. Las cuatro pruebas auxiliares del
ejecutor también pasaron; **no se suman** a los 32 escenarios HTTP.

## Ejecución y evidencia

Workflow nuevo `.github/workflows/backend-http-once.yml`, limitado a la rama
`test/backend-http-aceptacion` y a sus paths de campaña; checkout separado de
ejecutor y candidato, Node 24 y Java 21. Invocación real:

```text
node qa/backend-http/run.mjs --expected-sha bc8e378bd6dd50a3b1484b06c268932ba5607dd3
```

Se conservan sin cambios los 32 escenarios y expectativas de b0dc59b. Solo se
ajustó el ejecutor para extraer el ID Docker de stdout, separándolo de stderr
(descarga de imagen). No se modificaron backend-ci.yml, módulos productivos,
endpoints, permisos, proveedores, migraciones ni decisiones académicas.

El runner creó su PostgreSQL 16.15 con nombre/etiqueta propios, puerto loopback
aleatorio y tmpfs; APP real se inició con el JAR construido y V001–V009. Los seeds
existentes Bruno M1/M7 y fixture QA se aplicaron exclusivamente a esa base. La
limpieza acreditó terminación del proceso Java y eliminación del contenedor propio.

Artefacto descargado e inspeccionado: **backend-http-42497720486-1**, ID
**13624485662**, 50 346 bytes; retención configurada de 14 días. Copia persistente
en `docs/evidencias/DC-EJEC-02-HTTP-REMOTO/`: resumen, matriz, 32 respuestas,
logs saneados, cuatro pruebas auxiliares y SHAs de producto/ejecutor.
`manifest.json` identifica hashes y el ZIP original. Las copias de logs se
guardan como .txt y normalizan espacios finales; el ZIP local conserva el original.

Los reportes locales anteriores permanecen como historia independiente: Docker
local ausente y 32 bloqueados. No se reemplazan ni se suman a esta ejecución.
El CI estándar que corrió en paralelo no forma parte de estos resultados.

## Alcance funcional y límites

Se acreditaron por HTTP login real y su revocación, protección/permiso de recursos,
creación persistida de borrador con propietario del servidor, programación de un
Caso Publicado/activo fixture, rechazo de Borrador/inactivo/inexistente, marca
académica persistida, convocatoria/listado/duplicado y denegación DD-02/M5.

DD-02 responde 501 y M5 rechaza Bearer personal con 403 según las expectativas;
**son denegaciones verificadas**, no habilitación estudiantil. No se probaron
publicación docente M3, inicio/finalización M5 ni evaluación/calificación M6 como
operaciones HTTP disponibles. No se ratifican DD-02/03/04 ni D07. El seed Publicado
no acredita un recorrido de publicación académica. No se certifica entrega SMTP.

No se reprodujeron defectos productivos en estos 32 escenarios. No se afirma
haber resuelto los errores históricos de otras suites APP ni ejecutado el
recorrido académico completo. Las 114 pruebas PostgreSQL históricas permanecen
exclusivamente asociadas a d8eb62d y no se repitieron.

RF/CU continúan sin asignar en la matriz porque no se dispone aquí del catálogo
canónico verificable. Estos resultados se limitan al SHA productivo identificado;
cambios posteriores requieren evaluación focalizada de su impacto.
