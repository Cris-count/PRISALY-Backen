# M3 — Consolidación de trabajo independiente y cierre de validación

Fecha: 2026-11-08, America/Bogota. Rama única: feature/m3-dc-ejec-03-rubricas-etag.

## Resultado y continuidad

El código de desarrollo de Mariana ya está incorporado a esta rama y a develop. No se crean implementaciones, ramas ni commits de código artificiales. Esta entrega consolida la documentación pendiente y la evidencia de validación, conservando los informes históricos sin reescribir sus resultados.

HEAD inicial: faade398bbff127e505b85953c356cfb3d53fccd. Base sincronizada y probada: **63eceb448e6c37596ce8115080f1cdda174538a9**. Se integró por fast-forward; no cambió ningún archivo de m3/src, app/src/main o m6/src con respecto al código previamente publicado. PR #72 está incorporado mediante el merge 516f310. No se modificó develop directamente ni se hizo merge de esta entrega.

La reanudación conserva las 229 pruebas unitarias/MVC y 100 integraciones PostgreSQL M3 ya aprobadas; no se repitieron. Se recuperó el resultado de la composición que había quedado ejecutándose, sin lanzar otra campaña ni reiniciar el inventario.

## Incidente Bad Request

La traza de la sesión registra el 2026-11-08 a las 19:26:34 -05:00 un evento task_complete con error.message igual a {"detail":"Bad Request"} y codex_error_info=other. El evento previo es la respuesta de functions.wait sobre la celda 114, completada correctamente, con código 0 para la inspección de árboles de fuentes.

Por tanto el fallo observado corresponde al cierre del turno del agente, después de la espera de herramientas. La traza no identifica la URL ni la causa HTTP interna; no se atribuye a una operación Git, a un endpoint M3 ni a Maven. La ejecución de composición, sesión 52325, continuó en segundo plano hasta las 19:29:44 -05:00 y terminó con BUILD SUCCESS y salida 0. Ningún log de las dos campañas de consolidación contiene Bad Request. No se perdió ni se repitió una ejecución a causa de ese mensaje.

## Trabajo independiente ya incorporado

Se reutiliza el inventario y respaldo anterior, sin repetirlo. Los nueve worktrees inspeccionados fueron PRISALY-Backen, audit-develop-20261105, audit-m3-20261105, candidate-dc-m3-int-02, m3-avance-independiente, m3-cierre-gestion, m3-dc-ejec-03, m3-sincronizado y verify-m3-postmerge-c52ae9e. Solo el destino se sincronizó; los demás no se editaron.

| Origen / evidencia | Incorporación comprobada |
| --- | --- |
| m3-avance-independiente, HEAD 7b8012b | Ancestro de la rama; incluye 888613b: integridad de referencias en CasoPersistenceMapper y pruebas de integridad/productor |
| m3-cierre-gestion, HEAD e785101 | Ancestro; GET/PATCH docentes y sus pruebas ya presentes |
| m3-sincronizado, HEAD 27ce433 | Ancestro; revocación y confidencialidad del contexto académico ya presentes |
| candidate-dc-m3-int-02, HEAD 888f949 | Compatibilidad APP de creación ya incorporada; no se copia ni reimplementa |
| WIP M3-INC-11, stash histórico | Sus cuatro archivos de código/pruebas y M3-INC-11.md coinciden exactamente con 327e8b6, ya ancestro; no se reaplica |
| Rúbricas/ETag | 419412f ya incorporado; no se duplica el codec ni el mapper |
| Errores institucionales | 0938348 y faade39 ya incorporados mediante PR #72 |

Los archivos del incremento independiente 888613b que ya existen son CasoPersistenceMapper.java, CasoPersistenceMapperIntegrityTest.java y ContextoAcademicoProducerMapperTest.java, bajo las rutas M3 correspondientes. El incremento 27ce433 está en ContextoAcademicoProtectedReaderTest.java. INC-11 está en CasoUpdateCommand.java, CasoCommandUpdateAdapter.java, CasoCommandAddContentIT.java, CasoUpdateContractTest.java y M3-INC-11.md. No se publica ninguno como cambio nuevo porque ya están integrados.

