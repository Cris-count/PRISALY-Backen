# M3-INC-10B — Comandos parciales y precondición del agregado

## Antecedente y alcance

Dirección Técnica autorizó, mediante la solicitud de este incremento, comandos
explícitos, omisiones conservadoras, null explícito, eliminación explícita y una
precondición verificable. Rama y commit reportados por el solicitante:
`feature/m3-gestion-casos`, `9247217`; PR #6 solo para revisión. No se ejecutó Git
para comprobarlos. No se declara aprobado ARQ-008.3. El nuevo contrato local de
M3 necesita conciliación documental con ARQ-008.2 y la eventual ARQ-008.3.

No se modifica `CasoRepositoryPort.update(Caso)`: conserva su rechazo explícito.
No se conectan servicios, autenticación, endpoints ni otros módulos.

## Contrato implementado

`CasoUpdatePort.findById(UUID)` devuelve `Optional<CasoUpdateResult>` con el caso
comprometido y una huella opaca. `update(UUID, String, List<CasoUpdateCommand>)`
devuelve el agregado completo y la nueva huella después de confirmar la operación.
El cliente obtiene primero la huella y la presenta como precondición; no debe
reconstruirla a partir de un DTO parcial. La huella no acredita autorización.

Los comandos son records públicos anidados en `CasoUpdateCommand`:

| Comando | Campos u operación |
|---|---|
| ActualizarDatosCasoCommand | NOMBRE, HISTORIA, DESARROLLO_SITUACIONAL, TIEMPO_ESTIMADO_SEGUNDOS |
| ActualizarEscenarioCommand | UUID existente; TITULO, CONTEXTO, INSTRUCCIONES, ORDEN |
| ActualizarPreguntaCommand | UUID existente; ENUNCIADO, TEXTO_AYUDA, ORDEN |
| EliminarOpcionCommand | UUID existente de la opción que se desea eliminar |

Cada asignación nombra exactamente un campo. La ausencia de comando conserva el
campo; `valor = null` es asignación explícita únicamente para textos SQL nullable.
Los valores deben ser String o Integer según el campo, sin conversiones implícitas.
Los campos obligatorios y órdenes se validan; no se renumeran otros elementos.
Una lista vacía solicita cero modificaciones, no vaciado del agregado. Se comprueba
la precondición también en ese caso. Los comandos se ejecutan en el orden recibido;
un conflicto UNIQUE intermedio revierte toda la composición (no se inventan órdenes
temporales para intercambios). Dos eliminaciones del mismo UUID también revierten.

No hay comandos de propiedad, actividad, visibilidad, edición, publicación,
Archivado, fechas, asociación/reasignación, alta de hijos ni edición de rúbrica.
Esto delimita el subconjunto implementado, sin aprobar políticas pendientes.
Las rúbricas y sus asociaciones sí se incluyen íntegramente en la precondición.

## Huella canónica inicial de 10B (v1, sustituida por v2 al final de este documento)

No existe versión persistente de concurrencia en las seis tablas de V001–V009.
No se utiliza `fechaActualizacion` como versión ni se añade columna. El uso de
`xmin` se limita a una observación de prueba que verifica ausencia de UPDATE inútil.

`CasoAggregateFingerprint` produce `m3-v1:` y SHA-256 hexadecimal de una codificación
binaria con versión. Incluye todos los componentes de los seis records de filas del
snapshot: UUID, FK, textos, flags, estados, fechas, orden físico, decimales y JSON.
Ordena las colecciones por UUID de fila; conserva orden físico como dato incluido.
Incluye conteos, tipos, nombres de componentes, marcadores null y longitudes UTF-8;
no concatena textos con separadores ambiguos ni usa `record.toString()`.
BigDecimal se representa numéricamente sin ceros finales irrelevantes. Instant se
representa con segundos y nanosegundos; UUID con sus bits. Las asociaciones no
adquieren un orden académico.

JSONB se toma del texto emitido por PostgreSQL, tanto en la lectura JPA existente
como en la lectura transaccional JDBC. No se serializa otra vez ni interpreta DD-03.
Cambios de representación almacenada de números JSON pueden causar conflictos
conservadores aunque no cambie su significado; nunca se acepta por ello una
sobrescritura silenciosa. La codificación debe versionarse si cambia su formato.

