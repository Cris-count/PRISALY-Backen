# Estabilización M4 — informe y conciliación

> Resolución posterior de Arquitectura: la implementación M5 mediante SesionRepository está reconocida; no se autorizan cambios adicionales de código ni mecanismos duplicados hasta conciliar. La sección 11 prevalece sobre la propuesta de cierre D-06 de la sección 10. Se conservan sus pruebas como evidencia del candidato 513c444, sin declararlas aprobación final de Arquitectura.

> Corte vigente: 02/11/2026, base develop b3d77d8 más el incremento D-06 de la sección 10. Regresión completa: 839 aprobadas, M4=182, M5=283, app=40, cero fallos/errores/omitidas. Las secciones 1–9 son antecedentes; el bloqueo de arranque de 1960eff y la propuesta pendiente quedan sustituidos por los resultados de la sección 10. Los archivos evidence contienen ahora esta última ejecución.

01/11/2026. Auditoría de referencia ee77a57969ad35e5842c0f95a6a7142192ac269c. Base real inspeccionada antes de modificar código: develop19cf52bc4f84f07b03f8414d03924483ea324993. Rama: feature/m4-estabilizacion. No se modifica directamente develop ni se realiza merge unilateral.

## 1. Conciliación previa

| Comprobación | Resultado y evidencia |
|---|---|
| JPA/BD-005.2 | Sesion14 columnas y Convocatoria6 coinciden con V001: UUID, timestamptz→Instant, tamaños, nulidad y estados oficiales. Sin Version ni columnas inventadas. Restricciones y FK conservadas V002–V009. Matriz completa en ../conciliacion-bd0052/INFORME.md. |
| RF-09 | Caso Publicado y activo, consulta bajo FOR UPDATE en escritura; V009 vuelve a validar al persistir Sesion. El adaptador alternativo Java omitía activo: corregido. |
| F3-03 | V007/V008 asignan la marca en inserción/reasignación. M4 la confirma, no la escribe. La confirmación ahora exige escritura y controla errores SQL. |
| Contexto público | ProgramacionParticipacionPort/ProgramacionAutenticadaPort y ContextoProgramacion conservan firmas y records. Son hechos persistidos, no autorización de inicio. observadoEn procede de PostgreSQL. |
| Historial | Único proveedor productivo ParticipacionesM4DesdeM5. El puente duplicado de app ya fue retirado en19cf52b; no se recrea. SesionRepositoryAdapter añade guardia y frontera503 al consumirlo. |
| M1/M7 | Identidad docente y permiso exacto desde M1. DD-02 mantiene denegación estudiantil. M7 registra aviso/cola dentro de la transacción M4; SMTP opcional no equivale a entrega académica completa. |
| V010 | Originada en a1564a7. Blob18c4f98131d0caf5a0399b5139c7a3cbf37a3f09, intacto. Excluida del descubrimiento y archivada. Aplicación en bases compartidas no acreditada. Sin repair/clean compartido. |
| RF-18/F3-04 | Se conserva duración positiva independiente del estimado. F3-04 admite inferior/igual/superior y V007 no requiere migración. No se introduce máximo≤estimado. Texto original RF-18 por conciliar. |

## 2. Matriz de contratos M3–M4–M5

| Proveedor → consumidor | Contrato | Responsabilidad / error / transacción |
|---|---|---|
| M3/BD → M4 | CasoAcademicoPort: estaPublicado(UUID), solicitarBloqueoAcademico(UUID) | Solo metadatos y confirmación F3-03. Publicado+activo; indisponibilidad503; misma TX de programación. El adaptador Java no pretende confirmar un bloqueo atómico que su contrato no ofrece. |
| M4 → M5 | ProgramacionParticipacionPort.consultarSesion(UUID,UUID), consultarConvocatoria(UUID,UUID) | Optional<ContextoProgramacion>; UUID estudiante confiable del servidor. Vacío=ausente/ajeno; error=ConsultaProgramacionNoDisponibleException503. Instantánea sin reserva de elegibilidad. |
| M4/M1 → consumidor estudiantil | ProgramacionAutenticadaPort.consultarSesion(UUID), consultarConvocatoria(UUID) | Identidad obtenida exclusivamente de StudentAccessPort. Sin identidad real, denegación segura. |
| M5 → M4 | ParticipacionesPort.tieneParticipacionesOIntentos(UUID) | Convocatorias por puerto M4, participaciones por contrato público M5. M4 mantiene Sesion bloqueada y exige TX de escritura; fallo503, nunca false por indisponibilidad. |
| M4 → M7 | NotificacionPort.notificarConvocatoria(UUID,UUID) | Solicitud atómica de aviso/cola. FK y unicidad de Convocatoria se validan con flush antes de llamar a M7. Rollback conjunto. |

## 3. Respuesta y propuesta para M5

Se recibió la solicitud de activar el lector en app, publicar listado propio, definir elegibilidad y acordar bloqueo. Es una solicitud de conciliación, no aprobación de reglas ni de un escritor de Participacion.

Propuesta: M5 sería el único escritor de Participacion durante el primer inicio autorizado, junto con Intento y en una sola transacción. M4 conserva Sesion/Convocatoria. Datos: identidad confiable M1, convocatoria persistida, contexto M4 y políticas aprobadas. La restricción existente uq_participacion_idconvocatoria evita duplicados; deben acordarse la idempotencia/respuesta ante repetición y el rollback de ambos registros. No se crea Participacion desde M4.

En un primer inicio, un UUID de convocatoria candidato no concede acceso: el servidor debe comprobar identidad/titularidad. Para un intento existente, M5 resuelve Intento→Participacion→Convocatoria desde persistencia.

