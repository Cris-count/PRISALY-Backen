# PRISALY — Misión 01: cierre técnico M4–M5

Fecha: 2026-11-08 (America/Bogota).

## Alcance y estado Git

Backend: rama `trabajo/consolidacion-inicial-20261108`, HEAD inicial `62080fc20ebb2c9b3cd57cd4eb82963c2d3b6231`. Frontend: `trabajo/consolidacion-sim01-20261108`, HEAD `8b940a52fa70dd67e35d249873c0019ae9209385`. Ambos estaban limpios al empezar. No se cambió de rama, no hubo push, PR, merge, reescritura ni cambios de autoría. La corrección queda local y sin commit: HEAD final igual al inicial; el parche no está representado por ese SHA.

## Comparación histórica

Se inspeccionó `6eb33ef1d0387d79caa0b0d3d8b2d0af32f146e8` (autor histórico nicocesp). Ese commit retira el puente duplicado de app, refuerza el proveedor único y ajusta sus pruebas y documentación. No es ancestro directo del HEAD integrado. La evolución `2692ceb579851dff885d9e8ffffb7fe10a8eebb6` (autora histórica Ingrid), que sí es ancestro, recuperó selectivamente el refuerzo.

`git diff 6eb33ef HEAD` es vacío para el proveedor `ParticipacionesM4DesdeM5`, sus dos suites (unitaria e IT) y la configuración duplicada eliminada de app. El puerto `ParticipacionesPort` solo difiere en su comentario descriptivo. No hay funcionalidad perdida del proveedor que recuperar. No procede fusionar/cherry-pick completo de la rama antigua ni restaurar sus informes históricos.

Se conservan: transacción activa de escritura obligatoria; consulta de todas las convocatorias, incluidas retiradas; consulta mediante HistorialParticipacionPort; FK Intento→Participacion cubre ambos tipos de historial; rechazo de datos incompletos; fallos técnicos convertidos en M4-PROVEEDOR-NO-DISPONIBLE conservando causa; nunca se interpreta un fallo como ausencia de historial. M4 mantiene el bloqueo de sesión y adapta la frontera HTTP a 503. Con historial impide edición/reasignación (409). La dependencia Maven sigue M5→M4, sin ciclo.

## Funcionalidad real

| Área | Implementado | Límite real |
|---|---|---|
| Historial M4–M5 | Proveedor único, contrato M5, protección de edición/reasignación | Pruebas de composición deben corresponder al parche actual |
| Contexto/titularidad | ProgramacionParticipacionPort y adaptador JDBC M4; identidad M1 y proxy protector M5 | No implica habilitar identidad estudiantil productiva |
| Inicio/reanudación | Primer intento interno; reanudación conserva id, respuestas, inicio y límite; repetición rechazada | Producción ensambla ElegibilidadNoRatificada y LimiteTemporalNoRatificado; primer inicio sigue bloqueado |
| Concurrencia de inicio | Reserva M4 en la misma transacción, READ COMMITTED; bloqueos Sesion→Convocatoria→Participacion→Intento; revalidación de asignación | Ratificación arquitectónica D-06 no se deduce de pasar pruebas |
| Respuestas | Registro, actualización provisional y guardado CAS con revisión esperada; obsoleta→409; misma opción también avanza revisión CAS | No se cambia ninguna política de edición; pertenencia de pregunta/opción/caso respaldada por FK/triggers |
| Persistencia | JPA/JDBC; transacción MANDATORY; bloqueo pesimista; reloj PostgreSQL; flush y rollback; lectura coherente | No se auditaron bases compartidas ni historiales Flyway productivos |
| Finalización | Estado terminal, motivo y fin persistidos; rechaza segunda finalización y nuevas respuestas; serializa con guardados | Idempotencia durable D07 ausente; POST preparatorio sin registrar; vencimiento académico no definido |
| HTTP | GET de intento y PUT de respuesta registrados en IntentosEstudianteM5Controlador | IntentosM5Controlador (POST inicio/finalización) no es bean MVC; mantener así |
| Frontend SIM-01 | Narrativa/demo y puerto de participación aislado | ParticipacionPendienteGateway sigue sin adaptador HTTP real; no se conectó ni modificó |

