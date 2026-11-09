# Cierre nocturno — Backend PRISALY

Fecha: 8 de noviembre de 2026, America/Bogota. Ejecutor de esta sesión: Codex. La revisión y sus resultados no se atribuyen a Ingrid ni a otro integrante.

## Identificación y respaldo

Repositorio: C:/Users/Heiner/Desktop/PRISALY-ENTREGA/PRISALY-Backen.
Rama inicial/final: trabajo/consolidacion-inicial-20261108.
SHA inicial/final: 62080fc20ebb2c9b3cd57cd4eb82963c2d3b6231. No se crearon commits; los cambios locales no están representados por ese SHA. No hubo push, PR, merge, reescritura ni modificación de develop. No se cambió configuración de autoría.

Antes de editar se registraron estado y SHA, diff binario contra HEAD, archivos no versionados y sus hashes, y un bundle de todas las referencias. Evidencias: ../EVIDENCIAS-NOCTURNO-20261108/PRISALY-Backen-*. El Frontend se respaldó independientemente.

### Trabajo preexistente conservado

Había nueve archivos locales provenientes de M01-01 y Misión 03: dos servicios M5, las dos suites M01-01, pruebas de duración M4, workflow Backend y los dos informes anteriores. Sus nueve SHA256 coinciden al terminar. No se sobrescribió ningún cambio preexistente. La corrección M01-01 y F3-04 siguen presentes y probadas. backend-preservacion-final.json contiene las comprobaciones por archivo. El listado y diff inicial identifican qué cambios no pertenecen a esta sesión.

## Regresión completa ejecutada

Se ejecutó el reactor completo, incluyendo compilación, testCompile, unitarias/MVC y Failsafe docker-it, con Java 21.0.2, Maven Wrapper existente y caché Maven local. Se usó -fae para continuar módulos independientes ante un eventual fallo; no fue necesario corregir fallos del producto.

Comando reproducible: backend-verificacion-argumentos.json contiene todos los argumentos exactos. Se invocó mvnw.cmd con -o -B -fae -Dmaven.repo.local=C:/Users/Heiner/.m2/repository -Pdocker-it verify, selección de todas las clases IT/ITCase/PostgreSqlDockerSmokeTest inventariadas, -Dfailsafe.failIfNoSpecifiedTests=false y m4.baseline.migrations absoluto a app/src/main/resources/db/migration.

Se conservó la única exclusión deliberada del verificador CI existente: Bd0052BaselineIT#diagnosticoV010CreaDosTablasParalelasYEludeTriggerOficial, diagnóstico histórico fuera del runtime canónico. Se ejecutaron los otros tres métodos canónicos de esa clase. Las suites de guarda de esquema siguen probando el rechazo de V010; no se borró ni alteró migración alguna. La selección exige las 46 suites de integración; todas están presentes, sin omisiones.

Resultado: BUILD SUCCESS, 14 min 13 s. **813 unitarias/MVC +412 de integración =1225 pruebas aprobadas**, 0 fallos, 0 errores, 0 omitidas.

| Módulo | Unitarias/MVC | Integración |
| --- | ---: | ---: |
| M1 | 38 | 18 |
| M2 | 0 | 0 |
| M3 | 229 | 100 |
| M4 | 129 | 70 |
| M5 | 293 | 81 |
| M6 | 52 | 19 |
| M7 | 25 | 27 |
| APP | 47 | 97 |
| Total | 813 | 412 |

No se suman a estas cifras las ejecuciones históricas de 803/99 ni 73/75. backend-suites-iniciales.json conserva el inventario XML antes de las repeticiones focalizadas; backend-resumen-inicial.json comprueba presencia y totales. Log: backend-verificacion-inicial.log.

Las pruebas usaron Testcontainers desechables, fixtures de política explícitas y proveedores locales de correo de prueba. Las pruebas de candidatos DD-02 aplican sus migraciones únicamente dentro de fixtures PostgreSQL efímeros; no se habilitaron flags productivos ni se aplicaron migraciones sobre datos reales. No se usó compose ni una base compartida. Los fallos SQL inyectados, rechazos de restricciones y timeouts esperados forman parte de las pruebas, no son fallos del resultado final. Los avisos de metadatos Maven locales no impidieron BUILD SUCCESS y no se reparó la caché durante esta misión.

## BN-01 — Reproducibilidad de PostgreSQL en la prueba de conexión

La revisión detectó que PostgreSqlDockerSmokeTest era la única prueba con etiqueta mutable postgres:16-alpine y aceptaba cualquier versión 16.x. No se afirma que hubiera fallado ni que la imagen inicial tuviera una versión incorrecta: la limitación es que esa prueba no garantizaba el entorno canónico 16.15 requerido por CI. Las demás pruebas PostgreSQL están fijadas a 16.15.

Archivo modificado: m4/src/test/java/com/prisaly/m4/infrastructure/PostgreSqlDockerSmokeTest.java. Se fija postgres:16.15 y se verifica server_version_num=160015; se actualizan sus nombres descriptivos. Sin cambios de código productivo, contratos, dependencias, BD-005.2 o migraciones.

