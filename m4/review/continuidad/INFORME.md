# PRISALY — M4: continuidad y cierre de bloqueos

Fecha: 30 de octubre de 2026, America/Bogota.
Estado: candidato técnico. No terminado, no autorizado para integración ni despliegue.
Trabajo de esta fase exclusivamente local: sin commits, push, comentarios, solicitudes de revisión ni cambios al PR. Se respeta la instrucción posterior «aun no subas nada al git».

## 1. Cambios implementados

Se conservó el módulo, su patrón State, casos de uso, persistencia y las 66 pruebas originales. No se modificó V010 ni se importaron o alteraron archivos productivos de otros integrantes.

### Seguridad de proveedores

- IdentidadStubAdapter, CasoAcademicoStubAdapter y NotificacionStubAdapter se trasladaron íntegramente a src/test y perdieron @Component. Solo TestM4Application los registra mediante @Import explícito. Ningún perfil de producción puede cargar esas clases desde el JAR productivo.
- M4ProviderAutoConfiguration se registra en META-INF/spring mediante AutoConfiguration.imports. Se evalúa después de los beans de aplicación y configura respuestas seguras únicamente si falta el puerto correspondiente, mediante ConditionalOnMissingBean.
- Sin proveedor M1, tienePermiso devuelve false: crear/modificar operaciones protegidas rechaza con M4-SIN-PERMISO (HTTP 403).
- Sin M3, tanto consultar publicación como solicitar bloqueo lanzan M4-PROVEEDOR-NO-DISPONIBLE. Nunca se considera publicado un caso desconocido.
- Sin M5, consultar historial lanza M4-PROVEEDOR-NO-DISPONIBLE. No se convierte la falta de información en false.
- Sin M7, emitir convocatoria no simula envío: la operación falla y el servicio transaccional debe revertirla. La entrega real y su atomicidad siguen pendientes de integración.
- El manejador REST traduce M4-PROVEEDOR-NO-DISPONIBLE a HTTP 503.
- La configuración no expone una propiedad que habilite dobles permisivos en el artefacto de producción. Para integrar debe existir un bean proveedor del puerto; no se ha implementado todavía un proveedor real de M1/M3/M5 en esta rama.

### Empaquetado de dependencias

La inspección detectó que el empaquetado incremental de app conservaba un JAR M4 antiguo, con stubs, pese a que el módulo nuevo ya no los incluía. Se configuró maven-jar-plugin.forceCreation=true únicamente en app/pom.xml para recrear el JAR antes del repackage de Spring Boot. Este cambio en app corresponde a la seguridad del paquete M4 y no altera módulos ajenos.

check-package.ps1 verifica SHA256 idéntico entre el JAR M4 local y el anidado, ausencia de stubs y TestM4Application, registro de configuración segura y recurso V010 único. Un BUILD SUCCESS sin comprobar el contenido no fue aceptado como evidencia suficiente.

### Protección de historial

SesionRepositoryAdapter ya no devuelve false fijo. Delega en ParticipacionesPort.tieneParticipacionesOIntentos(UUID).

ParticipacionesPort es un puerto de salida definido por M4, pendiente de enlazar con un contrato público aprobado de M5; NO se presenta como integración M5 terminada. Su contrato exige incluir participaciones e intentos y coordinar concurrencia con las escrituras M5.

Actualizar programación ya verificaba historial; ahora cambiarCaso también lo verifica antes de modificar la sesión. Una señal positiva produce M4-SESION-CON-PARTICIPACIONES; sin proveedor no se permite modificar por desconocimiento.

Se añadieron pruebas SQL de conservación de caso/título cuando el doble M5 señala historial. No crean Participacion/Intento reales y no resuelven la carrera de altas M5 frente a actualizaciones M4. Esa demostración requiere el contrato y bloqueo compartidos.

## 2. Línea base encontrada y conciliación

Se actualizaron las referencias remotas mediante fetch de lectura. develop ahora está en a42c738027f86915a1284905df24e6e4c28422b4 y contiene V001–V009, compose.yaml y docs/BD-005.2-REFERENCE.md. No se hizo merge ni checkout de esos archivos sobre producción.

El documento de referencia remite al diagrama aprobado; el PDF/DBML original y la confirmación formal de vigencia no se obtuvieron. Las nueve migraciones sí se inspeccionaron y copiaron a un snapshot de pruebas en m4/target/bd0052-baseline. Cada copia se verificó contra el blob Git original; baseline-manifest.txt conserva la referencia.

### Sesiones

