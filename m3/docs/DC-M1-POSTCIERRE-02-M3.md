# Conciliación institucional M3: estados, autorización y evidencia

Base: `cf57327ea4e7d6fde22c716db5b4ea5a34da3546`, integrada por fast-forward
en la rama existente. Este incremento no modifica APP/M1, M4 ni migraciones.

## Estado de la composición

APP registra `M3CaseAuthorizationAdapter` como proveedor de `CasoCurrentUserPort`
y `CaseAuthorizationPort`, mediante `IdentityAccessPort` de M1. Solo creación
comprueba el permiso existente CREAR_CASO. `canModifyCase` y `canPublishCase`
devuelven false. No se encontró proveedor productivo de
`AutorizacionConsultaCasoPort` ni de `CasoPatchConcurrencyContract`.

Por ello GET institucional sigue cerrado: identidad acreditada no equivale a
permiso de lectura. Sin política, incluso propietario o UUID inexistente reciben
denegación antes de consultar el caso. PATCH debe seguir denegado en todos los
estados: la denegación ocurre antes del repositorio, del transporte de huella y
de cualquier actualización. No se incorporan proveedores positivos productivos.

## Matriz de trazabilidad

| Estado/condición | Definición y transición existente | Operación técnica existente | Autorización institucional hoy | Evidencia / pendiente |
| --- | --- | --- | --- | --- |
| Sin caso → BORRADOR | Builder y createDraft crean BORRADOR | POST inserta el subconjunto admitido; commit antes de 201 | CREAR_CASO por M1/APP, con identidad acreditada | CasoBuilder, CasoApplicationService.createDraft, M3CaseAuthorizationAdapter; no se asigna CU no verificado |
| BORRADOR | CasoEstado; publicación condicionada a validaciones | GET interno; PATCH parcial con huella, propietario y sin bloqueo | GET sin política; PATCH denegado | CasoValidation, servicios protegidos, DC-M3-REST-GET-PATCH y contratos M3; visibilidad y estados editables pendientes |
| BORRADOR → PUBLICADO | CasoPublication.publish y validaciones actuales | Transición de dominio y soporte transaccional; no publicación REST | canPublishCase=false; DD-03 pendiente | M3-INC-13A/13B: rúbrica persistida sin equivalencia legacy aprobada; INCOMPLETE_RUBRIC se conserva |
| PUBLICADO | CasoEstado; republicar se rechaza | Consulta interna; comandos existentes sujetos a guards y huella | GET sin política; PATCH denegado | CasoPublication.ALREADY_PUBLISHED; existir un comando no autoriza su uso institucional |
| PUBLICADO y activo=true | Predicado de disponibilidad RF-09 | Consulta del contrato CasoAcademicoPort para programación | No concede permiso GET/PATCH docente | M3-INC-10D, matriz de cumplimiento y protocolo M4 existente |
| Bloqueo académico (no es un estado nuevo) | Fecha/flag tras primera programación, F3-03 | Rechazo de edición evaluable y conservación de fecha/huella | PATCH sigue denegado incluso sin bloqueo | ContenidoEvaluableLock, pruebas PostgreSQL de programación/concurrencia; no se cambia M4 |
| ARCHIVADO | Enum; sin transición/restauración implementada autorizada | Operaciones rechazadas por ARCHIVED_CASE_OPERATION_PENDING | Sin autorización de edición/reactivación; GET requiere visibilidad explícita | CasoValidation.assertNotArchived; matriz de cumplimiento, fila Archivado |
| activo=false (no es un estado nuevo) | Metadato independiente del estado | Se conserva; reemplazo no puede alterarlo | No se infiere visibilidad ni permiso | CasoReplacementCommands y AutorizacionConsultaCasoPort.verificarVisibilidad |

Fuentes documentales: `m3/docs/entrega/MATRIZ-CUMPLIMIENTO-M3.md`,
`M3-INC-10D.md`, `M3-INC-13A.md`, `M3-INC-13B.md`,
`DC-M3-REST-GET-PATCH.md` y `M3-DC-EJEC-03-CONTRATOS.md`.
Las menciones antiguas a ausencia de controladores no describen el código actual:
los endpoints existen, pero su autorización institucional sigue pendiente.

