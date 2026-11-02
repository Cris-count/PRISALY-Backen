# Conciliación del proveedor de historial M4–M5

01/11/2026. Base develop ee77a57. Cambios locales, sin commit ni push.

## Comparación y decisión

| Aspecto | Proveedor anterior de app | ParticipacionesM4DesdeM5 |
|---|---|---|
| Consulta mediante puertos M4/M5 | Sí | Sí |
| Validación explícita de convocatorias incompletas | No | Sí |
| Adaptador propio del proveedor M5 | No | Sí |
| Guardia de transacción de escritura | Sí | Añadida en esta conciliación |
| Error técnico como503, sin SQL público | Sí | Añadido en esta conciliación |

Se conserva ParticipacionesM4DesdeM5 y se elimina el archivo HistorialM4M5Configuration de app. No se usa Primary ni Qualifier para ocultar el duplicado. App ensambla los módulos mediante su escaneo habitual y descubre un único ParticipacionesPort productivo de M5.

El adaptador conservado mantiene su consulta vía ConvocatoriaRepository y HistorialParticipacionPort, sin lectura directa de tablas M5 desde M4 ni ciclo Maven. Se agregan la guardia de transacción de escritura y la adaptación de fallos a M4-PROVEEDOR-NO-DISPONIBLE (503), con causa interna y mensaje público genérico. Las convocatorias incompletas tampoco se interpretan como historial vacío.

El respaldo de M4 conserva ConditionalOnMissingBean: se comprueba que no existe cuando está M5 y que rechaza con503 cuando falta el proveedor. Sin historial se aplican las demás validaciones M4. Con participaciones o intentos, incluso con convocatoria retirada, edición/reasignación se rechazan con409.

## Verificación

Regresión completa PostgreSQL Docker: **657 pruebas aprobadas**, cero fallos, errores u omitidas. M1=14, M3=160, M4=149, M5=221, M6=44, M7=48, app=21. Ver [evidencia](evidence/RESULTADO.md).

App verifica proveedor único de clase ParticipacionesM4DesdeM5, ausencia del bean eliminado y del respaldo, edición con/sin historial, reasignación, retiro, otra sesión, fallo SQL/503/rollback/recuperación y rechazo fuera de transacción de escritura. La identidad docente del IT sigue siendo fixture explícita src/test; no habilita acceso estudiantil.

Se adaptan las pruebas unitarias y PostgreSQL de M5 al contrato de error503 y se añade rechazo de solo lectura. El contexto transaccional simulado solo existe en la prueba unitaria; las IT utilizan transacciones y PostgreSQL reales. La prueba de ausencia usa la autoconfiguración productiva M4 sin un proveedor M5.

Se retiró únicamente el .class obsoleto del proveedor eliminado en target antes de empaquetar. No se cambiaron POM, migraciones ni implementaciones M1/M2/M3/M6/M7.

## Límites

DD-02, inicio/reanudación, elegibilidad, vencimiento y repetición siguen pendientes. El futuro inicio de participaciones debe tomar el mismo bloqueo de sesión; no se declara resuelta esa carrera. V010 compartida y el orden global de bloqueos requieren ratificación.

M2 continúa sin lógica funcional; M6 tiene lectura y contratos, sin algoritmo académico productivo. Los límites operativos SMTP y el patrón Observer M7 no se modifican en esta fase. Las pruebas aprobadas no demuestran un recorrido estudiantil completo.

Histórico:445 tras organizar M4;524 antes del primer puente;534 tras ese puente. La cifra antigua207/157 incluía17 casos de un XML obsoleto y correspondía a190/140. La evidencia vigente usa exclusivamente XML posteriores al inicio de la ejecución actual.