Propuestas Java no implementadas: listarConvocatoriasEstudianteActual() y bloquearConvocatoriaDelEstudianteActual(UUID), obteniendo estudiante desde M1. El segundo requiere TX de escritura MANDATORY y devuelve hechos, no permiso. Paginación, firma final y errores por ratificar. No se añaden rutas.

Protocolo propuesto: Sesion→Convocatoria→Participacion→Intento, sosteniendo los bloqueos hasta commit; volver a comprobar condiciones después de bloquear. El futuro inicio debe compartir Sesion FOR UPDATE con edición/convocatoria/cambio de estado M4. Coordinar retirada/cierre y evitar orden inverso Intento→Sesion. No se declara resuelta esa carrera sin el inicio real M5.

Activación: Integración puede proponer app con --prisaly.m4.consulta-bd0052.enabled=true. No se altera aquí la configuración productiva solicitada por M5 sin ratificación del ensamblado; activar lectura no autoriza académicamente. Firmas y protocolo ampliados en ../conciliacion-bd0052/CONTRATOS-Y-ENDPOINTS.md.

## 4. H04/H06/H08/H09/H16

La tabla suministrada por el usuario es una reconstrucción temática, no la matriz literal certificada. El chat compartido enumera los códigos; los adjuntos originales no son visibles. Los siguientes estados son del incremento técnico y no certifican cierre formal de la auditoría.

| Hallazgo / tema reconstruido | Resultado del incremento | Pendiente |
|---|---|---|
| H04: convocatoria/participación | M4 conserva sus tablas; convocatoria válida/concurrente no crea Participacion. Se documenta propuesta de escritor único M5 y restricción oficial. | Ratificar creación durante primer inicio, idempotencia y operación transaccional M5. |
| H06: tiempo de sesiones | 15 casos con fixtures de reloj fijo para tres estados y cinco fronteras, sin mutación/creación por consultar contexto. | DD-01/DD-05, autoridad temporal académica, vencimiento, reanudación y cierre. No scheduler ni políticas nuevas. |
| H08: contratos | Firmas públicas conservadas; mapeo oficial, Publicado+activo en ambos adaptadores, contexto y proveedor real verificados. | Ratificar listado propio, puerto de bloqueo y responsabilidades compartidas. |
| H09: dependencias | Guardia de escritura, errores controlados503, rollback, FK/unicidad antes de M7, proveedor único y ausencia segura verificados. | Activación controlada del lector en app y protocolo del futuro inicio. |
| H16: máximo/estimado | 30/60/120 segundos frente a estimado60 pasan en PostgreSQL. Se conserva F3-04. | Conciliar texto original RF-18 y criterio de cierre de auditoría; no imponer máximo≤estimado. |

Fuente consultada: https://chatgpt.com/share/6ae803d4-0034-83e9-8b64-5c607121a290. La reconstrucción enviada en este chat se usa como guía, no como transcripción certificada.

## 5. Clases y servicios modificados

- CasoAcademicoM3Adapter: exige caso activo además de Publicado y UUID coincidente.
- CasoAcademicoBd0052Adapter: adapta fallo SQL a503 y exige escritura al confirmar F3-03.
- SesionRepositoryAdapter: exige escritura al consultar historial; traduce fallo del proveedor a503 con causa interna y mensaje genérico.
- ConvocatoriaRepositoryAdapter: saveAndFlush antes de notificación para validar FK/unicidad en la frontera M4.

SesionAcademicaService, State, puertos/records públicos, tablas, columnas, estados y rutas productivas se conservan. Solo se adapta una prueba de app al503 de M4, comprobando también la causa original M5. Sin cambios en M1/M3/M5/M7 ni migraciones.

## 6. Verificación y límites

**684 pruebas aprobadas**, cero fallos, errores u omitidas. M4=178 (114 Surefire y64 Failsafe). Ver evidence/RESULTADO.md, conteo.md y suites.json. El total XML coincide con el resumen Maven; no se reutiliza evidencia antigua.

State: SesionAcademicaDomainTest cubre SinIniciar→EnCurso→Finalizado, rechazo de cierre sin inicio, reapertura, edición y convocatorias fuera del estado permitido. Las15 pruebas temporales controlan datos/fronteras con Clock.fixed; NO inyectan un reloj académico en producción ni validan un mecanismo de expiración inexistente. observadoEn sigue siendo PostgreSQL.

Concurrencia nueva: seis solicitudes simultáneas de la misma convocatoria producen un éxito y cinco rechazos de duplicado, con una sola fila y cero participaciones. Se conservan las pruebas de programación concurrente y cambio de estado/convocatoria. Los fallos de SQL de caso/historial se provocan solo en contenedores efímeros y se revierten.

El primer verify falló por Docker Desktop apagado, antes de M4. Se inició Docker y se repitió toda la ejecución: BUILD SUCCESS. Las38 pruebas focalizadas de adaptadores/HTTP también pasaron; están incluidas en la regresión y no se suman dos veces.

No se habilita identidad estudiantil, inicio, repetición ni evaluación. La integridad del futuro inicio según contrato ratificado no puede demostrarse aún: solo FK/UNIQUE y consultas existentes con fixtures. M4 continúa como candidato técnico, no módulo terminado.

## 7. Entrega Git

Rama feature/m4-estabilizacion desde19cf52b; commit y SHA publicados se comunican con el PR de entrega hacia develop. PR en borrador, sin merge. La evidencia corresponde a esa base más este incremento. No se modifica develop directamente.

## 8. Verificación posterior a integración — 02/11/2026

### Versión y alcance

