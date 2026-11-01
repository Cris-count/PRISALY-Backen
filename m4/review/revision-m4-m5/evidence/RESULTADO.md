# Resultado final ejecutado

31/10/2026, 19:19:34 America/Bogota. **BUILD SUCCESS**, duración4:09. Comando: mvnw.cmd verify -Pdocker-it, con m4.baseline.migrations apuntando al directorio oficial de app.

| Módulo | Surefire | Failsafe | Total |
|---|---:|---:|---:|
| M1 | 8 | 6 | 14 |
| M3 | 89 | 38 | 127 |
| M4 | 107 | 42 | 149 |
| M5 | 126 | 19 | 145 |
| app | 1 | 9 | 10 |
| **Total** | **331** | **114** | **445** |

**Cero fallos, errores y omitidas.** M2/M6/M7 compilan su estructura básica, pero no aportan funcionalidad ni suites. Surefire incluye una comprobación Docker M4; no todas sus ejecuciones son unitarias puras.

Evidencia: [conteo por runner](conteo.md), [suites y casos sin propiedades de entorno](suites.json), [paquete M4](package-check.txt) y [paquete M5](m5-package-check.txt). El colector solo incluyó archivos posteriores al marcador de inicio de esta ejecución; suma XML445 y suma de resúmenes Maven445, verificadas independientemente. No se cuenta el XML obsoleto Surefire de17 casos del30/10.

## Reorganización de M4 verificada

Se conservan los puertos públicos, rutas, DTO, estados y migraciones. SesionController atiende sesiones y ConvocatoriaController atiende convocatorias. La carga de sesiones a modificar centraliza permisos, propietario y bloqueo de fila. Los datos de creación se validan antes de consultar o bloquear proveedores.

Nueve ejecuciones nuevas aprobadas: identidad docente nula y mutación sin docente(2); duración/rango inválidos sin acceso a proveedores(3); validación400 y proveedor ausente503 de convocatorias por HTTP(2); rutas de convocatorias con persistencia201/consulta200/propietario ajeno403 y rechazo de programación en transacción de solo lectura(2). Este último caso comprueba ausencia de sesión y de marca F3-03.

El primer intento de esta fase falló en una aserción del tipo de excepción: Spring traduce IllegalStateException de un repositorio a InvalidDataAccessApiUsageException. Se ajustó la prueba para verificar la excepción traducida, su causa y el mensaje. La repetición completa pasó. No se relajó la protección transaccional.

## Integración M4–M5 y ensamblado

La corrección503 de M5 sigue aprobada: la excepción pública M4 se traduce sin filtrar SQL. Los19 casos PostgreSQL M5 incluyen sus16 originales más3 de integración: cadena persistida con consulta M4 y filtrado de titular, retiro persistido e indisponibilidad SQL real con rollback y recuperación.

Los JAR M4/M5 incluidos en app coinciden con los compilados locales. Ambos controladores M4 están presentes, sin stubs ni pruebas, sin V010 automática, con una copia histórica V010 idéntica al fuente y sin clases de estados retirados. La validación JPA y la guardia de21 tablas permanecen aprobadas.

Las pruebas de modificación de esquema y rollback usan exclusivamente PostgreSQL efímero. No se aplican a una base compartida ni habilitan inicio/reanudación productivos. Los datos e identidades fixture no sustituyen autenticación estudiantil.

La secuencia de referencia fue431 pruebas al incorporar M5,436 después de corregir503 y445 tras reorganizar M4. Los fallos de entorno anteriores no se cuentan como pruebas aprobadas. El log completo de esta ejecución está en m4/target/organizacion-verificacion.log, fuera de la documentación activa.

Rama local develop, HEAD feec0fb. Reorganización M4 y cambios M5 locales, sin commit, push ni cambios del índice. Permanecen los bloqueos académicos y de proveedores descritos en el informe.
