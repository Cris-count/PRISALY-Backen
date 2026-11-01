# Resultado final ejecutado

01/11/2026, 17:39:23 America/Bogota. **BUILD SUCCESS**, duración3:13. Base develop8b6dc33, cambios locales de historial M4–M5.

Comando: mvnw.cmd -B verify -Pdocker-it, con m4.baseline.migrations apuntando al directorio oficial app/src/main/resources/db/migration.

| Módulo | Surefire | Failsafe | Total |
|---|---:|---:|---:|
| M1 | 8 | 6 | 14 |
| M3 | 89 | 38 | 127 |
| M4 | 107 | 42 | 149 |
| M5 | 172 | 30 | 202 |
| M7 | 10 | 12 | 22 |
| app | 1 | 19 | 20 |
| **Total** | **387** | **147** | **534** |

**Cero fallos, errores u omitidas.** M2/M6 sin suites. Surefire incluye una comprobación Docker M4; no todas sus pruebas son unitarias puras.

[Conteo por runner](conteo.md), [suites sanitizadas](suites.json), [paquete M4](package-check.txt), [paquete M5](m5-package-check.txt). El colector filtra por el inicio de esta ejecución:534 en XML nuevos y534 en resúmenes Maven, comprobados independientemente. Incluye M7.

## Diez casos nuevos en app

HistorialM4M5IT usa el contexto completo y los adaptadores reales M4/M5/M7. Verifica un único ParticipacionesPort, edición sin convocatoria y sin historial, bloqueo con participación sin intento o con intento existente, retiro que conserva protección, aislamiento del historial de otra sesión, rechazo de reasignación, error SQL real con503/rollback/recuperación, proveedor ausente y rechazo sin transacción de escritura. La identidad docente es exclusivamente una fixture src/test.

El fallo SQL se provoca renombrando Participacion dentro de una transacción en PostgreSQL efímero; el rollback restaura el esquema. No se ejecuta sobre bases compartidas. El caso verifica503 mediante el manejador público M4 y la ausencia de detalles SQL en el mensaje; no añade endpoints.

La primera ejecución falló por omitir Rol.descripcion en la fixture. Se corrigió la prueba, se aprobaron10 casos focalizados y se repitió toda la regresión con éxito. Se conserva el log fallido local para diagnóstico; no se cuenta como ejecución aprobada.

Logs locales ignorados: m4/target/historial-verificacion.log (primer intento), historial-focalizado.log y historial-verificacion-final.log. Marcador: historial-inicio.txt.

Las pruebas no habilitan inicio/reanudación ni sustituyen autenticación estudiantil. El protocolo de bloqueo del futuro inicio sigue pendiente. Sin cambios de migraciones, commit ni push.
