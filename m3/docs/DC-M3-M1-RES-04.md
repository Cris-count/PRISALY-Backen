# DC-M3-M1-RES-04 — Corrección de errores institucionales M3

## Alcance y base

Fecha: 2026-11-08, America/Bogota. Base sincronizada y probada: cf72da817317ff8a47a111cf466933ca11b60ee0 (PR #70), con el delta M3 de este incremento. HEAD inicial antes de sincronizar: 3f97e7e22aac18f655461b882dfd9a5f0f2195d6.

La sincronización fue fast-forward, sin conflictos. Se conservaron PR #68 y #70 como ancestros. Los ocho archivos locales iniciales tienen respaldo externo SHA-256 y stash retenido (8c3691b0397aa725690688f34e7f0d0965829cfd). Git normalizó CRLF de los documentos al aplicar el stash; se recuperaron los bytes originales desde el respaldo y se verificaron 8/8 antes de extender la corrección. Los tres informes históricos permanecen locales fuera del PR.

No se modifican APP/M1, M4, M5/M6, migraciones, permisos, estados, CORS ni ETag. La configuración CORS de PR #70 se conserva, incluyendo If-Match y exposición de ETag; no es aporte de esta corrección. No se reimplementan los adaptadores de Cristian.

## Defecto, corrección y contrato

Los servicios de consulta y actualización confundían errores técnicos de identidad con ausencia de autenticación (401). La consulta también convertía NO_DISPONIBLE o una excepción técnica de autorización en denegación (403); la actualización hacía lo mismo con excepciones de canModifyCase.

Se conserva el manejador HTTP y sus respuestas genéricas. Los dos servicios ahora distinguen:

| Resultado institucional | HTTP y comportamiento |
| --- | --- |
| Identidad ausente/null o CasoAuthenticationException | 401, sin lectura ni escritura |
| Denegación explícita, DENEGADO o canModifyCase=false | 403, sin acceso no autorizado |
| NO_DISPONIBLE, decisión null anómala o excepción técnica de autorización | 500 sanitizado; nunca AUTORIZADO |
| Excepción técnica de identidad, incluida IllegalArgumentException del proveedor | 500 sanitizado, sin reclasificar como entrada HTTP inválida |

El código interno CASE_AUTHORIZATION_UNAVAILABLE se traduce por el manejador existente a 500/CASE_OPERATION_FAILED, con mensaje genérico. No se adjuntan causa ni mensajes privados a ese error de aplicación. Las denegaciones de dominio conocidas se mantienen como denegación; los errores técnicos de repositorio conservan su recorrido previo.

La verificación de acceso sucede antes de cargar el caso. La de visibilidad requiere primero existencia y titularidad: puede haber una lectura autorizada previa al fallo de visibilidad, pero no se devuelve ficha, ETag ni contenido y no se escribe. Esta distinción evita afirmar falsamente que todo fallo de visibilidad ocurre antes de cualquier lectura.

GET continúa sujeto al proveedor y a la política institucional: permiso y estados productivos permanecen vacíos. PATCH continúa denegado por canModifyCase=false. El orden autorización → titularidad/bloqueo → precondición se conserva. ETag m3-v2, concurrencia transaccional, rúbricas, publicación y bloqueo académico M4 no cambian.

## Regresiones y reproducción

- CasoGestionControllerTest: ausencia de identidad 401; denegación de identidad 403; fallo técnico de identidad 500; NO_DISPONIBLE/decisión null/excepción técnica de lectura 500; indisponibilidad/excepción de visibilidad 500 sin ficha ni ETag; fallo técnico de escritura 500 antes de persistencia y precondiciones; FORBIDDEN explícito de escritura 403. Los mensajes privados no aparecen en las respuestas de fallo verificadas.
- CasoDocenteConsultaServiceTest: separa DENEGADO de NO_DISPONIBLE/null, tanto en acceso como en visibilidad; mantiene ausencia de proveedor, titularidad, inexistencia y orden de consulta. Un fallo de elegibilidad no consulta repositorio.
- CasoPersistentUpdateServiceTest: verifica fallo de identidad o autorización técnica sin repositorio, conservando ausencia, denegación, propietario, bloqueo y errores de persistencia.

Ejecución negativa real antes de extender la corrección: 53 pruebas, 7 fallos, 0 errores, 0 omisiones; log local m3/target/res04-red.log. Demuestra las expectativas 500 frente al 403 anterior. La corrección de identidad previa ya estaba en el worktree preservado y también se publica ahora; el fallo histórico 401 se documentó en el incremento anterior, no se presenta como reproducido de nuevo por esta ejecución negativa.

El primer intento final detectó una expectativa de prueba alterada accidentalmente para un fallo de repositorio. Se restauró su aserción original y se repitió la validación completa; no se cambió persistencia para satisfacerla. El intento inicial sin acceso al caché Maven no ejecutó pruebas y no se cuenta.

## Validación nueva

Java 21, PostgreSQL 16.15 en contenedores aislados; no es certificación de la BD institucional consolidada. Comando:

```powershell
.\mvnw.cmd -B -o -pl app -am -Pdocker-it '-Dtest=CasoGestionControllerTest,CasoEtagControllerTest,CasoDocenteConsultaServiceTest,CasoPersistentUpdateServiceTest,CasoMetadataTest,CasoPublicationContractTest,CasoApplicationServiceTest,CasoPersistenceMapperIntegrityTest,RubricaStructureTest,M3CaseReadAuthorizationAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=ComposicionInstitucionalM1IT,ComposicionM3LecturaInstitucionalIT,ComposicionM3EdicionPreparadaIT,CasoPersistenceAdapterIT,RubricaJpaReadIT,CasoCommandUpdateIT,CasoJpaAggregateTransactionIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
```

Resultado combinado de esta ejecuci?n nueva: **237 pruebas ?nicas aprobadas**: 150 unitarias/MVC M3, 63 integraciones PostgreSQL M3, 7 unitarias APP y 17 integraciones APP/M1 (6 sin pol?tica, 6 lectura institucional con pol?tica fixture y 5 edici?n preparada).

La primera invocaci?n completa aprob? 150 unitarias/MVC y 61 integraciones PostgreSQL; una de las dos pruebas de CasoJpaAggregateTransactionIT fall? por timeout Hikari de adquisici?n de conexi?n (l?mite existente de 500 ms; se observ? espera de 633 ms), sin conexi?n para obtener metadatos JDBC. El reactor se detuvo antes de APP. Se conservaron el log y XML del error; no se modific? ese test ni su timeout.

Se repiti? exclusivamente ese grupo de dos pruebas y se continu? con APP/M1, usando el siguiente comando. Las dos aprobaron, al igual que 7 unitarias APP y las 17 integraciones APP/M1: cero fallos, errores u omisiones; BUILD SUCCESS y salida 0. No se afirma que la primera invocaci?n haya sido exitosa. Las 63 integraciones M3 se cuentan una sola vez.

```powershell
.\mvnw.cmd -B -o -pl app -am -Pdocker-it '-Dtest=M3CaseReadAuthorizationAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=CasoJpaAggregateTransactionIT,ComposicionInstitucionalM1IT,ComposicionM3LecturaInstitucionalIT,ComposicionM3EdicionPreparadaIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
```

Logs locales: m3/target/res04-verify.log y m3/target/res04-composition-retry.log. El primero conserva el timeout y el segundo acredita la repetici?n y la composici?n. Los resultados se cotejaron con XML Surefire/Failsafe. La fuente probada es cf72da8 m?s el delta de los cinco archivos Java de este commit, sin modificaciones posteriores; se verifican sus SHA-256 antes de publicar.

Las pruebas unitarias/MVC de autorización M3 usan puertos controlados; las unitarias del adaptador APP también controlan IdentityAccessPort. Las composiciones APP/M1 usan identidad, seguridad y PostgreSQL reales. Las de lectura y edición preparada añaden políticas/permisos fixture y, cuando corresponde, transporte ETag solo de prueba; no acreditan políticas ratificadas ni autorización productiva de PATCH. La composición sin política conserva la denegación productiva y verifica ausencia de proveedor de concurrencia productivo.

La regresión PostgreSQL conserva concurrencia con un único ganador, huella obsoleta, rollback, rúbricas, publicación negativa y bloqueo por programación académica. Las pruebas HTTP de Cristian se ejecutan sin editar APP. El fallo técnico real de tabla de permisos de su IT no sustituye las nuevas pruebas M3 específicas de NO_DISPONIBLE.

## Publicación y pendientes

Solo se publican los dos servicios, los tres archivos de pruebas y este informe. No se duplican la implementación GET/PATCH, el codec, la integridad de rúbricas previa ni los cambios APP de PR #68/#70.

Quedan fuera de Mariana la ratificación de permisos y estados/actividad visibles/editables, la activación institucional del transporte ETag, la certificación con política aprobada y la conciliación ARQ-008.3. Se conservan 400 actual/428 candidato por If-Match ausente, 409 actual/412 candidato por versión obsoleta y 204 actual/200 candidato para PATCH. No se habilitan esas alternativas ni se modifica DD-03.

La autorización de esta entrega incluye commit, push y PR hacia develop; no incluye merge. El estado CI se informa en el PR y en la entrega, separado de la ejecución local.
