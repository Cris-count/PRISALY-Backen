# DC-M3-REST-03 — Creación autorizada y POST de borradores

Estado: compilación, pruebas técnicas, arranque y trece solicitudes Bruno
validados el 4 de noviembre de 2026 en la corrida histórica registrada. La
colección Postman se ejecutó posteriormente mediante Newman; sus resultados
se conservan separados en EVIDENCIA-POSTMAN.md. Capturas Desktop pendientes. Los apartados
iniciales conservan el registro histórico de preparación; sus menciones a tareas
pendientes quedan actualizadas por el registro de ejecución al final.
Rama comprobada leyendo `.git/HEAD`: `feature/m3-gestion-casos`.

## Alcance

Único endpoint nuevo: `POST /api/v1/casos`. El controlador declara `/v1/casos`;
el prefijo `/api` procede del context path existente de app. No hay GET, PATCH
ni publicación nueva. La creación inicial acepta exclusivamente el caso raíz,
sin escenarios ni rúbrica. Los borradores incompletos están admitidos por el
Builder; no se añaden reglas de completitud o calificación.

## Contrato HTTP definitivo

Entrada `CrearCasoRequest`:

| Campo | Tipo Java | Restricción |
| --- | --- | --- |
| `idCaso` | UUID | Obligatorio, representación UUID completa; aportado por el cliente. |
| `titulo` | String | Obligatorio, no vacío, hasta 150 puntos de código Unicode. |
| `tiempoEstimadoSegundos` | Integer | Obligatorio, entero JSON positivo dentro del rango de INTEGER. No convierte cadenas ni decimales. |
| `historia` | String | Opcional, nullable. |
| `desarrolloSituacional` | String | Opcional, nullable. |

No se aceptan campos adicionales, duplicados, documentos JSON concatenados,
propietario, identidad del llamador, estado, actividad, fechas ni contenido
anidado. Se rechazan con 400; no se ignoran ni se descartan estructuras legadas.

El controlador recibe el cuerpo textual y lo decodifica inmediatamente al DTO
con Jackson Core, ya disponible en M3. La lectura local por tokens impide que
las opciones globales de coerción de Jackson acepten, por ejemplo, `1.5` como
segundos enteros o ignoren `docenteId`. No es un parser JSON propio ni procesa
JSONB académico. Se conserva el texto recibido sin normalizar las narrativas.

Respuesta `CrearCasoResponse`: `idCaso` UUID y `estado` String (`BORRADOR`).
No devuelve fecha de creación: PostgreSQL puede asignarla mediante DEFAULT y
el contrato existente devuelve el objeto construido, no una relectura.

Ejemplo Postman (variables que debe aportar el responsable):

