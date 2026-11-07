# DC-MACRO-02-M5M6 — lectura protegida de intentos

Base: `origin/develop`, `0bf802edc72d0ef0d49cc0db6a0a5ce443d1a7b1`.
Rama: `feature/m5-m6-lectura-protegida`. Worktree independiente; checkout previo conservado.

## Componente entregado

`FabricaConsultaIntentoEvaluacion.para(SolicitudEvaluacion)` produce una implementación
de `ConsultaIntentoEvaluacionPort.findById(UUID)`. Cada llamada exige el mismo intento
de la solicitud y vuelve a invocar `AutorizacionEvaluacionPort.verificarAcceso(solicitud)`.
La rúbrica procede de la solicitud completa; nunca se inventa para satisfacer la firma.
No hay autorización almacenada en un ThreadLocal, ni un lector global inyectable.

Después de autorizar, invoca `AccesoProtegidoParticipacion.consultarIntento`, conservando
`ControlAccesoIntentos`, identidad del servidor y titularidad M4. Resuelve el vínculo
Intento–Participacion–Convocatoria mediante una extensión del repositorio existente
`RepositorioIntentos.vinculo`, implementada en `RepositorioIntentosJpa`. No crea otro
repositorio. El caso se obtiene de `ProgramacionParticipacionPort.consultarConvocatoria`,
verificando convocatoria y estudiante. No consulta por SQL tablas académicas ni de M4.

La lectura usa una transacción nueva, read-only y REPEATABLE_READ. El resumen, vínculo y
consulta M4 participan en una instantánea estable cuando comparten el transaction manager
y datasource institucionales. APP debe conservar esa composición; no se certifica una
instantánea distribuida entre diferentes bases. No se escriben respuestas, notas o estados.

La salida contiene solo UUID, enum y records inmutables con lista defensiva. Verifica
la referencia devuelta, vínculo obligatorio, correspondencia de contexto y preguntas
duplicadas. `PrepararContextoEvaluacionService` mantiene las comprobaciones académicas
pregunta–escenario–caso y opción–pregunta sobre el contexto protegido M3. No se duplica
esa consulta ni se certifica integridad académica únicamente con los datos de M5.

## Resultados

| Situación | Comportamiento |
| --- | --- |
| UUID nulo | Argumento inválido, sin lecturas |
| UUID distinto de la solicitud ligada | Denegación M6, sin lecturas |
| Autorizador devuelve DENEGADO, NO_DISPONIBLE, null o lanza excepción | Denegación M6 sin causa sensible y sin lecturas |
| Intento inexistente tras control M5 | Optional.empty |
| M5 rechaza identidad o titularidad | Denegación M6 sin causa sensible; no consulta vínculo ni contexto |
| Intento EnCurso | Snapshot EN_CURSO; M6 conserva INTENTO_NO_FINALIZADO al preparar |
| Vínculo ausente o contexto que no coincide, respuestas duplicadas | Excepción; no devuelve snapshot parcial |
| UUID persistido inválido o proveedor devuelve null | Excepción; nunca ausencia ni éxito |
| Fallo técnico de persistencia o M4 | Se propaga; transacción revierte. M6 lo traduce a FALLO_CONSULTA_INTENTO |

## Spring y bloqueos confirmados

`EnsamblajeConsultaIntentoEvaluacion` es un ensamblaje opt-in mediante `@Import`, sin
`@Configuration` ni autodetección. Publica solo la fábrica y exige los seis proveedores
reales, incluido `AutorizacionEvaluacionPort`. Sin ellos, falla el contexto importado.
No se importa en APP en este PR: no se modifica M1, M3, M4 o APP.

**Cristian/APP:** importar el ensamblaje solo tras ratificar el actor y permiso institucional;
aportar `AutorizacionEvaluacionPort` real, construir el lector con la solicitud completa
y usarlo dentro de la preparación protegida M6. El contrato M5 existente exige estudiante
titular; no autoriza lectura docente o de servicio. Con DD-02 pendiente y
`DenyingStudentAccessService`, la integración positiva permanece denegada. Habilitar una
lectura institucional no estudiantil necesita un contrato de autorización acordado; este PR
no lo simula ni sustituye la identidad por un identificador recibido del cliente.

**Mariana/M3:** no bloquea el mapeo M5 de este PR. Para preparación académica positiva sigue
pendiente el adaptador productivo de `ConsultaContextoAcademicoPort`, sobre
`ContextoAcademicoConsultaPort.consultar(UUID,String)`, y la conciliación de los campos de
`ContextoAcademicoPersistido`. La emisión/verificación institucional de capacidades debe
componerse con los puertos existentes; este PR no implementa una consulta académica alterna.

**Dirección Central:** DD-02, DD-03, DD-04 y D07 permanecen intactas; D-06 y migraciones
históricas no se modifican. No hay POST REST ni cálculo/persistencia de calificaciones.

## Por verificar fuera del PR

Actor/permiso ratificado para leer como docente o servicio; composición APP positiva;
proveedores institucionales de capacidades y compatibilidad efectiva del contexto M3 en
esa composición. Las autorizaciones positivas de las pruebas son exclusivamente dobles
de prueba, no evidencia de habilitación institucional productiva.

## Reproducción

```powershell
./mvnw.cmd -pl m5 -am -Pdocker-it -Dit.test=ConsultaIntentoEvaluacionM5M6IT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Ejecuta pruebas unitarias/Spring del reactor requerido y solo la integración PostgreSQL
nueva. Reutiliza las migraciones canónicas y `CadenaIntentoPrueba`, sin duplicar fixtures.
Las demás campañas PostgreSQL quedan fuera de este comando deliberadamente.