La comparación git log --branches='*m3*' --not HEAD --no-merges -- m3 no muestra commits M3 pendientes de las ramas locales de Mariana. La comparación origin/develop..HEAD antes del commit documental tampoco mostró commits pendientes. Los 73 archivos Java del ZIP histórico tienen blobs alcanzables en la historia de HEAD; ninguno requiere rescate como código independiente. El ZIP de XML no contiene Java.

Los ZIP, el parche propuesto obsoleto DC-INT-02-retirar-config-antigua.patch y DC-INT-02-PREPARACION.md del worktree antiguo se conservan localmente, fuera de esta entrega. No se aplica una eliminación APP ya incorporada por 888f949.

## Conservación

Respaldo externo: M3-CONSOLIDACION-RESPALDO-20261108-173054, hermano de los worktrees. Contiene copias de los archivos locales con manifiesto SHA-256, diffs, bundle de referencias y un bundle completo con reflogs, ambos verificados. El bundle con reflogs incluye los objetos de los stashes 8c3691b0397aa725690688f34e7f0d0965829cfd y 0cedf878ac13c078188255fdc48f31c59cea9fe1. No se eliminó ningún stash, respaldo ni worktree.

Los cuatro informes locales previos conservan sus bytes originales, cotejados con el manifiesto. Los resultados pre-PR #68 y post-PR #68 son evidencia histórica de sus SHA declarados; no describen el estado productivo actual. El dictamen PR #69 conserva su alcance y observaciones al momento de su revisión. Este informe identifica el estado actual sin alterar esos registros.

## Pruebas conservadas y composición completada

Todas las cifras siguientes proceden de las dos ejecuciones de consolidación sobre 63eceb4, no de campañas anteriores:

| Campaña | Resultado |
| --- | --- |
| Suite completa M3, unitarias/MVC | 229 aprobadas |
| Suite completa M3, PostgreSQL 16.15 | 100 aprobadas |
| Contratos/preparación M6 focalizados | 27 aprobadas |
| Creación, autorizadores y mapper APP focalizados | 28 aprobadas |
| Composiciones APP/M1/M3/M6 PostgreSQL | 33 aprobadas |
| Total | **417 aprobadas**, cero fallos, errores u omisiones |

La campaña M3 terminó a las 19:25:15; la de composición a las 19:29:44 -05:00. Ambas BUILD SUCCESS, salida 0. No se repitieron las 329 de M3 durante la reanudación posterior al incidente.

Desglose de la composición:

| Clase | Cantidad | Naturaleza de la evidencia |
| --- | ---: | --- |
| ContratosConsumidoresTest | 6 | Contratos M6 con datos controlados |
| ModelosLimitesEInmutabilidadTest | 3 | Límites estructurales y copias |
| PrepararContextoEvaluacionServiceTest | 10 | Puertos y referencias controlados |
| PrepararEvaluacionProtegidaTest | 8 | Autorización/rúbrica/calificación controladas |
| M3CaseAuthorizationAdapterTest | 9 | Adaptador con IdentityAccessPort controlado |
| M3CaseReadAuthorizationAdapterTest | 7 | Adaptador de lectura con identidad controlada |
| M3CasoCreationTest | 6 | Composición Spring focalizada de creación |
| MapeoContextoAcademicoM3Test | 6 | Mapper estructural M3–M6 |
| ComposicionInstitucionalM1IT | 6 | M1/APP/PostgreSQL reales, política productiva pendiente y denegadora |
| ComposicionM3LecturaInstitucionalIT | 6 | HTTP, M1, adaptador y PostgreSQL reales; permiso/visibilidad fixture |
| ContextoAcademicoM3M6IT | 4 | Contexto M3 y PostgreSQL reales, capacidad/verificación controladas |
| M3CasoCreationIT | 12 | APP/M1/creación/persistencia reales sobre usuarios y datos efímeros |
| ComposicionM3EdicionPreparadaIT | 5 | HTTP/M1/PostgreSQL reales, política de edición y codec suministrados solo por configuración de prueba |
| Total de composición | **88** | **55 unitarias + 33 integraciones** |

