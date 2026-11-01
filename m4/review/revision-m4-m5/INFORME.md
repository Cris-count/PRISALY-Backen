# Revisión conjunta y conexión de historial M4–M5

01/11/2026. Base local develop, 8b6dc33. Nuevo puente de historial y documentación locales, sin commit ni push.

## Resultado

Regresión completa con PostgreSQL Docker: **534 pruebas aprobadas**, cero fallos, errores u omitidas. M1=14, M3=127, M4=149, M5=202, M7=22, app=20. XML de la ejecución y resúmenes Maven coinciden. Ver [resultado y evidencia](evidence/RESULTADO.md).

M2 y M6 compilan su estructura, sin funcionalidad ni pruebas para certificar. M7 ya dispone de registro de avisos, Observer, adaptador M4 y22 pruebas. M5 ya incluye identidad desde M1, titularidad desde M4, protección de operaciones e historial persistido. DD-02 sigue abierta: esos adaptadores no habilitan una identidad estudiantil permisiva.

## Cambios de esta fase

- App ensambla ParticipacionesPort de M4 con HistorialParticipacionPort de M5 en HistorialM4M5Configuration. M4 resuelve sus convocatorias con su puerto de repositorio; M5 consulta sus participaciones. No se añade dependencia Maven M4→M5 ni se accede a repositorios internos M5.
- La consulta exige la transacción de escritura que mantiene el bloqueo de sesión adquirido por el servicio M4. Una participación protege también sus intentos por FK obligatoria.
- Se incluyen convocatorias retiradas. Con historial se rechaza edición/reasignación con409 M4-SESION-CON-PARTICIPACIONES. Sin historial se aplican las demás validaciones existentes de M4.
- Proveedor ausente o fallo de consulta:503 M4-PROVEEDOR-NO-DISPONIBLE, mensaje genérico y rollback. Los respaldos seguros M4 permanecen para ensamblados sin el puente.
- Diez casos nuevos en app prueban el proveedor único, sesiones sin convocatoria, convocatoria sin historial, participación sin intento, intento existente, retiro con historial, historial de otra sesión, reasignación, error SQL/rollback/recuperación, proveedor ausente y uso sin transacción de escritura. Algunas pruebas agrupan más de una comprobación.
- Se actualizan los documentos vigentes y el colector incluye M7. No se crean nuevas carpetas de informes.

La primera ejecución falló en la fixture nueva: faltaba Rol.descripcion, obligatoria. Se corrigió únicamente la fixture. Los10 casos pasaron de forma focalizada y luego pasó la regresión completa. No se relajó el esquema ni ninguna protección.

## Integración real y simulada

Reales: PostgreSQL16.15, FlywayV001–V009, repositorios M4, consulta pública de historial M5, servicio transaccional M4, registro M7 y contexto de aplicación. La identidad docente de HistorialM4M5IT es un doble explícito en src/test. Las suites de seguridad M1 se ejecutan por separado. No se demuestra un recorrido estudiantil completo ni Angular.

## Límites y siguientes pasos

- El futuro inicio de participaciones debe bloquear la misma fila de sesión antes de insertar. M5 aún no implementa ese inicio; no se certifica una carrera contra esa operación futura. Sigue pendiente ratificar el orden global de bloqueos M3–M4–M5.
- DD-02, elegibilidad, inicio/reanudación, vencimiento y repetición requieren decisiones y recorrido real de estudiante. Titularidad no equivale a elegibilidad.
- Confirmar V010 en bases compartidas; no modificarla ni reparar historiales unilateralmente. Se conserva fuera de las migraciones automáticas.
- Ratificar contratos, respuestas de acceso y alcance final de comunicaciones M7. M2/M6 siguen pendientes.

## Reproducir

Desde raíz backend, ejecutar mvnw.cmd verify -Pdocker-it con m4.baseline.migrations apuntando a app/src/main/resources/db/migration. Capturar la hora inicial y pasarla a collect-evidence.ps1 para excluir XML antiguos. check-package.ps1 verifica el empaquetado M4.

Histórico:445 pruebas tras organizar M4;524 al incorporar los avances recientes de M5/M7;534 tras este puente. La antigua cifra207/157 incluía17 pruebas de un XML obsoleto: esa ejecución fue190/140. La evidencia vigente no reutiliza esos informes antiguos.

No se modificaron implementaciones de M1/M2/M3/M5/M6/M7, POM ni migraciones. App solo recibe el ensamblado entre contratos y sus pruebas. Los logs crudos permanecen en target, ignorados por Git.
