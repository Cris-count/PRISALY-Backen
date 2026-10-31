# M4 — Estado al preparar el commit «M4 listo para integracion»

Fecha: 31 de octubre de 2026. El título del commit fue solicitado por el usuario; no constituye aprobación de merge, compatibilidad BD-005.2 ni certificación de integración completa.

El commit reúne el trabajo local M4 pendiente de las fases anteriores y la corrección HTTP: aislamiento de dobles de prueba, proveedores ausentes que fallan de forma segura, consulta pública interna para M5, protección provisional de historial, empaquetado seguro y evidencias. No modifica M1 ni M5 ni cambia V001–V010. El cambio app/pom.xml evita que el empaquetado conserve un JAR M4 antiguo.

## Errores HTTP corregidos

- UUID de ruta/query/cabecera inválido, cabecera requerida ausente, JSON ilegible/vacío, fecha o enum no convertible: HTTP 400, M4-SOLICITUD-INVALIDA.
- Validación de campos: HTTP 400, M4-VALIDACION, conservando mensajes de validación.
- Tipo de contenido no soportado: HTTP 415. Los estados/cabeceras estándar del framework se conservan, incluyendo Allow para HTTP 405. Si no se seleccionó controlador, Spring gestiona el rechazo antes del advice específico de M4; no se instala un advice global que altere otros módulos.
- Duplicados conocidos de sesión/convocatoria: HTTP 409, únicamente cuando Hibernate informa SQLSTATE 23505 y una restricción reconocida. Se soporta también el nombre de unicidad de convocatoria BD-005.2.
- Conflictos optimistas, pesimistas y timeout de bloqueo JPA: HTTP 409, M4-CONFLICTO-CONCURRENCIA.
- Integridad desconocida y fallos inesperados: HTTP 500 genérico sin SQL, stacktrace o nombres de tablas en el cuerpo. No se presentan falsamente como duplicación.
- Se mantienen 403/404/409/503 de reglas de negocio y proveedores no disponibles.

## Validación

Se añadieron 29 ejecuciones MockMvc de errores y una prueba HTTP con duplicación PostgreSQL real, comprobando además que el intento fallido de actualización no cambió el título persistido. Los tests HTTP aislados no certifican el filtro de autenticación/seguridad de app.

Ver http-2026-10-31/evidence/regression-summary.txt para el resultado final. Los XML adjuntos excluyen propiedades del entorno. Los informes continuidad, contrato-m4-m5 y escaneo-2026-10-31 conservan la evidencia histórica de esas fases; sus referencias a cambios sin publicar describen el estado de entonces.

Resultado final: BUILD SUCCESS, 123 pruebas aprobadas (92 Surefire + 31 Failsafe), cero fallos, errores u omitidas. El incremento respecto a las 93 anteriores es de 29 pruebas HTTP aisladas y una prueba HTTP/PostgreSQL real. No se reejecutaron ni se incluyen las pruebas del módulo M5.

## Bloqueos aún abiertos

1. Escrituras M4 sobre tablas propuestas frente a consultas M5 sobre "Sesion"/"Convocatoria" oficiales. Resolver BD-005.2 y el historial de aplicación de V010 con el Integrante 1.
2. Reasignación de caso sin solicitud F3-03 al destino; acordar y probar el mecanismo atómico aprobado.
3. Vincular principal autenticado con docente/estudiante y autorizar recursos; X-Docente-Id no es prueba de identidad. Resolver además su incompatibilidad CORS.
4. Evitar uso autorizante de verificarAccesoSesion legado, que aún permite sesión abierta antes de su fecha prevista.
5. Acordar concurrencia y versión del resultado; el objeto devuelto por el repositorio puede contener versión anterior al flush, y el esquema oficial no tiene esa columna.
6. Proveedores reales M1/M3/M5/M7 y transacciones compartidas. El puerto nuevo de consulta M5 está apagado por defecto y no autoriza inicio/reanudación.
7. Reglas académicas de ventana, reloj, vencimiento, reanudación y repetición; pruebas de carreras entre módulos y validación en infraestructura oficial.

No se realiza push, merge ni modificación del PR en esta fase. El mensaje de commit no elimina estos bloqueos.
