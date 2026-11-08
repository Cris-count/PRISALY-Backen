# DC-CIERRE-BACKEND-02 — dictamen focalizado M4–M5

## Identificación

Responsable: Nicolás / M4. Rama local `feature/m4-alineado-20261104`, HEAD `f1d0c2c03cac5f9091e5ee9063f02c65acdec333`. Fetch realizado para esta revisión: origin/develop avanzó a **f9890a94491b6c0afe49e75b80f372755fa82c05**, merge PR #41. Candidato M5 publicado: `origin/trabajo/m5-rest-a01-publicacion-20261105`, **168d4f7f95dd2abe3845a9710af56df49c0d2886**. La referencia f1d0c2c de la orden ya no es el tip remoto.

Se revisó el diff identificado f1d0c2c..168d4f7, 35 archivos, directamente desde las referencias Git sin aplicarlo al checkout local. Rama M4 permanece dos commits detrás de origin/develop para esta revisión; cambios documentales locales conservados. Sin cambios funcionales M4, commit ni push.

## Dictamen: compatible condicionado

No se encontró incompatibilidad estática en las fronteras D-06 revisadas. La conformidad runtime queda pendiente de compilación/arranque del candidato integrado y PostgreSQL aislado autorizado.

| Control | Resultado de revisión del diff |
|---|---|
| ParticipacionesPort, HistorialParticipacionPort y ProgramacionInicioCoordinadoPort | Sin modificación. Todo el módulo m4, el proveedor ParticipacionesM4DesdeM5, CoordinacionInicioDesdeM4, ConfiguracionParticipacion y HistorialM4M5IT son idénticos entre base y candidato. Comprobado con git diff --quiet, salida 0. |
| Proveedor/reserva únicos | En el árbol integrado hay un implementador real por contrato: ParticipacionesM4DesdeM5, HistorialParticipacionJdbc y ProgramacionInicioCoordinadoJdbcAdapter. Fallbacks M4 protegidos con ConditionalOnMissingBean. El parche no agrega otro proveedor ni mecanismo de reserva; cardinalidad efectiva Spring todavía no acreditada sobre este SHA. |
| Orden transaccional | FachadaParticipacionTransaccional mantiene coordinacionInicio antes del servicio iniciarOReanudar. El adaptador M4 mantiene locks Sesion y después Convocatoria; M5 continúa Participacion e Intento. |
| Aislamiento | La reserva M4 sigue exigiendo transacción de escritura y valida read committed en PostgreSQL. El cambio nuevo REPEATABLE_READ se aplica únicamente a la plantilla de consulta read-only; no sustituye la plantilla de escritura de inicio. Esta conserva el aislamiento por defecto y la guardia M4 rechaza un aislamiento real incompatible. Validar configuración efectiva del entorno en runtime. |
| Rollback | Se mantiene TransactionTemplate sin captura que convierta fallos de reserva/inicio en éxito. La nueva operación guardarRespuesta usa la plantilla de escritura existente. Atomicidad y ausencia de huérfanos requieren repetición runtime. |
| A-01 | ResumenIntento conserva únicamente tiempos persistidos en su igualdad; esta observación del diff no acredita las pruebas comunicadas por M5. |

No se ratifica D-06 ni se habilita DD-02/D07 por este dictamen. La revisión está limitada a compatibilidad M4–M5; no es aprobación general del nuevo REST M5.

## Evidencia nueva y límites

Comprobaciones nuevas: fetch, identificación de base/candidato, inventario de 35 archivos, comparación de contratos/coordinación y revisión estática de transacción/aislamiento/rollback. No se ejecutaron suites nuevas. docker info volvió a fallar: pipe dockerDesktopLinuxEngine ausente. No se atribuyen las 80 pruebas de M5 (que incluyen 23 HistorialM4M5IT) ni las 21 selectivas M4 anteriores al nuevo origin/develop.

Repetir sobre f9890a9 o un candidato posterior explícitamente seleccionado:

- HistorialM4M5IT: proveedor único, participación sin intento, historial retirado y errores SQL con rollback; casos D-06 de carreras inicio/edición, dos inicios, relectura del ganador, reserva ajena, timeout, independencia entre sesiones y fallo entre Participacion/Intento sin huérfanos.
- ParticipacionesPortWiringIT: cardinalidad efectiva y ausencia del puente historialM4M5.
- ParticipacionesM4DesdeM5IT e InicioIntentoIT: contrato de historial e inicio en PostgreSQL.
- Pruebas pertinentes de ProgramacionParticipacionPostgreSqlIT para aislamiento/reserva solo mediante una ejecución autorizada que no ejecute clean/repair; la suite actual contiene clean y no debe lanzarse íntegra bajo la restricción vigente.

No usar PM4-21 como sustituto de estas pruebas: sigue bloqueada hasta historial legítimo M5 por flujo autorizado. No se ejecutaron consultas ni DDL en bases compartidas; V010 continúa pendiente de infraestructura.

## Dependencias, PR y próxima acción

- M1/M3: acreditar compilación, arranque y autorización efectiva sobre la base integrada actual; el correctivo #38 ya está en su ascendencia, sin necesidad de recrearlo.
- Infraestructura: motor Docker/PostgreSQL aislado autorizado; auditoría V010 compartida por separado antes de despliegue compartido.
- M5/Arquitectura: ratificación final D-06 y ejecución conjunta identificada; el PR M5 #41 ya figura integrado en el Git remoto, no sigue siendo un parche local sin publicar.
- PR documental M4 sigue preparado localmente, no publicado. No se propone otro PR funcional M4 ni duplicar #41.
- Próxima acción: acordar f9890a9 como base conjunta (o documentar su sucesor), sincronizar controladamente el checkout cuando corresponda y ejecutar las verificaciones runtime indicadas al disponer del entorno. Mantener bloqueos académicos vigentes.