El catálogo integral V27, los CU históricos asignados y una máquina formal de
estados ratificada no están disponibles en esas fuentes; la limitación consta
expresamente en M3-INC-10D y la matriz de cumplimiento. El enum y las transiciones
de código no sustituyen un requisito ni ratifican permisos. Se requiere a Dirección
Central la fuente/versionado y el mapeo de operaciones por estado, sin inventarlos.

## Defecto técnico corregido

GET/PATCH convertían cualquier RuntimeException de identidad en 401. Dos pruebas
MVC reprodujeron 500→401 y FORBIDDEN/403→401. Se elimina esa reclasificación:
null/ausencia conserva CasoAuthenticationException/401; la denegación explícita
conserva 403; el fallo técnico llega al manejador HTTP y se sanitiza como 500.
No se consulta ni escribe el repositorio cuando falla la identidad.

## Separación de evidencias

- A: pruebas M3 con puertos controlados verifican propietario/ajeno, inexistencia,
  ausencia de identidad, fallo técnico, proyección de cuatro campos, snapshot/ETag,
  huella obsoleta, rollback y concurrencia. No acreditan permisos institucionales.
- B: `ComposicionInstitucionalM1IT` usa APP/M1 reales, seguridad HTTP y PostgreSQL:
  ausencia de proveedores ratificados, GET/PATCH denegados y caso persistido sin
  cambios. La ausencia de identidad es 401. El servicio deniega PATCH antes de
  acceder al repositorio; se conserva canModifyCase=false.
- Híbrida: `ComposicionM3LecturaPreparadaIT` usa identidad y persistencia reales,
  pero añade política de lectura y transporte ETag SOLO de prueba. Sus GET 200,
  ajeno 403 e inexistente 404 son preparación, no GET institucional habilitado.
  PATCH usa el proveedor productivo denegador y verifica que el caso no cambia.
- Sigue pendiente inyectar un fallo real de resolución M1/APP para acreditar el
  500 institucional de extremo a extremo. Las nuevas pruebas M3 de fallo del
  puerto son evidencia A; no se presentan como esa prueba B.

Cristian debe conectar, tras ratificación, `verificarAcceso(UUID,UUID)` y
`verificarVisibilidad(UUID,UUID,CasoEstado,boolean)`, así como los permisos y
estados editables de canModifyCase. También requiere ratificación del transporte
ETag/If-Match y CORS. No debe usar CREAR_CASO para lectura/modificación.

Nicolás no es un proveedor de permiso docente: la dependencia concreta con M4 es
el protocolo `estaPublicado(UUID)` / `solicitarBloqueoAcademico(UUID)`, su bloqueo
de fila y el trigger de programación. La prueba técnica del protocolo se conserva;
la certificación conjunta sobre PostgreSQL consolidado y privilegios/triggers
reales debe coordinarse con él y Cristian. No se presupone esa certificación.

## Ejecución sobre esta base

```powershell
.\mvnw.cmd -B -o -pl app -am -Pdocker-it '-Dtest=CasoGestionControllerTest,CasoEtagControllerTest,CasoDocenteConsultaServiceTest,CasoPersistentUpdateServiceTest,CasoMetadataTest,CasoPublicationContractTest,CasoApplicationServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=ComposicionInstitucionalM1IT,ComposicionM3LecturaPreparadaIT,CasoPersistenceAdapterIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
```

Resultados: 126 unitarias/MVC M3 y 47 integraciones M3 PostgreSQL aprobadas;
5 pruebas B de ComposicionInstitucionalM1IT y 3 híbridas de
ComposicionM3LecturaPreparadaIT aprobadas. Sin fallos, errores ni omisiones,
BUILD SUCCESS. PostgreSQL 16.15 en contenedores aislados; no es la BD consolidada.

Además de comprobar el caso conservado en PostgreSQL y la denegación antes del
repositorio, el log SQL de APP muestra dos solicitudes mapeadas a PATCH,
ambas terminadas en Case modification denied, sin DML observado entre el mapeo
y la resolución de la denegación. Esto no sustituye una auditoría global de BD.

Pendientes B concretos: GET positivo sin políticas sustituidas; token bearer
inválido específicamente sobre GET/PATCH (los IT ejecutados cubren ausencia y
cabeceras/cookie no acreditadas); e inyección de fallo del proveedor real M1/APP.
No se añaden esos tests en APP porque este incremento no autoriza modificarlo.
La corrección M3 de error de identidad sí queda cubierta por pruebas A.
