# M7 — diagnóstico y propuesta para revisión

> Diagnóstico inicial conservado como antecedente. La ratificación fue confirmada posteriormente por el responsable en este chat. El estado vigente, implementación y evidencia están en [M7-INC-01.md](M7-INC-01.md); las limitaciones de la primera ejecución descritas abajo no representan la verificación posterior.

Fecha: 2026-11-01. Estado: inspección realizada; implementación productiva bloqueada por definición de alcance y semántica de éxito. Este documento no aprueba decisiones ni declara M7 terminado.

## Base verificada

Árbol inicialmente limpio en `develop`. `git fetch origin` completado; HEAD y origin/develop coinciden en `2c96c912923c8e90ab1c74e3fcfd8aeb29265249`. Se creó `feature/m7-comunicaciones`; no había otra rama M7 en el inventario local/remoto. Sin commits, push ni merges.

El reactor Maven contiene M1–M7 y App, Java 21 y Spring Boot 4.1.2. App ya depende de M7. M7 no declara dependencias propias ni contiene código funcional: solamente pom y package-info. No se encontraron AGENTS.md en el espacio inspeccionado.

## Matriz inicial

| Componente | Existe | Implementado | Dependencias | Pendientes |
| --- | --- | --- | --- | --- |
| Dominio M7 | Paquete raíz | No | V27 y casos de uso | Conceptos, reglas y datos autorizados |
| Aplicación M7 | No | No | Semántica de comunicaciones | Casos de uso aprobados |
| Infraestructura M7 | No | No | Canal autorizado | Adapter y configuración |
| Puertos Java | Sí, en M4 | Interfaz y fallback | NotificacionPort | Proveedor M7 |
| Observer | No | No | Evento y efecto aprobados | Publicador y observadores funcionales |
| Adapter | No en M7 | No | Puerto M4 y canal | Implementación sin cambiar M4 |
| Persistencia | Tablas oficiales | Sin JPA M7 | Aviso, EnvioCorreo | Asignación funcional y política |
| API REST M7 | No | No | ARQ-008.3 e identidad M1 | Verificar si corresponde |
| Pruebas M7 | No | No | Alcance autorizado | Unitarias y PostgreSQL |
| Integración M4 | Consumidor real | Falta proveedor | Transacción M4 | Mantener rechazo seguro |

## Evidencia y alcance

No se encontraron V27, catálogo de casos de uso ni ARQ-008.2/008.3 en los archivos locales inspeccionados. No es posible asignar números RF/CU a M7 con evidencia disponible. La solicitud establece Observer y Adapter, pero no determina el canal ni el significado de éxito. La existencia de una tabla no autoriza por sí sola una funcionalidad.

| Funcionalidad | Clasificación | Evidencia / condición |
| --- | --- | --- |
| Solicitar notificación de convocatoria | Exigida por consumidor existente; pendiente M7 | NotificacionPort y convocarEstudiante |
| Observer y Adapter | Exigidos por la orden; pendientes | Implementarlos después de resolver el efecto válido |
| Registro de avisos internos | Soporte estructural; pendiente de decisión funcional | V001, Aviso |
| Registro de correo pendiente | Soporte estructural; pendiente de contrato | V001, EnvioCorreo |
| Envío externo | Dependiente de autorización y proveedor | No hay proveedor M7 ni configuración de canal encontrada |
| Consulta propia y lectura | Pendiente de contrato e identidad | ARQ-008.3 ausente; DD-02 abierto |
| Avisos de resultados | Pendiente de requisitos y DD-12 | No hay contrato M7 encontrado |
| Reportes | Sin respaldo verificable para alcance vigente | No implementar sin V27/CU |
| WhatsApp, SMS, colas u outbox nuevos | Fuera del alcance verificado | No incorporarlos |

M3, M5 y M6 no aportan un contrato Java de comunicación M7 en la búsqueda realizada. El aviso a M6 mencionado en M5 corresponde a evaluación; no demuestra un requisito de comunicación al estudiante.

## Contrato real y transacciones

`com.prisaly.m4.application.port.NotificacionPort` expone `void notificarConvocatoria(UUID sesionId, UUID estudianteId)`. Su Javadoc solicita envío, no devuelve comprobante y no define reintentos ni idempotencia. estudianteId es identidad académica, no idUsuarioDestino.

`SesionAcademicaService` tiene `@Transactional`: valida docente/propiedad y estado, rechaza duplicados, guarda Convocatoria y llama al puerto antes de retornar. Una excepción runtime debe revertir la operación. `M4ProviderAutoConfiguration` aporta un fallback que lanza `ReglaNegocioException`, código `M4-PROVEEDOR-NO-DISPONIBLE`; M4ExceptionHandler lo traduce a 503. Los dobles de notificación están en src/test.

Bloqueo: no se ha acordado si retorno normal significa registro interno, correo pendiente o envío. Un evento publicado no prueba ninguno de esos efectos. Un envío remoto dentro de la transacción podría sobrevivir al rollback; procesarlo después del commit cambia el comportamiento ante fallo. Ninguna opción debe activarse silenciosamente.

## Soporte BD-005.2

V001 contiene las 21 tablas oficiales. Catálogo: Rol, Permiso, Usuario, UsuarioPermiso, Estudiante, Caso, Escenario, Pregunta, OpcionRespuesta, Sesion, Convocatoria, Participacion, Intento, Respuesta, AutorizacionRepeticion, Rubrica, RubricaPregunta, Calificacion, RetroalimentacionGeneral, Aviso y EnvioCorreo.