- Commit auditado, sin cambios de código: `1960eff2028af6e40849e44733afe2986bb27ea4` (develop local).
- Se actualizaron las referencias con `git fetch origin`. `origin/develop` avanzó a `b9583b1365be836e6418190195f8737a3c49b9f4`; esa versión NO se incorpora ni queda certificada por esta ejecución. Contiene una corrección posterior del ensamblado de emisión DD-02 en M7.
- No se realizan merges, commits, push, cambios funcionales ni modificaciones de migraciones. Solo se actualiza documentación de M4.
- Java 21, Maven Wrapper, Docker Desktop 29.8.2 y PostgreSQL 16.15 en contenedores efímeros; la prueba smoke de M4 utiliza postgres:16-alpine. Ninguna base compartida se utiliza.

### Ejecuciones y resultados originales

Primera ejecución, terminada a las 12:41:21 de Colombia, BUILD SUCCESS:

```powershell
.\mvnw.cmd -B -pl m4 -am verify -Pdocker-it '-Dm4.baseline.migrations=C:/Users/NICOLAS/OneDrive/Desktop/Prisaly/PRISALY-Backen/app/src/main/resources/db/migration'
```

| Alcance | Surefire | Failsafe | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|---:|
| M1, dependencia de M4 | 17 | 6 | 0 | 0 | 0 |
| M3, dependencia de M4 | 123 | 77 | 0 | 0 | 0 |
| M4 | 114 | 64 | 0 | 0 | 0 |
| Total de esta ejecución | 254 | 147 | 0 | 0 | 0 |

M4: **178/178 aprobadas**. Surefire incluye la prueba Docker smoke; no todas sus 114 ejecuciones son unitarias puras. Las tres suites condicionadas por `m4.baseline.migrations` se ejecutaron: Bd0052BaselineIT (4), M4M1ContratosPostgreSqlIT (6) y ProgramacionParticipacionPostgreSqlIT (22). Las otras 32 IT corresponden a SesionAcademicaPostgreSqlIT. No se suman nuevamente las suites condicionadas al total de 178.

La prueba de diagnóstico de V010 aplica deliberadamente su contenido histórico exclusivamente dentro de su contenedor descartable; no modifica el archivo ni constituye auditoría de bases compartidas. Los clean/rollback de fixtures también quedan confinados a esas bases efímeras.

Segunda ejecución, terminada a las 12:42:31 de Colombia, BUILD FAILURE:

```powershell
.\mvnw.cmd -B -pl app -am verify -Pdocker-it '-Dtest=__SinSuitesUnitarias__' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=HistorialM4M5IT,ParticipacionesPortWiringIT,DocenteConvocatoriaM1M7IT,NotificacionPortWiringIT' '-Dfailsafe.failIfNoSpecifiedTests=false' '-Dm4.baseline.migrations=C:/Users/NICOLAS/OneDrive/Desktop/Prisaly/PRISALY-Backen/app/src/main/resources/db/migration'
```

Esta ejecución selecciona solo el ensamblado indicado; no pretende repetir todas las pruebas de los módulos.

| Suite de app | Pruebas registradas | Fallos de aserción | Errores | Omitidas |
|---|---:|---:|---:|---:|
| HistorialM4M5IT | 10 | 0 | 10 | 0 |
| ParticipacionesPortWiringIT | 1 | 0 | 1 | 0 |
| DocenteConvocatoriaM1M7IT | 5 | 0 | 5 | 0 |
| NotificacionPortWiringIT | 1 | 0 | 1 | 0 |
| Total | 17 | 0 | 17 | 0 |

Los 17 errores impiden ejecutar las aserciones funcionales: el contexto Spring no arranca. Las repeticiones abortadas por el umbral de contexto se registran como errores, no como 17 defectos independientes. Resultado conjunto de ambas ejecuciones: 401 pruebas aprobadas y 17 errores; no se declara regresión global aprobada.

Evidencia local de esta ejecución, bajo target (no versionada): `m4/target/cierre-1960eff-m4.log`, `m4/target/cierre-1960eff-app.log`, marcador `m4/target/cierre-1960eff-inicio.txt`, XML originales de Surefire/Failsafe y resumen sanitizado `m4/target/cierre-1960eff-evidence/{conteo.md,suites.json}`. El recolector filtra por hora de inicio para excluir XML históricos. Los archivos de evidence de la sección 6 conservan su alcance histórico de 684 pruebas.

### Comportamientos de M4 comprobados y límites de integración

| Área | Evidencia y conclusión |
|---|---|
| Sesiones / BD-005.2 | Mapeo de Sesion y Convocatoria oficial, mismas filas en creación/consulta/edición, fechas válidas y duración positiva; suites PostgreSQL aprobadas. |
| RF-09 / M3 | Creación con caso Publicado y activo; rechazo Borrador/inactivo; triggers y bloqueo F3-03, también rollback de programación/reasignación. Es integración SQL oficial con Caso, no certificación del servicio Java de publicación de M3. |
| State | SinIniciar → EnCurso → Finalizado; rechazos de cierre prematuro, reapertura y modificación/convocatoria fuera del estado permitido. No se añade vencimiento automático. |
| Permisos y propiedad | M4M1ContratosPostgreSqlIT verifica identidad y permisos persistidos de M1, lecturas propias/ajenas, cabecera que no suplanta y revocación. Pruebas de acceso estudiantil conservan denegación segura. |
| Convocatorias | Creación, unicidad, FK validada mediante saveAndFlush antes del aviso; ninguna Participacion creada desde M4. |
| Concurrencia local M4 | Programación: 10 solicitudes, 1 éxito y 9 duplicados esperados. Convocatoria: 6 solicitudes, 1 creación y 5 duplicados, una fila y cero participaciones. Carrera cambio de estado de sesión/convocatoria aprobada; NO equivale a primer inicio de intento M5. |
| Rollback / indisponibilidad | SQL de caso/historial produce error controlado; recuperación tras rollback, sin cambios parciales. Proveedor ausente no autoriza ni se convierte en ausencia de historial. |
| M4–M5 | Código conserva ParticipacionesPort y proveedor ParticipacionesM4DesdeM5 → HistorialParticipacionPort, sin segunda consulta de historial en M4. El lector JDBC de contexto está habilitado en application.yml. La prueba real de app no puede confirmar el ensamblado por el defecto de arranque descrito abajo. |
| M4–M7 | Código conserva proveedor NotificacionM4Adapter y aviso/cola en la transacción de convocatoria. Las pruebas aisladas de M4 usan dobles de identidad/notificación: no demuestran entrega SMTP. La nueva prueba de proveedor real en app queda bloqueada al arrancar. |

