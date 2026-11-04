# DC-M3-REST-02 — Primer POST de casos: bloqueo de ensamblaje seguro

> Registro histórico de la inspección inicial. DC-M3-REST-03 autorizó después
> la separación de creación y el ensamblaje en app. El código preparado y las
> validaciones todavía pendientes se documentan en `DC-M3-REST-03.md`.
> El estado que sigue corresponde a aquella inspección, no al código posterior.

Estado: inspección realizada; endpoint y DTO HTTP no implementados ni habilitados.
No se ejecutaron Git, Maven, Docker ni pruebas. La rama indicada por `.git/HEAD`
es `feature/m3-gestion-casos`.

## Evidencia local

Rutas relativas a la raíz del backend:

| Fuente | Hallazgo |
| --- | --- |
| `m3/src/main/java/com/prisaly/m3/application/service/CasoApplicationService.java` | Constructor obligatorio con `CaseAuthorizationPort` y `CasoRepositoryPort`. `createDraft(String userId, CasoBuilder builder)` construye, autoriza mediante `canModifyCase`, inserta y devuelve el borrador. No es un componente Spring registrado automáticamente. |
| `m3/src/main/java/com/prisaly/m3/application/port/CaseAuthorizationPort.java` | Solo declara `canModifyCase(String userId, String caseId)` y `canPublishCase(String userId, String caseId)`. No recibe propietario ni distingue creación de modificación. |
| Fuentes productivas inspeccionadas de M1, M3 y `app` | No se encontró un proveedor productivo de `CaseAuthorizationPort` ni ensamblaje de `CasoApplicationService`. La comprobación institucional requerida no puede sustituirse por una autorización siempre verdadera. |
| `m1/src/main/java/com/prisaly/m1/application/port/IdentityAccessPort.java` | Ofrece `currentUser()`, `requireUser(UUID)`, `hasPermission(UUID, String)` y `hasRole(UUID, String)`. |
| `m1/src/main/java/com/prisaly/m1/application/service/IdentityAccessService.java` | Servicio registrado en Spring. Obtiene un principal UUID del contexto autenticado y comprueba la existencia y actividad del usuario. |
| `m1/src/main/java/com/prisaly/m1/presentation/PersonalAuthorizationController.java` | Precedente real de comprobación de `CREAR_CASO` mediante `currentUser()` y `hasPermission()`. Este endpoint de comprobación no debe invocarse por HTTP desde M3. |
| `app/src/main/java/com/prisaly/config/HttpApiConfig.java` | Exige autenticación en las rutas no declaradas públicas, con respuestas 401 y 403. Autenticación global no sustituye el permiso de creación ni la autorización de M3. |
| `app/src/main/resources/application.yml` | Context path `/api`: la futura presentación debe declarar `/v1/casos`. |
| `app/src/main/resources/db/migration/V001__bd0052_estructura.sql` | `Caso.idDocente` referencia `Usuario.idUsuario`. Esto permite identificar la fuente del UUID propietario, pero una FK no concede permiso institucional. |
| `m3/pom.xml`, `pom.xml`, `app/pom.xml` | M3 dispone de JPA y jackson-core; no declara dependencia de presentación web ni de M1. Las dependencias de `app` no dan acceso de compilación inverso a M3. |
| `m3/src/main/java/com/prisaly/m3/infrastructure/persistence/jpa/CasoPublicationAdapter.java` | Conserva constructor con autorización obligatoria y no tiene `@Repository`. No se modificó la corrección P0. |

## Bloqueo que impide habilitar POST

M1 puede proporcionar una identidad autenticada y comprobar `CREAR_CASO`.
El propietario deberá obtenerse de esa identidad, nunca del cuerpo HTTP.
Sin embargo, falta el proveedor institucional y su ensamblaje para satisfacer
la comprobación adicional que `createDraft()` ya exige sobre un caso todavía
inexistente. No es válido inventar una búsqueda de titularidad de una fila que
aún no existe, ni implementar una autorización genérica que permita modificar
cualquier caso por disponer del permiso de creación.

No se añadió un controlador registrado que falle al arrancar por dependencias
ausentes, ni un controlador con una identidad o autorización alternativa.
Tampoco se amplió un contrato entre módulos para ocultar el bloqueo.

## Contrato mínimo de cuerpo a conciliar al resolver el ensamblaje

Propuesta pendiente de materialización, no DTO existente:

| Campo de entrada | Tipo Java propuesto | Condición |
| --- | --- | --- |
| `idCaso` | `UUID` | Obligatorio, suministrado explícitamente; no generación automática. |
| `titulo` | `String` | Obligatorio, no vacío; máximo 150 puntos de código, conforme al dominio. |
| `tiempoEstimadoSegundos` | `Integer` | Obligatorio y estrictamente positivo; sin valor ficticio ante ausencia. |
| `historia` | `String` | Opcional, nullable. |
| `desarrolloSituacional` | `String` | Opcional, nullable. |

Este primer cuerpo mínimo correspondería a un borrador sin contenido académico
inicial: el Builder permite escenarios vacíos y rúbrica ausente. No se propone
aceptar contenido anidado y descartarlo. La ausencia de campos de propietario,
estado, actividad y fechas evita que el cliente los determine libremente.
Los valores de estado y actividad serían exclusivamente los comportamientos
ya existentes del Builder; la fecha ausente utiliza el DEFAULT SQL existente.

Respuesta mínima propuesta: `idCaso` UUID y `estado` con el valor público que
se apruebe para representar BORRADOR. No devolver fechas como si hubieran sido
releídas: `createDraft()` retorna el objeto construido, mientras PostgreSQL puede
asignar `fechaCreacion` durante INSERT.

Plantilla ilustrativa (requiere sustituir los marcadores; no es una solicitud
lista para ejecutar ni contiene UUID o contenido académico productivos):

```json
{
  "idCaso": "<UUID explícito del nuevo caso>",
  "titulo": "<título del borrador>",
  "tiempoEstimadoSegundos": "<entero positivo, enviar como número JSON>",
  "historia": null,
  "desarrolloSituacional": null
}
```

## Restricciones de la persistencia existente

`CasoPersistenceAdapter.insert()` usa la transacción existente de inserción del
agregado. El mapper exige UUID raíz y docente válidos; prohíbe importar bloqueo
académico; exige precisión temporal de microsegundos cuando se suministran fechas.
En contenido anidado exige órdenes explícitos y UUID válidos. Rechaza la
`descripcion` legada de escenario y los criterios legados de rúbrica, sin
equivalencia SQL aprobada. Una rúbrica persistible requiere estructura explícita
y asociaciones reales, no conversión de `CriterioRubrica`.

La futura respuesta 201 debe emitirse después de confirmar la transacción.
Si INSERT participa en una transacción externa REQUIRED, el retorno del adaptador
por sí solo no demuestra el commit externo: el ensamblaje deberá fijar la frontera
transaccional antes de construir la respuesta HTTP.

## Cambios pendientes fuera del alcance autorizado

Propuesta de archivo nuevo en `app`, que NO se creó:
`app/src/main/java/com/prisaly/config/M3CasoCreationConfig.java`.

Su responsabilidad sería ensamblar explícitamente el servicio y el puente de
identidad/autorización utilizando el proveedor institucional real aprobado.
Debe comprobar `IdentityAccessPort.currentUser()` y `CREAR_CASO`, vincular el
propietario al UUID autenticado y satisfacer las condiciones adicionales de M3.
Antes de escribirlo debe ratificarse cómo el proveedor implementará
`canModifyCase` para creación sin ampliar indebidamente permisos sobre casos
existentes. No se propone que `app` conceda permisos por su cuenta.

Arquitectura deberá aprobar cómo la presentación M3 recibe esa identidad sin
introducir dependencias o contratos intermodulares no autorizados. Después se
podrá incorporar la dependencia web necesaria en M3, los DTO, el controlador
`/v1/casos` y la traducción de errores, sin modificar M1.

## Validación pendiente antes de Postman

- Identidad ausente: 401; permiso o autorización M3 denegados: 403, sin INSERT.
- Datos/JSON inválidos: 400 antes de persistir; no traducir toda excepción a 400.
- Conflictos concretos de unicidad: 409 sin detalles SQL. Una FK fallida no debe
  clasificarse automáticamente como conflicto del cliente si revela un fallo de
  ensamblaje de identidad.
- Error técnico: conservar tratamiento de servidor, nunca devolver 201.
- Creación válida: una sola llamada a `createDraft()`, UUID conservado, propietario
  autenticado, commit confirmado y 201; rollback del agregado ante fallo.
- Rechazar o tratar explícitamente campos no admitidos; no aceptar estructuras
  legadas que el mapper rechaza ni contenido anidado que luego se ignore.
- Verificar arranque sin reactivar `CasoPublicationAdapter` y sin exigir un bean
  productivo inexistente.

No hay pruebas nuevas ni resultados de ejecución de este incremento. No se
declara implementado el endpoint ni completada la integración institucional.
