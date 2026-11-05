# PRISALY — alineación documental M4 antes de las pruebas

Fecha de revisión: 4 de noviembre de 2026. Esta revisión es de código, contratos y documentos; no constituye una prueba nueva.

## Versiones que no deben confundirse

| Referencia | Identificación | Uso correcto |
|---|---|---|
| Checkout actual | `develop`, `451a58eba17b47524b27929f12ffd655116f83f4`, árbol limpio al revisar | Código integrado para la conciliación de contratos. No hay una corrida Postman acreditada sobre este SHA en este documento. |
| JAR de la corrida Postman parcial | SHA-256 `407634803302733A8DFFE7B6A689E981CD174F7A813A30321B1A8AFB1B33ED8B` | Fue preparado desde `feature/m4-cierre-d06`, `6c75000f2c6152b508b986fa502598fc6bd3262e`. No atribuir sus respuestas a `develop` actual. |
| Documento técnico `docs/PT-DCP-01-M4.md` | Cita una base anterior y pruebas Maven | Antecedente técnico. La entrega que exige Postman necesita una matriz separada con sus propias ejecuciones. |
| Capturas Postman históricas | `Evidencias_Postman/` | Antecedente de otra versión; no mezclar con `Evidencias_Postman_2026-11-04/`. |

## Contratos y trazabilidad entre módulos

| Frontera | Contrato o ruta comprobada en el árbol actual | Qué acredita | Límite documental |
|---|---|---|---|
| M1 → M4 | Identidad docente autenticada y permiso `GESTIONAR_SESION`; `X-Docente-Id` no sustituye el token | Autorizar creación, consulta, edición y convocatoria de sesiones propias | El 401 sin token y el 403 de usuario autenticado sin permiso son casos diferentes. |
| M3 → M4 | `POST /v1/casos` crea un borrador; `CasoAcademicoPort.estaPublicado(UUID)` y adaptador BD-005.2 comprueban `Publicado` y `activo` antes de programar | M4 acepta únicamente un caso que ya satisface esa precondición | El `CasoPublicationAdapter` es infraestructura interna; no se encontró una ruta REST de publicación en el controlador M3 revisado. El seed local de Bruno coloca un caso directamente en `Publicado`; no demuestra el flujo HTTP completo de publicación. |
| M4 → M7 | `POST /m4/sesiones/{sesionId}/convocatorias` usa `NotificacionPort`; `GET` en la misma ruta lista convocatorias | Un 201 acredita creación o aceptación de convocatoria y solicitud transaccional de aviso | El 201 no prueba entrega de correo. El funcionamiento interno de la cola y SMTP corresponde a M7. |
| M4 ↔ M5 | Historial por `ParticipacionesPort` / `HistorialParticipacionPort`; contexto de programación y coordinación D-06 por puertos internos | La edición M4 consulta historial; M5 consume contexto sin crear rutas HTTP M4 para intentos | La ratificación arquitectónica de D-06 y la prueba PM4-21 con historial real siguen pendientes. |

Las rutas HTTP de M4 existentes en el código son `POST/GET /m4/sesiones`, `GET/PUT /m4/sesiones/{sesionId}`, `PATCH /m4/sesiones/{sesionId}/estado` y `POST/GET /m4/sesiones/{sesionId}/convocatorias`, bajo el prefijo de aplicación `/api`. La ruta `GET /m4/sesiones/{sesionId}/acceso/{estudianteId}` está retirada y responde 410; no representa acceso productivo estudiantil. No se encontraron los textos completos de ARQ-008.2 y ARQ-008.3 en este checkout; la conformidad final de nombres de rutas queda por confirmar contra esos documentos.

## Cruce con los casos de Cristian

| ID en Bruno de Integrante 1 | Operación | Correspondencia M4 | Estado según `bruno/MATRIZ-RESULTADOS.md` |
|---|---|---|---|
| `M7-PREP-02` (`04-02`) | Crear sesión con caso seed | PM4-01; preparación de la convocatoria | La matriz local inicial no tenía evidencia visible. Integrante 1 informa posteriormente HTTP 201 e ID de sesión; pendiente de incorporar y revisar la captura y la versión exacta. |
| `M7-via-M4-01` (`04-03`) | Convocar estudiante | PM4-05; recorrido HTTP asociado a `CP-M4-INT-07` | La matriz local inicial no tenía evidencia visible. Integrante 1 informa posteriormente HTTP 201 con la sesión resuelta. `CP-M4-INT-07` es una prueba PostgreSQL de FK y orden de notificación; no equivale por sí sola a una captura HTTP ni prueba entrega SMTP. |
| `M7-VER-01` (`04-04`) | Listar convocatorias | PM4-06 | No ejecutada en esa matriz. |
| `M7-via-M4-02` (`04-05`) | Crear sesión sin permiso | PM4-18 | La matriz inicial registra 403 con captura; Integrante 1 confirma posteriormente el mismo resultado. Su SHA y entorno se conservan por separado. |
| `M7-via-M4-03` (`04-06`) | Convocar sin token | Escenario adicional de seguridad M4–M7; PM4-09 cubre consulta de sesión sin token, que es otra operación | La matriz local inicial registra 401 con `sesionId` sin resolver, que no acreditaba el escenario. Integrante 1 informa una repetición posterior con `sesionId` resuelto y HTTP 401; pendiente de incorporar y revisar la captura y la versión exacta. |

