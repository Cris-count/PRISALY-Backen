# DC-RAT-01 — revisión de compatibilidad M4 y garantías D-06

Fecha de revisión: 8 de noviembre de 2026. Responsable: Nicolás / M4.
Fuente: acta DC-RAT-01 suministrada por Dirección Central en esta conversación.
**Estado del acta: pendiente de aprobación formal. Este dictamen no es una ratificación.**

Rama `feature/m4-alineado-20261104`, HEAD
`05a3bce4c24ccd069d23be3f44b4fd449d86269c`.
Se preserva el parche local previo de HistorialM4M5IT, todavía sin commit ni
validación PostgreSQL de sus dos casos nuevos. Esta revisión no lo modifica.

## Dictamen focalizado

Compatible en las fronteras revisadas, sujeto a aprobación aplicable y criterios
de aceptación. No requiere cambiar contratos M4 ni el mecanismo D-06 para
registrar la propuesta. No certifica implementación completa de DD-02/03/04.

| Decisión propuesta | Compatibilidad y condición de implementación |
|---|---|
| DD-02: cuentas estudiantiles individuales administradas por PRISALY | `ProgramacionAutenticadaPort` obtiene identidad exclusivamente de `StudentAccessPort`. `IdentidadEstudianteDesdeM1` consume `studentId`, no `userId`, y rechaza respuestas incompletas. M1 deberá acreditar estado activo, permisos y vínculo inequívoco Usuario–Estudiante; M4 no crea autenticación alternativa. El acta no elige por sí sola OTP, cookies ni una migración. |
| DD-02: recursos propios y separación institucional | D-06 filtra convocatoria por estudiante y revalida el contexto tras bloquear. La titularidad estudiantil no concede permiso institucional M6. `M6EvaluacionAuthorizationAdapter` mantiene denegación ante ausencia de política ratificada. El lector integrado actual exige autorización institucional y protección M5: una futura operación institucional con identidad propia requiere contrato aprobado, sin suplantar estudiante para satisfacer ese lector. |
| DD-03: configuración docente y suma de respuestas correctas | M4 entrega contexto de programación; no interpreta rúbricas ni calcula puntos. `EstrategiaEvaluacion` sigue siendo un contrato sin algoritmo por defecto. M3/M6 deben validar asociaciones Caso–Pregunta–Rúbrica y especificar fórmula, escala, redondeo e incompletas frente a las fuentes académicas antes de habilitar cálculo. |
| DD-04: último intento autorizado y evaluado | El historial consultado por M4 bloquea edición por existencia de participación, no por calificación oficial. No se debe filtrar ese historial por resultado evaluado ni sustituirlo por el intento con mayor nota. M5/M6 deben acreditar que un intento posterior no evaluado conserva la nota anterior. |

La definición operativa del orden de «último», incluidos empates y relación entre
orden de intentos y orden de evaluación, deberá quedar explícita en los criterios
aprobados de M5/M6; este dictamen no introduce una regla de desempate.

## Garantías D-06 preservadas

`ProgramacionInicioCoordinadoPort` y su adaptador JDBC exigen una transacción
de escritura existente, aislamiento READ COMMITTED y orden de locks Sesion →
Convocatoria. M5 continúa Participacion → Intento dentro de la transacción de
escritura existente. La consulta informativa no reemplaza la reserva ni autoriza
el primer inicio. `ElegibilidadNoRatificada` continúa en el ensamblado M5.

DC-RAT-01 somete DD-02, DD-03 y DD-04 a aprobación; no ratifica D-06 ni D07,
ni define elegibilidad temporal o repetición. Las pruebas con fixtures no
constituyen aprobación académica ni autorización productiva.

## Registro de aprobación requerido

Antes de habilitar una función dependiente, vincular un registro verificable con:
fecha de aprobación, participantes y competencias, referencia DC-RAT-01,
decisiones y alcance aprobados, observaciones/condiciones, requisito V27 exacto,
PR revisado, commit implementado y pruebas de aceptación del mismo candidato.
La fecha de esta revisión no sustituye la fecha de aprobación; los responsables
de implementación nombrados en el acta no se registran como firmantes.

No se encontró una fuente oficial V27 identificable en este workspace en el
seguimiento previo. El vínculo exacto continúa pendiente; no se inventan códigos.

## Entrega y evidencia

Archivo nuevo de esta revisión: este documento. Sin cambios productivos,
migraciones, proveedores, contratos, flags, commit, push, PR ni merge.
HEAD final sigue siendo el SHA de partida y no incluye este documento local.

Validación focalizada: `ProgramacionAutenticadaTest`, `M4CoordinacionInicioTest`
y `M6EvaluacionAuthorizationAdapterTest`, mediante Maven `-pl app -am test`,
con selección explícita y agente Mockito local. **BUILD SUCCESS: 17 aprobadas,
0 fallos, 0 errores y 0 omitidas** (4 + 4 + 9, respectivamente: reserva D-06,
programación autenticada y autorización M6). `git diff --check` aprobado.
Log local ignorado: `dc-rat-01-m4.log`.
No se ejecuta PostgreSQL ni se amplía la evidencia previa a estas decisiones.