Varios comentarios e informes antiguos dicen “sin endpoints”, “inicio sin implementar” o “sin persistencia productiva”. Son descripciones históricas superadas por el código. No deben convertirse en una lista nueva de trabajo de implementación.

## Primer defecto reproducible y corrección concreta

M01-01: `RegistrarRespuestaServicio.registrar` y `ActualizarRespuestaServicio.actualizar` guardaban el agregado y construían la salida con `ResumenIntento.de`, que deja `tiempos=Optional.empty`. El mismo intento consultado mediante el repositorio conserva inicio/límite/fin. Por ello el resultado de escribir y consultar no cumplía el mismo contrato de resumen; un consumidor que exige tiempos, como el mapper de IntentosM5Dto, no puede usar ese resumen. El PUT actual devuelve RespuestaGuardada y no atraviesa estas dos salidas: no se afirma una caída HTTP productiva por este defecto.

Reproducción aislada: repositorio con intento existente y tiempos persistidos; registrar una respuesta o actualizar una existente; comprobar tiempos del resultado. Antes del cambio: 2 pruebas, 2 fallos (se obtuvo Optional.empty). Corrección aplicada: ambas operaciones invocan `repositorio.resumir(referencia, intento)` después de guardar, como consultar/finalizar. Sin modificar dominio, políticas, SQL, esquema, permisos ni endpoints.

Archivos del parche:

- m5/src/main/java/com/prisaly/m5/application/servicio/RegistrarRespuestaServicio.java
- m5/src/main/java/com/prisaly/m5/application/servicio/ActualizarRespuestaServicio.java
- m5/src/test/java/com/prisaly/m5/application/servicio/RespuestasTiemposPersistidosTest.java (nuevo; dos regresiones)
- m5/src/test/java/com/prisaly/m5/infrastructure/persistencia/RepositorioIntentosJpaIT.java (dos escenarios existentes ahora comparan la salida de escritura con una consulta real y exigen tiempos)

La corrección cierra una inconsistencia funcional interna; no completa por sí sola el flujo académico estudiantil.

## Ejecución y límites

Todos los comandos usan Maven Wrapper existente, Java 21, modo offline y `-Dmaven.repo.local=%USERPROFILE%/.m2/repository`. No se descargaron dependencias ni se modificó la caché para reparar avisos. El intento inicial sin ruta explícita de caché no resolvía el parent; la ejecución restringida con ruta explícita falló al leer un JAR. Al ejecutar fuera del entorno restringido, la compilación y las regresiones pudieron ejecutarse. Los avisos de un POM Maven Resolver local no se interpretan como defectos de PRISALY.

Evidencias: carpeta hermana EVIDENCIAS-CONSOLIDACION-INICIAL-20261108, archivos mision01-*.log. La evidencia previa de 801 pruebas es histórica; abajo se registra la ejecución de esta misión por separado.

### Unitarias/MVC actuales

Comando: `.\mvnw.cmd -o -B -Dmaven.repo.local=%USERPROFILE%/.m2/repository -pl app -am test`.

Resultado: BUILD SUCCESS, 803 pruebas, 0 fallos, 0 errores, 0 omitidas. Desglose: M1 38; M3 229; M4 119; M6 52; M5 293; M7 25; app 47. Incluye 2 regresiones nuevas aprobadas. Log: mision01-unitarias-despues.log; resumen: mision01-unitarias-resumen.json.

Reproducción antes del parche: selección `-Dtest=RespuestasTiemposPersistidosTest -Dsurefire.failIfNoSpecifiedTests=false -pl m5 -am test`, 2/2 fallidas por tiempos ausentes, no por infraestructura. Log: mision01-regresion-antes.log.

### PostgreSQL y composición actual

