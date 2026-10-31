# M4: conciliación y preparación de integración

Fecha: 2026-10-30. Rama: feature/m4-programacion-convocatorias.
Base develop verificada por fetch: 288f9aafd188341371bf02e605305eb535c45ce4 (igual al HEAD inicial).
Estado: candidato técnico; NO listo para merge. No se autoriza ni ejecuta merge.

## Migración V010

Las copias originales de app/src/main/resources/db/migration y m4/src/main/resources/db/migration eran idénticas (SHA256 DB5C291A0836B81F32F8D41D4A740C7B1580E0B5CA3DD01A0E13D8CD29FDBDB4).
app depende de m4 y habilita Flyway sin cambiar su ubicación predeterminada db/migration: ambas copias eran recursos candidatos en ejecución. Las pruebas aisladas de m4 no incluían app y por ello no detectaban el riesgo.
Se elimina la copia no rastreada de app. Único origen fuente: m4/src/main/resources/db/migration/V010__m4_sesiones_convocatorias.sql. Se conserva el SQL íntegro. La prueba de catálogo verifica un único recurso en el classpath de pruebas. Paquete app verificado: cero V010 directas en BOOT-INF/classes y exactamente una en BOOT-INF/lib/m4-0.0.1-SNAPSHOT.jar (package-check.txt); package -DskipTests terminó con BUILD SUCCESS.
No se modificaron V001–V009; no existen en esta línea base.

## Conciliación BD-005.2 (pendiente)

La especificación aprobada de 21 tablas no está disponible en el checkout. No se afirma equivalencia ni aprobación del Integrante 1. V010 declara explícitamente nombres propuestos.

| Tabla | Estructura implementada | Pendiente de BD-005.2 |
| --- | --- | --- |
| sesiones_academicas | id UUID PK; caso_id/docente_id UUID NOT NULL; titulo varchar(200); descripcion varchar(1000); fecha_inicio/fecha_fin TIMESTAMP; estado varchar(20); creado_en/actualizado_en TIMESTAMP; version integer | Nombre, columnas, nulabilidad, zonas horarias, estados, referencias a M1/M3 y numeración V010 |
| convocatorias | id UUID PK; sesion_id UUID FK sesiones_academicas; estudiante_id UUID; convocado_en TIMESTAMP; confirmada boolean | Nombre, columnas, FK a estudiantes, vínculo con participación/intentos M5 |

Restricciones actuales: fecha_fin > fecha_inicio; cinco estados; índice único uq_sesion_caso_docente_fechas (caso, docente, inicio, fin); UNIQUE convocatoria (sesion, estudiante); FK convocatoria a sesión.
No hay FK de caso/docente/estudiante hacia otros módulos, tabla de participaciones ni restricción SQL que compruebe publicación del caso. RF-09 se comprueba en el servicio contra un stub. No inventar las otras 19 tablas.
Coordinación requerida: Integrante 1 debe aportar BD-005.2 y V001–V009, confirmar origen de migraciones y comprobar si V010 ya fue aplicada en alguna BD compartida. No cambiar una migración aplicada sin acordar una migración posterior.

## Matriz de integración

| Contrato | Unitarias | PostgreSQL IT | Integración real pendiente |
| --- | --- | --- | --- |
| M1 autorización | Mockito IdentidadPort | IdentidadStubAdapter: permiso por defecto true | Proveedor M1 real, identidad autenticada, permisos denegados |
| M3 publicación RF-09 | Mockito CasoAcademicoPort | CasoAcademicoStubAdapter: publicado por defecto true, Borrador configurado | Consulta persistida, inexistente/Borrador/Publicado, atomicidad con programación |
| M3 F3-03 | Verificación Mockito | Set en memoria de solicitudes | Bloqueo real, rechazo de edición y comportamiento ante rollback/concurrencia |
| M4 persistencia | Repositorios Mockito | Spring Data JPA + Flyway + PostgreSQL 16 Testcontainers | Infraestructura Docker oficial y esquema compartido |
| M5 elegibilidad | Servicio M4 con mocks | Servicio M4 y convocatorias reales | Consumidor M5, creación de participaciones e intentos reales |
| M5 participaciones vinculadas | Respuesta configurada en mock | SesionRepositoryAdapter devuelve false fijo | Consulta real; actualmente no protege sesiones con participaciones existentes |
| M7 notificación | Mockito | NotificacionStubAdapter, solo log | Entrega real y coordinación transaccional |

En el checkout M1/M3/M5 solo contienen package-info. Las referencias remotas de M3 y M5 tienen avances de dominio, pero no fueron integradas ni usadas por estas pruebas. M3 ofrece isAvailableForScheduling(Caso) y lockAfterFirstSessionScheduled(Caso), no el puerto UUID de M4. Acordar resolución de identidad, repositorio, persistencia y transacción con sus responsables.
Los stubs están en src/main y son @Component: se cargarían también en app. Bloqueo de integración: reemplazarlos o acordar perfiles explícitos antes de desplegar.

## Validación reproducible

Ejecutar desde raíz: .\mvnw.cmd -pl m4 -am verify -Pdocker-it
Surefire incluye 48 pruebas de dominio/servicio y 1 smoke Docker (49); Failsafe incluye 17 pruebas PostgreSQL. El smoke no es una prueba unitaria.
Evidencias: regression-summary.txt, surefire-reports/, failsafe-reports/ y execution.log en este directorio. Los XML excluyen properties del entorno para evitar adjuntar secretos; conservan resultados y salida de pruebas.
La suite registra conexión JDBC sin contraseña, validate/info de Flyway, origen V010, resultados SQL RF-09, rollback y concurrencia, y logs del servidor PostgreSQL efímero.
Rollback reforzado: saveAndFlush antes del fallo, seguido de SELECT con conexión independiente. Concurrencia reforzada: solo acepta M4-SESION-DUPLICADA o SQLSTATE 23505 del índice uq_sesion_caso_docente_fechas; cualquier otra excepción falla la prueba; exige 1 éxito, 9 duplicados esperados y una fila SQL.
Primer intento con clean falló por bloqueo Windows de m1/target; verify permite recompilar y ejecutar sin eliminar ese directorio. No se interpreta como fallo de M4.
La ejecución Testcontainers es local, no certifica la infraestructura oficial del Integrante 1.

## Git y revisión

Conservar la implementación original y sus pruebas. Commits separados para implementación y para conciliación/evidencias. PR en borrador hacia develop; cuerpo preparado en PR_BODY.md. Solicitar revisiones del Integrante 1 y responsables M3/M5 cuando se conozcan sus usuarios GitHub; no inferirlos de autores Git. No mezclar las ramas de otros integrantes.

Bloqueos: BD-005.2 y V001–V009; aprobación de esquema por Integrante 1; adaptadores reales M1/M3/M5 (y M7); prueba real F3-03; participaciones/intentos; validación en Docker oficial; revisores identificados. Las 66 pruebas verdes no cierran estos bloqueos.
Los reportes adjuntos excluyen un XML Surefire antiguo de SesionAcademicaPostgreSqlIT, que corresponde exclusivamente a Failsafe en la ejecución actual. Se normalizaron espacios finales de las evidencias.