Una huella de estado detecta diferencias del estado actual; no detecta un historial
ABA (cambio y restauración exacta del estado). Detectar todos los eventos históricos
requeriría otra decisión y un mecanismo persistente. No se promete esa garantía.

## Transacción y protocolo de bloqueo

Cada composición usa `TransactionTemplate` con el JpaTransactionManager del mismo
EMF, REQUIRES_NEW y READ_COMMITTED. Se verifica autoCommit desactivado y aislamiento
JDBC efectivo y los valores SQL `current_setting('transaction_isolation')` y
`current_setting('transaction_read_only')`. Resultado y nueva precondición solo se
entregan después del commit.
Esto no participa en el rollback de una transacción externa. Requiere otra conexión
si hay una transacción externa suspendida: no invocar mientras esta retiene el mismo
caso. El agotamiento del pool y los tiempos de espera no se resuelven con reintentos
automáticos en este incremento.

Dentro de una sola conexión Hibernate `doReturningWork`:

1. SELECT Caso FOR UPDATE; caso inexistente se rechaza sin INSERT.
2. Lectura de las otras cinco tablas por JDBC en esa misma conexión, sin cachés.
3. Verificación de presencia/habilitación de los seis triggers de contenido V007 y
   `session_replication_role = origin`; una configuración incompatible se rechaza.
4. Comparación de la huella; un estado diferente produce STALE_CASE antes de escribir.
5. Rechazo de Archivado y del contenido bloqueado por F3-03.
6. Validación de pertenencia de todos los UUID antes de la primera mutación.
7. Solo las asignaciones/eliminaciones solicitadas. SQL parametrizado; identificadores
   SQL provienen de switches cerrados. IS DISTINCT FROM evita UPDATE innecesario.
8. Relectura completa, validación mediante los records existentes y cálculo de la nueva
   huella. Cualquier excepción de validación, SQL o commit revierte toda la operación.

Un UUID hijo desconocido o de otro caso recibe ELEMENT_NOT_IN_CASE, sin reasignarlo.
CASE_NOT_FOUND identifica ausencia de raíz. Las FK/UNIQUE se dejan actuar y sus errores
se propagan. No se escriben fechas de auditoría ni fechaBloqueoAcademico. Una lectura
vacía de comandos sobre caso bloqueado no escribe; un comando, aun redundante, se
rechaza cuando el caso está bloqueado. Los archivados no se actualizan.

## Rutas de escritura inspeccionadas y F3-03

La única ruta productiva previa de M3 es `CasoPersistenceAdapter.insert`: inserta
primero una nueva raíz y luego sus hijos en la misma transacción, rechaza bloqueos
importados y no realiza upsert ni modificaciones de raíces existentes. La nueva raíz
no es visible para otras transacciones hasta confirmar. La otra firma update antigua
sigue no soportada; no puede eludir este protocolo. Los records, builders y servicio
actuales solo reconstruyen objetos y no escriben PostgreSQL. Las seis entidades JPA
continúan @Immutable. Los SQL de fixtures de pruebas no son rutas productivas.

Bloquear Caso no bloquea por sí mismo todas las filas hijas. La exclusión mutua se
apoya además en V007: INSERT/UPDATE/DELETE de Escenario, Pregunta, OpcionRespuesta,
Rubrica y RubricaPregunta obtienen FOR UPDATE sobre las raíces afectadas antes de
completar la mutación. V007/V008/V009 usan la misma raíz para programación y
reasignación de sesiones. El bloqueo se mantiene hasta commit/rollback.

Si programa primero, la huella incluye la nueva fecha y el comando antiguo recibe
STALE_CASE. Un comando con la huella actual recibe EVALUABLE_CONTENT_LOCKED. Si
actualiza primero, programación espera al commit y bloquea el estado resultante.
Un escritor SQL que ya bloqueó un hijo antes de esperar por la raíz puede causar
interbloqueo con el protocolo raíz-primero: PostgreSQL debe abortar una transacción;
el error se propaga y no se reintenta con una huella antigua.

La garantía presupone migraciones oficiales y escritores sin privilegios para
alterar/omitir las protecciones. La comprobación de habilitación no certifica el
cuerpo de funciones ni impide a un administrador cambiar triggers posteriormente.
Escritores privilegiados, otras bases/EMF o rutas nuevas requieren nueva auditoría;
no se certifican integraciones de módulos no inspeccionados. Un escritor externo
debe adoptar además precondiciones para evitar sus propias sobrescrituras obsoletas.

