# Preparación y pruebas de integración PRISALY — 02/11/2026

## Estado y alcance

Repositorio: C:/Users/Heiner/Desktop/PRISALY/PRISALY-Backen.
Rama candidata: integracion/m4-m5-validacion-20261102.
Base: 3984485c2271936119efcbddbbf42c24249431fd, igual a develop y origin/develop tras git fetch origin exitoso. El remoto sí fue accesible en esta ejecución. El árbol inicial solo contenía resultado.txt sin seguimiento; índice y archivos versionados limpios. No se necesitó avanzar develop.

No hay commit candidato: las pruebas corresponden a esa base más el diff de preparación. No se ejecutaron commit, merge a develop ni push. El índice permanece vacío. resultado.txt continúa sin seguimiento; fuentes sources/ intactas.

## Plan aplicado y procedencia selectiva

1. Inspección del estado real, plan anterior, conversación Ingrid 4, puertos, documentación y configuración Maven; comprobación de Docker y Java.
2. Fetch de origin y creación de la rama desde develop sincronizado.
3. Comparación de los tres archivos del proveedor/pruebas contra el padre de 6eb33ef: sin diferencias. Recuperación de esos tres archivos exclusivamente desde 6eb33ef1d0387d79caa0b0d3d8b2d0af32f146e8, sin aplicar el commit completo.
4. Conciliación documental sin sustituir el protocolo D-06 existente ni ratificar decisiones académicas.
5. verify y verify -Pdocker-it con m4.baseline.migrations absoluto; revisión de XML generados en cada ejecución.

El proveedor ParticipacionesM4DesdeM5 exige una transacción de escritura antes de consultar. Adapta fallos de consulta a ReglaNegocioException con código M4-PROVEEDOR-NO-DISPONIBLE, mensaje público genérico y causa interna conservada; no devuelve false ante indisponibilidad. SesionRepositoryAdapter ya protegía la ruta M4; el refuerzo sitúa esas garantías también en el proveedor. La precondición transaccional directa lanza IllegalStateException; la ruta M4 la traduce a indisponibilidad. El handler M4 ya mapea el código a HTTP 503.

Las pruebas selectivas añaden rechazo sin transacción/solo lectura y verifican traducción de errores con causa. La suite PostgreSQL usa adaptadores reales. Se conserva la regresión D-06 y el ensamblado actual de app; no se restaura el puente duplicado eliminado por 19cf52b. Los tres archivos Java resultantes coinciden con 6eb33ef (git diff --quiet devuelve 0).

El cierre 98f595e y el inicio/reanudación c99b8a2 son ancestros de develop, integrados mediante PR #17 y PR #21. D-06 entró en PR #28 (513c444 y 6c75000). No se fusionó ninguna rama antigua ni se recuperaron los documentos con SHA normalizados de a12f69d.

## Archivos preparados

| Archivo | Cambio y procedencia |
|---|---|
| m5/src/main/java/com/prisaly/m5/infrastructure/programacion/ParticipacionesM4DesdeM5.java | Recuperación selectiva de 6eb33ef: precondición transaccional y traducción segura |
| m5/src/test/java/com/prisaly/m5/infrastructure/programacion/ParticipacionesM4DesdeM5Test.java | Recuperación selectiva de 6eb33ef: contexto transaccional y errores con causa |
| m5/src/test/java/com/prisaly/m5/infrastructure/persistencia/ParticipacionesM4DesdeM5IT.java | Recuperación selectiva de 6eb33ef: errores y rechazo de solo lectura con PostgreSQL |
| app/src/test/java/com/prisaly/config/HistorialM4M5IT.java | Adaptación local de dos aserciones al contrato seleccionado, tras fallos reproducidos; conserva los 23 casos D-06/historial |
| m4/review/ESTADO_ACTUAL.md | Estado real de integración PR #28 e inicio/reanudación; diferencia entre evidencia histórica y nueva |
| m4/review/revision-m4-m5/INFORME.md | Corrige PR #28 presentado como borrador; conserva ratificación final pendiente |
| m5/docs/M5-INFORME-CIERRE.md | Actualización inicial; conserva el informe original como histórico |
| m5/docs/M5-DOCUMENTO-TECNICO.md | Contrato reforzado, 7 pruebas unitarias/8 IT y coordinación ya implementada |
| docs/INTEGRACION-M4-M5-20261102.md | Este informe, nuevo y sin seguimiento, para revisión |

