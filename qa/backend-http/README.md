# DC-EJEC-02 — aceptación HTTP de backend

Ejecuta el JAR Spring Boot real por HTTP, con PostgreSQL **16.15** en un contenedor
nuevo y desechable. No es MockMvc, ni una campaña Postman Desktop, ni una repetición
de las 114 pruebas PostgreSQL de M4. Solo modifica archivos bajo `qa/backend-http/`.

## Ejecutar

Requisitos: Node.js 24 (sin npm), Java 21, Docker Engine Linux operativo, Maven
Wrapper del repositorio y acceso a sus dependencias/imagen `postgres:16.15`.
Desde la raíz de **este checkout**:

```powershell
node --test qa/backend-http/run.test.mjs
node qa/backend-http/run.mjs
$LASTEXITCODE
```

El candidato predeterminado es `bc8e378bd6dd50a3b1484b06c268932ba5607dd3`.
Se verifican HEAD/rama y que el árbol de fuentes productivas y configuración Maven
coincida con ese candidato. Solo se permiten cambios locales de QA; por eso un
commit posterior que contenga únicamente la entrega QA sigue siendo ejecutable.
Para otro candidato revisado: `--expected-sha SHA_COMPLETO`. No usar este parámetro
para eludir la revisión de un cambio contractual.

Modos auxiliares, sin aprobación de escenarios HTTP:

```powershell
node qa/backend-http/run.mjs --preflight-only
node qa/backend-http/run.mjs --build-only
```

`--build-only` permite verificar la construcción aunque Docker no esté disponible.
Un exit 0 de estos modos indica solo su precondición/construcción; sus escenarios
quedan bloqueados. La aceptación completa requiere **modo http**, estado `passed`,
32 escenarios aprobados y exit 0. Exit 1 significa fallo de escenario/ejecutor o
limpieza; exit 2, bloqueo de precondición/infraestructura. Nunca se transforman
escenarios no ejecutados en aprobados.

## Aislamiento y evidencia

- No hay argumento de URL externa, JDBC, contraseña ni nombre de base existente.
- PostgreSQL nuevo: etiqueta UUID propia, `--rm`, tmpfs, puerto aleatorio en
  `127.0.0.1`. La contraseña se genera en memoria y se pasa por entorno, no argv.
- APP: puerto aleatorio loopback, JAR recién construido, Flyway canónico V001–V009,
  JPA validate, DD-02 y worker SMTP deshabilitados. No se alteran proveedores.
- Se filtra el entorno para excluir configuración Spring/BD, proxies y opciones
  JVM heredadas. El servidor debe permanecer vivo y haber registrado su arranque,
  además de responder `/api/health` antes de iniciar la campaña.
- Reutiliza `bruno/scripts/m1-bruno-seed-local.sql` y `m7-bruno-seed-local.sql`
  dentro de la base propia. **No ejecuta sus scripts Compose**. Son credenciales
  de fixtures públicos; no cuentas ni autorizaciones productivas.
- `fixture.sql` añade otro docente fixture y casos Borrador/inactivo, sin tablas,
  permisos nuevos ni migraciones. El Publicado del seed prueba consumo de un
  estado persistido, no publicación académica por HTTP.
- El payload de sesión reutiliza PM4-01 de la colección M4 existente. No ejecuta
  PM4-21 ni fabrica participación/historial para desbloquearlo.
- Evidencia en `evidence/<UTC>-<UUID>/`: summary.json, matrix.csv, log de
  precondiciones Docker, build.log/app.log cuando esas fases ocurren, y un JSON
  saneado por solicitud efectivamente enviada. Nunca fabrica respuestas HTTP.
- Se conservan código HTTP, cuerpo saneado, método, ruta, precondición, SHA y
  errores de aserción. Bearer/password/cookies/challenge/código y hashes BCrypt
  se ocultan. No se exportan inspecciones Docker que contengan el entorno secreto.
- Cada error HTTP debe conservar el contrato `code/message`, no exponer SQL o
  stack traces y no cambiar la huella de los datos académicos de seis tablas.
  También se comprueban propietario persistido y marca F3-03 en los positivos.
- En salida normal/fallo, termina solo el proceso Java iniciado por este ejecutor;
  elimina PostgreSQL solo tras verificar ID, nombre y etiqueta UUID. No ejecuta
  Docker prune, Compose down ni limpieza de recursos ajenos. Una terminación
  forzada del host puede impedir finally: conservar el runId para recuperación.

`evidence/` se ignora en Git. `results/DC-EJEC-02/` conserva la ejecución seleccionada
para revisión. Los logs se sanean antes de escribirlos.

## Contratos y trazabilidad

`SCENARIOS` en `run.mjs` es el inventario ejecutable; matrix.csv es su resultado.
Todas las rutas tienen prefijo de contexto **/api**. Los RF/CU permanecen `null`
porque no se dispone del catálogo canónico V27/CU verificable en este checkout.
Los IDs HTTP-01…32 son identificadores de escenarios QA, no RF nuevos.

| Grupo | Escenarios | Fuente del contrato inspeccionada |
|---|---|---|
| Estado y autenticación | 01–10, 31–32 | APP ApiStatusController/HttpApiConfig; M1 PersonalAuthController, PersonalSessionController, IdentityExceptionHandler |
| Creación de borradores | 11–14 | M3 CasoCreationController, CrearCasoRequest, CasoCreationExceptionHandler; APP M3CaseAuthorizationAdapter; Bruno m3/01-creacion |
| Programación y referencias | 15–23 | M4 SesionController, CrearSesionRequest, SesionAcademicaService, M4ExceptionHandler; PM4-01 existente |
| Convocatorias | 24–26 | M4 ConvocatoriaController; fixture oficial Bruno M7; rechazo de duplicados vigente |
| DD-02 y recursos M5 | 27–30 | M1 EstudianteAccessController; M5 IntentosEstudianteM5Controlador/TraductorErroresM5; APP EstudianteIntentosM5SecurityIT como referencia, sin ejecutarlo |

Operaciones fuera de la campaña por no estar publicadas en el candidato:
GET/PATCH/publicación docente M3, POST de inicio/finalización M5 y evaluación M6.
No se cuentan como omisiones exitosas. GET/PUT M5 se prueban por denegación;
ni un Bearer personal ni un seed otorgan identidad estudiantil.

## Estado de entrega

Ver `RESULTADO.md`. La construcción y las cuatro pruebas del ejecutor no
acreditan por sí mismas ningún escenario HTTP ni persistencia funcional.
