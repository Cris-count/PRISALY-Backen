# DC-M3-M1-RES-03 — Validación post-PR #68

Fecha: 2026-11-08, America/Bogota. Alcance exclusivo de compatibilidad M3–APP.

## Sincronización y conservación

Rama existente: feature/m3-dc-ejec-03-rubricas-etag; worktree m3-dc-ejec-03.
SHA anterior: 446a5e1df071de54a7f56c8febdaa056b0987fc2.
Fetch comprobó origin/develop y se ejecutó únicamente `git merge --ff-only origin/develop`, sin conflictos ni commit de merge.
SHA efectivo: **3f97e7e22aac18f655461b882dfd9a5f0f2195d6**.
No se usaron stash, reset, clean, nueva rama ni sobrescrituras. Los cambios remotos afectan APP y no intersectan los siete archivos locales.

Los siete archivos iniciales se copiaron conservando sus rutas, con manifest-sha256.csv, tracked.patch y git-status.txt. Después del fast-forward se verificó SHA-256: **7/7 idénticos byte a byte**. Los dos documentos anteriores se conservan como evidencia histórica; sus referencias al SHA anterior no describen esta nueva ejecución.

| Estado local inicial | Archivo conservado |
| --- | --- |
| M | m3/src/main/java/com/prisaly/m3/application/service/CasoDocenteConsultaService.java |
| M | m3/src/main/java/com/prisaly/m3/application/service/CasoPersistentUpdateService.java |
| M | m3/src/test/java/com/prisaly/m3/CasoDocenteConsultaServiceTest.java |
| M | m3/src/test/java/com/prisaly/m3/CasoGestionControllerTest.java |
| M | m3/src/test/java/com/prisaly/m3/CasoPersistentUpdateServiceTest.java |
| ?? | m3/docs/DC-M1-POSTCIERRE-02-M3.md |
| ?? | m3/docs/DC-M3-M1-RES-03-VERIFICACION.md |

## Compatibilidad del proveedor real

M3CaseReadAuthorizationAdapter implementa AutorizacionConsultaCasoPort sin conversiones ni cambios de firma:

```java
Decision verificarAcceso(UUID usuario, UUID caso);
Decision verificarVisibilidad(UUID usuario, UUID caso, CasoEstado estado, boolean activo);
```

La configuración registra un único proveedor; la prueba de composición verifica esa unicidad. El adaptador consulta IdentityAccessPort real, comprueba identidad activa coincidente y el permiso de la política configurada. No consulta repositorios M3; la existencia y titularidad permanecen en CasoDocenteConsultaService. CREAR_CASO no autoriza lectura. El orden M3 permanece identidad → autorización → existencia → titularidad → visibilidad → respuesta.

PoliticaLecturaCaso rechaza configuración parcial y estados inválidos. application.yml conserva permiso="", estados-visibles="" y requiere-activo=true: la política productiva está pendiente y deniega. No se editaron propiedades, APP ni M1.

HttpApiConfig incorpora permiso para DispatcherType.ERROR, para no convertir el despacho interno de fallos técnicos en 401. No registra codec de concurrencia. La composición comprueba que no existe bean CasoPatchConcurrencyContract.

## GET y PATCH

GET institucional: **adaptador conectado y compatible; habilitación positiva pendiente por política y contrato ETag**. Con identidad válida y configuración productiva sin ratificar, responde 403. Ausencia/invalidación del bearer se evalúa por la cadena institucional. Los GET 200 de las nuevas IT usan proveedor real, M1, servidor HTTP y PostgreSQL reales, pero permiso y visibilidad de fixture: no ratifican una política institucional.

PATCH: **canModifyCase=false**, sin cambios. BORRADOR no se habilita por defecto; PUBLICADO y ARCHIVADO siguen protegidos. Denegación antes de repositorio y de If-Match; pruebas M3 verifican que no escribe, y APP relee PostgreSQL y conserva el título. ETag m3-v2, bloqueo académico, rúbricas, migraciones y DD-03 permanecen intactos.

## Conciliación HTTP y CORS

| Escenario | Actual | Candidato pendiente |
| --- | --- | --- |
| If-Match ausente, tras autorización controlada | 400 | 428 |
| ETag obsoleto | 409 STALE_CASE | 412 |
| PATCH exitoso con composición controlada | 204 sin cuerpo | 200 con cuerpo y ETag |
| If-Match inválido | 400 | Sin cambio solicitado |
| GET con codec controlado | 200 con ETag fuerte del snapshot | Ratificar y conectar transporte productivo |
| GET real con política fixture de PR #68 | 200 sin ETag | No satisface todavía aceptación GET con ETag fuerte |

El codec rechaza validadores débiles, comodines, listas y duplicados. La autorización conserva precedencia sobre las precondiciones. No se cambió código HTTP. ARQ-008.3 completo no está disponible en las fuentes locales documentadas; no se declara conformidad con un texto ausente.