No se modificó ningún pom: la propiedad se suministra expresamente en ambos comandos y evita introducir rutas personales en configuración compartida. No se modificaron V001–V009, V010 ni docs/BD-005.2-REFERENCE.md. Los contenedores de pruebas son efímeros; no se migraron bases compartidas.

## Comandos de pruebas

Ejecutados desde el repositorio, con Java Zulu 21.0.2 y Docker Desktop 4.60.1 / Engine 29.2.0, contenedores PostgreSQL 16.15 y smoke M4 postgres:16-alpine:

```powershell
.\mvnw.cmd -B verify '-Dm4.baseline.migrations=C:/Users/Heiner/Desktop/PRISALY/PRISALY-Backen/app/src/main/resources/db/migration'
.\mvnw.cmd -B verify -Pdocker-it '-Dm4.baseline.migrations=C:/Users/Heiner/Desktop/PRISALY/PRISALY-Backen/app/src/main/resources/db/migration'
```

Los resultados de cada ejecución se cuentan por separado; no se suman dos regresiones. Los XML se filtran por marcador de inicio para excluir reportes antiguos. Surefire incluye smoke Docker: sus cifras no se presentan como unitarias puras. M2 no tiene suites.



## Primera regresión Docker y corrección

La primera ejecución completa Docker terminó con BUILD FAILURE (código 1), 841 casos, 2 fallos, 0 errores y 0 omisiones; 839 aprobados. Falló exclusivamente HistorialM4M5IT (21/23 aprobados):

- consultaFueraDeTransaccionSeRechazaPorMandatory esperaba IllegalTransactionStateException del repositorio M5. Ahora el proveedor rechaza antes con IllegalStateException. Se renombró para expresar la precondición y se ajustó el tipo esperado.
- errorSqlNoSeTraduceASinHistorialYRollbackRecupera esperaba PersistenciaIntentoException como causa inmediata del adaptador M4. Ahora existe una capa ReglaNegocioException del proveedor. Se verifica el código del proveedor, su causa PersistenciaIntentoException y la ausencia del nombre SQL interno en el mensaje. Se conservan HTTP 503, rollback, comprobación del título original y edición posterior exitosa.

No se eliminaron escenarios, ampliaron tipos a RuntimeException ni omitieron pruebas. No se reemplazó HistorialM4M5IT por la versión antigua de 6eb33ef; se conservaron sus 23 casos, incluidos los D-06. Esta adaptación es nueva de la preparación actual, no una recuperación literal de aquel commit. La evidencia del primer fallo permanece en docker-verify.log y docker-suites.json. Se repite el reactor Docker completo tras la corrección.

## Resultados finales

| Módulo | verify Surefire | Docker Surefire | Docker Failsafe | Total Docker | Fallos / errores / omitidas Docker |
|---|---:|---:|---:|---:|---|
| m1 | 18 | 18 | 6 | 24 | 0 / 0 / 0 |
| m2 | 0 | 0 | 0 | 0 | 0 / 0 / 0 |
| m3 | 125 | 125 | 77 | 202 | 0 / 0 / 0 |
| m4 | 118 | 118 | 64 | 182 | 0 / 0 / 0 |
| m5 | 231 | 231 | 54 | 285 | 0 / 0 / 0 |
| m6 | 40 | 40 | 19 | 59 | 0 / 0 / 0 |
| m7 | 24 | 24 | 25 | 49 | 0 / 0 / 0 |
| app | 1 | 1 | 39 | 40 | 0 / 0 / 0 |
| Total | 557 | 557 | 284 | 841 | 0 / 0 / 0 |

verify: código 0. Docker final: código 0. Los resúmenes y duración constan al final de cada log.

### Suites prioritarias de la ejecución Docker final

