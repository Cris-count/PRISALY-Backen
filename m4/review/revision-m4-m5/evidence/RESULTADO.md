# Resultado final ejecutado

01/11/2026, 19:21:15 America/Bogota. **BUILD SUCCESS**, duración4:53. Base develop ee77a57, conciliación local del proveedor único M5.

Comando: mvnw.cmd -B verify -Pdocker-it con m4.baseline.migrations apuntando a app/src/main/resources/db/migration.

| Módulo | Surefire | Failsafe | Total |
|---|---:|---:|---:|
| M1 | 8 | 6 | 14 |
| M3 | 100 | 60 | 160 |
| M4 | 107 | 42 | 149 |
| M5 | 182 | 39 | 221 |
| M6 | 25 | 19 | 44 |
| M7 | 23 | 25 | 48 |
| app | 1 | 20 | 21 |
| **Total** | **446** | **211** | **657** |

**Cero fallos, errores y omitidas.** M2 sin suites. Surefire incluye una comprobación Docker M4: no todas sus pruebas son unitarias puras.

[Conteo por runner](conteo.md), [suites sanitizadas](suites.json), [paquete M4](package-check.txt), [paquete M5](m5-package-check.txt). XML nuevos657 y resúmenes Maven657, comprobados independientemente. El colector excluye informes anteriores al marcador de inicio.

## Proveedor único verificado

ParticipacionesM4DesdeM5Test:7 casos. ParticipacionesM4DesdeM5IT:8 casos PostgreSQL. HistorialM4M5IT:10 casos en el contexto completo. Además pasan NotificacionPortWiringIT y las9 pruebas de seguridad HTTP de app.

Se comprueba un único ParticipacionesPort de clase ParticipacionesM4DesdeM5, sin historialM4M5 ni respaldo cuando existe el proveedor real. Sin M5, la autoconfiguración M4 conserva un respaldo que rechaza con503. Se elimina HistorialM4M5Configuration de app y se retira su .class obsoleto de target.

La protección cubre edición con/sin convocatorias o historial, participación sin intento, intento existente, retiro con historial, aislamiento entre sesiones, reasignación y fallo SQL real con503/rollback/recuperación. Se rechaza el uso sin transacción de escritura o con solo lectura.

La identidad docente del IT es una fixture src/test. El fallo SQL y el rollback usan PostgreSQL efímero; no se modifican bases compartidas. No se certifican inicio/reanudación, identidad estudiantil productiva ni la carrera del futuro inicio contra la edición de sesiones.

Log crudo local ignorado: m4/target/proveedor-unico-verificacion.log. Marcador: proveedor-unico-inicio.txt. Sin cambios de migraciones, commit ni push.