| Aspecto | Implementación M4/V010 | Línea base V001–V009 | Decisión pendiente |
| --- | --- | --- | --- |
| Tabla | sesiones_academicas | "Sesion" | Usar entidad aprobada; no mantener tablas paralelas |
| Identidad | id | "idSesion" | Ajustar mapeo JPA y consultas |
| Caso/docente | caso_id/docente_id sin FK externas | "idCaso"→"Caso"; "idDocenteProgramador"→"Usuario" | Conservar integridad compartida |
| Nombre | titulo varchar(200) | "nombreEspacioAcademico" varchar(150) | Acordar API, semántica y longitud |
| Fechas previstas | fecha_inicio/fecha_fin TIMESTAMP; LocalDateTime | "inicioPrevisto"/"finPrevisto" timestamptz | Acordar Instant/OffsetDateTime y zona horaria, sin conversiones implícitas |
| Fechas reales | No implementadas | "fechaInicioReal"/"fechaFinReal" | Incorporar trazabilidad aprobada |
| Tiempo máximo | No implementado | "tiempoMaximoParticipacionSegundos" NOT NULL y >0 | Requisito obligatorio para crear sesión oficial |
| Otros campos | descripcion varchar(1000) | "lugarFisico", "mensajePersonalizado", "codigoAutorizacionHash" | No equiparar descripción y mensaje sin acuerdo |
| Estados | PROGRAMADA, ABIERTA, EN_CURSO, CERRADA, CANCELADA | SinIniciar, EnCurso, Finalizado | No hay equivalencia uno a uno; requiere decisión académica |
| Auditoría/versión | creado_en, actualizado_en, version | "fechaCreacion"; sin version | Acordar concurrencia; no inventar columna oficial |
| Duplicados | Único caso/docente/inicio/fin | No se encontró el mismo índice en V001–V009 | Ratificar regla e implementación |
| RF-09 | Consulta de puerto, antes stub | V009 sobre "Sesion", bloquea "Caso" FOR UPDATE y exige Publicado + activo | La tabla propuesta no activa esa protección |
| F3-03 | Solicitud por puerto, antes Set en memoria | V007/V008 bloquean contenido al insertar/reasignar "Sesion" | Coordinar un único mecanismo transaccional |

### Convocatorias e historial

| Aspecto | M4/V010 | Línea base | Decisión pendiente |
| --- | --- | --- | --- |
| Tabla | convocatorias | "Convocatoria" | Unificar mapeo |
| Identificadores | id, sesion_id, estudiante_id | "idConvocatoria", "idSesion", "idEstudiante" | FK a "Sesion" y "Estudiante" oficiales |
| Fecha | convocado_en TIMESTAMP | "fechaConvocatoria" timestamptz | Acordar tipo temporal |
| Autorización | confirmada boolean | "estadoAutorizacion" varchar(25), "fechaRetiro" | No existe equivalencia booleana demostrada |
| Unicidad | sesión/estudiante | sesión/estudiante | Regla compatible en concepto, nombres y FK distintos |
| Historial | Puerto M4; sin proveedor real | "Participacion"→"Convocatoria"→"Sesion"; "Intento"→"Participacion" | Consulta pública M5 y coordinación de locks |
| Reasignación | Guardia de historial añadida | V005/V006 impiden cambiar caso desde que hay convocatoria, y protegen relaciones de participación | Respetar regla oficial, más conservadora que esperar intentos |

No basta con renombrar dos tablas: deben resolverse campos obligatorios, estados, tiempos y mecanismos de integridad. No se cambió V010: su blob sigue siendo 18c4f98131d0caf5a0399b5139c7a3cbf37a3f09, idéntico al HEAD inicial. Sigue pendiente conocer si fue aplicada en una base compartida y acordar su tratamiento con el Integrante 1.

## 3. Integración real, simulada y ausente

| Área | Evidencia | Alcance y limitación |
| --- | --- | --- |
| M4 persistencia | Spring/JPA/Flyway/PostgreSQL real | Esquema propuesto V010, no esquema conciliado |
| M4 seguridad sin proveedores | Contextos Spring de varios perfiles y servicio real con repositorios Mockito | Denegación segura y ausencia de escrituras; no autentica usuarios reales |
| M1 real | IdentityAccessPort y servicios encontrados en develop | No se conectaron a M4 ni se ejecutó integración M1–M4 |
| M3 simulado | Dobles importados solo en pruebas M4 | RF-09 y solicitud F3-03 del servicio siguen usando sustitutos |
| M3 disponible remoto | Rama 856e0d9: repositorio público findById(String), lectura JPA UUID inmutable, servicio de dominio | No hay integración productiva M4 con lectura/bloqueo persistido de M3 en esta rama |
| Línea base SQL | Bd0052BaselineIT usa migraciones compartidas sin modificar | Comprueba triggers reales, pero no llama al servicio M3 ni a M4 |
| M5 simulado | Doble de historial + PostgreSQL M4 | Verifica guardias M4, no historial real M5 |
| M5 remoto | Rama 8c03043: fachada de intentos; repositorio sin proveedor productivo | No se encontró consulta pública de historial por sesión ni implementación PostgreSQL |
| M7 | Doble solo en pruebas; fallo seguro fuera de ellas | No se demuestra entrega real |

Riesgo adicional para integrar M1: el controlador M4 recibe X-Docente-Id. Debe vincularse al principal autenticado, no confiar en un UUID suministrado por el cliente. Esta fase no implementa el contrato de autenticación ni certifica autorización de consultas HTTP.

## 4. Pruebas y evidencias