Comando principal: `.\mvnw.cmd -o -B -Dmaven.repo.local=%USERPROFILE%/.m2/repository -pl app -am -Pdocker-it -Dtest=RespuestasTiemposPersistidosTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ParticipacionesM4DesdeM5IT,InicioIntentoIT,RepositorioIntentosJpaIT,HistorialM4M5IT,ProgramacionParticipacionPostgreSqlIT,ConcurrenciaLectorM5M6ReservaD06IT -Dfailsafe.failIfNoSpecifiedTests=false verify`.

BUILD SUCCESS. Resultados ejecutados: ParticipacionesM4DesdeM5IT 8/8; InicioIntentoIT 15/15; RepositorioIntentosJpaIT 28/28; ConcurrenciaLectorM5M6ReservaD06IT 3/3; HistorialM4M5IT (app real) 23/23. Total: 77 IT aprobadas, sin fallos ni errores. Las 2 regresiones unitarias también se repitieron y pasaron. Log: mision01-integracion.log. Este comando inicialmente omitió los 8 casos de ProgramacionParticipacionPostgreSqlIT por faltar m4.baseline.migrations; se ejecutan aparte abajo (la ejecución habilitada descubre 22 casos).

Las suites arrancan PostgreSQLContainer postgres:16.15 con conexión efímera y cierre automático. Los TRUNCATE/clean y las migraciones de los fixtures se limitaron a esos contenedores. No se usó compose, conexión externa, base compartida ni configuración estudiantil productiva. HistorialM4M5IT confirma proveedor único, composición de app, reservas D-06, dos inicios concurrentes sin duplicados, rollback sin huérfanos, espera/timeout y recuperación, edición contra inicio, historial retirado y error SQL→503 sin falsa ausencia.

Comando complementario M4: `.\mvnw.cmd -o -B -Dmaven.repo.local=%USERPROFILE%/.m2/repository -pl m4 -am -Pdocker-it -Dtest=M4CoordinacionInicioTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ProgramacionParticipacionPostgreSqlIT -Dfailsafe.failIfNoSpecifiedTests=false -Dm4.baseline.migrations=%USERPROFILE%/Desktop/PRISALY-ENTREGA/PRISALY-Backen/app/src/main/resources/db/migration verify`.

Resultado: BUILD SUCCESS, ProgramacionParticipacionPostgreSqlIT 22/22, 0 fallos/errores/omitidas; M4CoordinacionInicioTest 4/4 repetidas y aprobadas. Log: mision01-integracion-m4.log. Total final de IT distintas: **99 aprobadas** (22 M4 + 54 M5 + 23 app). La omisión inicial de 8 quedó resuelta; no se cuentan omitidas como aprobadas ni se suman repeticiones como casos nuevos. No se ejecutó la totalidad de IT de otros módulos.

## Dictamen y siguiente cierre

El proveedor histórico está recuperado y validado: no queda trabajo de integración de 6eb33ef. Se cerró M01-01 con dos líneas productivas, dos regresiones unitarias y validación PostgreSQL de las dos salidas. `git diff --check` aprobado. Los SHA256 de los cuatro archivos del parche se guardaron en mision01-parche-sha256.json para identificar el candidato sin crear un commit.

Sigue faltando una implementación durable de idempotencia D07 para publicar los POST con las garantías previstas; requiere conciliar el contrato pendiente antes de implementarla. Elegibilidad, límite/vencimiento, repetición y acceso estudiantil productivo dependen de decisiones DD vigentes. No se presentan como defectos ni se modifican. El frontend todavía necesita un adaptador real cuando el flujo quede autorizado; SIM-01 y su SHA se conservan intactos.

HEAD inicial y final Backend: `62080fc20ebb2c9b3cd57cd4eb82963c2d3b6231`; parche local sin commit. Frontend inicial/final: `8b940a52fa70dd67e35d249873c0019ae9209385`, árbol limpio. El informe es el quinto archivo agregado/modificado en Backend, además de los cuatro archivos del parche. Sin publicación ni operaciones sobre develop.