Antes: la prueba de conexión pasó dentro de las 412 IT con su criterio anterior de cualquier 16.x.
Después: verify focalizado -pl m4 -am -Pdocker-it, -Dit.test=PostgreSqlDockerSmokeTest y -Dtest=M4MigrationResourcesTest,M4CoordinacionInicioTest, con la caché local y failIfNoSpecifiedTests=false. BUILD SUCCESS; 5 unitarias y 1 IT aprobadas, esta última contra 16.15 exacto. Log backend-postgres-version-final.log. No se suman como seis casos distintos nuevos, porque son repeticiones de la regresión principal. Se repitió solo lo afectado por el cambio de configuración de prueba.

RF/CU relacionados: infraestructura transversal de CI y trazabilidad de RF-18/CU-11 M4; no introduce una regla académica. H-07 queda respaldado por ejecución local completa y por el ajuste canónico de esta prueba; los workflows no fueron publicados ni ejecutados remotamente.

## Revisión por prioridad Backend

| Bloque | Diagnóstico y estado conservado | Evidencia y RF/CU |
| --- | --- | --- |
| M3/permisos | Creación usa identidad M1 y CREAR_CASO. Lectura institucional tiene política de configuración vacía; no se completó con permisos inferidos. Modificación/publicación siguen denegadas en el adaptador institucional. | Unitarias y 100 IT M3, composición APP. RF-02, RF-05..09, RF-17; CU-05..10 históricos. |
| ETag/If-Match | CasoStrongEtagContract candidato valida una sola etiqueta fuerte m3-v2. No es bean; el controlador aplica concurrencia únicamente si se suministra contrato autorizado. No se registró ni se cambió semántica wildcard/listas/estados por conveniencia. | CasoEtagControllerTest, contratos de actualización y composición cubiertos en la regresión. RF-05..09; gestión de caso. |
| M5/idempotencia | CAS y bloqueo pesimista protegen respuestas. Finalización interna es terminal y una repetición se rechaza; eso no equivale a un recibo durable idempotente D07. Los POST preparatorios siguen sin bean MVC. No se agregó ledger ni se habilitaron los POST. | 293 unitarias y 81 IT M5; pruebas de inicio, respuestas, fallos, concurrencia y persistencia. RF-10..12; CU-14..16. |
| M4/autorización tardía | Reserva coordinada Sesion→Convocatoria→Participacion→Intento e historial siguen protegidos. F3-04 se conserva por instrucción expresa; H-09 sigue siendo discrepancia documental. La base de cálculo de ingreso tardío está pendiente DD-05; no se deduce de fechas informativas. Retiro H-11 sigue sin política/contrato suficiente. | 129 unitarias y 70 IT M4, HistorialM4M5IT y seguridad APP. RF-04, RF-18..22; CU-11..13. |
| Persistencia M5–M6 | Lector protegido de intento/participación/contexto y snapshot aislado; autorización institucional conservadora. Lecturas de calificación/retroalimentación no equivalen a cálculo o publicación de resultado. No se implementó fórmula DD-03 ni escritura de nota. | IT de composición y concurrencia M5–M6, 19 IT M6. RF-12..14, RF-17; CU-16/17/19. |
| M7 | Aviso+correo Pendiente atómicos, duplicados, conflictos, leases y recuperación SMTP de prueba validados. El worker permanece deshabilitado; éxito de encolado no implica entrega al buzón. No se mandaron correos reales ni se inventó evento de retiro. | 25 unitarias y 27 IT M7, integración docente APP. RF-21; CU-11/12. |

No se encontraron nuevos defectos funcionales reproducibles en estos bloques que autoricen cambiar políticas o exponer capacidades deshabilitadas. La regresión completa no sustituye ratificación institucional ni acredita un simulador productivo terminado.

## Cambios de esta sesión y estado integrable

Solo estos archivos Backend se añadieron/modificaron en esta sesión:

- m4/src/test/java/com/prisaly/m4/infrastructure/PostgreSqlDockerSmokeTest.java
- docs/CIERRE-NOCTURNO-BACKEND-20261108.md (este informe)

El diff nocturno independiente excluye los cambios M01/M03 preexistentes, que permanecen disponibles por separado. git diff --check aprobado; las historias y hashes preexistentes se conservaron.

Puede integrarse tras revisión: M01-01 y pruebas M03 ya presentes, workflow CI Backend previamente preparado, ajuste de la prueba a 16.15 y evidencias de regresión. La revisión debe identificar el candidato como HEAD más diff local, no atribuir al SHA base cambios que aún no están comprometidos.

No debe integrarse como habilitación productiva: DD-02/identidad estudiantil, permisos M3 todavía no ratificados, publicación/PATCH/ETag institucional, D07/POST de M5, elegibilidad/tiempo/ingreso tardío, retiro de estudiantes, fórmula M6, liberación de resultados o worker SMTP. No se encontró OpenAPI operativo completo en los repositorios locales para completar por inferencia el recorrido Frontend. Son dependencias pendientes y no se modificaron.

La autorización nocturna permite trabajo técnico, pero no sustituye esas ratificaciones ni autoriza publicación, datos reales o cambios de BD-005.2. No quedó una solicitud de permiso pendiente para las tareas técnicas completadas.