## Pruebas incorporadas y limitaciones

`CasoUpdateContractTest`: comandos, null explícito, obligatoriedad/tipos/UUID,
pertenencia, huella independiente de recuperación, inclusión de tablas/referencias,
rechazos por huella obsoleta y F3-03.

`CasoCommandUpdateIT`: PostgreSQL 16.15, V001–V009 sin modificaciones, actualización
parcial, conservación de hijos/rúbricas/órdenes/fechas, no-op, eliminación explícita,
elementos ajenos/inexistentes, precondición obsoleta, rollback tras UNIQUE intermedio,
aislamiento entre casos, dos comandos concurrentes, modificación concurrente de
pregunta/rúbrica y programación concurrente. Conexiones independientes y esperas
observadas con pg_blocking_pids; límites de espera, sin sleeps de coordinación.

La FK restrictiva Respuesta→OpcionRespuesta se comprueba por catálogo. No se declara
probado un DELETE del adaptador que alcance 23503: una Respuesta válida requiere una
sesión que ya activa F3-03; este rechazo tiene prioridad y no se falsea quitando el
bloqueo. Queda pendiente una decisión sobre si se necesita otra evidencia de FK,
sin crear estados imposibles ni desactivar protecciones.

No se ejecutaron pruebas, Maven, Git ni Docker. Tampoco se comprobó el arranque real
de app, la administración de privilegios ni el comportamiento de un pool productivo.
No se modificaron dominio, entidades, POM, migraciones, app u otros módulos.

Validación manual desde la raíz del backend:

```powershell
.\mvnw.cmd -B -pl m3 -am test
.\mvnw.cmd -B -pl m3 -am -Pdocker-it "-Dit.test=CasoCommandUpdateIT,CasoPersistenceAdapterIT,CasoJpaAggregateTransactionIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```

## M3-INC-10B.2 — Evidencia de concurrencia y transacciones (pendiente de ejecución)

Línea base reportada por el usuario: 89 pruebas unitarias y 26 de integración
aprobadas. Esos resultados corresponden a la versión anterior, no a las pruebas
añadidas aquí. No se ejecutaron Git, Maven, Docker ni pruebas en esta intervención.
No se modificaron producción, contratos, POM, migraciones ni otros módulos.

### Evidencia existente y complemento

La inspección de CasoCommandUpdateIT identificó cobertura previa de eliminación
de opción sin referencias, rechazo de identificadores ajenos, rollback compuesto,
dos actualizaciones con la misma precondición, escritores de Pregunta/Rubrica que
bloquean explícitamente Caso y programación que obtiene primero la raíz.
CasoJpaAggregateTransactionIT cubre REQUIRES_NEW del lector REPEATABLE_READ.
No se duplican estos escenarios. Se añade CasoCommandConcurrencyIT para el
escritor READ_COMMITTED, con siete métodos y nueve variantes:

| Prueba nueva | Evidencia esperada, aún no ejecutada |
| --- | --- |
| directQuestionAndRubricWritersWaitForRootAfterM3HasValidatedFingerprint | Dos variantes: UPDATE directo de Pregunta o Rubrica, sin SELECT raíz previo, espera al backend de M3 mientras este conserva Caso después de validar la huella. El resultado de M3 conserva el hijo anterior; tras el segundo commit, releer muestra ambos cambios y rechaza la huella anterior. |
| directChildWriterObtainingRootViaTriggerFirstMakesPendingM3PreconditionStale | Dos variantes: el trigger del escritor directo obtiene primero Caso; M3 espera y, después del commit externo, rechaza STALE_CASE sin escribir. |
| m3CommitsFirstThenWaitingSessionLocksTheUpdatedCase | La inserción válida de Sesion espera mientras M3 actualiza; después activa fechaBloqueoAcademico mediante triggers y las modificaciones evaluables con huella actual son rechazadas. |
| writerRequiresNewSuspendsAndRestoresOuterTransactionAndCommitsIndependently | Suspensión/reanudación de recursos y sincronizaciones, PID y transacción internos diferentes, misma conexión/transacción en las doce lecturas académicas, aislamiento PostgreSQL real READ_COMMITTED interno y REPEATABLE_READ externo conservado. El commit interno sobrevive al rollback externo. La conexión interna no ve la historia pendiente de otro caso escrita por la externa. |
| writerPoolExhaustionIsBoundedAndResumesOuterResource | Pool de una conexión retenida por la transacción externa: fallo acotado al iniciar REQUIRES_NEW, recuperación del recurso externo, ausencia de escritura y operación posterior exitosa. |
| failedInnerCompositionRollsBackEveryCommandWhileOuterTransactionCanCommit | Un cambio de nombre seguido de orden duplicado produce 23505; ambos comandos internos se revierten y la transacción externa independiente puede confirmar. |
| legitimateHistoricalReferenceRemainsProtectedByRestrictiveFkAndF303Priority | Referencia histórica válida, catálogo FK restrictivo, rechazo de EliminarOpcionCommand por F3-03 y rechazo JDBC 23514 sin perder la respuesta ni la opción. |