CORS inspeccionado en HttpApiConfig ya sincronizado: métodos GET/POST/PUT/PATCH/DELETE/OPTIONS; cabeceras permitidas Authorization, Content-Type y Accept. **If-Match no está permitido y ETag no está expuesto**. El PR #68 no modifica esa parte. Un navegador que solicite If-Match en preflight queda sin autorización CORS y no puede leer ETag desde JavaScript con esta configuración. No se presenta esta inspección como prueba HTTP de CORS ejecutada; la certificación de origen permitido/ajeno y exposición queda pendiente. No se modificó APP.

## Pruebas y clasificación

Comando ejecutado sobre el nuevo SHA más los cinco cambios M3 conservados:

```powershell
.\mvnw.cmd -B -o -pl app -am -Pdocker-it '-Dtest=CasoGestionControllerTest,CasoEtagControllerTest,CasoDocenteConsultaServiceTest,CasoPersistentUpdateServiceTest,CasoMetadataTest,CasoPublicationContractTest,CasoApplicationServiceTest,CasoPersistenceMapperIntegrityTest,RubricaStructureTest,M3CaseReadAuthorizationAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=ComposicionInstitucionalM1IT,ComposicionM3LecturaInstitucionalIT,CasoPersistenceAdapterIT,RubricaJpaReadIT,CasoCommandUpdateIT,CasoJpaAggregateTransactionIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
```

Java 21; PostgreSQL 16.15 en contenedores aislados, no BD institucional consolidada. Log local ignorado: m3/target/res03-post68-verificacion.log. La antigua ComposicionM3LecturaPreparadaIT fue retirada por PR #68 y no se duplica.

- Controladas M3: identidad ausente/fallida, autorización denegada/indisponible, ajeno, inexistente, GET con ETag fuerte, PATCH sin permiso/sin escritura, formato estricto y obsolescencia.
- PostgreSQL M3: concurrencia y único ganador, bloqueo por programación académica, rollback e integridad/conservación de rúbricas.
- Unitarias APP: M3CaseReadAuthorizationAdapterTest usa IdentityAccessPort controlado; no se reporta como integración real.
- ComposicionInstitucionalM1IT: M1/APP y PostgreSQL reales, configuración sin política; verifica proveedor único, GET/PATCH 403 sin modificación y 401 sin identidad.
- ComposicionM3LecturaInstitucionalIT: HTTP real, bearer/M1/adaptador/BD reales, política de fixture. Incluye GET 200 sin ETag; sin permiso, ajeno e inactivo/no visible 403 según recorrido; caso inexistente 404; bearer inválido/ausente y cuenta desactivada 401 por filtro; PATCH 403 sin escritura; fallo técnico real con tabla de permisos temporalmente indisponible en BD aislada → 500 sanitizado. No acredita política ratificada ni ETag productivo.

Resultados finales: **145 unitarias/MVC M3 + 63 integraciones PostgreSQL M3 + 7 unitarias del adaptador APP + 5 integraciones de composición sin política + 5 integraciones HTTP con política fixture = 225 pruebas aprobadas**. Cero fallos, errores u omisiones. Maven BUILD SUCCESS; código de salida Maven/PowerShell 0. Las cinco pruebas HTTP con proveedor real no constituyen ratificación de su política fixture. Verificación de formato Git: git diff --check sin incidencias.

## Defectos y límites

No se reimplementó autorización, GET/PATCH ni ETag. Se conserva la corrección local previa que deja pasar el fallo técnico de identidad hasta 500 sanitizado y conserva denegación explícita 403 y ausencia 401. El PR #68 aporta por separado la corrección de error dispatch APP. No se añadió otra corrección de producción.

Límites reproducibles de integración: CORS sin If-Match/exposición de ETag; inexistencia de bean productivo de concurrencia; política sin ratificar. NO_DISPONIBLE del adaptador se convierte en denegación 403 por el consumidor M3: el 500 del IT de infraestructura no prueba que todas las indisponibilidades del puerto produzcan 500. Ese mapeo requiere conciliación, sin cambio unilateral.

## Condiciones pendientes y Git

GET positivo exige ratificación de permiso, estados/actividad, contrato ARQ-008.3/ETag y CORS, seguida de pruebas con esa política aprobada. PATCH requiere después permiso y estados editables expresamente aprobados, decisión de 204/200 y 400/428–409/412, transporte estricto y repetición de no escritura/concurrencia/bloqueo/rúbricas. Hasta entonces permanece denegado.

Esta ejecución agrega únicamente este informe; los siete archivos originales siguen íntegros. Estado final verificado: cinco archivos seguidos modificados y tres documentos sin seguimiento. No hay cambios locales en APP/M1/M4/M5/M6 ni migraciones. No se hizo commit, push, PR ni merge adicional; solo el fast-forward solicitado. La rama original permanece en el SHA sincronizado.

Respaldo verificable: `C:\Users\57316\OneDrive\Escritorio\PRISALY\DC-M3-RES03-RESPALDO-POST68-20261108-150913`; manifiesto SHA-256 conservado.


