# Estabilización M4 — ejecución verificada

01/11/2026, 22:51:33 America/Bogota. **BUILD SUCCESS**, duración4:50. Rama feature/m4-estabilizacion; base19cf52b más este incremento.

Comando: mvnw.cmd -B verify -Pdocker-it con m4.baseline.migrations apuntando a app/src/main/resources/db/migration.

| Módulo | Surefire | Failsafe | Total |
|---|---:|---:|---:|
| M1 | 8 | 6 | 14 |
| M3 | 100 | 60 | 160 |
| M4 | 114 | 64 | 178 |
| M5 | 181 | 38 | 219 |
| M6 | 25 | 19 | 44 |
| M7 | 23 | 25 | 48 |
| app | 1 | 20 | 21 |
| **Total** | **452** | **232** | **684** |

**Cero fallos, errores y omitidas.** M2 no aporta suites. Surefire incluye una comprobación Docker M4: no todas sus ejecuciones son unitarias puras.

[Conteo](conteo.md), [suites sanitizadas](suites.json), [paquete M4](package-check.txt), [paquete M5](m5-package-check.txt). Solo XML posteriores a estabilizacion-inicio.txt. Total XML684 y suma de resúmenes Maven684, comprobados independientemente.

## Evidencia del incremento

- M4EstabilizacionAdaptersTest:6 casos nuevos, incluido Publicado inactivo, fallos de proveedores, guardia de escritura y F3-03.
- M4HttpErrorsTest:32 casos en total; nuevo503 de edición sin exponer causa SQL por HTTP.
- SesionAcademicaPostgreSqlIT:duraciones30/60/120 frente a caso estimado60; validación FK antes de notificar; fallos SQL reales de Caso/Participacion con rollback y recuperación; concurrencia de seis convocatorias (1 éxito/5 duplicados/1 fila/0 participaciones).
- ProgramacionParticipacionPostgreSqlIT:15 casos temporales nuevos (3 estados×5 fronteras) con Clock.fixed para fixtures, lectura de contexto sin escribir ni autorizar. No son pruebas de un scheduler ni de vencimiento aprobado.
- App:contexto completo, proveedor real único M5, aviso/cola M7 y seguridad HTTP. El fallo real del historial atraviesa la frontera M4 como503 y conserva la causa M5.

29 ejecuciones M4 nuevas sobre la línea base149; M4 total178. No sumar aparte las38 pruebas focalizadas que también forman parte del verify final.

El verify inicial falló por Docker apagado en M1; no se cuenta como ejecución aprobada. Docker se inició y se repitió la regresión completa. Logs locales ignorados: m4/target/estabilizacion-focalizado.log, estabilizacion-verificacion.log (fallo de entorno), estabilizacion-verificacion-final.log. Marcador: estabilizacion-inicio.txt.

Las fixtures de identidad no sustituyen M1 estudiantil. No se crea Participacion desde M4 ni se prueban inicio/repetición productivos sin contrato ratificado. SQL de fallos y Flyway clean de fixtures existentes se usan exclusivamente en PostgreSQL efímero, nunca en una base compartida. V001–V010 intactas.