Comandos ejecutados, conservados para reproducción y no repetidos al cerrar esta entrega:

```powershell
.\mvnw.cmd -B -o -pl m3 -am -Pdocker-it verify
.\mvnw.cmd -B -o -pl app -am -Pdocker-it '-Dtest=MapeoContextoAcademicoM3Test,M3CasoCreationTest,M3CaseAuthorizationAdapterTest,M3CaseReadAuthorizationAdapterTest,ContratosConsumidoresTest,PrepararContextoEvaluacionServiceTest,PrepararEvaluacionProtegidaTest,ModelosLimitesEInmutabilidadTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=M3CasoCreationIT,ComposicionInstitucionalM1IT,ComposicionM3LecturaInstitucionalIT,ComposicionM3EdicionPreparadaIT,ContextoAcademicoM3M6IT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
```

Logs locales ignorados: m3/target/consolidacion-m3-integral.log y m3/target/consolidacion-app-m6.log. No se publican logs brutos ni XML históricos como resultados nuevos.

## Código probado y compatibilidad

Los árboles Git de fuentes, que deben permanecer idénticos en el commit documental publicado, son:

- m3/src: fdc20093fd626a3b657848a491709a8912c8d5c3.
- app/src: 36df8fd32ba28c66a5599b0ccd9c33ac59e486a2.
- m6/src: 3999700a4ac30cdbdca2a04e9c41ab55ee71d697.

Se comprobaron creación, consulta docente, PATCH con autorización controlada, titularidad, restricciones de Publicado/Archivado, rúbricas y asociaciones, altas académicas, publicación negativa, bloqueo M4, concurrencia, errores institucionales 401/403/500 y ETag m3-v2. No se cambia ningún módulo de otro integrante.

GET productivo sigue sujeto al proveedor de Cristian y a la política institucional pendiente. PATCH productivo conserva canModifyCase=false; que una prueba edite un BORRADOR con permiso fixture no habilita la edición institucional. CORS de PR #70 se conserva sin modificación. La publicación académica positiva continúa condicionada por DD-03 y completitud de rúbrica; no se certifica publicación persistente positiva. Archivado permanece protegido y no se inventa una operación institucional de archivado/reactivación.

Los contratos M3–M6 conservan UUID, jerarquías Caso/Escenario/Pregunta/Opción/Rúbrica, BigDecimal exacto de valorPregunta y JSON opaco. Las pruebas no ratifican fórmula DD-03, selector DD-04, D07 ni autorización académica productiva.

## Documentos pertinentes y publicación

Se publican exclusivamente cinco documentos en la misma rama:

1. DC-M1-POSTCIERRE-02-M3.md: evidencia histórica de identidad y estados, previa a PR #68.
2. DC-M3-M1-RES-03-VERIFICACION.md: conciliación histórica anterior a sincronizar PR #68.
3. DC-M3-M1-RES-03-POST-PR68.md: validación histórica post-PR #68.
4. DC-REV-69-M3-DICTAMEN.md: revisión cruzada asignada a Mariana, con observación reproducible sobre vínculos huérfanos.
5. M3-CONSOLIDACION-TRABAJO-INDEPENDIENTE.md: este cierre y el resultado vigente de consolidación/composición.

No se fabrican commits de desarrollo ni se duplican commits incorporados en develop. El nuevo commit es documental; la sincronización lleva también a la rama remota la historia de develop ya existente, sin editarla ni fusionar unilateralmente el PR. La comparación contra develop debe mostrar solamente estos documentos. El SHA remoto y el PR se verifican e informan en la entrega final, sin afirmar aprobación de CI antes de que ocurra.