```http
POST {{baseUrl}}/v1/casos
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

```json
{
  "idCaso": "{{nuevoCasoUuid}}",
  "titulo": "Borrador de prueba manual",
  "tiempoEstimadoSegundos": 300,
  "historia": null,
  "desarrolloSituacional": null
}
```

`nuevoCasoUuid` debe contener un UUID explícito completo y no usado. El título y
los 300 segundos son datos de prueba, no valores predeterminados productivos.
No se debe enviar un propietario. El token debe proceder del mecanismo M1
existente y corresponder a una cuenta activa con `CREAR_CASO` asignado realmente.

## Autenticación y autorización

1. Se conserva la cadena de seguridad de app y el filtro bearer provisional M1.
2. `CasoCurrentUserPort` es un puerto local M3; su proveedor en app consulta
   `IdentityAccessPort.currentUser()`, sin dependencia Maven M3 → M1.
3. El propietario que recibe el Builder es ese UUID institucional.
4. `CasoApplicationService.createDraft` conserva el orden construcción →
   autorización → INSERT → retorno, consultando ahora
   `canCreateCase(userId, draft.docenteId())`.
5. `M3CaseAuthorizationAdapter` vuelve a obtener identidad M1, exige cuenta
   activa, comprueba coincidencia de usuario y propietario y consulta
   `hasPermission(current.userId(), "CREAR_CASO")`.
6. Discordancias deniegan creación. Fallos técnicos se propagan; no se convierten
   en ausencia ni autorización. Errores institucionales conocidos se traducen
   a ausencia de autenticación o denegación sin detalles sensibles.

V001 vincula `Caso.idDocente` con `Usuario.idUsuario` mediante
`fk_caso_iddocente`. No se inventa una entidad Docente ni otro identificador.

`canModifyCase` y `canPublishCase` retornan false explícitamente en este proveedor,
conforme a la autorización de DC-M3-REST-03. No consultan ni reutilizan el permiso
de creación. Las llamadas y reglas de reemplazo, copia y publicación permanecen
intactas. Su habilitación institucional requiere políticas posteriores.

## Transacción y persistencia

El controlador delimita esta operación HTTP mediante TransactionTemplate con
REQUIRES_NEW. El escritor existente participa por REQUIRED en esa transacción.
La respuesta 201 se construye solo después del retorno de execute, que incluye
el commit. Un error del callback o del commit impide devolver 201.

Esta frontera evita devolver éxito sujeto al commit posterior de un llamador.
No realiza la relectura REQUIRES_NEW del agregado antes del commit. No se cambian
el lector, el mapper, el adaptador SQL, m3-v2, F3-03 ni las migraciones.

En app se utiliza el gestor existente; el escritor ya exige JpaTransactionManager
con el mismo EntityManagerFactory. Si hubiera una transacción externa, se
suspendería: el commit HTTP es independiente y puede necesitar otra conexión.
En el flujo HTTP previsto no se crea una transacción externa antes del controlador.
Debe comprobarse el comportamiento real del pool durante validación integrada.

El Builder conserva BORRADOR, activo=true y fechas no asignadas. Estos son los
comportamientos existentes; no se generan UUID ni fechas Java. PostgreSQL conserva
su DEFAULT de creación y sus restricciones PK/FK/UNIQUE/CHECK.

## Errores HTTP

| HTTP | Condición |
| --- | --- |
| 201 | Ejecución e inserción confirmadas por la transacción de creación. |
| 400 | JSON/campos/tipos inválidos o validaciones de creación reconocidas. |
| 401 | Identidad no autenticada; también se conserva la respuesta del filtro de app. |
| 403 | Cuenta denegada, discordancia de identidad/propietario o falta de permiso. |
| 409 | SQLSTATE 23505, conflicto de unicidad. |
| 500 | Fallos técnicos o de commit y errores no reconocidos. |

El advice se limita al controlador de creación. No devuelve mensajes internos,
SQL, UUID propietarios ni trazas. Una FK fallida no se transforma automáticamente
en error del cliente: el propietario procede del ensamblaje institucional.

## Archivos

Modificados:

- `m3/pom.xml`: añade únicamente starter-web, sin versión explícita (padre Boot
  4.1.2); conserva las dependencias y perfil existentes. No se añade M1 a M3.
- `m3/src/main/java/com/prisaly/m3/application/port/CaseAuthorizationPort.java`.
- `m3/src/main/java/com/prisaly/m3/application/service/CasoApplicationService.java`.
- `m3/src/test/java/com/prisaly/m3/CasoApplicationServiceTest.java`.
- `m3/src/test/java/com/prisaly/m3/CasoMetadataTest.java`: configura creación en
  el mock que utiliza la prueba existente de metadatos del Builder.
- `m3/src/test/java/com/prisaly/m3/RubricaStructureTest.java`.
- `m3/src/test/java/com/prisaly/m3/infrastructure/CasoPersistenceAdapterIT.java`.
- `m3/docs/DC-M3-REST-02.md`: conserva el diagnóstico histórico con nota de sucesión.

Creados:

- `m3/src/main/java/com/prisaly/m3/application/port/CasoCurrentUserPort.java`.
- `m3/src/main/java/com/prisaly/m3/application/port/CasoAuthenticationException.java`.
- `m3/src/main/java/com/prisaly/m3/presentation/CasoCreationController.java`.
- `m3/src/main/java/com/prisaly/m3/presentation/CasoCreationExceptionHandler.java`.
- `m3/src/main/java/com/prisaly/m3/presentation/dto/CrearCasoRequest.java`.
- `m3/src/main/java/com/prisaly/m3/presentation/dto/CrearCasoResponse.java`.
- `m3/src/main/java/com/prisaly/m3/presentation/dto/InvalidCasoRequestException.java`.
- `m3/src/test/java/com/prisaly/m3/presentation/CrearCasoRequestTest.java`.
- `app/src/main/java/com/prisaly/config/M3CaseAuthorizationAdapter.java`.
- `app/src/main/java/com/prisaly/config/M3CasoCreationConfig.java`.
- `app/src/test/java/com/prisaly/config/M3CaseAuthorizationAdapterTest.java`.
- `app/src/test/java/com/prisaly/config/M3CasoCreationTest.java`.
- Este documento.

## Pruebas preparadas y compatibilidad

- Creación consulta la capacidad específica y conserva el orden de operaciones.
- Denegación no inserta; errores de construcción/persistencia mantienen prioridad.
- Las cuatro clases anónimas implementan el método nuevo explícitamente.
- La fixture de creación PostgreSQL autoriza creación y deniega modificación;
  las fixtures de publicación y copia deniegan creación.
- DTO: campos exactos, UUID, nulos, Unicode, límites, tipos estrictos, enteros
  fuera de rango, duplicados, JSON inválido y campos no admitidos.
- Adaptador app: permiso M1, identidad/propietario coincidentes, suplantación,
  cuenta inactiva, ausencia de autenticación, fallo técnico y denegación de las
  otras capacidades.
- MockMvc standalone: prefijo de contexto, 201 después del callback de commit,
  400/401/403/409/500, fallo de commit y ausencia de exposición SQL.
- Configuración ligera: proveedor común de identidad/autorización y servicio,
  sin registrar el puerto de publicación.

Los mocks son exclusivamente de prueba. Las pruebas unitarias HTTP no demuestran
un commit PostgreSQL real ni ejecución del filtro de seguridad de app. Las pruebas
PostgreSQL existentes de INC-13A se adaptan al nuevo contrato, pero la solicitud
HTTP con autenticación real todavía requiere verificación manual.

`CasoPublicationAdapter` no se editó ni se registró mediante @Repository o @Bean.
No se habilita publicación ni se cambia Rubrica.isComplete().

## Validación manual pendiente

Desde la raíz del backend, sin que estos comandos hayan sido ejecutados:

```powershell
mvn -pl m3,app -am test "-Dtest=CasoApplicationServiceTest,CasoMetadataTest,RubricaStructureTest,CasoPublicationContractTest,CrearCasoRequestTest,M3CaseAuthorizationAdapterTest,M3CasoCreationTest" -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl m3 -am verify -Pdocker-it -Dit.test=CasoPersistenceAdapterIT -Dfailsafe.failIfNoSpecifiedTests=false
mvn -pl app -am verify -Pdocker-it -Dit.test=PersonalAuthSecurityIT -Dfailsafe.failIfNoSpecifiedTests=false
```

Después: arrancar el backend con el procedimiento existente, autenticar mediante
M1, ejecutar el POST, verificar la fila y su propietario en PostgreSQL, repetir
el UUID para comprobar 409 y probar ausencia de token/permiso. Confirmar rollback
y que nunca se devuelve 201 ante fallo de escritura o commit. No hay GET nuevo
para realizar esa comprobación.

Riesgos pendientes: compilación y resolución MVC de Boot 4.1.2 sin ejecutar;
arranque del ensamblaje completo; seguridad bearer institucional actualmente
provisional; prueba HTTP real con PostgreSQL; cualquier implementación del puerto
fuera de este checkout deberá incorporar el nuevo método abstracto. No se declara
el endpoint operativo ni el incremento aprobado.

## Seguimiento integral REST y colecciones

La orden integral posterior autoriza el control inicial Git en modo lectura.
HEAD comprobado: `ac32db0c38a99d8eaeb9ba9403718ddfce7d8a85`, rama
`feature/m3-gestion-casos`. Estado inicial: siete archivos M3 modificados,
quince archivos sin seguimiento (incluido el ZIP protegido) y ningún cambio
preparado en el índice. Se conservaron estos cambios. HEAD es la base: no
identifica por sí solo el código de trabajo con archivos todavía sin commit.
No existe un SHA nuevo del código final y no se creó ningún commit.

### Inventario de endpoints y autorización

| Operación | Implementación local | Disponibilidad verificada | Bloqueo |
| --- | --- | --- | --- |
| POST `/api/v1/casos` | Controlador, DTO, autorización institucional y persistencia preparados. | No se ejecutó backend ni HTTP real. | Validación manual de arranque, PostgreSQL y M1. |
| GET `/api/v1/casos/{idCaso}` | No se añadió controlador. Los lectores y puertos de persistencia existen. | No habilitado. | No existe política institucional de consulta/visibilidad ni un puerto de autorización de lectura aprobado. |
| PATCH `/api/v1/casos/{idCaso}` | No se añadió controlador. CasoUpdatePort y sus siete comandos permanecen intactos. | No habilitado. | El proveedor institucional deniega modificación; faltan políticas de recurso/titularidad ratificadas. |
| Publicación | Adaptador interno sin registro Spring. | No habilitada. | Autorización denegada y DD-03 pendiente. |

Ni CREAR_CASO ni la huella autorizan consultar, modificar o conocer la existencia
de un caso. Una FK de propietario tampoco concede permiso. No se añadió una ruta
que solo simule autorización o convierta ausencia de política en permiso.
El eventual 404 del router por falta de GET/PATCH no es una política de visibilidad.

La futura activación de PATCH deberá reutilizar exactamente findById(UUID) y
update(UUID, String, List<CasoUpdateCommand>), exigir `^m3-v2:[0-9a-f]{64}$`,
mantener bloqueo raíz, pertenencia, atomicidad, rollback y triggers F3-03.
No se habilita CasoRepositoryPort.update(Caso).

| Subtipo existente | Campos reales que deben conservarse al aprobar la presentación |
| --- | --- |
| AgregarEscenarioCommand | idEscenario UUID, titulo/contexto/instrucciones nullable, orden int positivo. |
| AgregarPreguntaCommand | idPregunta/idEscenario UUID, enunciado, textoAyuda nullable, estadoEdicion Borrador/Lista, visible boolean, orden positivo. |
| AgregarOpcionRespuestaCommand | idOpcion/idPregunta UUID, texto, esCorrecta boolean explícito, explicacion nullable, orden positivo. |
| ActualizarDatosCasoCommand | campo NOMBRE/HISTORIA/DESARROLLO_SITUACIONAL/TIEMPO_ESTIMADO_SEGUNDOS y valor validado por campo. |
| ActualizarEscenarioCommand | idEscenario UUID, campo TITULO/CONTEXTO/INSTRUCCIONES/ORDEN y valor. |
| ActualizarPreguntaCommand | idPregunta UUID, campo ENUNCIADO/TEXTO_AYUDA/ORDEN y valor. |
| EliminarOpcionCommand | idOpcion UUID; eliminación explícita, sujeta a pertenencia, historia y F3-03. |

No se definió un discriminador JSON nuevo ni una respuesta de lectura con
respuestas correctas: esas decisiones de presentación y seguridad quedan
pendientes junto a GET/PATCH. No se modifica ningún subtipo ni se inventa
equivalencia de rúbrica para completar el endpoint.

### Corrección adicional de entrada

El DTO ahora rechaza NUL y sustitutos Unicode aislados en título y narrativas.
Jackson puede representar esas cadenas JSON, pero PostgreSQL no puede guardar
ese texto sin error o pérdida. Se devuelve 400 antes de abrir la transacción.
Los pares suplementarios válidos siguen contando como un punto de código.
No se cambian los límites SQL ni reglas académicas.

### Matriz de cobertura preparada

| Escenario | Unidad/HTTP simulado | Integración PostgreSQL preparada | Cliente preparado | Resultado real |
| --- | --- | --- | --- | --- |
| Creación válida, propietario M1 y commit | M3CasoCreationTest | M3CasoCreationIT: lectura independiente posterior al POST. | Bruno/Postman 01. | Pendiente. |
| Sin autenticación | M3CasoCreationTest | Filtro real de app, 401 y ausencia de fila. | 02. | Pendiente. |
| Sin permiso/revocación | M3CaseAuthorizationAdapterTest y M3CasoCreationTest | Asignación M1 revocada, 403 y ausencia de fila. | 03 con token real sin permiso. | Pendiente. |
| Cuenta inactiva | M3CaseAuthorizationAdapterTest | Cuenta M1 desactivada después del login; 401 del filtro real. | Prueba manual adicional. | Pendiente. |
| UUID, título, tiempo, desconocidos y propietario JSON | CrearCasoRequestTest y M3CasoCreationTest | 400 y ausencia de fila. | 04–08, 10–11. | Pendiente. |
| NUL/Unicode inválido | CrearCasoRequestTest | 400 sin INSERT. | 12 (NUL). | Pendiente. |
| UUID duplicado | M3CasoCreationTest | 409 y conservación del primer caso. | 09, después de 01. | Pendiente. |
| Dos POST concurrentes con el mismo UUID | Orden de commit simulado. | Barrera de inicio, conexiones reales, un 201/un 409 y una sola fila. | No automatizado en clientes. | Pendiente. |
| Error de escritura/commit | M3CasoCreationTest y pruebas existentes INC-13A. | Regresión CasoPersistenceAdapterIT. | No se añade un endpoint para fabricar fallos. | Pendiente. |
| GET y política 404/visibilidad | No se inventa permiso. | M3CasoCreationIT comprueba ausencia de mapping. | Cuatro escenarios documentados, no ejecutables. | Bloqueado. |
| PATCH, huella, estados y F3-03 | Pruebas existentes de comandos y concurrencia. | Regresiones INC-10B/10D/11/12. | Ocho escenarios documentados, no ejecutables. | HTTP bloqueado; ejecución técnica pendiente. |

M3CasoCreationIT arranca app real y usa M1, autenticación bearer existente,
JpaTransactionManager, migraciones oficiales y PostgreSQL 16.15. No usa mocks
productivos, no desactiva triggers ni inventa propietarios: los obtiene de
usuarios registrados por el servicio institucional en la base efímera de prueba.
La prueba concurrente usa barrera con límite, lock_timeout=5s,
statement_timeout=10s y adquisición Hikari de 5s; no utiliza Thread.sleep.
El test no es evidencia docente Postman, aunque llegue a ejecutarse exitosamente.

### Colecciones y evidencia

- Colección Bruno independiente: `bruno/m3-gestion-casos/`, con formato igual
  al bruno.json existente y doce solicitudes POST. No se edita la colección M1.
- Exportación Postman v2.1:
  `bruno/exports/PRISALY_M3_Postman_Collection.json`.
- Entornos de ejemplo sin tokens/contraseñas reales para ambos clientes.
- Las carpetas Consulta y Actualización contienen únicamente descripción de
  bloqueos; no se envían solicitudes a endpoints ausentes.
- Ver procedimiento y registro vacío de evidencia en
  `bruno/m3-gestion-casos/README.md` y `EVIDENCIA-POSTMAN.md`.

Se validó estructuralmente el JSON de los artefactos y la correspondencia de
las doce solicitudes con el único POST implementado. Esto no equivale a importar
en Bruno/Postman ni a ejecutar sus scripts o las peticiones HTTP.

Archivos adicionales de esta continuación:

- Modificados: CrearCasoRequest.java, CrearCasoRequestTest.java y este documento.
- Creado: `app/src/test/java/com/prisaly/config/M3CasoCreationIT.java`.
- Creados: `bruno/m3-gestion-casos/bruno.json`, su entorno local.example.bru,
  README.md, EVIDENCIA-POSTMAN.md, doce .bru en 01-creacion y PENDIENTE.md
  en 02-consulta y 03-actualizacion.
- Creados: colección y entorno de ejemplo JSON en `bruno/exports/`.

### Ejecución manual siguiente

La orden condiciona ejecución a entorno disponible y autorización para ejecutarlo.
Esta intervención no ejecutó Maven, Docker, backend, Bruno ni Postman; tampoco
intentó conexiones y no atribuye el pendiente a un error de entorno no observado.
Los únicos comandos Git ejecutados fueron consultas de control/inspección.

Con la autorización de ejecución y el entorno preparados, desde la raíz:

```powershell
mvn -pl m3,app -am test "-Dtest=CasoApplicationServiceTest,CasoMetadataTest,RubricaStructureTest,CasoPublicationContractTest,CrearCasoRequestTest,M3CaseAuthorizationAdapterTest,M3CasoCreationTest" -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl app -am verify -Pdocker-it "-Dit.test=M3CasoCreationIT,PersonalAuthSecurityIT" -Dfailsafe.failIfNoSpecifiedTests=false
mvn -pl m3 -am verify -Pdocker-it "-Dit.test=CasoPersistenceAdapterIT,CasoCommandAddContentIT,CasoCommandUpdateIT,CasoCommandConcurrencyIT" -Dfailsafe.failIfNoSpecifiedTests=false
```

Después arrancar con el procedimiento institucional existente y ejecutar Postman.
Registrar versión exacta probada: HEAD y cambios locales, o el commit que el
responsable decida crear posteriormente. No atribuir los cambios actuales sin
commit exclusivamente al SHA base ni rellenar resultados/fechas de prueba por
anticipado. No se declara M3 terminado.

## Ejecución integral autorizada — 4 de noviembre de 2026

Esta sección actualiza los estados pendientes de la preparación anterior.
Ejecutor técnico: sesión de automatización local autorizada por Integrante 2.
Rama inicial y final: feature/m3-gestion-casos. HEAD inicial y final:
`ac32db0c38a99d8eaeb9ba9403718ddfce7d8a85`, con cambios locales sin commit.
No hubo commit, push, merge, rebase, cambio de rama ni modificación del ZIP.

### Resultados comprobados

| Verificación | Resultado real | Evidencia técnica local |
| --- | --- | --- |
| Compilación completa `.\mvnw.cmd -B -pl app -am package -DskipTests` | BUILD SUCCESS en los nueve proyectos. | 02-package-host.log. |
| Unidad M3 | 131 pruebas, cero fallos/errores/omitidas. | 03-unit.log. |
| Unidad ensamblaje app | 11 pruebas, cero fallos/errores/omitidas. | 03-unit.log. |
| Integración PostgreSQL M3 | 77 casos distintos aprobados por clase, en varias corridas. | 04-integration.log, 13-regression-retry.log y resúmenes por clase. |
| Integración creación y seguridad app | Siete M3CasoCreationIT y nueve PersonalAuthSecurityIT aprobadas. | 09-app-integration-isolated.log y 13-regression-retry.log. |
| Verificación Failsafe final | BUILD SUCCESS; los fallos iniciales se conservaron. | 16-failsafe-verify.log. |
| Arranque JAR | Spring Boot 4.1.2 iniciado, sin error de inyección. | 05-backend.log y logs depurados del proceso independiente. |
| PostgreSQL aislado | 16.15, nueve migraciones oficiales aplicadas. | 10-postgres-isolated.log. |
| GET /api/health | 200, service=prisaly-backend, status=ok. | 06-health.log. |
| Bruno CLI 4.3.0 | 13/13 solicitudes, 14/14 tests, 27/27 aserciones. | 21-bruno-all-local-fixture.log y .json depurado. |
| Commit y propiedad | Una fila Borrador, 300 segundos, fecha SQL presente y propietario igual al userId autenticado por M1. | 22-postgres-case-verification.log. |
| Postman GUI | Importación y solicitudes no completadas; sin capturas HTTP. | EVIDENCIA-POSTMAN.md sigue pendiente. |

Los archivos están en
`docs/evidence/m3-rest-f58fb21709c14437861e25f5b7ce7b6d/`, ruta ignorada por Git
según la configuración existente. No confundir integration-test con verify:
se ejecutó esta última fase explícitamente después de revisar los resultados.
Los reintentos no se cuentan como casos nuevos.

Distribución PostgreSQL M3: CasoCommandAddContentIT 22; CasoCommandConcurrencyIT
7; CasoCommandUpdateIT 13; CasoJpaAggregateReadIT 1; CasoJpaAggregateTransactionIT
2; CasoJpaReadIT 1; CasoPersistenceAdapterIT 24; EvaluableContentJpaReadIT 1;
ContextoAcademicoProducerReadIT 4; PostgreSqlDockerIT 1; RubricaJpaReadIT 1.
Total: 77. Con las 16 de app, son 93 casos PostgreSQL distintos.

### Matriz funcional observada

| Escenario | Evidencia obtenida | Resultado |
| --- | --- | --- |
| Creación autorizada | Bruno y PostgreSQL independiente. | 201, BORRADOR y commit comprobado. |
| Sin autenticación | Bruno y seguridad app. | 401. |
| Sin CREAR_CASO | Bruno con token M1 válido sin permiso. | 403, sin inserción. |
| Cuenta inactiva y revocación | Integración app, M1 real y BD efímera. | Rechazo y ausencia de inserción; no es ejecución Postman. |
| UUID inválido | Bruno. | 400. |
| Título ausente/excesivo | Bruno. | 400 en ambos casos. |
| Tiempo cero/negativo/decimal | Bruno. | 400 en los tres casos. |
| Campo desconocido/propietario en JSON | Bruno. | 400; no se ignora el propietario. |
| Identidad diferente del propietario | Unidad del puente M1/M3. | Denegación; no existe campo HTTP para acreditar esa identidad. |
| UUID repetido | Bruno y PostgreSQL independiente. | 409 y una sola fila. |
| Texto NUL | Bruno. | 400 antes de persistir. |
| Sustitutos Unicode aislados | Unidad del DTO. | Rechazo; pares suplementarios válidos conservados. |
| Fallo de persistencia/commit | Unidad del controlador y regresión PostgreSQL. | Sin 201; rollback según los escenarios técnicos. No se fabricó un endpoint de fallos. |
| Concurrencia y F3-03 | Regresiones PostgreSQL de comandos y publicación. | Pruebas aprobadas, triggers oficiales activos; no es publicación REST habilitada. |

Las solicitudes HTTP reales se enviaron desde Bruno, no desde Postman. La
preparación de identidad utilizó el endpoint real M1. Se ejecutó únicamente la
fixture ya existente `bruno/scripts/m1-bruno-seed-local.sql` dentro del contenedor
nuevo propiedad de esta validación, inicialmente sin usuarios ni casos. No se
alteró ese script ni se inventaron permisos. Sus credenciales de prueba se leyeron
localmente sin imprimirse; los tokens se suministraron mediante entorno temporal
privado. Se redactaron los informes compartibles y se eliminaron el entorno y
el informe privados. Esto demuestra funcionamiento técnico con fixtures locales,
no éxito productivo ni identidad institucional productiva.

### Incidencias y correcciones

1. El entorno restringido impidió resolver el padre Maven y abrir Docker. La
   ejecución autorizada fuera de esa restricción permitió compilar, sin cambiar
   el POM raíz ni versiones.
2. La primera corrida PostgreSQL tuvo errores de conexión/arranque y una salida
   anormal de JVM (1073807364). Se conservó el log y se repitieron los afectados;
   ese código por sí solo no acredita un defecto del dominio.
3. El rollback compuesto no acreditó inicialmente el SQLSTATE 23505 esperado y
   la corrida registró adquisición de conexión agotada a 500 ms. El arnés ahora
   precalienta sus conexiones con límite de inicialización de 10 s, restaurando
   los 500 ms operativos antes del test. No aumenta el tamaño del pool ni relaja
   la aserción de unicidad o la prueba de agotamiento real. Las siete pruebas de
   concurrencia aprobaron tras el ajuste.
4. CasoCommandAddContentIT, CasoJpaAggregateTransactionIT y el test de seguridad
   app agotaron la espera de arranque del contenedor. Sus bases efímeras ahora
   usan tmpfs y arranque acotado a 120 s. No cambian SQL, triggers, restricciones
   ni esperas de coordinación transaccional.
5. Una selección aislada leyó el resumen Failsafe anterior fallido y no ejecutó
   app. Se archivaron los resúmenes antes de repetir y se verificaron los nuevos.
   No se borraron fallos ni se presentó aquel intento como éxito.
6. Los guards Bruno consultaban solo bru.getVar y no veían variables del entorno.
   Se añadió el fallback bru.getEnvVar en todas las solicitudes sin omitir las
   comprobaciones. El primer intento afectado no envió HTTP.
7. Un entorno temporal PowerShell tenía BOM y Bruno lo rechazó antes del envío.
   Se generó en UTF-8 sin BOM, conservando el intento fallido.
8. El primer backend dejó de escuchar y un intento Bruno obtuvo ECONNREFUSED.
   Se inició el JAR como proceso oculto independiente y se vinculó a 127.0.0.1
   antes de la corrida autenticada. Solo se detuvo el proceso propio identificado
   por su PID y JAR; no se interrumpieron servicios compartidos.
9. Postman heredaba ELECTRON_RUN_AS_NODE y salía sin ventana. Se limpió solo esa
   variable al lanzarlo. Después, la pantalla inicial y la falta de foco
   verificable impidieron completar la importación. No se enviaron datos del
   proyecto a su asistente ni se seleccionaron cuentas externas.

Se añadió tiempo negativo a Bruno y Postman. Los resultados esperados de los
clientes no se convierten automáticamente en resultados observados.

### Archivos y estado restante

Además de conservar el trabajo previo, esta fase modificó:

- Los arneses M3 CasoCommandConcurrencyIT, CasoCommandAddContentIT y
  CasoJpaAggregateTransactionIT.
- `app/src/test/java/com/prisaly/security/PersonalAuthSecurityIT.java`, solo
  almacenamiento/espera de la base efímera del test de ensamblaje.
- Los doce .bru anteriores para resolver variables de entorno.
- La colección Postman; añadió `01-creacion/13-tiempo-negativo.bru`.
- Este documento, README de la colección y EVIDENCIA-POSTMAN.md.

No hubo cambios productivos adicionales de Java, modelos o permisos durante
esta fase. Los ajustes de tests se compilaron y ejecutaron. No se modificaron
M1, M6, POM raíz, compose.yaml, migraciones ni el ZIP.

Se deja un backend técnico local en `http://127.0.0.1:18080/api`, contenedor
`prisaly-m3-validation-f58fb217`, sin volúmenes persistentes compartidos.
backend-runtime-v2.json identifica su PID y logs privados sin contraseña. La
base contiene la fixture M1 y el caso técnico observado. Para repetir el alta
hay que aportar otro UUID explícito; no es un servicio productivo.