### Defecto confirmado: falta proveedor productivo de autorización M3

`m3/.../CasoPublicationAdapter.java`, registrado con `@Repository`, exige `CaseAuthorizationPort` en su constructor (líneas 26–33). En este commit no existe un bean productivo de ese contrato en el ensamblado revisado. Las cuatro suites de app reproducen `NoSuchBeanDefinitionException: No qualifying bean of type 'com.prisaly.m3.application.port.CaseAuthorizationPort'` al crear `casoPublicationAdapter`.

Impacto: bloqueo del contexto completo que impide certificar M3–M4–M5–M7 sobre 1960eff. Responsable de conciliación: M3, M1 y ensamblado app. La corrección debe conservar denegación segura y contar con autorización real; no se introduce stub permisivo ni se modifica M3 desde esta verificación. Una vez resuelto, repetir las cuatro suites sobre el nuevo SHA. La corrección posterior observada de M7 no se presenta como solución de este defecto M3.

### D-06 — bloqueo coordinado M4–M5 (pendiente)

M4 bloquea la fila Sesion (`PESSIMISTIC_WRITE`) antes de comprobar historial y editar. El inicio implementado por M5 serializa sobre Participacion y después Estudiante, con unicidad por convocatoria y transacción atómica de Participacion/Intento. Ese protocolo no toma el mismo bloqueo de Sesion que M4.

Existe por tanto una ventana entre comprobar que no hay historial y confirmar una edición: un primer inicio concurrente podría crear historial sin exclusión mutua con esa edición. Las pruebas de duplicados M4 y de concurrencia interna M5 no cierran esta carrera compartida. El primer inicio productivo continúa denegado por `ElegibilidadNoRatificada` y los límites temporales no ratificados; habilitar el lector de contexto no lo autoriza.

Debe ratificarse un protocolo compartido, orden global de bloqueos, revalidación bajo bloqueo y tratamiento de rollback/interbloqueo. Después se necesita una prueba PostgreSQL con ambas operaciones reales simultáneas y el resultado académico acordado. En esta fase solo se documenta D-06: no se implementa puerto de bloqueo, nueva regla temporal ni política de inicio/repetición.

### Dictamen de cierre

**Regresión propia de M4 aprobada; cierre de integración no satisfecho.** No hay fallos en sus 178 pruebas, incluidas las condicionadas. El defecto de autorización M3 bloquea 17 pruebas de app y D-06 permanece abierto. No se declara M4 terminado ni se certifica el commit remoto posterior. Cambios de esta fase: documentación únicamente, sin nuevo commit ni publicación.

## 9. Propuesta conjunta D-06 — pendiente de aprobación de Arquitectura

Preparada el 02/11/2026 sobre el código local 1960eff, sin implementar el protocolo. M5 comunica aceptación de su cierre y del simulador, con 225 pruebas unitarias y 53 PostgreSQL; se registra como información del equipo, no como ejecución realizada aquí ni como evidencia de ese mismo SHA. Antes de implementar habrá que contrastar esta propuesta con el commit exacto aceptado de M5. No se transmite a terceros desde esta sesión.

### Operaciones y riesgo preciso

| Operación M4 existente | Coordinación necesaria |
|---|---|
| actualizarSesion | Cambia título, descripción y fechas previstas. Bloquea Sesion y consulta historial antes de escribir. Es la carrera principal: una primera Participacion concurrente puede aparecer después de la comprobación de historial. Se conserva la prohibición actual de editar cuando existe cualquier participación, incluso sin intento. |
| cambiarCaso | Bloquea Sesion y rechaza historial; además rechaza cualquier convocatoria. Un primer inicio válido necesita una convocatoria, por lo que la reasignación ya está impedida para esa sesión. Debe mantenerse y probarse esta protección; no se propone permitir reasignar tras convocar. |
| cambiarEstado | Cambia estado y marcas reales bajo bloqueo de Sesion. Debe serializarse con la lectura decisoria del inicio. Qué estados permiten iniciar y qué efecto tiene finalizar sobre intentos existentes son decisiones académicas pendientes, no una regla nueva de D-06. |
| convocarEstudiante | Crea la convocatoria bajo bloqueo de Sesion y encola el aviso. No invalida una participación existente; participa en el mismo orden de bloqueo. El inicio no debe utilizar una convocatoria no confirmada. |

No hay operación productiva de retirada, borrado o cancelación equivalente en M4 revisado. Si otro ensamblado introduce esos escritores, deben inventariarse antes del cierre; no se crean aquí. Tampoco se asume que un campo informativo o un estado aislado autoricen al estudiante.

### Mecanismo propuesto y responsabilidades