Las 66 originales se conservan. Se añadieron diez ejecuciones de seguridad (incluyen cinco perfiles), dos escenarios de protección de historial con M5 simulado y cuatro diagnósticos de línea base SQL.

Resultado final: BUILD SUCCESS, 82 pruebas, cero fallos, cero errores y cero omitidas.

| Ejecución | Pruebas | Fallos/errores/omitidas |
| --- | ---: | --- |
| Surefire: 48 originales de dominio/servicio + 1 smoke Docker + 10 seguridad | 59 | 0/0/0 |
| Failsafe M4: 17 originales + 2 historial M5 simulado | 19 | 0/0/0 |
| Failsafe línea base SQL: RF-09, F3-03, rollback y diagnóstico V010 | 4 | 0/0/0 |
| Total | 82 | 0/0/0 |

Ver resultados definitivos en evidence/regression-summary.txt y XML evidence/surefire-reports y evidence/failsafe-reports. Los reportes adjuntos excluyen propiedades del proceso; la configuración JDBC registrada no incluye contraseña.

PostgreSQL 16.15 efímero ejecutó V001–V009 correctamente. Se comprobaron RF-09 (Borrador rechazado con SQLSTATE 23514 y restricción oficial), F3-03 (marca persistida y edición de contenido rechazada), y rollback de sesión y marca mediante conexión independiente. Ninguna de estas cuatro pruebas llama a los servicios de M3 o M5.

El diagnóstico reprodujo el bloqueo: 21 tablas con V001–V009, 23 al añadir V010; la tabla propuesta sesiones_academicas aceptó un Borrador por SQL sin activar los triggers oficiales. No es una corrección de esa incompatibilidad.

El paquete app del checkout M4 terminó con BUILD SUCCESS. Se inspeccionó su JAR y el JAR M4 anidado: cero StubAdapter/TestM4Application, configuración segura registrada y exactamente una V010. Este paquete todavía no incorpora el develop nuevo ni certifica su compatibilidad. Evidencia: evidence/package-check.txt.

El diagnóstico de incompatibilidad está diseñado para reproducir el problema, no para declararlo resuelto. Que esa prueba pase significa que se confirmó la coexistencia incorrecta de las tablas propuestas y aprobadas.

Incidencias de ejecución: clean de M4 falló al eliminar directorios generados bajo Windows/OneDrive; verify recompiló. La comprobación del JAR determina si sobrevivió alguna clase de prueba. El primer intento de descargar postgres:16.15 falló por proxy Docker; se reintentó desde la caché. En una ejecución intermedia se corrigieron dos aserciones nuevas para leer el nombre de restricción desde el campo estructurado de PostgreSQL, no desde su texto de error. No se cambió SQL productivo para hacerlas pasar.

### Reproducción

Desde la raíz del backend, PowerShell:

```powershell
& ./m4/review/continuidad/prepare-baseline.ps1
$snapshotM4 = (Resolve-Path ./m4/target/bd0052-baseline).Path.Replace('\', '/')
./mvnw.cmd -pl m4 -am verify -Pdocker-it "-Dm4.baseline.migrations=$snapshotM4"
./mvnw.cmd -pl app -am package -DskipTests
& ./m4/review/continuidad/check-package.ps1
```

Sin la propiedad m4.baseline.migrations, Bd0052BaselineIT se omite expresamente. No interpretar una ejecución sin snapshot como validación BD-005.2.

## 5. Bloqueos y responsables

| Bloqueo | Estado / siguiente evidencia |
| --- | --- |
| Stubs permisivos productivos | Corregido localmente; verificar y revisar cambios antes de publicar |
| BD-005.2 | Migraciones recuperadas; incompatibilidades concretas identificadas. Falta diagrama original y ratificación/mapeo aprobado |
| V010 aplicada y numeración | Sin confirmación del Integrante 1; no se altera migración |
| M3 real | Acordar contrato UUID/Publicado+activo y única frontera de escritura F3-03, transacciones y concurrencia |
| M5 real | Contrato público de historial y proveedor persistente aún no disponibles; ausencia bloquea modificaciones |
| PostgreSQL oficial | Se usa imagen compartida en contenedor desechable; no se usa base/volumen oficial |
| Integración M1–M3–M4–M5 | No demostrada; no sustituir por resultados SQL aislados |
| Coordinación y revisión | Borradores locales en SOLICITUDES_PENDIENTES.md; NO enviados por instrucción del usuario |
| PR #2 | Última lectura: abierto, no mergeado, draft=false; debe volver a borrador cuando se autoricen cambios remotos |

## 6. Git y estado del entregable

Rama local: feature/m4-programacion-convocatorias. HEAD permanece en ddfbad7513d703e9eb060700bd2ffb500a67cf2f. No se añadieron commits ni archivos al índice y no se hizo push.

Commits anteriores, sin cambios en esta fase: a1564a7, d7c187f y ddfbad7. El PR #2 no contiene las correcciones locales de continuidad. Los informes de m4/review anteriores se conservan como evidencia histórica, no como estado actualizado de esta fase.

M4 permanece candidato técnico con bloqueos. No se declara terminado ni listo para merge o despliegue.
