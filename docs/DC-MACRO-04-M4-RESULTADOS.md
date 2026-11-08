# DC-MACRO-04 — ejecución focalizada M3–M4–M5

7 de noviembre de 2026. Nicolás / M4. **Compatible condicionado por PostgreSQL no ejecutado**, sin defecto funcional M4 reproducido en la campaña disponible.

## Versión y trabajo ejecutado

Fetch y avance rápido en `feature/m4-alineado-20261104` desde 3563177 hasta **d8eb62d0e4873ea28f426ed3d59392a5cd1ae95c**, origin/develop confirmado al comenzar. Se preservaron PM4-21 e informes locales; develop local no fue modificado. PR recientes relevantes: #48 agrega lectura protegida M5→M6 consumiendo programación M4; #49 conserva denegación institucional M6; #50 agrega pruebas de revocación/confidencialidad del productor M3. Ninguno cambia fuentes M4 ni el proveedor/reserva D-06 existente.

Compilación nueva `.\mvnw.cmd -B -pl app -am -DskipTests package`: **BUILD SUCCESS**, sin ejecutar pruebas en ese comando. JAR app SHA-256 **906CD18F04A8F8B44B76ECCDFADE88555864B00AD108F2388A79CCF8131C4008**.

Campaña local nueva de servicio/contratos/contexto: **109 aprobadas, 0 fallos, 0 errores, 0 omitidas**; **77 M4 + 32 consumidores M5**. No se suman suites históricas ni totales de módulos a los mismos casos. Resumen por suite en `docs/evidencias/DC-MACRO-04/resumen-local.json`; logs locales ignorados dc-macro-04-build.log y dc-macro-04-focused.log.

```powershell
.\mvnw.cmd -B -pl app -am test '-Dtest=SesionAcademicaDomainTest,M4MigrationResourcesTest,SesionAcademicaServiceTest,ContratosM4AdaptersTest,M4ProviderSecurityTest,M4CoordinacionInicioTest,ProgramacionAutenticadaTest,ProgramacionParticipacionConfigurationTest,M4EstabilizacionAdaptersTest,ParticipacionesM4DesdeM5Test,TitularidadIntentosDesdeM4Test,TitularidadSesionDesdeM4Test,ConsultaIntentoEvaluacionDesdeM5Test' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-javaagent:C:/Users/NICOLAS/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
```

Agente Mockito suministrado solo a la invocación para evitar el bloqueo attach previo, sin cambiar pom ni seguridad de la aplicación. Las pruebas usan dobles cuando requieren JDBC/proveedores; **no son PostgreSQL, ni un backend arrancado, ni evidencia Postman**.

## Alcance acreditado y límites

| Frontera | Evidencia actual y límite |
|---|---|
| M3→M4 | ContratosM4AdaptersTest y M4EstabilizacionAdaptersTest aprobados: Publicado/activo; rechazo de Borrador, inexistente y referencia equivocada; el agregado no sustituye F3-03 atómico; fallos SQL no se transforman en permiso. El adaptador canónico consulta Caso y confirma marca del trigger. Integridad real y rollback SQL pendientes de IT. |
| M4→M5 | ProgramacionAutenticadaTest, TitularidadIntentosDesdeM4 y TitularidadSesionDesdeM4 aprobados: identidad del servidor, convocatoria propia y sesión, ausencia/ajena denegadas. ProgramacionParticipacionPort devuelve hechos, no habilitación académica. |
| PR #48 | ConsultaIntentoEvaluacionDesdeM5Test 11/11: mantiene protección M5, vínculo/contexto coincidentes, denegación institucional, duplicados/inconsistencias y error técnico; no añade repositorio M4 ni reserva alternativa. Resuelve Caso por Convocatoria→Sesion en el puerto M4. La instantánea read-only REQUIRES_NEW/REPEATABLE_READ no sustituye la reserva de escritura READ COMMITTED. |
| D-06 | M4CoordinacionInicioTest y proveedor M5 aprobados con dobles: rechazan TX ausente/read-only/aislamiento incompatible y errores de proveedor. Código mantiene Sesion→Convocatoria antes de Participacion→Intento; TransactionTemplate no absorbe fallos. Carreras y ausencia de huérfanos aún necesitan PostgreSQL. |
| Proveedores | Autoconfiguraciones/condiciones unitarias aprobadas; un implementador real por ParticipacionesPort, HistorialParticipacionPort y ProgramacionInicioCoordinadoPort. Cardinalidad del contexto productivo completo pendiente de IT app, no acreditada por simples búsquedas. |
| Recorrido HTTP | Controladores existentes de sesiones/convocatorias se conservan. No se ejecutó HTTP nuevo contra el JAR. PM4-21 sigue bloqueada hasta Participacion legítima de M5; invitación/Convocatoria sola no basta. |