Se propone usar el bloqueo exclusivo de la fila oficial Sesion como punto común de serialización. M4 ya lo adquiere mediante JpaSesionRepository.bloquearPorId. M5 deberá obtenerlo mediante un nuevo contrato Java público de M4, dentro de la MISMA transacción de escritura que cree Participacion e Intento. No habrá HTTP entre módulos ni SQL de M5 contra las tablas de programación.

Firma candidata, solo documental; nombre y adopción requieren aprobación:

```java
public interface ProgramacionInicioCoordinadoPort {
    Optional<ContextoProgramacion> bloquearYConsultarConvocatoria(
        UUID convocatoriaId, UUID estudianteAutenticadoId);
}
```

- M4 implementaría la resolución de convocatoria, el bloqueo de Sesion y, si se ratifica para proteger cambios de asignación/retirada, el bloqueo de Convocatoria. No bloquea ni escribe tablas de M5 y no evalúa elegibilidad académica.
- La operación exige transacción activa de escritura y el mismo gestor/conexión del monolito (MANDATORY); rechaza ausencia o solo lectura. Mantiene los bloqueos hasta commit/rollback de M5, sin REQUIRES_NEW ni liberación al retornar del método.
- La resolución inicial de sesionId solo localiza la fila a bloquear; no autoriza. Después se bloquea Sesion, después Convocatoria y se releen asignación, titularidad y contexto con visibilidad actual. Si cambió la asociación durante la espera, abortar toda la operación con conflicto controlado; no seguir con el bloqueo de una sesión equivocada.
- Se propone READ COMMITTED y lectura fresca posterior al bloqueo, sin reutilizar una instantánea previa o entidad obsoleta en caché. Otro aislamiento requiere demostrar su equivalencia y manejar abortos de serialización; el lector consistente REQUIRES_NEW de M3 no sirve como reserva de M4.
- M5 conserva identidad desde M1, la evaluación de elegibilidad y límites mediante políticas ratificadas, escritura exclusiva de Participacion/Intento, idempotencia, unicidad y rollback. La verificación previa del proxy puede conservarse, pero no reemplaza la revalidación dentro de la transacción bloqueada.
- En el flujo actual IniciarIntentoServicio bloquea Participacion primero. No basta con añadir el nuevo puerto después: su llamada debe preceder al PRIMER bloqueo de Participacion. Evitar una ruta inversa Participacion → Sesion, incluida la detección de reanudación. El tratamiento de reanudación mantiene sus reglas y debe revisarse sin habilitarla ni cambiarla por esta propuesta.
- Orden candidato para este recorrido: Sesion → Convocatoria → Participacion → Estudiante (incluye el trigger V005) y luego el acceso/escritura de Intento. No se proclama orden global aprobado: Arquitectura debe contrastarlo con bloqueos de Caso, programación, autorizaciones de repetición, respuesta/finalización y todos los triggers V003–V009. No se tocan migraciones ni se amplía el alcance a repetición.
- Espera acotada, deadlock y fallo SQL: abortar la transacción completa, conservar causa interna y devolver error controlado sin autorizar ni dejar escrituras parciales. Presupuesto de espera, códigos HTTP y eventual reintento total acotado requieren ratificación; nunca reintentar una sentencia dentro de una transacción abortada.
- Vacío significa convocatoria inexistente o ajena sin revelar datos. Proveedor ausente, transacción inválida o fallo técnico no se convierten en vacío ni en permiso. La firma reutiliza ContextoProgramacion como hechos, no como autorización.

La reserva de Sesion solo resuelve escritores que cooperan en este protocolo. No certifica exclusión con SQL externo ni con activación/desactivación concurrente de Caso en M3; ese alcance y sus bloqueos deben revisarse separadamente. Tampoco decide vencimiento, reloj académico, repetición o cierre de intentos.

### Resultado propuesto cuando compiten

| Primer bloqueo confirmado | Resultado esperado, sujeto a ratificación |
|---|---|
| M5 bloquea Sesion y confirma primer inicio autorizado | La edición espera, relee historial tras adquirir el bloqueo y rechaza por M4-SESION-CON-PARTICIPACIONES. Participacion e Intento quedan íntegros. |
| M5 bloquea Sesion pero hace rollback | No queda escritura parcial; la edición que esperaba vuelve a comprobar todas sus precondiciones y puede continuar si siguen válidas. |
| M4 bloquea y confirma edición | M5 espera; lee la programación nueva y evalúa las políticas aprobadas sobre esos datos. No inicia con fechas, duración o estado capturados antes de esperar. Puede iniciar o rechazar según esas políticas, no por una prioridad automática del docente. |
| M4 hace rollback | M5 lee los datos confirmados anteriores y decide con las políticas vigentes. |
| Reasignación con convocatoria existente | Se rechaza por la protección vigente, independientemente del ganador. No se traslada el intento a otro caso. |
| Cambio de estado confirma antes del inicio | M5 evalúa el estado confirmado con la regla académica ratificada. Si el inicio ganó primero, D-06 solo serializa: no inventa cancelación del intento cuando después se finalice la sesión. |

Ganar significa adquirir y conservar el bloqueo hasta terminar la transacción, no llegar primero por HTTP. Un bloqueo no autoriza por sí mismo. Sin políticas aprobadas el primer inicio sigue denegado.

### Pruebas preparadas como especificación, no implementadas ni ejecutadas

Próxima ubicación propuesta: pruebas de integración de app, con PostgreSQL 16, V001–V009 sin cambios, M4 y M5 reales y sus puertos públicos. Fixtures de identidad y elegibilidad temporal solo en src/test, expresamente diferenciados de proveedores productivos. El bloqueo de arranque de la sección 8 debe resolverse antes de esa ejecución, sobre el SHA conjunto acordado.