El fixture `bruno/scripts/m7-bruno-seed-local.sql` es solo para entorno local. Declara un caso fijo Publicado y activo, un estudiante y el permiso del docente. Su presencia en el repositorio no demuestra que se haya aplicado en la base de una corrida concreta.

### Actualización recibida después de la matriz local

Integrante 1 reporta haber ejecutado los cuatro casos anteriores desde Bruno Desktop en `feature/m1-m7-estabilizacion`, con HEAD base `b078a0c` y cambios locales todavía sin consolidar. Reporta además comprobación PostgreSQL de un `Aviso` tipo `Convocatoria` y un `EnvioCorreo` asociado en estado `Pendiente`, con cero intentos de envío. Ese estado acredita persistencia y encolamiento reportados; no acredita entrega de correo. Estas afirmaciones son **evidencia comunicada por el responsable**: la copia de `bruno/MATRIZ-RESULTADOS.md` disponible en este checkout aún muestra los estados anteriores y aquí no se recibieron las capturas ni el resultado SQL originales para verificarlos independientemente. La referencia `b078a0c` es HEAD base, no un SHA que identifique todos los cambios locales del JAR utilizado.

M3 confirma que mantiene como dependencia la verificación de caso Publicado y activo mediante `CasoAcademicoPort.estaPublicado(UUID)`. Su responsable revisará si una ruta REST de publicación forma parte del contrato aprobado; esta alineación no le atribuye un endpoint nuevo ni declara completado ese flujo HTTP.

## Estado previo a la siguiente ejecución

- La colección versionada `postman/PRISALY-M4-PT-DCP-01.postman_collection.json` contiene 21 casos M4 y dos preparaciones M1. Incluye PM4-19, PM4-20 y PM4-21. La copia abierta en Postman Desktop debe compararse con ese JSON antes de usarla como versión final.
- En una corrida parcial anterior a la instrucción de detener pruebas se observaron respuestas de PM4-01 a PM4-18 sobre el JAR `407634…ED8B`; las capturas están en el directorio local de entrega `Entregables/Segundo_corte/M4/Evidencias_Postman_2026-11-04/`, fuera de este repositorio. PM4-16 tuvo un primer intento 201 por datos de fechas incorrectos en la solicitud y luego se corrigió a 409. Registrar ambos hechos; no ocultar el intento inicial.
- PM4-19 y PM4-20 **no fueron ejecutadas**. PM4-21 **no fue ejecutada** y requiere historial M5 legítimo. La ficha histórica M4-17 de inicio estudiantil también sigue no ejecutada; no es PM4-17, que rechaza un caso Borrador.
- El probador de una ejecución posterior debe ser quien efectivamente opere Postman. La corrida parcial anterior fue operada por Codex en Postman Desktop durante la sesión de Nicolás; no atribuirla únicamente a Nicolás si el campo exige ejecutor material.
- Para el entregable docente, las suites Maven/JUnit y las solicitudes Bruno son antecedentes técnicos. La evidencia calificable debe proceder de Postman y del mismo backend identificado en cada captura/matriz, salvo aceptación expresa del docente para otro medio.

## Orden para el trabajo posterior de Nicolás en Postman

1. Identificar el checkout y construir o seleccionar un JAR de **esa misma versión**; registrar SHA Git, SHA-256 del JAR, perfil, base y fecha. Si se prueba `develop`, no reutilizar como prueba de `develop` las capturas del JAR anterior.
2. Preparar en una base local aislada un docente con `GESTIONAR_SESION`, otro autenticado sin ese permiso, un caso Publicado y activo, uno Borrador y un estudiante. Registrar IDs sin contraseñas ni tokens en capturas.
3. Ejecutar PRE-01 y PRE-02 en Postman; después los casos M4 positivos y negativos con sus precondiciones. Guardar solicitud, HTTP, cuerpo, aserciones y captura por ID. Para PM4-19 usar fechas iguales con los demás campos válidos. Para PM4-20 finalizar primero una sesión propia sin historial M5 y comprobar el rechazo por estado.
4. Mantener PM4-21 bloqueada hasta disponer de una participación o intento creado por el flujo legítimo de M5. No insertar historial artificial para forzar el rechazo.
5. Registrar por separado el cruce M4–M7 de Cristian. Si se repite para el docente en Postman, resolver `sesionId` antes de convocar y antes del caso sin token. No atribuir entrega de correo al 201.