| Suite | Casos | Fallos / errores / omitidas |
|---|---:|---|
| com.prisaly.m4.infrastructure.Bd0052BaselineIT | 4 | 0 / 0 / 0 |
| com.prisaly.m4.infrastructure.M4M1ContratosPostgreSqlIT | 6 | 0 / 0 / 0 |
| com.prisaly.m4.infrastructure.ProgramacionParticipacionPostgreSqlIT | 22 | 0 / 0 / 0 |
| com.prisaly.m5.infrastructure.programacion.ParticipacionesM4DesdeM5Test | 7 | 0 / 0 / 0 |
| com.prisaly.m5.infrastructure.persistencia.InicioIntentoIT | 15 | 0 / 0 / 0 |
| com.prisaly.m5.infrastructure.persistencia.ParticipacionesM4DesdeM5IT | 8 | 0 / 0 / 0 |
| com.prisaly.config.HistorialM4M5IT | 23 | 0 / 0 / 0 |
| com.prisaly.config.ParticipacionesPortWiringIT | 1 | 0 / 0 / 0 |

Las tres suites condicionadas de M4 ejecutaron 4 + 6 + 22 = 32 casos sin omisiones. La cifra anterior de 18 omitidas era de otra ejecución/alcance; no constituye el total de estas suites en el árbol actual. Las otras 32 IT de M4 son SesionAcademicaPostgreSqlIT; no se duplican en el recuento. Las 839 pruebas históricas del candidato M4 no se suman a esta ejecución: el refuerzo añade un caso Surefire y un caso Failsafe, dando 841 en el reactor actual.

## Limitaciones y decisiones pendientes

- Maven emitió advertencias por POM de caché local con bytes NUL/no analizables: jna-platform 5.17.0, commons-compress 1.27.1, httpclient5 5.6.5 (parent 14), maven-resolver-util 1.4.1, file-management 3.2.0 y plexus-xml 3.0.1. No se reparó ni borró la caché global. La ejecución acredita este entorno, no una descarga limpia reproducible; revisar caché/CI antes de publicación definitiva.
- El cierre final D-06 de Arquitectura sigue pendiente, incluyendo rama/SHA de la alternativa M5 y las ocho pruebas comunicadas externamente. No se incorporó una segunda implementación ni se dio esa cobertura por inspeccionada.
- Primer inicio productivo bloqueado por elegibilidad y límite temporal no ratificados; acceso estudiantil, REST M5, repetición y demás decisiones académicas no quedan habilitados por estas pruebas. Los dobles explícitos de las pruebas no son políticas productivas.
- No se certifican frontend, entrega SMTP real, escritores SQL externos ni estado de migraciones en bases compartidas. No se alteró BD-005.2 ni las migraciones históricas.
- origin/develop fue verificado al inicio; el remoto puede avanzar posteriormente. Antes de publicar deberá revisarse de nuevo. develop local no fue modificado.

## Evidencia y revisión

Directorio externo de evidencia: C:/Users/Heiner/.codex/.chatgpt-projects/g-p-6ad3ec30d3c48191a8a14ae6248818e9/output/integracion-20261102.

- COMANDOS.md: registro de comandos y secuencia.
- verify.log, verify-exit.txt, verify-start.txt, verify-suites.json: ejecución sin perfil Docker.
- docker-verify.log, docker-exit.txt, docker-start.txt, docker-suites.json: primera regresión y dos fallos.
- docker-final.log, docker-final-exit.txt, docker-final-start.txt, docker-final-suites.json: repetición completa tras ajustar aserciones.
- collect-current.ps1: recolección de XML filtrados por inicio; update-docs.ps1: parte de las sustituciones documentales.
- cambios.patch, git-status.txt, refs.txt, migraciones-diff.txt y comprobaciones finales: diff y preservación.

Los logs y resultado.txt no se preparan para commit. Este informe es un archivo nuevo, sin seguimiento, incluido entre los cambios que debe revisar el usuario. Commit, merge a develop y push siguen pendientes de revisión y autorización. La fase de preparación no equivale a integración final en develop.