| Caso propuesto | Orquestación y aserción |
|---|---|
| D06-01 inicio confirma antes de editar | TX A M5 toma reserva de Sesion; TX B M4 intenta edición y se comprueba que espera. A crea y confirma; B rechaza. Una Participacion/un Intento, programación anterior intacta. |
| D06-02 edición confirma antes de iniciar | TX A M4 conserva el bloqueo hasta confirmar. B M5 espera y solo evalúa el contexto actualizado; no usa el anterior. La política de fixture hace observable el cambio sin fijar una regla académica de producción. |
| D06-03 rollback de inicio | Inyectar fallo tras insertar Participacion y antes de confirmar Intento; tras rollback no queda ninguno nuevo y la edición puede completar su validación. |
| D06-04 rollback de edición | Edición no confirmada seguida de rollback; el inicio lee los valores originales confirmados, nunca los provisionales. |
| D06-05 reasignación con convocatoria | Disputar reasignación e inicio con convocatoria persistida; reasignación siempre rechazada, relación convocatoria/sesión/caso e historial intactos. |
| D06-06 cambio de estado en ambos órdenes | Verificar espera y estado fresco, sin afirmar qué estado autoriza ni cerrar automáticamente el intento. Resultado académico ligado a la decisión aprobada. |
| D06-07 inicios duplicados y sesión distinta | Dos inicios de la misma convocatoria conservan una participación y un intento; otra sesión no espera por un bloqueo global artificial. Mantener la reanudación y sus datos sin cambios. |
| D06-08 fallo seguro | Sin proveedor, sin transacción, solo lectura, identidad ajena, timeout o víctima de deadlock: sin escrituras parciales, error controlado y recuperación en una nueva transacción. |
| D06-09 nueva asignación durante localización | Si la asociación puede cambiar antes de bloquear, simular la intercalación: verificar conflicto o revalidación aprobada, nunca usar una reserva de la sesión anterior. |

Usar conexiones/transacciones realmente independientes, barreras y latches con tiempo máximo. Demostrar la espera con pg_blocking_pids/pg_locks desde una tercera conexión; no usar solo sleep ni lanzar hilos sin controlar el orden. Verificar al final desde otra conexión confirmada las filas, claves, fechas e inexistencia de huérfanos. Capturar errores inesperados, terminar ejecutores y liberar barreras en finally. No sumar pruebas diseñadas como si estuvieran aprobadas.

### Aprobaciones necesarias y entrega

Arquitectura debe ratificar: puerto/DTO y su ubicación, transacción/aislamiento y orden de bloqueos, semántica al competir, alcance de cambios de estado/reanudación, errores/esperas y matriz de pruebas. M4 implementaría únicamente su reserva/consulta; M5 integraría la llamada antes de bloquear Participacion; app ensamblaría proveedores y ejecutaría la prueba conjunta. El equipo debe fijar los SHA de M4, M5 y app antes de implementar.

Estado: propuesta lista para revisión, NO aprobación ni cierre D-06. Las demás decisiones pendientes siguen siendo requisitos independientes para el primer inicio. En esta fase no se publican contratos ni se implementa código; tampoco se crean pruebas ejecutables, se modifican migraciones o se envía la propuesta automáticamente a Arquitectura.


## 10. Cierre técnico verificable M4 / D-06 — 02/11/2026

### Referencia exacta y autorización

Se actualizó origin con fetch y se creó feature/m4-cierre-d06 desde develop **b3d77d89e324c1f61ea0364417a37bd2c7c354cc**. No se integraron cambios mediante merge local. La Dirección Técnica autorizó implementar D-06 con el orden relativo **Sesion → Convocatoria → Participacion → Intento**. Las secciones 8 y 9 conservan la verificación y propuesta anteriores, no describen el estado vigente. El SHA final del candidato se comunica con el commit y el PR; la ejecución se realizó sobre esta base más el incremento, sin cambios Java posteriores a la regresión final.

### Implementación y responsabilidades

- M4 publica ProgramacionInicioCoordinadoPort.bloquearYConsultarConvocatoria(UUID convocatoriaId, UUID estudianteAutenticadoId). Es una reserva interna Java, no un endpoint ni una autorización académica.
- ProgramacionInicioCoordinadoJdbcAdapter exige transacción de escritura MANDATORY y READ COMMITTED. Localiza la sesión por convocatoria/estudiante, bloquea Sesion y después Convocatoria con FOR UPDATE y vuelve a leer su asociación y contexto. Una asociación distinta, datos incoherentes, otro aislamiento o fallo SQL generan indisponibilidad segura. Convocatoria inexistente/ajena devuelve vacío. La lectura inicial no constituye autorización.
- Los bloqueos se conservan hasta commit/rollback de la transacción M5; no hay REQUIRES_NEW ni liberación al retornar el puerto. No se consulta ni escribe Participacion/Intento desde M4.
- M5 adapta el puerto mediante CoordinacionInicioDesdeM4, obtiene otra vez la identidad del servidor y comprueba los identificadores devueltos. FachadaParticipacionTransaccional ejecuta la reserva dentro de su transacción ANTES de delegar al caso de uso, que conserva los bloqueos de Participacion y el acceso a Intento. Sin coordinador, la fachada deniega inicio/reanudación; no hay fallback permisivo. El proxy previo se conserva y no sustituye esta revalidación transaccional.
- Se conserva el bloqueo implícito de Estudiante impuesto por V005 al insertar Intento; no se cambia ese trigger ni se amplía el protocolo a repetición. Respuesta/finalización no añaden un bloqueo inverso de Sesion. El alcance probado es el inicio/reanudación frente a operaciones cooperantes M4, no escritores SQL externos ni cambios de Caso externos.
- M4 ya bloqueaba Sesion antes de editar, cambiar estado y convocar. Si M5 confirma primero, la edición espera y rechaza por historial; si M4 confirma primero, M5 espera y observa los datos nuevos antes de decidir. Si el ganador revierte, la otra operación observa únicamente datos confirmados.
- Reasignar caso sigue prohibido desde la primera convocatoria, incluso sin participación. Se prueba además su rechazo con historial concurrente. No se introduce cancelación, retirada, vencimiento ni política de elegibilidad.
- READ COMMITTED permite que las sentencias posteriores al bloqueo vean el resultado confirmado tras esperar. Las políticas actuales de M5 siguen siendo ElegibilidadNoRatificada y LimiteTemporalNoRatificado: el primer inicio productivo continúa denegado.