### Coordinación y límites de la prueba

El DataSource instrumentado existe exclusivamente en el test. Una barrera antes
del primer UPDATE detiene M3 después de validar su precondición, conservando el
bloqueo real de Caso. Los escritores utilizan conexiones independientes y se
identifican por pg_backend_pid(); pg_blocking_pids() confirma la espera real antes
de liberar la barrera o confirmar al primer escritor. No se utiliza Thread.sleep
ni se desactivan triggers. El sondeo de bloqueos tiene límite de cinco segundos.

Antes de cada sentencia académica se consultan txid_current(),
current_setting('transaction_isolation'), current_setting('transaction_read_only')
y autoCommit sobre su propia conexión. Se comparan las seis consultas anteriores
y las seis posteriores a la escritura, así como la mutación. Esto comprueba el
aislamiento efectivo, no solamente la configuración inicial de Hikari. Para los
escritores directos en autocommit se utiliza su PID para coordinar; el xid de una
sonda independiente en autocommit no identifica la transacción del UPDATE siguiente.

Los límites son del arnés de pruebas: adquisición Hikari 500 ms; statement_timeout
PostgreSQL 10 s; socketTimeout JDBC 12 s; barreras 10 s; futuros y terminación de
workers 15 s; timeout JUnit 60 s por prueba. No son configuración productiva ni
una recomendación de dimensionamiento definitiva. La disponibilidad del contenedor
y los tiempos de arranque son además responsabilidad de Testcontainers/entorno.

### Garantías F3-03 y limitaciones

V007 hace que las escrituras de las cinco tablas hijas obtengan FOR UPDATE sobre
Caso; V007–V009 coordinan la programación y exigen caso publicado y activo.
READ_COMMITTED permite que M3 vea los commits precedentes después de esperar por
la raíz. La exclusión entre validación, escritura y snapshot posterior depende
de estos triggers oficiales activos y del modo normal de ejecución de triggers.
El mero bloqueo de Caso no bloquea automáticamente las tablas hijas.

La secuencia programación primero ya cubierta rechaza inicialmente STALE_CASE
si se presenta la huella anterior: la precondición precede a F3-03 en el adaptador.
Al releer la huella del caso bloqueado se obtiene EVALUABLE_CONTENT_LOCKED. La
secuencia inversa añadida conserva el cambio confirmado antes de programar y
rechaza modificaciones evaluables adicionales. No se modifica M4 ni se certifica
su integración funcional: la programación de prueba se realiza por JDBC con datos
válidos y todos los triggers oficiales activos.

Los UPDATE directos de hijos pueden adquirir primero su tupla y luego esperar por
Caso en el trigger. Si simultáneamente M3 modifica ese mismo hijo, puede existir
un interbloqueo: PostgreSQL abortará una transacción, sin sobrescritura silenciosa.
Las nuevas pruebas usan un cambio raíz frente al hijo para aislar la coordinación
del trigger, y no demuestran ausencia de interbloqueos ni reintentos automáticos.
Los escritores externos no adquieren por ello una precondición SHA-256 propia:
la protección frente a datos de entrada obsoletos requiere usar el protocolo M3.
Tampoco se certifican privilegios productivos ni resistencia a usuarios capaces
de reemplazar/desactivar triggers; contar sus nombres no autentica sus cuerpos.

