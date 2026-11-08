# Cierre backend 48 horas — revisión del 5 de noviembre de 2026

## Dictamen

Cierre condicionado, no aprobación productiva. Revisión de código y documentos locales; no se consultó ni actualizó el remoto. No se modificaron reglas académicas, código, migraciones ni contratos. No se hizo commit ni push.

## Versión y pendientes Git

- Rama: `feature/m4-alineado-20261104`.
- SHA revisado: `c2d5dcf58c0e485ac42dabb863e10854462281c6`.
- Árbol limpio al comenzar. Este informe es una incorporación documental sin commit.
- Frente al upstream local `origin/feature/m4-alineado-20261104`: 0 pendientes de subir, 0 de bajar.
- Frente a la referencia local `origin/develop` (`451a58e`): 1 commit por integrar, 0 por recibir: `c2d5dcf M4 alineado`.
- Estas cifras no acreditan el estado actual del servidor GitHub: no hubo fetch.

## Contrato M1 y compatibilidad

M1 autentica personal con Bearer opaco provisional, expiración y revocación al cerrar sesión. `IdentityAccessPort` expone identidad activa, rol y permisos individuales; los adaptadores de M3/M4 consumen ese puerto. El filtro recarga estado y permisos por solicitud. No se infieren permisos a partir del rol ni de encabezados enviados por el cliente. BD-005.2 mantiene un rol principal en Usuario y asignaciones UsuarioPermiso, sin inventar RolPermiso ni tablas Docente/Administrador.

401 corresponde a identidad ausente o credenciales inválidas; 403 a identidad autenticada sin capacidad o titularidad requerida. La cadena HTTP exige autenticación; las decisiones de negocio permanecen en los servicios. Las consultas repetidas a M1 no son una segunda fuente de permisos, aunque implican lecturas repetidas de persistencia.

M3: creación con `CREAR_CASO` y propietario derivado del usuario autenticado; produce Borrador. Modificar/publicar siguen denegados en el adaptador M1 y no deben habilitarse por equivalencia con CREAR_CASO. No se encontró endpoint REST de publicación. M4 exige `GESTIONAR_SESION`, titularidad y caso Publicado/activo; X-Docente-Id no sustituye la identidad autenticada. El flujo HTTP completo crear → publicar → programar no está acreditado.

**Bloqueo de cableado:** `CaseAuthorizationM1Config.caseAuthorizationFailClosed` y `M3CasoCreationConfig.m3CaseAuthorizationAdapter` publican ambos `CaseAuthorizationPort`. `casoApplicationService(CaseAuthorizationPort, ...)` no especifica selección y tampoco hay Primary. Por inspección, hay riesgo concreto de ambigüedad de inyección en el contexto completo; no se reprodujo el arranque en esta revisión. El test de configuración M3 carga únicamente M3CasoCreationConfig y no acredita la convivencia con el fallback. Proponer un único proveedor real que conserve false para modificar/publicar; verificar cardinalidad y arranque completo antes de integrar. No resolver habilitando permisos nuevos.

## DD-02

El plan v1.1 declara ratificación técnica condicional, sin autorización productiva. No equivale a implementación completa. El gate local de migraciones está documentado como aprobado; integración, M3–M6 compartido y demos siguen pendientes de auditoría, bloqueando V011. App conserva V001–V009; no crear/reutilizar V010 para DD-02 ni alterar migraciones aprobadas.

La configuración actual mantiene `prisaly.m1.student-access.enabled=false`: `/validar` responde 501 para solicitudes válidas. Existe una preparación con desafíos en memoria y consumo mediante reemplazo atómico. `StudentAccessPort` sigue siendo DenyingStudentAccessService. Activar el flag no crea una sesión estudiantil productiva ni completa el contrato M4/M5.

Brechas observadas frente al plan: código OTP en claro en memoria; sin HMAC ni persistencia transaccional cifrada; validate no comprueba vencimiento ni limita intentos; sin cookie/sesión productiva. La emisión de prueba registra el desafío antes de llamar a entrega y no revierte ese registro si entrega falla. M7 usa InMemoryStudentAccessDeliveryAdapter; ENTREGADO_PREP explícitamente no llama SMTP. Estas piezas son preparación, no evidencia DD02-01…14 aprobada ni implementación del rollback DD02-12a.

## Convocatoria y correo

201 en `POST /m4/sesiones/{id}/convocatorias` acredita aceptación/creación de convocatoria y solicitud transaccional de aviso, no envío SMTP. El recorrido académico M4→M7 persiste Aviso y EnvioCorreo Pendiente en la transacción; fallo de encolado y fallo SMTP posterior son distintos. El worker está deshabilitado por defecto. Deben verificarse fila/estado/intentos, ejecución del worker y respuesta del proveedor para afirmar envío. Incluso aceptación SMTP no acredita llegada al buzón. ENTREGADO_PREP del prototipo OTP tampoco acredita envío.

## Endpoints implementados inspeccionados

Prefijo de aplicación: `/api`. Inventario acotado a las fronteras solicitadas, no catálogo exhaustivo de M1–M7.

