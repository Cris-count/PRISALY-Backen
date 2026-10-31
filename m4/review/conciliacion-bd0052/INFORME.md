# M4 — Conciliación con BD-005.2
Fecha: 31/10/2026. Rama: feature/m4-programacion-convocatorias. Base revisada: 315746e. PR #2 en borrador; no merge autorizado.

## Alcance e inventario
Se corrige M4 existente: dominio, State, JPA, repositorios, servicio transaccional, DTO HTTP, autoconfiguración, empaquetado y pruebas. No se modifica código de M1–M3/M5–M7 ni app, V001–V009 o el contenido de V010. Los informes anteriores describen sus respectivas instantáneas; no certifican esta corrección.

Dependencias: IdentityAccessPort de M1, CasoAcademicoPort de M4 y metadatos/triggers oficiales de Caso, ParticipacionesPort y NotificacionPort. ProgramacionParticipacionPort, ProgramacionAutenticadaPort y ContextoProgramacion conservan su firma. Los dos informes locales previos no versionados se preservan sin incorporarlos automáticamente.

## Defectos y correcciones
| Antes | Corrección | Evidencia |
|---|---|---|
| Escrituras en sesiones_academicas/convocatorias; lecturas públicas oficiales | Toda escritura JPA usa public."Sesion"/"Convocatoria" | SesionAcademicaPostgreSqlIT: escritura, lectura y contexto M5 del mismo registro |
| V010 automática crea 23 tablas | Fuente intacto, recurso archivado fuera de db/migration; guardia rechaza tablas paralelas | M4MigrationResourcesTest y Bd0052BaselineIT |
| Cinco estados no compatibles | Tres estados exactos y patrón State | SesionAcademicaDomainTest, prueba real de fechas de transición |
| LocalDateTime y versión inventada | Instant, duración explícita, sin @Version ni actualizadoEn | ddl-auto=validate contra nueve migraciones |
| Reasignación eludía F3-03 destino | Prevalidación bloqueante y triggers V008/V009; comprobación posterior | Reasignación, Borrador y rollback reales |
| Duplicación sin índice oficial de sesión | Advisory lock de transacción caso/docente, consulta excluyendo propio ID | Diez solicitudes simultáneas: una creación y nueve conflictos |
| Actualizaciones concurrentes sin versión oficial | FOR UPDATE sobre Sesion para mutaciones | Carrera convocatoria/inicio y rollback |
| Formato HTTP exclusivo | {code,message}, mismo formato estructural de M1/M5 | M4HttpErrorsTest, POST y conflicto HTTP con PostgreSQL |

## Matriz completa de Sesion
Fuente: V001; restricciones y triggers posteriores V005–V009. Cada fila representa un atributo oficial, sin columnas nuevas.
| Columna exacta | Tipo / nulabilidad | Decisión Java y uso |
|---|---|---|
| idSesion | UUID PK | UUID id |
| idCaso | UUID NOT NULL, FK Caso | UUID casoId, reasignación protegida |
| idDocenteProgramador | UUID NOT NULL, FK Usuario | UUID docenteId; identidad M1 |
| nombreEspacioAcademico | varchar(150) NOT NULL | titulo, validación máximo 150 |
| inicioPrevisto | timestamptz NOT NULL | Instant fechaInicio |
| finPrevisto | timestamptz NOT NULL | Instant fechaFin; mayor que inicio |
| fechaInicioReal | timestamptz nullable | Instant; inicio de transición EnCurso |
| fechaFinReal | timestamptz nullable | Instant; transición Finalizado |
| tiempoMaximoParticipacionSegundos | integer NOT NULL, >0 | int requerido al crear; no se deriva del rango |
| lugarFisico | varchar(200) nullable | conservado en dominio/mapeador; sin edición HTTP nueva |
| mensajePersonalizado | text nullable | String; getDescripcion interno conservado; alias de entrada descripcion |
| codigoAutorizacionHash | varchar(255) nullable | conservado al actualizar; no se expone HTTP |
| estado | varchar(20) NOT NULL, CHECK catálogo | converter exacto SinIniciar/EnCurso/Finalizado |
| fechaCreacion | timestamptz NOT NULL | Instant creadoEn, no actualizable |

No existe columna version ni actualizadoEn. No existe unicidad oficial para caso/docente/fechas: se mantiene la regla de aplicación mediante lock, sin inventar índice ni migración. No se garantiza detección de edición obsoleta del navegador por falta de token de versión.

## Matriz completa de Convocatoria
| Columna exacta | Tipo / nulabilidad | Decisión |
|---|---|---|
| idConvocatoria | UUID PK | UUID id |
| idSesion | UUID NOT NULL, FK Sesion | UUID sesionId |
| idEstudiante | UUID NOT NULL, FK Estudiante | UUID estudianteId |
| fechaConvocatoria | timestamptz NOT NULL | Instant convocadoEn |
| estadoAutorizacion | varchar(25) NOT NULL | String, Autorizado como default oficial V001; no equivale a permiso de intento |
| fechaRetiro | timestamptz nullable | Instant; se conserva, sin operación de retiro inventada |

Unicidad oficial uq_convocatoria_idx1: sesión/estudiante. Se elimina confirmada como dato persistido: confirmar() rechaza operación no definida. El catálogo completo de autorización/repetición requiere ratificación académica.

Relaciones: Caso → Sesion → Convocatoria → Participacion → Intento. M4 no escribe Participacion/Intento. M5 resuelve convocatoria desde participación persistida; no debe autorizar con un ID libre del cliente. V005/V006 impiden cambiar caso desde la primera convocatoria y protegen orígenes de participaciones.

