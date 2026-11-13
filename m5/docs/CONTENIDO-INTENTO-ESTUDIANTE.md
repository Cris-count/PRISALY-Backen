# Lectura estudiantil del contenido de un intento (DC-BE-SIM-EJEC-02)

## Contrato para Nicolás

`GET /api/v1/estudiantes/intentos/{idIntento}/contenido`

Sin cuerpo. `idIntento` es UUID. Autenticación: sesión estudiantil M1; en navegador enviar
la cookie con `credentials: "include"`. Nunca enviar ni confiar en un idEstudiante del cliente.
Un Bearer de personal no sustituye esa sesión. Mantener la configuración existente de origen/CORS.
La implementación no cambia ni activa DD-02. Para probar positivamente se necesita el entorno
QA opt-in de M1 con su esquema candidato y claves desechables; no habilitarlo en producción.

Respuesta 200, `Cache-Control: no-store`:

| Campo | Tipo / origen persistido |
|---|---|
| idIntento | UUID, Intento.idIntento |
| idSesion | UUID, programación M4 de la convocatoria del intento |
| caso.idCaso | UUID, Sesion.idCaso resuelto por M4 |
| caso.titulo | string, Caso.nombre |
| caso.historia | string o null, Caso.historia |
| caso.desarrolloSituacional | string o null, Caso.desarrolloSituacional |
| caso.escenarios[] | en orden persistido; desempate por UUID |
| escenario.idEscenario, titulo, contexto, instrucciones, orden | UUID, string/null, string/null, string/null, int |
| escenario.preguntas[] | en orden persistido; desempate por UUID |
| pregunta.idPregunta, enunciado, orden | UUID, string, int |
| pregunta.opciones[] | en orden persistido; desempate por UUID |
| opcion.idOpcion, texto, orden | UUID, string, int |

DTO Java: `ConsultaContenidoIntento.Contenido` y los records anidados de
`ContenidoCasoEstudiantilPort`. Listas inmutables. Los campos texto conservan lo almacenado;
no hay fallback ni conversión de contexto/instrucciones en un guion.
Los idPregunta/idOpcion pueden usarse en el PUT existente, sin cambiar su revisión esperada/CAS.
El GET existente conserva estado, tiempos y respuestas propias; esta ruta no los duplica.

Errores: 400 UUID inválido; 401 credencial ausente/no válida; 403 identidad no estudiantil o
intento ajeno (mismo criterio actual M5); 404 intento inexistente; 409 con
`code=CONTENIDO_INTENTO_NO_DISPONIBLE` si el contenido no puede entregarse de forma segura;
500 genérico ante fallo de persistencia, a través del manejador M5 existente.
El manejo de sesión M1 también puede rechazar credenciales revocadas o vencidas.

## Relación real y reutilización

`Intento.idParticipacion → Participacion.idConvocatoria → Convocatoria.idSesion → Sesion.idCaso`.
M5 consulta solo sus tablas mediante `RepositorioIntentos.vinculo`; usa
`ControlAccesoIntentos`, `IdentidadEstudianteActual` y `TitularidadIntentosDesdeM4` para acreditar
identidad/titularidad. `ProgramacionParticipacionPort` de M4 resuelve sesión/caso y comprueba la
convocatoria del estudiante autenticado. Solo el adaptador M3 consulta las tablas académicas.
La vista docente no se reutiliza porque contiene corrección y explicaciones. Se reutilizan los
contratos de acceso y programación, con una nueva proyección M3 por lista permitida.

El coordinador abre una transacción PostgreSQL REPEATABLE READ de solo lectura. Titularidad,
vínculo, programación y contenido comparten ese snapshot. El adaptador M3 exige una transacción
existente (MANDATORY); no abre un snapshot independiente. No se reserva ni modifica el intento.

## Límites deliberados y dependencias pendientes

- La proyección nunca selecciona esCorrecta, explicacion, rúbricas, pesos, calificaciones ni
  retroalimentación. No libera resultados ni activa M6.
- Falla cerrada si el caso no está publicado/activo o si hay escenarios inactivos o preguntas
  invisibles/no listas. Rechaza el contenido completo: no inventa cuáles forman parte del intento.
  La selección de subconjuntos o un recorrido distinto requiere un contrato académico explícito.
- No existe relación Backend de intento a un modelo de guion que permita producir pasos de
  simulación. Nicolás recibe el caso/escenarios/preguntas reales, no un guion inventado.
- No resuelve ingreso tardío, duración, repetición, elegibilidad ni reservas D2/D3/F4-H1.
  Como el GET actual, comprueba titularidad de un intento existente. Finalizar no publica soluciones.
- No incorpora snapshots históricos/versionados del contenido: usa el caso efectivamente vinculado
  a la sesión y las restricciones existentes de M3. Tampoco crea un intento ni publica sus POST.
- El uso productivo sigue dependiendo de la autorización/configuración institucional de M1/DD-02;
  este incremento deja su flag desactivado. Frontend no fue modificado.

## Verificación focalizada

Pruebas nuevas: `ConsultaContenidoIntentoTest` (7) y `ContenidoIntentoEstudianteHttpIT` (6).
La prueba HTTP usa APP real, PostgreSQL 16.15, sesiones M1 reales y un caso SQL controlado;
verifica titular, ajeno, credenciales, IDs manipulados, no filtración, no escritura, orden,
compatibilidad GET/PUT y rechazo cerrado de contenido restringido. Su decorador QA comprueba
con SHOW de PostgreSQL el aislamiento repeatable read y transaction_read_only=on al leer M3.
No sustituye el adaptador real por un mock.

Desde la raíz, con Java 21, Docker y sin variables que apunten a bases compartidas:

```powershell
.\mvnw.cmd -B -Pdocker-it -pl app -am verify `
  "-Dtest=ConsultaContenidoIntentoTest,ArquitecturaCapasTest,IntentosEstudianteM5MvcTest,EnsamblajeEvaluacionNoPublicadoTest" `
  "-Dit.test=ContenidoIntentoEstudianteHttpIT,RecorridoEstudianteControladoHttpIT,EstudianteIntentosM5SecurityIT" `
  "-Dsurefire.failIfNoSpecifiedTests=false" "-Dfailsafe.failIfNoSpecifiedTests=false"
```

Estas pruebas crean únicamente PostgreSQL desechable. Las migraciones candidatas de DD-02
se usan solo por la fixture QA existente. No ejecutar contra bases compartidas.
