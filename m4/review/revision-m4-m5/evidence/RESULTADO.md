# Regresión de cierre M4 / D-06 — 02/11/2026

Base develop: b3d77d89e324c1f61ea0364417a37bd2c7c354cc más incremento de feature/m4-cierre-d06. El SHA final se publica con el commit y PR; el código Java no cambia entre la regresión final y el commit.

**BUILD SUCCESS: 839 pruebas, 0 fallos, 0 errores, 0 omitidas.** Finalizó 13:05:14 de Colombia, duración 5:13.

| Módulo | Surefire | PostgreSQL Failsafe | Total |
|---|---:|---:|---:|
| M1 | 18 | 6 | 24 |
| M2 | 0 | 0 | 0 |
| M3 | 125 | 77 | 202 |
| M4 | 118 | 64 | 182 |
| M5 | 230 | 53 | 283 |
| M6 | 40 | 19 | 59 |
| M7 | 24 | 25 | 49 |
| app | 1 | 39 | 40 |
| Total | 556 | 283 | 839 |

Comando: mvnw.cmd -B verify -Pdocker-it con m4.baseline.migrations apuntando a app/src/main/resources/db/migration. Java 21, Docker Desktop 29.8.2, PostgreSQL 16.15; smoke M4 con postgres:16-alpine. Solo contenedores efímeros. Surefire no es sinónimo de pruebas unitarias puras.

Las tres suites condicionadas de M4 se ejecutaron (4 + 6 + 22), junto con 32 IT adicionales. D-06 añade 4 pruebas M4, 5 M5 y 13 app, todas incluidas en el total. Las 23 IT de HistorialM4M5IT incluyen los 10 casos anteriores.

Los XML posteriores al marcador de inicio coinciden con los resúmenes Maven: 839. Los resultados antiguos y repeticiones focalizadas no se suman. La primera prueba de app del candidato falló por ambigüedad StudentAccessDeliveryPort; se corrigió con Qualifier explícito en app y se repitió la regresión completa, que pasó. Ese intento fallido se conserva en target/cierre-d06-carreras.log y no se oculta ni se mezcla con el resultado final.

Detalle de transacciones, carreras, integración y limitaciones en la sección 10 de ../INFORME.md. suites.json solo conserva nombres y resultados, sin propiedades/credenciales. Logs/XML originales permanecen en target (no versionados).

No se habilita identidad estudiantil productiva, elegibilidad, vencimiento, repetición ni SMTP. Las pruebas de inicio usan políticas e identidad solo de prueba. El primer inicio productivo sigue denegado. No se modifican migraciones históricas ni se ejecutan operaciones contra bases compartidas.