### Defectos de ensamblado y cambios

La base actual incorpora las correcciones de M3/M7 posteriores a 1960eff, incluido el proveedor de autorización M3 con denegación segura. Durante la primera prueba del candidato se encontró otra causa reproducible: StudentAccessIssuanceConfig recibía dos candidatos StudentAccessDeliveryPort (studentAccessDeliveryOutboxM7 y studentAccessDeliveryM7). Se resolvió en app mediante Qualifier explícito al proveedor existente studentAccessDeliveryM7. No se modifica M7, no se duplica un proveedor ni se activa DD-02.

Producción modificada: nuevo puerto/adaptador D-06 y registro en M4ProgramacionConsultaAutoConfiguration; referencia documental en ProgramacionParticipacionPort; CoordinacionInicioDesdeM4, ConfiguracionParticipacion y FachadaParticipacionTransaccional en M5; selección de proveedor en StudentAccessIssuanceConfig de app. Sin cambios funcionales en M1/M3/M6/M7, recursos de migración, catálogos, tablas o rutas.

Pruebas nuevas: M4CoordinacionInicioTest (4), CoordinacionInicioTest de M5 (5) y 13 casos D-06 añadidos a HistorialM4M5IT (23 casos totales). ConfiguracionParticipacionTest y FachadaSinControlDePrueba adaptan sus dobles explícitos bajo src/test. Esas suites aisladas M5 no certifican por sí mismas D-06; la evidencia conjunta es la suite PostgreSQL de app.

### Regresión final y evidencia

Comando completo:

~~~powershell
.\mvnw.cmd -B verify -Pdocker-it '-Dm4.baseline.migrations=C:/Users/NICOLAS/OneDrive/Desktop/Prisaly/PRISALY-Backen/app/src/main/resources/db/migration'
~~~

BUILD SUCCESS el 02/11/2026 a las **13:05:14 de Colombia**, duración **5 min 13 s**, Java 21, Docker 29.8.2 y PostgreSQL 16.15 (smoke M4: postgres:16-alpine). Todas las bases son efímeras. El diagnóstico existente de V010 y la limpieza de fixtures solo operan en sus contenedores; los archivos históricos y bases compartidas no se modifican.

| Módulo | Surefire | Failsafe PostgreSQL | Total | Fallos/errores/omitidas |
|---|---:|---:|---:|---|
| M1 | 18 | 6 | 24 | 0/0/0 |
| M2 | 0 | 0 | 0 | Sin suites |
| M3 | 125 | 77 | 202 | 0/0/0 |
| M4 | 118 | 64 | **182** | 0/0/0 |
| M5 | 230 | 53 | **283** | 0/0/0 |
| M6 | 40 | 19 | 59 | 0/0/0 |
| M7 | 24 | 25 | 49 | 0/0/0 |
| app | 1 | 39 | **40** | 0/0/0 |
| Total | **556** | **283** | **839** | **0/0/0** |

Surefire incluye smoke Docker; no se presentan sus 556 ejecuciones como unitarias puras. Las suites M4 condicionadas se ejecutaron, sin sumarlas otra vez: Bd0052BaselineIT=4, M4M1ContratosPostgreSqlIT=6 y ProgramacionParticipacionPostgreSqlIT=22. SesionAcademicaPostgreSqlIT aporta otras 32 IT; total PostgreSQL M4=64.

El total XML filtrado por el marcador de inicio coincide exactamente con la suma de resúmenes Maven (839). No se suman las repeticiones focalizadas ni XML históricos. Evidencia sanitizada actual en evidence/RESULTADO.md, conteo.md y suites.json; logs originales locales no versionados en m4/target/cierre-d06-regresion.log y marcador cierre-d06-regresion-inicio.txt. La prueba focalizada previa de 23 IT pasó y queda incluida en la regresión, no añadida al total.

### Evidencias funcionales e integración

