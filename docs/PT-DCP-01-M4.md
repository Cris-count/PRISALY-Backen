# PLAN DE PRUEBAS DE SOFTWARE — PT-DCP-01
## Entrega Oficial de Información y Fichas Técnicas — Módulo 4: Programación Académica y Convocatorias

| Parámetro | Detalle |
| :--- | :--- |
| **Proyecto / Sistema:** | PRISALY Backend (Arquitectura Hexagonal / DDD / Spring Boot 3.4 / Java 21 / PostgreSQL 16) |
| **Módulo Auditado y Entregado:** | **Módulo 4 (M4) — Programación Académica y Convocatorias de Sesiones** |
| **Responsable Oficial (Integrante 3):** | **Nicolás Céspedes Díaz** (`nicocesp`) |
| **Rama Base:** | `develop` (al commit `a891c27` tras integración de PR #28 y PR #29) |
| **Fecha de Consolidación:** | 4 de noviembre de 2026 (Regresión de referencia: 02/11/2026 a las 13:05:14 COT) |
| **Estado del Código:** | *Working tree clean* (0 modificaciones en clases de producción o pruebas) |

---

## 1. Alcance y Delimitación Modular Estricta de M4

De estricto acuerdo con las directrices de arquitectura de PRISALY (`docs/DD02-PLAN-IMPLEMENTACION-M1-M7.md` e `INTEGRACION-M1-M7.md`) y la auditoría técnica Antigravity, la presente entrega documenta exclusivamente las responsabilidades, entidades y casos de prueba pertenecientes al **Módulo 4 (Programación Académica y Convocatorias)**. Las interacciones con otros módulos quedan delimitadas rigurosamente en sus fronteras públicas de puerto:

- **Frontera con M1 (Identidad y Acceso):** M4 consume el contrato público `IdentityAccessPort` para validar identidad docente persistida y verificar estrictamente el permiso `GESTIONAR_SESION`, garantizando coincidencia con el propietario de la sesión y rechazando suplantaciones por cabecera `X-Docente-Id`.
- **Frontera con M3 (Gestión de Casos Clínicos):** M4 interactúa con metadatos oficiales y triggers de la tabla `"Caso"` mediante `CasoAcademicoBd0052Adapter`. Exige estado `Publicado` y `activo=true` (RF-09) y confirma la marca automática de bloqueo académico `fechaBloqueoAcademico` (F3-03) en la misma transacción de creación.
- **Frontera con M7 (Comunicaciones y Notificaciones — Integrante 1):** M4 documenta de forma exclusiva su puerto de salida transaccional (`NotificacionPort`) implementado en `ConvocatoriaRepositoryAdapter`, el cual ejecuta `saveAndFlush` de la convocatoria antes de invocar a M7 para validar llaves foráneas y unicidad, manejando degradación segura a HTTP 503 ante indisponibilidad. Las suites internas de cola outbox, worker SMTP y despacho real de correos corresponden formalmente al **Integrante 1**.
- **Frontera con M5 y Coordinación D-06 (Integración M4-M5):** M4 audita y entrega como caso propio exactamente sus **4 pruebas unitarias de infraestructura** (`M4CoordinacionInicioTest`), las cuales validan exigencia de transacción activa `MANDATORY`, aislamiento `READ COMMITTED` y bloqueo ordenado `Sesion` &rarr; `Convocatoria`. La suite `app/HistorialM4M5IT` (23 pruebas en el módulo `app`) se referencia como evidencia externa de ensamble y no-regresión. Se precisa formalmente que, si bien la prueba técnica sobre el candidato M4 está aprobada en verde (cero fallos), la ratificación arquitectónica final inter-módulos permanece en estado *«Pendiente de confirmar»*.

---

## 2. Cumplimiento de Reglas Obligatorias del Docente y Resumen de Ejecución

La totalidad de las pruebas registradas han sido ejecutadas de forma real y automatizada en consola, verificadas mediante el log maestro `m4/target/cierre-d06-regresion.log` (**BUILD SUCCESS del 02/11/2026 13:05:14 COT**, duración 05:13 min, **182 pruebas propias de M4: 118 Surefire y 64 Failsafe con Testcontainers PostgreSQL 16.15**, Maven Failsafe Plugin 3.5.6).

| Regla Metodológica Docente | Estado | Evidencia de Cumplimiento en Entrega M4 |
| :--- | :---: | :--- |
| **1. No inventar resultados ni fechas** | **CUMPLE** | Trazabilidad exacta con log Maven: 182 pruebas propias M4 (118 Surefire, 64 Failsafe). Reactor total: 839 pruebas aprobadas, 0 fallos, 0 errores, 0 omitidas. |
| **2. No marcar como ejecutada una prueba propuesta** | **CUMPLE** | Acceso estudiantil (`StudentAccessPort` / DD-02) catalogado estrictamente como 'Propuesta / No ejecutada (Bloqueo DD-02)'. |
| **3. No confundir resultados de suite con individuales** | **CUMPLE** | Identificación precisa de métodos `testMethod` individuales para cada caso de prueba en las clases oficiales de M4. |
| **4. Escribir 'Pendiente de confirmar' si falta info** | **CUMPLE** | Aplicado a ejecutores futuros y al cierre institucional de la alternativa D-06 en Arquitectura. |
| **5. Indicar expresamente si no existe captura** | **CUMPLE** | Leyenda obligatoria incluida: *'No existe captura de pantalla (prueba automatizada de backend ejecutada en consola/headless por Maven)'*. |
| **6. Identificar al ejecutor real cuando se conozca** | **CUMPLE** | Nicolás Céspedes Díaz (Integrante 3) mediante Maven Surefire 3.5.6 / Failsafe 3.5.6 y Testcontainers PostgreSQL 16.15. |
| **7. Mantener resultados y restricciones BD** | **CUMPLE** | Conforme a BD-005.2: 14 cols `Sesion`, 6 cols `Convocatoria`, 3 estados oficiales (`SinIniciar`, `EnCurso`, `Finalizado`), V010 archivada fuera de Flyway. |
| **8. No modificar código ni nuevas implementaciones** | **CUMPLE** | Repositorio verificado con `working tree clean` (0 cambios en producción o pruebas durante la consolidación). |

---

## 3. Apartado 5: Objetivo de Pruebas de M4 y Casos Planeados

| Módulo | Objetivo de Pruebas | ID Caso | Prueba Automatizada Real (`Clase :: Método`) | Tipo de Prueba | Requisito / Contrato | Estado |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **M4** | **OBJ-M4-01: Persistencia oficial BD-005.2 y control de esquema Flyway**<br>Validar que JPA mapee exactamente las 14 columnas de `Sesion` y 6 de `Convocatoria` sin campos inventados (`@Version`), con `ddl-auto=validate` sobre 21 tablas oficiales en PostgreSQL 16.15 y exclusión de V010. | **CP-M4-01** | `SesionAcademicaPostgreSqlIT::esquemaHibernateYFlywaySonOficiales` | Integración PostgreSQL (Failsafe) | BD-005.2 / Flyway V001–V009 | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-02** | `SesionAcademicaPostgreSqlIT::actualizaSobreMismaFilaSinColumnaVersion` | Integración PostgreSQL (Failsafe) | BD-005.2 / Sin col version | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-03** | `Bd0052BaselineIT::diagnosticoV010CreaDosTablasParalelasYEludeTriggerOficial` | Integración PostgreSQL (Failsafe) | Control V010 / BD-005.2 | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-04** | `M4MigrationResourcesTest::propuestaV010NoSeDescubreAutomaticamenteYSeConservaIntacta` | Unitaria (Surefire) | Empaquetado Flyway / V010 | **Ejecutada — Aprobada** |
| **M4** | **OBJ-M4-02: Creación de sesiones, reglas de caso y marcas académicas (RF-09, F3-03, F3-04)**<br>Verificar que solo casos Publicados y activos se programen, activando la marca `fechaBloqueoAcademico` (F3-03) vía triggers oficiales y admitiendo duraciones flexibles. | **CP-M4-05** | `SesionAcademicaPostgreSqlIT::creaConsultaYExponeExactamenteLaMismaSesionOficial` | Integración PostgreSQL (Failsafe) | RF-09 / F3-03 / CU-04 | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-06** | `SesionAcademicaPostgreSqlIT::rf09BorradorNoPrograma` | Integración PostgreSQL (Failsafe) | RF-09 / Estado de Caso | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-07** | `SesionAcademicaPostgreSqlIT::rf09InactivoNoPrograma` | Integración PostgreSQL (Failsafe) | RF-09 / Flag activo | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-08** | `SesionAcademicaPostgreSqlIT::primeraProgramacionActivaF303YProtegeContenido` | Integración PostgreSQL (Failsafe) | F3-03 / Triggers V007–V009 | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-15** | `SesionAcademicaPostgreSqlIT::f304PermiteDuracionInferiorIgualOSuperiorAlEstimado` | Integración PostgreSQL (Failsafe) | F3-04 / RF-18 / Duración | **Ejecutada — Aprobada** |
| **M4** | **OBJ-M4-03: Ciclo de vida de la sesión y patrón GoF State**<br>Validar transiciones estrictas del catálogo oficial (`SinIniciar` &rarr; `EnCurso` &rarr; `Finalizado`), fijación de marcas reales y rechazo de apertura/cierre indebidos. | **CP-M4-11** | `SesionAcademicaPostgreSqlIT::estadosOficialesMantienenMarcasReales` | Integración PostgreSQL (Failsafe) | Patrón State / Fechas reales | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-12** | `SesionAcademicaDomainTest::stateRecorreSoloCatalogoOficial` | Unitaria (Surefire) | Patrón State en Dominio | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-13** | `SesionAcademicaDomainTest::noCierraSinIniciar` | Unitaria (Surefire) | Reglas de transición State | **Ejecutada — Aprobada** |
| **M4** | **OBJ-M4-04: Administración de convocatorias y control de unicidad**<br>Verificar asignación en `SinIniciar`, integridad de clave foránea FK Usuario/Estudiante, unicidad por sesión/estudiante y no emisión de intentos en M5. | **CP-M4-14** | `SesionAcademicaPostgreSqlIT::convocatoriaDuplicadaNoEscribeDosFilas` | Integración PostgreSQL (Failsafe) | `uq_convocatoria_idx1` | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-16** | `SesionAcademicaPostgreSqlIT::estudianteInexistenteEsRechazadoPorFK` | Integración PostgreSQL (Failsafe) | Integridad FK Estudiante | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-17** | `SesionAcademicaPostgreSqlIT::convocatoriaProtegeCasoInclusoSinParticipacion` | Integración PostgreSQL (Failsafe) | Frontera M4 / V005 | **Ejecutada — Aprobada** |
| **M4** | **OBJ-M4-05: Seguridad, permisos y autenticación docente (M1)**<br>Comprobar exigencia del permiso `GESTIONAR_SESION`, coincidencia de propietario de sesión, rechazo de suplantación y denegación segura del acceso estudiantil legado. | **CP-M4-19** | `M4M1ContratosPostgreSqlIT::lecturaPropiaYListadoUsanIdentidadYPermisoPersistidosM1` | Integración PostgreSQL (Failsafe) | IdentityAccessPort M1 | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-21** | `M4M1ContratosPostgreSqlIT::cabeceraDeOtroDocenteNoSuplantaLaIdentidadReal` | Integración PostgreSQL (Failsafe) | Seguridad / `X-Docente-Id` | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-22** | `SesionAcademicaPostgreSqlIT::docenteAjenoNoModifica` | Integración PostgreSQL (Failsafe) | Propiedad de Sesión | **Ejecutada — Aprobada** |
| **M4** | **OBJ-M4-06: Concurrencia y bloqueos transaccionales (Advisory / Pessimistic)**<br>Garantizar la serialización de creaciones simultáneas idénticas sin índices artificiales y prevenir condiciones de carrera en convocatorias simultáneas. | **CP-M4-09** | `SesionAcademicaPostgreSqlIT::concurrenciaIdenticaSeSerializaSinIndiceInventado` | Concurrencia PostgreSQL (Failsafe) | Advisory Lock caso/docente | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-08C** | `SesionAcademicaPostgreSqlIT::convocatoriasSimultaneasNoDuplicanNiCreanParticipacion` | Concurrencia PostgreSQL (Failsafe) | Lock Sesión / `uq_convocatoria` | **Ejecutada — Aprobada** |
| **M4** | **OBJ-M4-07: Integración con M5 (Historial y Coordinación D-06)**<br>Validar que historial en M5 bloquee actualizaciones M4; proveer contexto informativo persistido; y validar el puerto candidato D-06 (`Sesion` &rarr; `Convocatoria`). | **CP-M4-27** | `SesionAcademicaPostgreSqlIT::historialRealImpideModificarProgramacion` | Integración PostgreSQL (Failsafe) | `ParticipacionesPort` M5 | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-30** | `M4CoordinacionInicioTest::sinTransaccionNoConsultaNiAutoriza` | Unitaria / Contrato (Surefire) | D-06 / TX `MANDATORY` | **Técnica Aprobada / Cierre Pendiente** |
| **M4** | **OBJ-M4-08: Transaccionalidad y Frontera de Notificación hacia M7**<br>Asegurar reversión total de sesión y marcas ante fallos; validar FK en M4 antes del evento; y degradación segura a HTTP 503 sin exposición de SQL. | **CP-M4-32** | `SesionAcademicaPostgreSqlIT::rollbackRevierteSesionYMarcaEnLaMismaTransaccion` | Integración PostgreSQL (Failsafe) | Atomicidad All-or-Nothing | **Ejecutada — Aprobada** |
| **M4** | | **CP-M4-INT-07** | `SesionAcademicaPostgreSqlIT::fkDeConvocatoriaSeValidaAntesDeInvocarNotificacion` | Integración PostgreSQL (Failsafe) | `NotificacionPort` / saveAndFlush | **Ejecutada — Aprobada** |
| **M4/M1** | **OBJ-PROP: Pruebas propuestas condicionadas a decisiones institucionales** | **CP-PROP-01** | `M1StudentAccessTestPlan::S1_CredencialConvocatoriaValida` | Integración Productiva Estudiante | DD-02 / `StudentAccessPort` | **Propuesta / No ejecutada (Bloqueo DD-02)** |

---

## 4. Apartado 6: Fichas Técnicas Individuales (M4)

### Ficha Técnica: CP-M4-INT-07
- **Identificador del Caso:** `CP-M4-INT-07`
- **Nombre del Caso de Prueba:** Validación transaccional y frontera de notificación hacia M7 (saveAndFlush previo)
- **Módulo / Subsistema:** Módulo 4 (M4) — Programación académica y convocatorias (Interfaz de salida)
- **Tipo / Nivel de Prueba:** Integración con PostgreSQL 16.15 (Maven Failsafe Plugin 3.5.6)
- **Objetivo de la Prueba:** Garantizar que M4 valida las llaves foráneas de convocatoria y persiste la transacción mediante `saveAndFlush` antes de invocar el puerto de salida `NotificacionPort` hacia M7, impidiendo eventos huérfanos y manejando degradación segura HTTP 503 si el proveedor de mensajería no está disponible.
- **Requisito / Contrato Asociado:** RF-M4-NOTIF-01 / `NotificacionPort` (Frontera pública M4 &rarr; M7) / BD-005.2.
- **Precondiciones:** Sesión académica válida en estado `SinIniciar`; base de datos PostgreSQL 16.15 con esquema oficial BD-005.2; contenedor Testcontainers activo.
- **Datos de Entrada:** Sesión académica existente; intento de convocatoria con `estudianteId = UUID.randomUUID()` no registrado en tabla `"Estudiante"`; observador de notificación con contador de invocaciones.
- **Pasos de Ejecución:**
  1. Crear sesión académica oficial en base de datos.
  2. Instanciar servicio transaccional inyectando observador de notificación controlado.
  3. Ejecutar `service.convocarEstudiante(...)` con estudiante inexistente para forzar fallo de FK en PostgreSQL.
  4. Verificar que el fallo ocurra en el `saveAndFlush` antes de alcanzar el observador M7.
  5. Comprobar que el contador de llamadas hacia M7 sea exactamente cero y que no queden filas en tabla `"Convocatoria"`.
- **Resultado Esperado:** Lanzamiento de `DataIntegrityViolationException` en la frontera de M4; cero invocaciones a `NotificacionPort`; tabla Convocatoria con 0 filas; degradación segura HTTP 503 ante caída del proveedor sin fuga de SQL.
- **Resultado Obtenido:** **APROBADO.** Cero llamadas a notificación cuando falla la FK; 0 filas huérfanas en PostgreSQL; manejo de degradación validado.
- **Estado de Ejecución:** **Ejecutada y Verificada — Aprobada**
- **Ejecutor Real:** Nicolás Céspedes Díaz (Integrante 3) / Runner: Maven Failsafe 3.5.6 (JUnit 5 Jupiter)
- **Fecha y Hora de Ejecución:** 02/11/2026, 13:05:14 (Hora de Colombia, COT)
- **Evidencia Visual / Respaldo:** Reporte XML: `m4/target/failsafe-reports/TEST-com.prisaly.m4.infrastructure.SesionAcademicaPostgreSqlIT.xml`  
  Log maestro: `m4/target/cierre-d06-regresion.log`  
  *Captura de pantalla: No existe captura de pantalla (prueba automatizada de backend ejecutada en consola/headless por Maven).*
- **Delimitación Modular:** M4 documenta exclusivamente el contrato de salida y su validación transaccional previa. Las suites internas de cola outbox, worker SMTP y despacho real de correos corresponden formalmente al **Integrante 1 (M7)**.

---

### Ficha Técnica: CP-M4-08C
- **Identificador del Caso:** `CP-M4-08C`
- **Nombre del Caso de Prueba:** Control de concurrencia en convocatorias simultáneas mediante bloqueo pesimista
- **Módulo / Subsistema:** Módulo 4 (M4) — Programación académica y convocatorias
- **Tipo / Nivel de Prueba:** Prueba de Concurrencia con PostgreSQL 16.15 (Maven Failsafe Plugin 3.5.6)
- **Objetivo de la Prueba:** Garantizar que múltiples peticiones simultáneas de convocatoria sobre la misma sesión y estudiante se serialicen bajo el bloqueo pesimista `PESSIMISTIC_WRITE` (`FOR UPDATE`) de `Sesion` y la restricción `uq_convocatoria_idx1`, evitando registros duplicados o sobre-cupos y sin generar participaciones en M5.
- **Requisito / Contrato Asociado:** RN-M4-CONC-02 / Bloqueo pesimista sobre `SesionAcademicaEntity` / `uq_convocatoria_idx1` (BD-005.2).
- **Precondiciones:** Sesión creada en estado `SinIniciar`; pool multihilo sincronizado con `CountDownLatch`; PostgreSQL 16.15.
- **Datos de Entrada:** 6 hilos concurrentes intentando convocar simultáneamente al mismo estudiante (`estudianteId: UUID.randomUUID()`) a la misma sesión (`sesionId: UUID.randomUUID()`). Timeout de ejecución: 45 segundos.
- **Pasos de Ejecución:**
  1. Crear sesión y estudiante base en PostgreSQL.
  2. Inicializar 6 tareas concurrentes sincronizadas con barrera `CountDownLatch(1)`.
  3. Disparar simultáneamente los 6 hilos invocando `service.convocarEstudiante(...)`.
  4. Capturar éxitos y excepciones con código de negocio `"M4-CONVOCATORIA-DUPLICADA"`.
  5. Verificar el estado físico final de las tablas `"Convocatoria"` y `"Participacion"`.
- **Resultado Esperado:** Exactamente 1 convocatoria registrada con éxito; las restantes 5 peticiones concurrentes son rechazadas de forma controlada por duplicado; exactamente 1 fila en tabla Convocatoria y 0 en Participacion.
- **Resultado Obtenido:** **APROBADO.** 1 éxito, 5 duplicadas esperadas, 0 errores inesperados; exactamente 1 fila en Convocatoria y 0 en Participacion.
- **Estado de Ejecución:** **Ejecutada y Verificada — Aprobada**
- **Ejecutor Real:** Nicolás Céspedes Díaz (Integrante 3) / Runner: Maven Failsafe 3.5.6 (JUnit 5 Jupiter)
- **Fecha y Hora de Ejecución:** 02/11/2026, 13:05:14 (Hora de Colombia, COT)
- **Evidencia Visual / Respaldo:** Reporte XML: `m4/target/failsafe-reports/TEST-com.prisaly.m4.infrastructure.SesionAcademicaPostgreSqlIT.xml`  
  Log maestro: `m4/target/cierre-d06-regresion.log`  
  *Captura de pantalla: No existe captura de pantalla (prueba automatizada de backend ejecutada en consola/headless por Maven).*

---

### Ficha Técnica: CP-M4-09
- **Identificador del Caso:** `CP-M4-09`
- **Nombre del Caso de Prueba:** Serialización de programaciones idénticas vía PostgreSQL Advisory Lock transaccional
- **Módulo / Subsistema:** Módulo 4 (M4) — Programación académica y convocatorias
- **Tipo / Nivel de Prueba:** Prueba de Concurrencia con PostgreSQL 16.15 (Maven Failsafe Plugin 3.5.6)
- **Objetivo de la Prueba:** Demostrar que 10 peticiones concurrentes idénticas de creación de sesión (mismo docente, mismo caso y mismo intervalo temporal) se serializan limpiamente mediante un bloqueo consultivo transaccional PostgreSQL por `(idCaso, idDocente)`, evitando duplicaciones sin alterar el esquema oficial BD-005.2 con índices inventados.
- **Requisito / Contrato Asociado:** RN-M4-CONC-01 / Advisory Lock transaccional / BD-005.2 (14 columnas oficiales).
- **Precondiciones:** Docente autenticado con permisos; caso publicado y activo; pool de 10 hilos concurrentes sincronizados.
- **Datos de Entrada:** 10 tareas concurrentes con `idDocente = UUID.randomUUID()`, `idCaso = UUID.randomUUID()`, `titulo = "Espacio"`, `inicio = 2030-11-05T14:00:00Z`, `fin = 2030-11-05T15:00:00Z`, `tiempoMax = 1800`.
- **Pasos de Ejecución:**
  1. Crear caso y docente base en base de datos efímera.
  2. Configurar 10 tareas concurrentes con idénticos parámetros de programación.
  3. Liberar ejecución simultánea mediante `CountDownLatch.countDown()`.
  4. Capturar creaciones exitosas y rechazos controlados con código `"M4-SESION-DUPLICADA"`.
  5. Verificar conteo de filas en tabla `"Sesion"` y liberación de bloqueos.
- **Resultado Esperado:** Exactamente 1 sesión creada exitosamente; las 9 restantes son serializadas y rechazadas por conflicto temporal; cero deadlocks; exactamente 1 fila en tabla "Sesion".
- **Resultado Obtenido:** **APROBADO.** 1 creación exitosa, 9 duplicados esperados, cero errores inesperados; liberación limpia tras commit.
- **Estado de Ejecución:** **Ejecutada y Verificada — Aprobada**
- **Ejecutor Real:** Nicolás Céspedes Díaz (Integrante 3) / Runner: Maven Failsafe 3.5.6 (JUnit 5 Jupiter)
- **Fecha y Hora de Ejecución:** 02/11/2026, 13:05:14 (Hora de Colombia, COT)
- **Evidencia Visual / Respaldo:** Reporte XML: `m4/target/failsafe-reports/TEST-com.prisaly.m4.infrastructure.SesionAcademicaPostgreSqlIT.xml`  
  Log maestro: `m4/target/cierre-d06-regresion.log`  
  *Captura de pantalla: No existe captura de pantalla (prueba automatizada de backend ejecutada en consola/headless por Maven).*

---

### Ficha Técnica: CP-M4-15
- **Identificador del Caso:** `CP-M4-15`
- **Nombre del Caso de Prueba:** Validación de tiempo máximo de participación flexible e independiente del estimado (F3-04)
- **Módulo / Subsistema:** Módulo 4 (M4) — Programación académica y convocatorias
- **Tipo / Nivel de Prueba:** Integración con PostgreSQL 16.15 (Parameterized Test, Failsafe)
- **Objetivo de la Prueba:** Comprobar que conforme a la regla F3-04, la duración máxima de participación en segundos (`tiempoMaximoParticipacionSegundos`) debe ser estrictamente positiva pero admite valores menores (30s), iguales (60s) o mayores (120s) al tiempo estimado del caso (60s), sin imponer restricciones artificiales `duracion <= estimado`.
- **Requisito / Contrato Asociado:** F3-04 (Duración flexible de participación) / Conciliación técnica RF-18 / Dominio `SesionAcademica`.
- **Precondiciones:** Caso clínico persistido con `tiempoEstimadoSegundos = 60`; docente con permiso `GESTIONAR_SESION`.
- **Datos de Entrada:** Prueba parametrizada con valores de `tiempoMaximoParticipacionSegundos`: [30, 60, 120] frente a caso con estimado = 60s.
- **Pasos de Ejecución:**
  1. Para cada valor parametrizado (30s, 60s, 120s), invocar `service.crearSesion(...)`.
  2. Consultar la entidad persistida en PostgreSQL.
  3. Validar que el valor persistido coincida exactamente con el parámetro de entrada.
  4. Verificar que no se produzcan excepciones de validación ni se creen participaciones prematuras.
- **Resultado Esperado:** Las 3 sesiones son creadas y persistidas exitosamente con sus duraciones correspondientes; la duración asignada es independiente del tiempo estimado del caso.
- **Resultado Obtenido:** **APROBADO.** 3 ejecuciones parametrizadas exitosas en verde; persistencia de rangos flexibles verificada.
- **Estado de Ejecución:** **Ejecutada y Verificada — Aprobada**
- **Ejecutor Real:** Nicolás Céspedes Díaz (Integrante 3) / Runner: Maven Failsafe 3.5.6 (JUnit 5 Jupiter)
- **Fecha y Hora de Ejecución:** 02/11/2026, 13:05:14 (Hora de Colombia, COT)
- **Evidencia Visual / Respaldo:** Reporte XML: `m4/target/failsafe-reports/TEST-com.prisaly.m4.infrastructure.SesionAcademicaPostgreSqlIT.xml`  
  Log maestro: `m4/target/cierre-d06-regresion.log`  
  *Captura de pantalla: No existe captura de pantalla (prueba automatizada de backend ejecutada en consola/headless por Maven).*

---

### Ficha Técnica: CP-M4-30
- **Identificador del Caso:** `CP-M4-30`
- **Nombre del Caso de Prueba:** Coordinación de inicio de sesión inter-módulo (Protocolo D-06 / TX Mandatory)
- **Módulo / Subsistema:** Módulo 4 (M4) — Programación académica y convocatorias (Caso Oficial Propio de M4)
- **Tipo / Nivel de Prueba:** Unitaria de Infraestructura de Coordinación (Maven Surefire Plugin 3.5.6)
- **Objetivo de la Prueba:** Asegurar que el adaptador del puerto de coordinación inter-módulo `ProgramacionInicioCoordinadoPort` exija estrictamente una transacción activa de escritura (`MANDATORY`) y aislamiento `READ COMMITTED`, impidiendo lecturas obsoletas o consultas huérfanas sin transacción activa antes del bloqueo `Sesion` &rarr; `Convocatoria`.
- **Requisito / Contrato Asociado:** Directriz D-06 / Coordinación M4-M5 / Candidato commit `513c444`.
- **Precondiciones:** Instancia de `ProgramacionInicioCoordinadoJdbcAdapter` configurada; invocación fuera de contexto transaccional.
- **Datos de Entrada:** Invocación directa de `adapter.bloquearYConsultarConvocatoria(convocatoriaId, estudianteId)` sin abrir transacción en el hilo de ejecución.
- **Pasos de Ejecución:**
  1. Instanciar el adaptador JDBC de coordinación.
  2. Ejecutar la llamada al método del puerto sin transacción activa.
  3. Capturar la excepción esperada arrojada por la guardia transaccional.
  4. Validar que la excepción sea `IllegalStateException` con mensaje indicando la exigencia de transacción de escritura.
- **Resultado Esperado:** Rechazo inmediato por ausencia de transacción de escritura; sin ejecución de consultas SQL; causa interna preservada; denegación segura.
- **Resultado Obtenido:** **APROBADO.** Las 4 pruebas unitarias de coordinación de M4 pasan en verde (Surefire).
- **Evidencia Externa de Ensamble:** Suite `HistorialM4M5IT` (23 pruebas de integración en el módulo `app`, de las cuales 13 corresponden a carreras D-06 con barreras multihilo y `pg_blocking_pids`), aportando respaldo de ensamble y no-regresión.
- **Estatus Arquitectónico Institucional:** **Pendiente de confirmar** (La prueba técnica sobre el candidato M4 está aprobada en verde, pero la ratificación final del mecanismo único inter-módulos entre el PR #28 de M4 y la solución comunicada por M5 permanece en deliberación del equipo de Arquitectura).
- **Estado de Ejecución:** **Ejecutado Técnicamente / Cierre Institucional Pendiente de Confirmar**
- **Ejecutor Real:** Nicolás Céspedes Díaz (Integrante 3) / Runner: Maven Surefire 3.5.6 (JUnit 5 Jupiter)
- **Fecha y Hora de Ejecución:** 02/11/2026, 13:05:14 (Hora de Colombia, COT)
- **Evidencia Visual / Respaldo:** Reporte XML: `m4/target/surefire-reports/TEST-com.prisaly.m4.infrastructure.M4CoordinacionInicioTest.xml`  
  Log maestro: `m4/target/cierre-d06-regresion.log`  
  *Captura de pantalla: No existe captura de pantalla (prueba automatizada de backend ejecutada en consola/headless por Maven).*

---

### Ficha Técnica: CP-PROP-01 (Propuesta)
- **Identificador del Caso:** `CP-PROP-01`
- **Nombre del Caso de Prueba:** Emisión y validación de acceso estudiantil productivo desde convocatoria
- **Módulo / Subsistema:** Módulo 1 (M1) / Módulo 4 (M4) / Módulo 5 (M5) — Propuesta Arquitectónica
- **Tipo / Nivel de Prueba:** Integración Funcional End-to-End (Prueba Propuesta / No Ejecutada)
- **Objetivo de la Prueba:** Validar la emisión de credencial estudiantil y resolución de identidad mediante `StudentAccessPort.requireCurrentStudent()` contra la convocatoria académica, permitiendo el primer inicio de intento de participación.
- **Requisito / Contrato Asociado:** Decisión de Diseño DD-02 (Ratificación académica de identidad estudiantil) / CU-08.
- **Precondiciones:** Acta de ratificación formal DD-02 aprobada; flag `prisaly.m1.student-access.enabled=true`; sustitución controlada de `DenyingStudentAccessService` por adaptador productivo.
- **Datos de Entrada:** Token estudiantil o credencial OTP ratificada por DD-02 enviada al endpoint `POST /v1/acceso-estudiante/validar`.
- **Pasos de Ejecución:**
  1. *Pendiente de ejecutar:* Requiere aprobación arquitectónica institucional de DD-02.
  2. En el corte actual, el endpoint retorna HTTP 501 Not Implemented y el puerto ejecuta `DenyingStudentAccessService` arrojando denegación segura.
- **Resultado Esperado:** Retorno de `AuthenticatedStudent(userId, studentId)` tras validar convocatoria y vigencia.
- **Resultado Obtenido:** **No ejecutada:** Retenido deliberadamente en modo deny-safe (HTTP 501 / denegación de dominio).
- **Estado de Ejecución:** **Propuesta / No ejecutada (Bloqueo DD-02)**
- **Ejecutor Futuro:** Pendiente de confirmar (Asignación futura tras aprobación institucional)
- **Fecha y Hora de Ejecución:** Pendiente de confirmar (No ejecutada en corte 02/11/2026)
- **Evidencia Visual / Respaldo:** Documentación de bloqueo en `docs/M1-STUDENT-ACCESS-TEST-PLAN.md` y `docs/DD02-PLAN-IMPLEMENTACION-M1-M7.md`.  
  *Captura de pantalla: No existe captura (caso de prueba en estado de propuesta pendiente de liberación).*

---

## 5. Tabla de Datos de Entrada por Caso (M4)

| ID Caso | Parámetro / Campo | Tipo de Dato | Valor de Entrada Utilizado | Propósito / Restricción Validada |
| :--- | :--- | :--- | :--- | :--- |
| **CP-M4-05** | `docenteId` | UUID | `UUID.randomUUID()` (e.g. `c8f498c4-e8d1-4b13-b81b-53c829e00001`) | Identidad docente autenticada con permiso GESTIONAR_SESION |
| | `casoId` | UUID | `UUID.randomUUID()` | Caso académico existente, estado='Publicado', activo=true |
| | `titulo` | String | `"Espacio"` (150 caracteres máx.) | Nombre del espacio académico oficial |
| | `mensajePersonalizado` | String | `"Mensaje"` | Texto descriptivo / orientador de la sesión |
| | `fechaInicio / fechaFin` | Instant (ISO-8601) | `2030-11-05T14:00:00Z` / `2030-11-05T15:00:00Z` | Rango previsto válido (inicio < fin) |
| | `tiempoMaximoParticipacionSegundos` | Integer | `1800` (30 minutos) | Entero positivo (>0) exigido al crear |
| **CP-M4-06** | `casoId` | UUID | `UUID.randomUUID()` | Caso con `estado='Borrador'` |
| | Payload creación | JSON | Valores sintácticamente válidos | Provocar rechazo con código `M4-CASO-NO-PUBLICADO` |
| **CP-M4-08C** | Número de hilos concurrentes | Integer | `6` hilos | Pool de ejecución en paralelo |
| | Parámetros | UUIDs | Misma `sesionId` y mismo `estudianteId` | Verificar unicidad en concurrencia real y bloqueo pesimista |
| | Timeout de espera | TimeUnit | `45 SECONDS` | Límite máximo de espera por hilo |
| **CP-M4-09** | Número de hilos concurrentes | Integer | `10` hilos | Pool de ejecución en paralelo |
| | Sincronizador | CountDownLatch | Latch inicializado en 1 | Liberación simultánea de todas las peticiones |
| | Payload de creación | CrearSesionRequest | Mismo caso UUID, mismo docente UUID, mismo rango | Verificar adquisición de Advisory Lock por (idCaso, idDocente) |
| **CP-M4-15** | `tiempoEstimadoSegundos` (Caso) | Integer | `60` | Estimación de referencia en entidad Caso |
| | `tiempoMaximoParticipacionSegundos` [1, 2, 3] | Integer (Parametrizado) | `30`, `60`, `120` | Comprobar duraciones menor, igual y mayor al estimado (F3-04) |
| | `estado` | String | `"Publicado"` | Caso activo programable |
| **CP-M4-30** | Aislamiento transaccional | String | `READ COMMITTED` (PostgreSQL default) | Aislamiento exigido por el protocolo D-06 |
| | Propagación transaccional | Propagation | `MANDATORY` | Exigir transacción activa de escritura; fallo ante falta de TX |
| **CP-M4-INT-07** | `sesionId` | UUID | UUID de sesión en M4 | Contexto de sesión |
| | `estudianteId` (inválido) | UUID | `UUID.randomUUID()` (no persistido en Estudiante) | Forzar fallo de FK en saveAndFlush previo a la notificación M7 |

---

## 6. Reportes, Logs y Declaración sobre Capturas

### 6.1 Inventario Oficial de las 16 Suites de Pruebas de M4 (182 Pruebas)

| Módulo | Runner Maven | Clase / Suite de Pruebas | Pruebas Ejecutadas | Fallos | Errores | Omitidas | Resultado |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **M4** | Surefire | `com.prisaly.m4.application.AutorizacionM4ServiceTest` | 8 | 0 | 0 | 0 | **PASSED** |
| **M4** | Surefire | `com.prisaly.m4.application.SesionAcademicaServiceTest` | 17 | 0 | 0 | 0 | **PASSED** |
| **M4** | Surefire | `com.prisaly.m4.domain.SesionAcademicaDomainTest` | 20 | 0 | 0 | 0 | **PASSED** |
| **M4** | Surefire | `com.prisaly.m4.infrastructure.ContratosM4AdaptersTest` | 11 | 0 | 0 | 0 | **PASSED** |
| **M4** | Surefire | `com.prisaly.m4.infrastructure.M4CoordinacionInicioTest` | 4 | 0 | 0 | 0 | **PASSED** |
| **M4** | Surefire | `com.prisaly.m4.infrastructure.M4EstabilizacionAdaptersTest` | 6 | 0 | 0 | 0 | **PASSED** |
| **M4** | Surefire | `com.prisaly.m4.infrastructure.M4MigrationResourcesTest` | 1 | 0 | 0 | 0 | **PASSED** |
| **M4** | Surefire | `com.prisaly.m4.infrastructure.M4ProviderSecurityTest` | 10 | 0 | 0 | 0 | **PASSED** |
| **M4** | Surefire | `com.prisaly.m4.infrastructure.PostgreSqlDockerSmokeTest` | 1 | 0 | 0 | 0 | **PASSED** |
| **M4** | Surefire | `com.prisaly.m4.infrastructure.ProgramacionAutenticadaTest` | 4 | 0 | 0 | 0 | **PASSED** |
| **M4** | Surefire | `com.prisaly.m4.infrastructure.ProgramacionParticipacionConfigurationTest` | 4 | 0 | 0 | 0 | **PASSED** |
| **M4** | Surefire | `com.prisaly.m4.presentation.M4HttpErrorsTest` | 32 | 0 | 0 | 0 | **PASSED** |
| **M4** | **Subtotal Surefire** | *(12 suites unitarias, de servicio y adaptadores)* | **118** | **0** | **0** | **0** | **PASSED** |
| **M4** | Failsafe | `com.prisaly.m4.infrastructure.Bd0052BaselineIT` | 4 | 0 | 0 | 0 | **PASSED** |
| **M4** | Failsafe | `com.prisaly.m4.infrastructure.M4M1ContratosPostgreSqlIT` | 6 | 0 | 0 | 0 | **PASSED** |
| **M4** | Failsafe | `com.prisaly.m4.infrastructure.ProgramacionParticipacionPostgreSqlIT` | 22 | 0 | 0 | 0 | **PASSED** |
| **M4** | Failsafe | `com.prisaly.m4.infrastructure.SesionAcademicaPostgreSqlIT` | 32 | 0 | 0 | 0 | **PASSED** |
| **M4** | **Subtotal Failsafe** | *(4 suites de integración con PostgreSQL 16.15)* | **64** | **0** | **0** | **0** | **PASSED** |
| **M4** | **TOTAL M4** | **16 suites de prueba de Programación y Convocatorias** | **182** | **0** | **0** | **0** | **BUILD SUCCESS** |

### 6.2 Archivos de Reportes, Logs y Declaración sobre Capturas

- **Comando de Ejecución Oficial (PowerShell desde raíz `PRISALY-Backen`):**
  ```powershell
  $baseline = (Resolve-Path 'app/src/main/resources/db/migration').Path
  .\mvnw.cmd -B verify -Pdocker-it "-Dm4.baseline.migrations=$baseline"
  ```
- **Log Maestro de Regresión:** `PRISALY-Backen/m4/target/cierre-d06-regresion.log` (Reactor completo: 839 pruebas aprobadas en 5 min 13 s. BUILD SUCCESS del 02/11/2026 13:05:14 COT).
- **Resumen Verificable M4:** `PRISALY-Backen/m4/review/revision-m4-m5/evidence/RESULTADO.md`.
- **Inventario JSON de Suites:** `PRISALY-Backen/m4/review/revision-m4-m5/evidence/suites.json` (Trazabilidad detallada por suite y método).
- **Reportes XML Surefire M4:** `PRISALY-Backen/m4/target/surefire-reports/TEST-com.prisaly.m4.*.xml` (12 archivos XML con los 118 resultados unitarios).
- **Reportes XML Failsafe M4:** `PRISALY-Backen/m4/target/failsafe-reports/TEST-com.prisaly.m4.*.xml` (4 archivos XML con los 64 resultados de integración PostgreSQL).
- **Declaración Obligatoria sobre Capturas de Pantalla:**  
  > **No existen capturas de pantalla de interfaz de usuario.** Al tratarse exclusivamente de pruebas automatizadas de backend (unitarias, integración transaccional con PostgreSQL Testcontainers, concurrencia multihilo y seguridad de servicios/controladores REST), la ejecución se realiza enteramente en consola *headless* bajo el reactor de Maven. Los respaldos oficiales e inmutables son los reportes XML de JUnit y los logs de compilación registrados en las rutas citadas.

---

## 7. Certificación de Entrega y Cierre Técnico

Con los ajustes aplicados, la entrega del **Integrante 3 (Nicolás Céspedes Díaz)** queda delimitada al 100% en el ámbito funcional de M4, con **182 pruebas propias** trazables en consola de compilación, cero alteraciones sobre el código base del repositorio (`working tree clean`) y una nítida separación de fronteras que garantiza una consolidación fluida y libre de duplicidades con M7 (Integrante 1) y M5 (Integrante 4).