REQUIRES_NEW necesita otra conexión mientras la transacción externa retiene la
suya. Con capacidad insuficiente falla la adquisición y debe reanudarse la externa;
con una externa que retenga la misma raíz el escritor interno puede esperar a su
propio llamador. Deben definirse límites productivos, dimensionamiento y disciplina
de invocación; los límites del test no cambian el adaptador ni app. No se añadió
ninguna corrección silenciosa de producción ni integración de permisos M1.

### Referencias históricas y evidencia alternativa de FK

La fixture conserva la cadena Sesion → Convocatoria → Participacion → Intento →
Respuesta, con estudiante/caso nuevos, primer intento y valores/defaults admitidos
por V001–V009. No implementa ni certifica el ciclo funcional M6. Respuesta referencia
la opción y pregunta correctas; se comprueba por JOIN su pertenencia al caso.
El catálogo confirma convalidated y confdeltype='r' para
fk_respuesta_idopcionseleccionada_idpregunta, desde Respuesta a OpcionRespuesta.

Una respuesta legítima de esta cadena presupone Sesion, que ya activa F3-03.
Por ello la eliminación es rechazada antes de comprobar la FK: error de dominio
con huella actual y SQLSTATE 23514 por JDBC. No se fabrica un 23503 desactivando
triggers ni alterando la fecha de bloqueo. La evidencia alternativa combina FK
validada en catálogo, referencia existente válida, rechazo y conservación íntegra.
El caso de eliminación permitida sin referencias permanece en CasoCommandUpdateIT.

### Registro y validación manual

Registrar como M3-INC-10B.2 los métodos anteriores, versión/commit ejecutado,
PostgreSQL 16.15, resultados Failsafe, fallos/SQLSTATE y evidencia de bloqueos.
Hasta la ejecución del usuario, todos los resultados nuevos quedan pendientes;
no se declara aprobado 10B ni se avanza a 10C. Persisten las limitaciones de
privilegios, interbloqueos, ABA y dimensionamiento documentadas en la auditoría.

Desde PRISALY-Backen:

```powershell
.\mvnw.cmd -B -pl m3 -am -Pdocker-it "-Dit.test=CasoCommandConcurrencyIT,CasoCommandUpdateIT,CasoJpaAggregateTransactionIT,CasoPersistenceAdapterIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```

## M3-INC-10B.1 — Registro de correcciones y bloqueo JSONB

### 10B.1-UNICODE — corrección aplicada, ejecución pendiente

Causa: el comando cuenta puntos de código, pero Escenario usaba String.length()
(unidades UTF-16). Un carácter suplementario ocupa dos unidades; por ejemplo,
200 caracteres suplementarios superaban indebidamente el límite del dominio.
Además, Caso no comprobaba el máximo SQL de su nombre/título.

Corrección mínima: Escenario.titulo cuenta puntos de código, máximo 200, conserva
null; Caso.titulo comprueba el máximo 150 con el mismo criterio que el comando.
Son límites físicos existentes de V001, no límites académicos nuevos. No se cambia
el texto, no se trunca ni se normaliza. CasoUpdateCommand ya empleaba este criterio
y no necesita cambios. Se mantienen las demás validaciones anteriores.

La modificación de estos dos records es indispensable: cambiar solo el comando
mantendría una reconstrucción incompatible o rechazaría caracteres válidos en SQL.
Se añadieron cuatro pruebas unitarias de límites ASCII/suplementarios/mixtos,
rechazo de excesos y reconstrucción desde snapshot, y una prueba PostgreSQL de
escritura/relectura que verifica char_length(150/200). No se ejecutaron.

### 10B.1-JSONB — diagnóstico inicial, sustituido por la resolución autorizada siguiente

Causa: definicionEvaluacion se codifica como texto PostgreSQL, sin normalización
numérica semántica interna. BigDecimal fuera del JSON sí se normaliza. La escala
numérica JSON equivalente podía producir huellas distintas; este era el bloqueo
anterior a la autorización registrada en la sección siguiente.

Evidencia local de dependencias, inspeccionada sin ejecutar Maven:

- m3 declara spring-boot-starter-data-jpa para producción; starter-test es scope test.
- El POM local del starter-test aporta json-smart, JsonPath y JSONassert; su presencia
  en el classpath de pruebas no los hace disponibles para el código productivo M3.
