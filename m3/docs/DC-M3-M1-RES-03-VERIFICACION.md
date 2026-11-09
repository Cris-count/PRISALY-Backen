# DC-M3-M1-RES-03 — Verificación de integración GET/PATCH

Fecha: 2026-11-08 (America/Bogota). Alcance exclusivo M3–APP. La resolución se toma de la instrucción recibida; no se encontró su documento independiente ni el texto completo de ARQ-008.3. No se certifica conformidad con una especificación ausente.

## Base y conservación

Worktree `m3-dc-ejec-03`, rama `feature/m3-dc-ejec-03-rubricas-etag`.
SHA efectivo: `446a5e1df071de54a7f56c8febdaa056b0987fc2`, descendiente de `cf57327ea4e7d6fde22c716db5b4ea5a34da3546`.
Se conservaron sin edición los seis archivos encontrados: CasoDocenteConsultaService.java, CasoPersistentUpdateService.java, CasoDocenteConsultaServiceTest.java, CasoGestionControllerTest.java, CasoPersistentUpdateServiceTest.java y m3/docs/DC-M1-POSTCIERRE-02-M3.md (este último no seguido). Los cinco primeros están en sus rutas existentes bajo m3/src.

Fetch comprobado: origin/develop avanzó a `3f97e7e` (PR #68; commit `e312946`, dos commits por delante). Sus cambios afectan exclusivamente APP; se inspeccionaron mediante git show/diff y no se incorporaron ni ejecutaron en esta base. No hubo reset, merge, nueva rama, push ni PR. La evidencia de esta ejecución corresponde al SHA efectivo más los cambios locales conservados.

## Puertos y recorrido confirmado

```java
Decision verificarAcceso(UUID usuario, UUID caso);
Decision verificarVisibilidad(UUID usuario, UUID caso, CasoEstado estado, boolean activo);
```

AutorizacionConsultaCasoPort conserva exactamente ambas firmas. CasoDocenteConsultaService valida UUID, obtiene identidad, exige verificarAcceso AUTORIZADO, carga/exige existencia, comprueba docente propietario y exige verificarVisibilidad AUTORIZADO antes de proyectar respuesta. La lectura versionada obtiene ficha y huella del mismo snapshot. Sin proveedor no hay GET positivo. Excepciones/NO_DISPONIBLE del puerto de autorización se traducen en denegación 403; un fallo técnico de identidad/persistencia llega al 500 sanitizado. Son recorridos distintos, no debe prometerse 500 para todo fallo de autorización.

PATCH conserva identidad → canModifyCase → carga → propietario → bloqueo → precondición → aplicación parcial → actualización transaccional. canModifyCase=false en APP; la denegación precede a lectura, decodificación de If-Match y escritura. No se implementó proveedor institucional, código de permiso ni controlador adicional.

## Estados y respaldo

| Estado/condición | GET institucional en esta base | PATCH institucional | Respaldo |
| --- | --- | --- | --- |
| BORRADOR | Denegado, falta proveedor/política | Denegado; ser borrador no autoriza | CasoEstado, CasoBuilder, CasoApplicationService, adaptador APP |
| PUBLICADO | Denegado; necesita visibilidad expresa | Denegado | CasoPublication; contratos M3 |
| ARCHIVADO | Denegado; necesita visibilidad expresa | Denegado; guard ARCHIVED_CASE_OPERATION_PENDING | CasoValidation, matriz de cumplimiento |
| activo=false | No concede visibilidad | No concede edición | verificarVisibilidad; comandos conservan metadato |
| Bloqueo académico | No concede permiso | Guard EVALUABLE_CONTENT_LOCKED incluso con autorización controlada | ContenidoEvaluableLock, IT PostgreSQL |

Documentos: entrega/MATRIZ-CUMPLIMIENTO-M3.md, M3-INC-10D.md, M3-INC-13A.md, M3-INC-13B.md, DC-M3-REST-GET-PATCH.md, M3-DC-EJEC-03-CONTRATOS.md y DC-M1-POSTCIERRE-02-M3.md. No sustituyen una máquina de estados ratificada. No se modifica DD-03 ni completitud/publicación de rúbricas.

## Conciliación HTTP pendiente de aprobación

| Escenario | Controlador real / composición controlada | Resolución recibida / decisión pendiente |
| --- | --- | --- |
| GET autorizado | 200; ETag fuerte solo al suministrar CasoEtagConcurrencyContract; sin él no hay ETag | Exigir 200 con ETag fuerte al aceptar integración |
| PATCH autorizado vigente | 204 sin cuerpo ni ETag nuevo | Conservar; relectura GET para nueva versión |
| PATCH 200 con cuerpo y ETag | No implementado | Alternativa candidata, requiere decisión expresa |
| If-Match ausente | 400 con codec candidato, tras autorización | 428 solicitado: diferencia sin cambiar código |
| Huella obsoleta | 409 STALE_CASE | 412 solicitado: diferencia sin cambiar código |
| If-Match inválido | 400 | Coincide con escenario solicitado |
| Sin identidad | 401 | Conservado |
| Permiso/propiedad/visibilidad denegada | 403 | Conservado |
| Caso inexistente tras autorización | 404 | Conservado; denegación previa no revela existencia |
| Fallo técnico identidad/persistencia | 500 sanitizado | Conservado; error genérico sin detalle interno |
| Sin contrato de concurrencia, PATCH autorizado | 503 | Pendiente conectar contrato ratificado |

No se atribuyen estas diferencias al texto completo de ARQ-008.3: no está disponible. Las referencias locales reconocen esa ausencia. El documento antiguo M3-GET-PATCH-HTTP-CONTRATO-PENDIENTE dice que el transporte no está implementado: M3-DC-EJEC-03-CONTRATOS lo actualiza como codec candidato implementado, sin bean productivo ni ratificación.

CasoStrongEtagContract conserva m3-v2 y rechaza débiles, comodín, listas, duplicados, versiones/formato inválidos. No se alteró concurrencia para admitirlos. No se reordenó autorización para devolver 428/412 a solicitudes denegadas.

## Cristian: avance encontrado y escenarios de aceptación

En origin/develop existe ya M3CaseReadAuthorizationAdapter sobre IdentityAccessPort, su configuración y PoliticaLecturaCaso. Las propiedades productivas permiso/estados-visibles están vacías: política pendiente, GET denegado. Su IT ComposicionM3LecturaInstitucionalIT usa proveedor real pero permiso y visibilidad BORRADOR de fixture; no acredita ratificación académica ni GET institucional aceptado. No se ejecutó aquí ni se reporta aprobado.

| Escenario institucional | Evidencia ejecutable en esta base | Pendiente de integración/aceptación |
| --- | --- | --- |
| Bearer válido | IT M1/APP real autentica; GET/PATCH siguen 403 sin política | GET 200 con política ratificada y ETag fuerte |
| Bearer inválido | IT actual cubre ausencia/cabeceras falsas; no token inválido específico | Ejecutar GET/PATCH con token inválido: 401; el nuevo IT de Cristian incluye GET |
| Cuenta inactiva | No cubierta aquí por HTTP real | Ejecutar ambas rutas; el nuevo IT incluye GET 401 por filtro |
| Docente sin permiso | Sin proveedor ratificado se deniega globalmente | Validar proveedor real con política aprobada y cuenta sin permiso |
| Docente ajeno / inexistente | Positivos/403/404 con política controlada, no aceptación institucional | Repetir con proveedor real y política ratificada |
| Fallo técnico real | Fallos de puerto controlados M3; no avería real institucional aquí | Nuevo IT de Cristian prepara indisponibilidad de tabla aislada; ejecutar y distinguir fallo de identidad de NO_DISPONIBLE del puerto |
| CORS | Configuración inspeccionada | Origen permitido: preflight con Authorization, Content-Type, If-Match; PATCH permitido; GET expone ETag; origen ajeno rechazado |

HttpApiConfig actual y remoto permite Authorization/Content-Type/Accept, pero no If-Match, y no configura exposedHeaders ETag. Es una brecha reproducible por inspección de configuración; no se modificó APP ni se presenta como prueba HTTP ejecutada.

## Defectos y correcciones

Se conserva la corrección local previa: no reclasificar indiscriminadamente una RuntimeException de identidad como 401. Ausencia permanece 401, FORBIDDEN del puerto 403 y fallo técnico 500 sanitizado. Las pruebas MVC comprueban ausencia de lecturas/escrituras y de detalles internos. No se añadió otra corrección de producción. El error dispatch HTTP de APP tiene una corrección independiente en el PR #68, fuera de esta base; no se duplicó.

## Ejecución y resultados

Comando reproducible (Java 21; PostgreSQL 16.15 aislado, no BD institucional consolidada):

```powershell
.\mvnw.cmd -B -o -pl app -am -Pdocker-it '-Dtest=CasoGestionControllerTest,CasoEtagControllerTest,CasoDocenteConsultaServiceTest,CasoPersistentUpdateServiceTest,CasoMetadataTest,CasoPublicationContractTest,CasoApplicationServiceTest,CasoPersistenceMapperIntegrityTest,RubricaStructureTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=ComposicionInstitucionalM1IT,ComposicionM3LecturaPreparadaIT,CasoPersistenceAdapterIT,RubricaJpaReadIT,CasoCommandUpdateIT,CasoJpaAggregateTransactionIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
```

Log local ignorado: m3/target/res03-verificacion.log. Resultado de esta ejecución: 145 unitarias/MVC M3, 63 integraciones PostgreSQL M3, 5 integraciones de composición M1/APP real y 3 integraciones híbridas de lectura preparada aprobadas. Cero fallos, errores u omisiones; Maven registra BUILD SUCCESS. Las tres híbridas usan política controlada: no acreditan GET institucional positivo. El proceso contenedor de PowerShell devolvió exit_code=1 pese al BUILD SUCCESS; se corroboran los resultados con los XML Surefire/Failsafe y el resumen Maven, sin ocultar esa discrepancia de salida. No se duplican pruebas existentes equivalentes.

## Condiciones exactas de aceptación

GET: incorporar coordinadamente el adaptador real de Cristian; ratificación documental del permiso, estados/actividad y ARQ-008.3; conectar codec fuerte ratificado; ejecutar con M1 real válido/inválido/inactivo, sin permiso, ajeno, inexistente, fallos técnicos y CORS. GET 200 debe exponer ETag fuerte del mismo snapshot y solo la ficha autorizada. Hasta cumplirlas, pendiente por integración; no activar propiedades positivas por inferencia.

PATCH, posteriormente: aprobación expresa de permiso y estados editables; mantener canModifyCase=false hasta entonces; acordar 204 vigente o alternativa 200 y decidir 428/412 frente a 400/409; aprobar transporte estricto; ejecutar negativas sin escritura, obsolescencia, único ganador PostgreSQL, rollback, bloqueo M4 y conservación de rúbricas. ETag nunca sustituye autorización ni titularidad. No se activa publicación ni se cambia DD-03, migraciones o BD-005.2.

## Archivos y Git

Esta reanudación agrega únicamente este informe. Los seis archivos locales previos permanecen íntegros. Cinco archivos seguidos modificados y dos documentos no seguidos al finalizar, sin staging ni commits nuevos. Rama original conservada; dos commits detrás de origin/develop, sin integración automática de APP.