## State y operaciones conservadas
SinIniciar permite editar programación y asignar convocatorias antes de iniciar. Esta asignación no autoriza a participar. iniciar produce EnCurso y fechaInicioReal; cerrar desde EnCurso produce Finalizado y fechaFinReal. Finalizado es terminal. abrir/cancelar y confirmación de convocatoria se rechazan expresamente; no se asigna equivalencia silenciosa a ABIERTA/CANCELADA. Las clases anteriores se conservan como texto histórico en historico-state y en Git 315746e.

Las fechas reales de transición usan Instant.now del servidor. observadoEn del contexto usa clock_timestamp PostgreSQL. La autoridad temporal académica y la relación entre ventana prevista y real no están ratificadas: no se implementa vencimiento ni repetición en M4.

## RF-09, F3-03 y transacciones
CasoAcademicoBd0052Adapter consulta estado/activo mediante SQL público oficial; dentro de escritura bloquea Caso FOR UPDATE. No accede a repositorios internos de M3 ni escribe fechaBloqueoAcademico. guardar usa saveAndFlush: los triggers V007–V009 comprueban Publicado/activo y establecen la marca de bloqueo académico. Después del flush M4 confirma su persistencia dentro de la misma transacción.

Crear toma advisory lock transaccional caso/docente, bloquea Caso, comprueba duplicados y hace flush. Actualizar/reasignar toman primero Sesion FOR UPDATE, luego advisory lock y Caso. Convocar/iniciar/cerrar comparten el bloqueo de Sesion. El lock advisory solo protege escritores que respetan este protocolo; SQL externo no adquiere automáticamente ese lock. Casos de cruce de reasignaciones pueden producir un deadlock de PostgreSQL: se revierte la víctima, no se reintenta ciegamente. Falta ratificar orden global con operaciones de M3/M5.

La integración SQL real con las reglas de M3 queda demostrada por triggers; no equivale a integración completa con su servicio Java. El fallback del puerto Java M3 permanece sin autorización por defecto si falta proveedor.

## V010: escenario C, decisión reversible
El archivo V010 permanece en su ruta fuente con contenido intacto. Blob Git: 18c4f98131d0caf5a0399b5139c7a3cbf37a3f09. La referencia origin/develop inspeccionada contenía V001–V009, sin V010. En el repositorio, V010 productiva estaba en M4. Se publicó en la rama M4; eso no prueba su aplicación compartida.

Al inicio no había contenedores prisaly accesibles, .env local ni configuración de una base compartida autorizada. No se pudo consultar su flyway_schema_history ni inventariar datos de las tablas propuestas. Los contenedores de pruebas son efímeros y no demuestran ausencia de V010 en entornos de compañeros.

Medida local reversible: pom de M4 excluye V010 de db/migration y la empaqueta byte a byte en db/m4-propuesta. No se ejecuta DROP, clean, traslado de datos, repair, V011 ni edición de migraciones históricas. En una base donde V010 ya fue aplicada, Flyway puede rechazar la migración ausente antes de la guardia de M4: es un bloqueo deliberado, no debe desactivarse para arrancar. La guardia adicional rechaza 23 tablas/tablas propuestas.

Antes de desplegar, Integrante 1/Dirección Técnica deben confirmar historial y datos en cada entorno usando CONSULTAS-V010.sql. Si fue aplicada, diseñar y aprobar una corrección posterior con respaldo, mapeo de datos y validación; no se propone un script irreversible sin conocer datos. Esta fase no certifica despliegue en una base compartida.

## Seguridad y límites de integración
M1: se mantiene IdentityAccessPort, identidad docente autenticada, rechazo de suplantación X-Docente-Id y propiedad del recurso. La política GESTIONAR_SESION sigue pendiente de ratificación. DD-02/StudentAccessPort conserva denegación segura.

M5: puertos públicos preservados; Optional.empty() significa ausencia/titularidad no coincidente en el puerto sin identidad, no aprobación. ConsultaProgramacionNoDisponibleException es indisponibilidad, nunca ausencia ni false. ProgramacionAutenticadaPort aplica identidad y política M1. Los contextos son hechos informativos. La ruta booleana antigua devuelve410 y el método interno también rechaza.

La suite principal de persistencia usa identidad y notificación de prueba expresamente importadas. Para historial utiliza un proveedor SOLO DE TEST que consulta Participacion/Convocatoria/Intento reales. Esto prueba la protección del servicio sobre datos reales, pero no publica un contrato M5 de historial ni su adaptador productivo. Sin ParticipacionesPort real las modificaciones sensibles devuelven503. Sin NotificacionPort real, convocar falla y revierte. No se restaura el antiguo false permisivo.

Pendientes externos: contratos productivos de historial M5 y comunicación M7; decisiones de ventana, reloj, vencimiento, reanudación y repetición; orden global de locks; traducción a503 de la excepción M4 en el borde HTTP de M5; revisión de Arquitectura y V010 compartida. M5 no se modifica en esta fase.

## Verificación y reproducción
Ejecutar desde la raíz Maven:
```powershell
.\mvnw.cmd verify -Pdocker-it "-Dm4.baseline.migrations=C:/Users/NICOLAS/OneDrive/Desktop/Prisaly/PRISALY-Backen/app/src/main/resources/db/migration"
.\m4\review\continuidad\check-package.ps1 -EvidenceDirectory m4/review/conciliacion-bd0052/evidence
```
El perfil docker-it existe en el pom. PostgreSQL es 16.15; Hibernate usa validate. Ver evidence/RESULTADOS.md para cifras finales y alcance. La cobertura antigua de estados propuestos se sustituye por reglas oficiales; no se conserva artificialmente el número151. Su evidencia y fuentes siguen en Git315746e. Las pruebas de otros módulos se ejecutan como regresión, sin editarlos.

El resultado habilita revisión técnica de esta adaptación, no declaración de M4 terminado ni merge del PR.