- El reporte Surefire previo muestra esos analizadores y jackson-annotations, pero
  no Jackson databind/core, Gson ni un proveedor JSON-B de producción. El reporte
  se usa como evidencia histórica de dependencias, no como ejecución de este cambio.
- El BasicJsonParser del JAR local de Spring Boot referencia Double y no BigDecimal;
  no se adopta como analizador de precisión decimal exacta.

No se identificó un analizador JSON de producción adecuado para reutilizar con
garantía de precisión. Por la restricción expresa del solicitante, se detiene esta
parte: no se añade dependencia, no se promueve una dependencia test, no se usa
double, no se escriben expresiones regulares ni un parser improvisado. No se
modifican CasoAggregateFingerprint, lectores, adaptador, versión ni formato de huella.

Decisión requerida entonces: aprobar un analizador de producción que preserve números exactos
y su declaración de dependencia, o aprobar expresamente otro diseño seguro. Una
dependencia solo administrada por el padre o disponible en app no basta para M3.
Tras esa decisión se necesitan pruebas de huella sobre: 1.0/1.00, claves en distinto
orden, arrays en orden diferente, SQL NULL/JSON null, booleanos/cadenas/documentos
y números de alta precisión. Esas pruebas de corrección JSONB no se declaran
implementadas ni satisfechas en este incremento parcial.

Estado de aquella inspección: Unicode corregido en código, pendiente de ejecución;
JSONB pendiente de autorización de dependencia. La autorización y corrección
posteriores se registran a continuación. Siguen vigentes las demás limitaciones
de concurrencia/FK de la auditoría anterior, fuera del alcance de 10B.1.

## M3-INC-10B.1 — Resolución autorizada de canonicalización JSONB

Dirección Técnica autorizó expresamente incorporar un analizador productivo exacto
y modificar exclusivamente el POM de M3 para declararlo. Se incorpora
`com.fasterxml.jackson.core:jackson-core`, sin versión explícita. El POM local de
Spring Boot 4.1.2 importa jackson-bom 2.21.7 y administra jackson-core 2.21.7.
Esta dependencia mínima proporciona el parser de tokens; no se añade databind,
un starter web, otro analizador ni un proveedor Hibernate JSON FormatMapper.
No estaba declarado un parser productivo adecuado en M3. Las bibliotecas de
starter-test no justificaban su uso en main. No se ejecutó resolución Maven:
versión comprobada por lectura de los POM administrados, descarga/build pendientes.

### Algoritmo y precisión

`JsonbCanonicalizer` utiliza JsonParser de Jackson para gramática y escapes JSON.
La lógica local recorre sus tokens y construye valores semánticos; no implementa
un lexer/parser manual. Todos los números, incluso enteros, se construyen con
`new BigDecimal(tokenText)`, sin accesores Double/Float ni conversiones binarias.
El cero se representa como `0`; otros números usan stripTrailingZeros y
toPlainString, sin redondeos. Se comprueban escala/rango y longitud expandida
antes de materializar una notación exponencial grande. No se adopta RFC 8785.

Los objetos usan claves String decodificadas, ordenadas recursivamente de manera
determinista; claves repetidas conservan la última, como JSONB. Los arrays conservan
posición y multiplicidad. Strings Unicode se conservan sin NFC/NFD, trim ni cambios
de caso. Se rechazan U+0000 y sustitutos UTF-16 no emparejados, incompatibles con
JSONB PostgreSQL UTF-8. Nulos, booleanos, strings, números y contenedores son tipos
distintos; no se vuelve a serializar un documento como una cadena JSON.

La salida es una codificación tipada, no un documento JSON para reinsertar:
`q` = SQL NULL, `n` = JSON null, `t/f` = booleanos, `sN:valor` = string UTF-8,
`dN:valor` = número decimal normalizado, `aN:...` = array de N valores,
`oN:...` = objeto de N pares clave/valor. N en escalares cuenta bytes UTF-8;
los contenedores incluyen conteos y cada valor lleva tipo/longitud. Propiedad
ausente, propiedad con null, string "null" y SQL NULL permanecen distintos.