| Módulo | Método y ruta relativa | Alcance |
|---|---|---|
| M1 | POST `/v1/personal/autenticar` | Login personal provisional |
| M1 | POST `/v1/personal/cerrar-sesion` | Revocación |
| M1 | GET `/v1/personal/me` | Identidad personal |
| M1 | GET `/v1/personal/autorizacion/crear-caso` | Consulta/probe de permiso, no creación |
| M1 | POST `/v1/acceso-estudiante/validar` | Implementada con gate; 501 predeterminado |
| M3 | POST `/v1/casos` | Crear Borrador autorizado; sujeto a resolver cableado |
| M4 | POST/GET `/m4/sesiones` | Crear/listar propias |
| M4 | GET/PUT `/m4/sesiones/{sesionId}` | Consultar/editar |
| M4 | PATCH `/m4/sesiones/{sesionId}/estado` | Transición de estado |
| M4 | POST/GET `/m4/sesiones/{sesionId}/convocatorias` | Convocar/listar |
| M4 | GET `/m4/sesiones/{sesionId}/acceso/{estudianteId}` | Retirada: 410, no acceso estudiantil |

## Pruebas y límites de evidencia

Corrida nueva: `.\mvnw.cmd -B test`, Java 21.0.10. Log local `cierre-backend-test.log` (ignorado por Git).

- Aprobada: DenyingStudentAccessServiceTest, 1 caso, 0 fallos/errores.
- IdentityAccessServiceTest quedó sin resultado después del inicio de Mockito; se interrumpió la corrida sin avance. El dumpstream registra `Corrupted channel` y fallo attach al abrir la pipe Java (5). No atribuir todavía la causa única a ese mensaje.
- No hubo BUILD SUCCESS global. No hay fallo funcional de aserción demostrado; sí una ejecución bloqueada/incompleta. Los módulos restantes y las IT PostgreSQL/SMTP no quedaron acreditados en esta corrida.
- Evidencia histórica, NO repetida sobre este SHA: M1-M7-ESTABILIZACION-RESULTADOS.md declara 12 unitarias y 7 IT app aprobadas el 1 de noviembre, en otra rama.
- ALINEACION-M4-INTEGRACIONES-2026-11-04.md distingue capturas de un JAR anterior y reportes del responsable de evidencia independiente. PM4-19/20/21 no ejecutadas allí; PM4-21 requiere historial legítimo M5. No trasladar esos resultados al SHA actual ni afirmar SMTP por los 201.

## Dependencias y riesgos arquitectónicos

1. M1/M3: eliminar ambigüedad del proveedor sin inventar publicar/modificar; probar arranque real y permisos negativos.
2. Arquitectura: cerrar TD-M1-AUTH-PROVISIONAL. Tokens JVM locales no soportan continuidad tras reinicio ni coordinación entre instancias. M1 no es autenticación final ARQ-008.3.
3. DD-02: auditoría de bases compartidas, autorización de DDL V011, persistencia/seguridad OTP y pruebas obligatorias antes de cualquier activación.
4. M3/M4: confirmar contrato de publicación y validar caso Publicado/activo mediante flujo legítimo; el seed no acredita publicación HTTP.
5. M4/M5: ratificación D-06 y evidencia con historial real; conservar el proveedor único ParticipacionesPort y los rechazos seguros ante indisponibilidad.
6. M7/operación: configuración SMTP, secretos y activación autorizada del worker; comprobar encolado, reintentos y envío por separado.
7. Corregir documentación envejecida: BD-005.2-REFERENCE aún describe V001–V009 como pendientes de restaurar, aunque están en app. Mantener la fuente canónica del esquema y no modificarlo basándose en ese texto antiguo.

Se conservan cuatro capas (presentation/application/domain/infrastructure), módulos M1–M7, composición en app, puertos públicos y BD-005.2. Permanecen los patrones existentes, incluidos Builder de M3, State de M4, fachada de M5 y Observer de M7; no se introdujo refactorización ni regla académica adicional. Esto no sustituye una auditoría exhaustiva de todas las dependencias entre capas.

## PR propuesto y secuencia de las 48 horas

PR documental inmediato propuesto: `docs(backend): cierre 48h y bloqueos verificables M1–M3–M4–M7`, base develop, rama actual. Incluir este informe y revisar explícitamente el commit c2d5dcf ya presente frente a develop. No se creó PR remoto.

PR técnico posterior propuesto: `fix(app): unificar autorización M3 conservando permisos ratificados`. Rama sugerida `fix/m1-m3-autorizacion-unica`; un único CaseAuthorizationPort, modificación/publicación denegadas, prueba de cardinalidad con ambas configuraciones y contexto completo, creación autorizada, 401/403 y ausencia de escrituras al rechazar. Sin DD-02 productivo ni reglas nuevas.

Orden: primeras 12 horas resolver ejecución de pruebas y cableado; 12–24 horas correr seguridad e IT PostgreSQL sobre el SHA candidato; 24–36 horas completar matriz HTTP del mismo JAR y separar evidencia de cola/SMTP; 36–48 horas revisión de contratos, bloqueos y PR hacia develop. Son prioridades propuestas, no garantía de resolución de dependencias externas. No integrar como cierre productivo hasta superar los gates anteriores. Nunca push directo a develop.