Primer inicio no ratificado, DD-02/D07 y políticas pendientes siguen denegados. Inicio/finalización positivos de las IT son escenarios autorizados con fixtures/dobles de política en base efímera, no autorización productiva. El lector PR #48 tampoco habilita actor institucional M6: APP conserva denegación hasta ratificación.

## Campaña PostgreSQL: autorizada, preparada y coordinada

Docker local comprobado fuera del sandbox: pipe dockerDesktopLinuxEngine ausente. No se intentó otro ciclo de reparación de Desktop. DC-MACRO-04 ya autoriza preparación/clean **solo en Testcontainers aislados**; sustituye la antigua condición de autorización pendiente del informe DC-M4-24H-01. No autoriza operaciones en bases compartidas.

Ejecutor preparado: `m4/review/ejecutar-dc-macro-04.ps1`. Valida SHA, Docker, línea V001–V009, ausencia de cambios funcionales rastreados; deshabilita reutilización de contenedores; ejecuta selección canónica y exporta resumen de reportes nuevos. Falla si faltan suites runtime, hay omisiones o errores. No se ejecutó la parte PostgreSQL aquí; se comprobó la sintaxis PowerShell, no su éxito con Docker.

```powershell
# En el checkout identificado, con Docker operativo, Java 21 y autorización DC-MACRO-04.
powershell -NoProfile -File m4/review/ejecutar-dc-macro-04.ps1
```

Selecciona tres pruebas baseline, seis M1, 22 invocaciones de programación y la suite de persistencia/sesiones; añade historial M5, inicio, 23 casos HistorialM4M5IT, wiring M4/M5/M7 y ConsultaIntentoEvaluacionM5M6IT del PR #48. **Excluye** diagnosticoV010CreaDosTablasParalelasYEludeTriggerOficial. Las 18 omisiones conocidas eran 4+6+8 registros deshabilitados por falta de m4.baseline.migrations; al habilitar parámetros son 32 casos. La selección canónica cubre 31 y deja el diagnóstico V010 fuera explícitamente; no cambia ni borra ese test.

Escenarios de cierre: casos Borrador/inactivo/inexistente; FK estudiante inexistente; FK antes de notificar; convocatoria duplicada; carreras de programación y convocatoria/inicio; historial con/sin intento y retirado; carreras inicio/edición en ambos órdenes; dos inicios sin duplicación; rollback de sesión/marca, edición, fallo entre participación e intento sin huérfanos; timeout y recuperación; titularidad ajena; fallos de proveedor 503. Mantener los límites de las suites: M4M1ContratosPostgreSqlIT usa identidad M1 persistida pero dobles M3/M4/M7; no prueba el login estudiantil.

Coordinación efectuada por mensajes autorizados:

- Ingrid/M5 recibió SHA y selección de comandos, incluyendo lector PR #48. Respondió **ejecución no realizada por falta de acceso a Docker/checkout desde su conversación**; no se aceptaron resultados históricos como nuevos.
- Cristian/M1 recibió consulta de runner. Confirma CI estándar, sin evidencia de campaña PostgreSQL. Verificación directa del workflow: ejecuta `clean verify` sin docker-it ni m4.baseline.migrations, por tanto no cierra estas IT. Run #42396710033/job #128475530014 pasó sobre PR #50, no se atribuye como ejecución nueva de d8eb62d ni PostgreSQL.
- Solicitud de confirmación contractual a Mariana/M3 canalizada mediante Dirección Central por no tener chat propio identificado. Pendiente de respuesta verificable; no se inventa una política de habilitación adicional a Publicado/activo.

## Cierre y próxima ejecución

No se encontró defecto técnico M4 reproducible en las 109 pruebas disponibles, por lo que **no se crea PR funcional artificial**. Se preparan evidencia y ejecutor para revisión documental/técnica separada, todavía sin commit ni push. No se modifican capas, GoF, M1–M7, BD-005.2, D-06, migraciones ni decisiones académicas pendientes.

Bloqueo concreto restante: asignar un ejecutor con Docker para correr el script identificado; las solicitudes a otros chats no equivalen a una sesión de ejecución disponible. No hay resultado PostgreSQL nuevo, arranque HTTP ni participación productiva acreditados. El criterio alternativo de bloqueo explícito queda documentado, pero **no se declara cierre runtime ni despliegue aprobado**.