El único campo JSONB de las seis tablas del agregado es
Rubrica.definicionEvaluacion. CasoAggregateFingerprint lo identifica explícitamente
en RubricaRow y lo codifica con el mismo algoritmo desde ambas rutas de lectura.
No se normalizan los demás textos aunque parezcan JSON. Los lectores y el snapshot
no cambian: siguen transportando el documento PostgreSQL original como texto.
Las normalizaciones afectan solo la huella; no alteran el JSONB almacenado.

### Versión y compatibilidad

La huella nueva es `m3-v2:<sha256 hexadecimal>`. Se mantienen los seis componentes
del agregado y la codificación binaria tipada de filas, ordenadas por UUID,
incorporando el JSON canónico y el dominio de versión `M3-INC-10B.1:v2`.
El digest se aplica a los bytes UTF-8 de la representación Base64 de esa codificación
completa: SHA-256(UTF8(Base64(binary-v2))). La representación intermedia es reversible
y sin ambigüedades; no omite campos. SHA-256 no hace imposibles las colisiones.

El adaptador rechaza versiones bien formadas anteriores/desconocidas con STALE_CASE
y exige releer el caso. No convierte ni acepta m3-v1 como m3-v2. Un token malformado
produce IllegalArgumentException. No cambia la firma de CasoUpdatePort/Result.
La búsqueda local de consumidores encontró código del adaptador y pruebas de M3;
no se encontraron columnas SQL de huella ni consumidores productivos externos en
el código inspeccionado. No se certifican clientes o almacenes externos al checkout.
Un despliegue requiere invalidar cualquier precondición cliente antigua y releer.

### Límites operativos

Se rechazan documentos fuera del conjunto admitido, sin truncarlos ni redondearlos:
1 048 576 unidades UTF-16 de entrada; profundidad 64; 100 000 valores/claves;
150 000 caracteres por token numérico; escala absoluta de entrada hasta 150 000;
8 388 608 bytes canónicos, con presupuesto adicional de expansión numérica total
antes de asignar grandes strings. Para números no cero se respetan también los
máximos numeric de PostgreSQL 16: 131 072 dígitos enteros y 16 383 fraccionarios.
El cero evita expansión incluso con exponente grande, sujeto al límite de escala
de entrada. Los exponentes no representables por BigDecimal se rechazan.

Estos son límites de recursos, no reglas académicas ni cambios de BD-005.2.
Un JSONB válido pero demasiado grande puede existir en PostgreSQL: su huella y
actualización mediante este puerto se rechazarán explícitamente; no se pierde ni
modifica su contenido. La equivalencia se garantiza por diseño para los documentos
admitidos, pendiente de contraste ejecutado con PostgreSQL. No es una certificación
del conjunto ilimitado de documentos que PostgreSQL puede almacenar.

### Regresión y estado de auditoría

Se añade JsonbCanonicalizerTest (11 pruebas): números/escalas/exponentes/ceros,
objetos/duplicados/anidamiento, tipos/arrays, SQL NULL/JSON null/ausencia,
alta precisión y límites numeric, Unicode/escapes, errores y recursos,
determinismo, campo JSONB frente a textos ordinarios y rechazo de versiones antiguas.
Se actualiza la expectativa de versión en CasoUpdateContractTest.

Se amplía CasoCommandUpdateIT con dos pruebas: pares contrastados contra
`CAST(? AS jsonb) = CAST(? AS jsonb)` y huellas obtenidas por lecturas reales de
las seis tablas; y una actualización parcial que acepta la huella v2 después de
reescribir JSONB numéricamente equivalente y rechaza v1/versiones desconocidas
sin modificar datos. Se mantiene distinción SQL NULL/JSON null. Los fixtures
permanecen desbloqueados y los triggers oficiales activos.

La corrección Unicode previa se conserva. No se ejecutaron Maven, Git, Docker,
compilaciones ni pruebas. Canonicalización implementada, pendiente de ejecución
y QA; no se declara terminado ni aprobado 10B. Siguen pendientes las observaciones
de concurrencia, FK, privilegios, ABA, arranque y pool de la auditoría anterior.

Validación manual desde PRISALY-Backen:

```powershell
.\mvnw.cmd -B -pl m3 -am test
.\mvnw.cmd -B -pl m3 -am -Pdocker-it "-Dit.test=CasoCommandUpdateIT,CasoPersistenceAdapterIT,CasoJpaAggregateTransactionIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```