Aviso requiere destinatario Usuario, tipo, contenido y claveEvento; participación y lectura son opcionales. Tiene unicidad por (idUsuarioDestino, claveEvento). EnvioCorreo requiere destinatario Usuario, correo, asunto, contenido y claveIdempotencia única; convocatoria es opcional, estado inicial Pendiente y contador inicial cero. Incluye último error, fechas de intento/envío y próximo reintento. V002 exige contador no negativo y crea índice de pendientes. No se encontró catálogo cerrado de estados de envío que autorice inventarlo.

Ambas tablas tienen FK a Usuario; Aviso referencia Participacion y EnvioCorreo referencia Convocatoria. Estudiante.idUsuario permite la correspondencia estructural de destinatario, sin reemplazar autenticación. `docs/BD-005.2-REFERENCE.md` confirma el catálogo; para columnas prevalecen las migraciones verificadas. App ya usa ddl-auto=validate. No hacen falta tablas nuevas para las dos alternativas de registro; esto no decide cuál corresponde.

## Propuesta concreta a Arquitectura (no aprobada)

P0: ratificar qué efecto satisface NotificacionPort y qué RF/CU lo respalda. Si se aprueba registro atómico, usar la misma transacción de M4, adaptador de entrada M7 que implemente el puerto existente, publicador síncrono y observador de persistencia M7. El publicador debe garantizar un observador requerido; cero observadores o fallo implica excepción, nunca éxito. Dominio y aplicación no dependen de repositorios internos de otros módulos. M7 puede depender del contrato público M4 sin crear dependencia inversa.

Adapter de salida: persistencia en la tabla aprobada con consultas parametrizadas o JPA separado del dominio. El éxito significa exclusivamente el registro acordado. No declarar enviado/entregado al insertar Pendiente. Definir clave de evento, política de repetición y diferencia entre reintento técnico y nueva convocatoria antes de codificar idempotencia. Validar destinatario real y existencia de convocatoria en la transacción, sin exponer correo en eventos/logs.

Si se exige envío, acordar primero proveedor, contenido, autorización, política de errores y semántica transaccional. La entrega remota y PostgreSQL no son atómicos por defecto. No introducir infraestructura para resolverlo sin revisión.

P1: consulta/lectura solo con REST ratificado y titularidad resuelta por M1. IdentityAccessPort ofrece identidad de personal y permisos; StudentAccessPort deniega mientras DD-02 siga pendiente. No aceptar UUID del frontend como autenticación. P2: resultados requieren contrato y DD-12. P3: reportes/canales adicionales necesitan evidencia RF/CU.

Las cuatro capas se completarían así: dominio con evento y reglas aprobadas; aplicación con coordinación y puertos; infraestructura con adaptadores/persistencia; presentación sin HTTP hasta verificar necesidad. No se crean clases ornamentales ni UML que afirme implementación inexistente.

## PT-DCP-01 — fichas iniciales propuestas

Todos los casos siguientes están **no ejecutados como integración M7**; sus identificadores son locales propuestos, no numeración oficial.

| Caso | Preparación / acción | Resultado requerido |
| --- | --- | --- |
| M4-M7-01 | Docente autorizado, caso publicado, sesión válida; convocar | Convocatoria y efecto de comunicación aprobado en PostgreSQL |
| M4-M7-02 | Sesión no convocable o autorización insuficiente | Sin convocatoria ni efecto M7 |
| M4-M7-03 | Proveedor requerido ausente | Error controlado y sin éxito simulado |
| M4-M7-04 | Fallo del adaptador después de guardar convocatoria | Rollback de ambas escrituras si se aprueba registro atómico |
| M4-M7-05 | Fallo posterior a escritura M7 antes del commit | No queda comunicación de convocatoria revertida |
| M4-M7-06 | Doble petición y concurrencia | Política aprobada; sin duplicados ni pérdida de integridad |
| M4-M7-07 | Usuario ajeno/no autenticado, si se aprueba consulta | Denegación sin datos personales ni calificaciones |
| M4-M7-08 | Publicador sin observador o con observador fallido | Error y rollback; publicación no equivale a registro |

Regresión requerida: M4, M5 y App, con PostgreSQL 16.15 y perfil docker-it. Las pruebas M4 con NotificacionStubAdapter no prueban integración productiva M7. No se envían mensajes reales en pruebas.

## Verificación ejecutada

Java disponible: Temurin 21.0.10. `docker version` encontró cliente 29.8.2 pero falló al conectar con `dockerDesktopLinuxEngine` (pipe inexistente); PostgreSQL 16.15 no se verificó.

Se ejecutó `.\mvnw.cmd -B verify -Pdocker-it`, con salida en `m7-baseline-verification.log` en la raíz (ignorada por Git). El log confirmó una prueba aprobada de DenyingStudentAccessServiceTest, sin fallos/errores/omitidas en esa clase. La ejecución dejó de producir salida en IdentityAccessServiceTest después del aviso de auto-attach de Mockito y se interrumpió mediante Ctrl+C, salida 1. No se atribuye una causa al bloqueo sin más evidencia. La suite completa, las 445 pruebas históricas, M4/M5/App y la integración PostgreSQL **no quedaron verificadas** en esta ejecución. No se añadieron pruebas de una implementación inexistente.

## Publicación

No hay código productivo listo para PR. Preparar cambios funcionales y evidencia después de resolver los documentos y la decisión de éxito. Propuesta inicial de commit documental: `docs(m7): registrar diagnostico y bloqueos de integracion`. No se ejecutó commit ni push; requieren confirmación según la orden del usuario.