- RF-09/F3-03: caso Publicado y activo, rechazo Borrador/inactivo, bloqueo de contenido por triggers, rollback de programación y reasignación. M4 integra metadatos oficiales de M3 por SQL/triggers; no se certifica publicación docente autorizada por el servicio M3, cuyo contrato M1 sigue denegando de forma segura.
- State: tres estados oficiales, transiciones y restricciones conservadas. Fechas inválidas y duración no positiva rechazadas. F3-04 conserva duraciones inferiores/iguales/superiores al estimado, sin nueva restricción académica.
- M1: identidad docente persistida, permisos y propiedad; cabeceras no suplantan identidad. El acceso estudiantil productivo sigue disabled y denegado.
- M5: proveedor único de historial, participaciones con/sin intentos, convocatorias retiradas, titularidad real del contexto, indisponibilidad y rollback. Se ejecutaron las 53 IT de M5 y las 23 IT de historial/coordinación de app.
- M7: proveedor real, convocatoria y aviso/cola transaccionales, FK/unicidad antes del aviso; NotificacionPortWiringIT y DocenteConvocatoriaM1M7IT aprobadas. Worker SMTP desactivado en app; encolado no se presenta como entrega a buzón.
- D-06: inicio confirmado antes de editar y orden inverso; rollback en ambos órdenes; reasignación rechazada; cambio de estado observado tras esperar; reanudación conserva intento/inicio/límite; dos inicios no duplican; fallo real después de crear Participacion revierte ambas escrituras; timeout de fila produce indisponibilidad y recuperación; sesiones distintas no sufren un bloqueo global; transacción ausente/solo lectura y convocatoria ajena rechazadas.
- Las carreras usan conexiones/transacciones independientes, barreras y pg_blocking_pids para demostrar espera real desde una tercera conexión, con límites de espera y limpieza de ejecutores. No dependen solo de sleep. Las políticas que permiten crear intentos y la identidad estudiantil de esas pruebas son dobles explícitos; no se incorporan al contexto productivo.
- Paquete app: hashes de M4 y M5 anidados coinciden con los módulos compilados; nuevo coordinador presente. Sin stubs productivos, V010 activa ni estados alternativos. Archivo histórico V010 intacto. Evidencia en package-check.txt y m5-package-check.txt.

### Dictamen y límites pendientes

**Cierre técnico verificable del alcance M4/D-06 como candidato para revisión:** regresión sin fallos e integración conjunta demostrada. La propuesta de la sección 9 queda implementada en el alcance autorizado; aceptación final del código mediante PR hacia develop, sin merge unilateral. No equivale a declarar terminado el recorrido académico del estudiante.

Persisten DD-02/identidad productiva, elegibilidad y reglas temporales/repetición pendientes, conciliación RF-18 y auditoría de bases compartidas/V010. No se valida el simulador Angular en esta ejecución backend ni se habilita el primer inicio productivo. La protección D-06 exige que los escritores de programación utilicen el protocolo M4; no certifica SQL externo o políticas nuevas de cierre. Los tiempos de espera de la prueba no se convierten en una regla académica o configuración global de PostgreSQL.

## 11. Conciliación final solicitada por Arquitectura — sin cambios de código

Se contrastó la resolución con el candidato 513c444cd077696d5f57f914eb9575ebdab687b2 y las referencias origin actualizadas. Esta entrega registra conclusiones documentales; no implementa ni selecciona unilateralmente un protocolo definitivo.

- SesionRepository es una interfaz pública en com.prisaly.m4.application.port, propiedad de M4. No extiende JpaRepository ni devuelve entidades JPA. buscarParaActualizar(UUID) devuelve Optional<SesionAcademica>; su adaptador delega en JpaSesionRepository.bloquearPorId con PESSIMISTIC_WRITE. El repositorio JPA permanece dentro de infrastructure.persistence.
- actualizarSesion, cambiarCaso, cambiarEstado y convocarEstudiante llaman a cargarSesionParaModificar, que adquiere ese bloqueo bajo la transacción de escritura del servicio. El cambio de estado comparte por tanto la misma reserva de Sesion.
- El único flujo productivo de escritura de Convocatoria identificado es su creación desde convocarEstudiante, que bloquea Sesion. No se encontró una operación independiente de retirada/autorización. Sin embargo, guardar y los campos modificables permiten futuras actualizaciones: todos los escritores relevantes deben inventariarse y respetar el bloqueo común antes de afirmar que sobra la reserva explícita de Convocatoria.
- La dirección Maven es M5 → M4; M4 no depende de M5. Consumir el puerto de aplicación no introduce ciclos ni requiere acceso al repositorio JPA interno.
- Conforme a la resolución, SesionRepository puede conservarse. ProgramacionInicioCoordinadoPort no es una sustitución obligatoria por límites modulares. La alternativa del PR #28 no debe integrarse junto con una segunda implementación de M5. No se elimina ni se fusiona ninguna solución antes de la decisión final de Arquitectura.
- M5 debe acreditar reserva dentro de la misma transacción de persistencia, relectura/validación después de adquirirla y conservación hasta COMMIT/ROLLBACK, sin REQUIRES_NEW. La visibilidad pública de la interfaz no demuestra por sí sola esas garantías.
- Se buscaron ProtocoloD06IT/D06IT en los árboles de todas las referencias remotas disponibles sin encontrarlos. origin/feature/m5-inicio-reanudacion apunta a c99b8a28a2f27169224d85a1a9687d5088d10503. Las ocho pruebas se reconocen como información comunicada por M5, no como contenido inspeccionado o ejecutado aquí. Falta su rama/SHA para mapear cada escenario y pedir solo la cobertura que realmente falte.

Pendiente concreto: recibir el SHA de M5, revisar las ocho pruebas y su ensamblado transaccional, confirmar inventario de escritores de Convocatoria y ratificar un único contrato/implementación. Arquitectura emite después el cierre definitivo D-06. Actualización de integración del 02/11/2026: PR #28 ya fue incorporado a develop en 3984485c2271936119efcbddbbf42c24249431fd (incluye 513c444 y 6c75000), comprobado con fetch de origin. Esto acredita integración técnica, no ratificación final de Arquitectura. La alternativa M5 y sus ocho pruebas continúan pendientes de rama/SHA; no se incorpora una segunda implementación. El acceso estudiantil productivo continúa desactivado. No se repiten pruebas por este cambio exclusivamente documental; se conserva la regresión de 839 aprobadas con el alcance preciso de la sección 10.