JAR probado, SHA-256:
`51189B9EA5EA8295876111B17E3F73D433054C895AB721BCC197AE831EE756CA`.
El manifiesto final de archivos y el estado Git complementan HEAD para identificar
los cambios sin commit. HEAD no representa un commit nuevo de esta implementación.

Pendientes: completar Postman manualmente, importar colección/entorno, ajustar
baseUrl, obtener token M1 sin exportarlo y registrar respuestas/capturas auténticas
con fecha, probador y versión. GET y PATCH siguen bloqueados por políticas
institucionales; publicación y DD-03 no se habilitan. No se declara M3 terminado.

## Registro posterior de pruebas manuales — 4 de noviembre de 2026

Integrante 2 confirmó una corrida posterior con 132 pruebas unitarias (0 fallos,
0 errores) y 71 integraciones PostgreSQL (0 fallos, 0 errores), BUILD SUCCESS,
utilizando Testcontainers y PostgreSQL 16.15. Dentro de esos totales:
CasoPublicationContractTest, 9 aprobadas; CasoPersistenceAdapterIT, 25 aprobadas.
La confirmación procede del ejecutor; no se inspeccionaron sus XML/logs completos
ni se ejecutaron pruebas adicionales para registrar estos resultados.

Esta corrida es independiente de los conteos históricos anteriores. No se suman
ni se trasladan sus resultados a Bruno o Newman. El detalle, las regresiones y
los límites constan en [M3-INC-13B](M3-INC-13B.md), sección «Resultado real
reportado por Integrante 2». Los avisos de PostgreSQL y Hikari quedan registrados
sin clasificación de defecto: falta su texto y contexto para analizarlos.

No se verificó un SHA ni el artefacto de backend correspondiente a esta corrida;
el hash y HEAD históricos de este documento no se le atribuyen. Deben conservarse
los reportes y logs depurados junto con la versión efectivamente validada.

La colección Postman ya cuenta con ejecución automatizada Newman 6.2.1:
13 solicitudes y 39 aserciones aprobadas, registrada por separado en
`bruno/m3-gestion-casos/EVIDENCIA-POSTMAN.md`. Este hecho no convierte los
pendientes históricos de capturas Desktop en evidencia GUI ni identifica la
versión de backend de aquella corrida. Los resultados Bruno se conservan intactos.

El éxito de las pruebas de rechazo y rollback no acredita publicación persistente
exitosa: DD-03 y la autorización institucional correspondiente siguen pendientes.
GET/PATCH, copia persistida y transiciones de Archivado no se habilitan por este
registro documental. No se declara M3 terminado.
