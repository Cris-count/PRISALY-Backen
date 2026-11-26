# DC-107 — Cierre funcional después de Codex

Fecha: 2026-11-26 (UTC). Ejecutor: Claude Code.

**Base de trabajo:** `C:\Users\Heiner\Desktop\PRISALY-REVISION-DEVELOP`, rama `integration/prisaly-cierre-funcional` en los dos repositorios.

**Lo que no se hizo:**
- no se volvió a clonar nada;
- ningún `reset`, `clean` ni borrado de ramas, contenedores o volúmenes;
- PostgreSQL de dc105 no se reconstruyó; los QA protegidos no se tocaron;
- ningún merge ni push.

## 1. Estado recuperado

| Repositorio | SHA de partida | Estado encontrado |
|---|---|---|
| Backend | `c4ceaea6bbbc0ce5b1679469241ab763d914c380` (= `origin/develop`, PR #100) | Sin cambios de código de Codex; solo `app/logs/` sin seguimiento |
| Frontend | `7ad2a2d6511cd1071cf4fd6ff7469a8d22ae0ec6` (= `origin/develop`, PR #30) | Cambios de Codex sin confirmar: `angular.json`, `app.routes.ts`, `login-page.html` + 4 archivos nuevos (`acceso-roles.*`, `environment.dc106-qa.ts`, `proxy.dc106-qa.json`) |

**Preservación:**
- `DC-107/respaldo-codex/`: parche y copias de los archivos nuevos, con SHA-256.
- Commit propio a nombre de Codex (§2).

**Backend desde DC-99 (`32922bb`):**
- PR #99 y #100 solo añadieron documentación y un cambio de producción: `ClasificacionErrorCorreo`, que trata los errores de conexión SMTP como transitorios.
- Ese cambio se comprobó con `ClasificacionErrorCorreoTest`, `ProcesarEnviosPendientesServiceTest`, `M7EnvioWorkerPostgreSqlIT` y la IT RF-27 SMTP.

**QA dc105** (`prisaly-dc105-local`, compose `DC-105/entorno` + overrides `DC-106/*.yaml`):
- Servicios: frontend `:18175`, Mailpit `:18030`, backend y PostgreSQL, todos *healthy*. Volúmenes `pg_data` y `audit_data` intactos.
- Historial Flyway real (`flyway_schema_history`): `001–009, 013, 014–016, 019–023`, todos con éxito. **V017/V018 no aplicadas.** Contrato `bd0052-v013-integral-dc92`.

**Interruptores activos en dc105:**

| Grupo | Estado |
|---|---|
| Acceso estudiantil DD-02 B (cookie no segura, solo HTTP local) | Activo |
| Activación docente D-05 | Activo |
| Alta de estudiantes P-78 | Activo |
| Worker de códigos M7 | Activo |
| **POST de M5 (DC-107)** | **Activo** |
| Worker de correo M7 | Apagado |
| M6 (evaluación, liberación, retroalimentación) | Apagado |
| Simulador | Apagado |

## 2. Commits locales

Rama `trabajo/dc107-cierre-funcional` del Frontend, creada desde `7ad2a2d`. No hay push.

| Commit | Autor | Contenido |
|---|---|---|
| `e4daaa9` | Codex (asistente IA) | Trabajo de Codex preservado sin cambios: pantalla `/acceso` por roles, enlace en el login, configuración `dc106-qa` |
| `72f57e9` | Claude Code (asistente IA) | `fix(test)`: configuración de build `test` propia para `ng test` |

Backend: sin commits, porque no hubo cambios de código.

## 3. Correcciones implementadas

### Frontend: los 7 fallos y la desconexión de Karma

**Causa raíz:**
- El builder `@angular/build:unit-test` compila por defecto con la configuración `development`, que sustituye `environment.ts` por `environment.development.ts`.
- Desde `a60186a` (Cristian, «mejoras»), ese entorno habilita 6 rutas de personal para `ng serve`.
- Las specs FE-54, FE-55, FE-58 y `qa-navegacion` verifican el entorno de producción y el mapeo oficial, pero recibían el de desarrollo.
- Las aserciones fallaban y dejaban navegaciones del Router vivas tras destruir el inyector (`NG0205` en `afterAll`). Eso hacía que Chrome dejara de responder: «Disconnected, no message in 30000 ms».

**Comprobaciones:**
- **No era un defecto de Codex.** En `develop` limpio (worktree `DC-107/front-base`) fallaba igual.
- **Fallos deterministas:** cada spec fallaba también aislada. El número variable (3, 4 o 7) solo dependía de en qué punto se desconectaba Chrome.

**Corrección:**
- nueva configuración `build:test` sin `fileReplacements` y `test.options.buildTarget = PRISALY-Front:build:test`;
- ninguna prueba eliminada ni aserción debilitada;
- guards y permisos intactos.

**Resultado:**

| Comprobación | Resultado |
|---|---|
| Suite completa | **918/918**, 0 fallos, 0 omitidas, sin desconexión |
| `develop` limpio con la misma corrección | 917/917 |
| `tsc` app y spec | 0 errores |
| Builds production, qa y dc106-qa | Correctas |

### Backend: M5 (HTTP 404 al iniciar intento)

**Diagnóstico:** el 404 **no es un defecto**. `IntentosM5Controlador` solo se registra con `prisaly.m5.inicio-finalizacion.publicacion.enabled=true`. Ese valor es `false` en `application.yml` (decisión DC-BE-CIERRE-FUNCIONAL-09) y dc105 no lo activaba. El esquema (V001–V013 + DD-02 B), los permisos y los contratos ya estaban.

**Incremento mínimo, solo en el QA local dc105:**
- `DC-107/m5-qa.yaml` con `PRISALY_M5_INICIOFINALIZACION_PUBLICACION_ENABLED=true`.
- Se aplica con el mismo compose de DC-106. `DC-107/OPERAR.ps1` lo hace con PowerShell 7; con 5.1 hay que usar el comando Bash equivalente (`DC-107/m5-arranque.log`).
- Solo se recreó el contenedor del backend; PostgreSQL no se tocó.
- Es reversible: basta con arrancar sin el archivo.

**Pruebas previas, sobre el código actual:** 6 IT M5/M7 + 2 suites unitarias, **53/53** (`DC-107/m5-focal.log`).

## 4. Funcionalidades verificadas por rol (HTTP real contra dc105)

Recorrido `DC-107/recorrido-m5.py` contra `http://localhost:18175/api` (nginx del Frontend → backend). **41/41 pasos.** Evidencia en `DC-107/recorrido-m5.json`. Las credenciales ficticias se leen de `DC-106/.private` (DPAPI); no se escriben ni se muestran.

| Rol | Verificado |
|---|---|
| **Administrador** | Login 200; perfil `/me` con rol ADMINISTRADOR; catálogo de permisos; búsqueda de estudiante; logout 204 y token revocado (401). Esta es la comprobación de revocación que quedó interrumpida en DC-106A |
| **Docente** | Login 200; perfil con rol DOCENTE; **403 `ADMIN_REQUIRED`** en administración; caso creado (escenario, pregunta, opción, rúbrica) y publicado; sesión programada; estudiante convocado; sesión abierta (`PATCH estado` → `EnCurso`); logout |
| **Estudiante** | Código pedido (202) y recibido por Mailpit; validación 204 con cookie; consulta de su convocatoria; **401** en rutas de personal y de casos |
| **M5** | Inicio antes de abrir la sesión: **403 `INICIO_NO_AUTORIZADO`** (E1). Tras abrirla: iniciar **201**, reanudar **200** (mismo intento), consultar 200, responder 200, intento ajeno **404 `INTENTO_NO_ENCONTRADO`**, finalizar 200, finalizar otra vez 200 con el mismo `fin`. En BD: `Intento.estado = Finalizado`. Logout del estudiante con cookie revocada (401) |
| **M6** | `POST /v1/evaluacion/intentos/{id}`: **404**, como corresponde (apagado) |

Altas de docente y estudiante y activación docente: las verificó Codex en DC-106 (`DC-106/http-cuentas.json`). No se repitieron, para no crear cuentas nuevas.

**No verificado en DC-107:** el recorrido por las **pantallas** Angular. El HTTP pasó por el mismo nginx que sirve Angular, pero no se manejó la interfaz.

## 5. M6: estado y dependencias

- **Esquema** presente en dc105: `Calificacion`, `LiberacionCalificacion` (V019) y `RetroalimentacionGeneral`.
- **Código actual:** `EvaluacionDocenteM6HttpIT` 12/12, `LiberacionN2QaHttpIT` 2/2, `ResultadosDd12QaHttpIT` 1/1, `NotificacionCalificacionRf27QaHttpIT` 1/1, `ConsultaIntentoEvaluacionM5M6IT` 3/3, `EvaluacionM6DesactivadaHttpIT` 1/1. Total **20/20** (`DC-107/m6-focal.log`).
- **Dependencia de datos encontrada:** M6 evalúa con `RubricaPregunta` (valor por pregunta). La rúbrica que crea el recorrido de Codex solo lleva `criterios`, así que dc105 tiene **0** `RubricaPregunta`. M3 ya lo admite por HTTP: `PUT /v1/casos/{id}/rubrica` con `"preguntas":[{"idPregunta","valorPregunta","criterio"}]`. No hace falta cambiar código.
- **Preparado, no aplicado:** `DC-107/m6-qa.yaml` (evaluación, liberación y retroalimentación; RF-27 apagado, porque además exigiría el worker M7). Se aplicará cuando DC lo autorice.

## 6. Simulador: D-SIM y D-ESQ-1

**Componentes:**
- **Backend:** V017 `GuionNarrativoCaso` (guion versionado e inmutable al publicar, hash SHA-256 y definición JSON) y V018 `FaseIntento` (Exploración → Cuestionario por intento, sin retroceso).
- **Código:** M3 narrativa (`prisaly.m3.narrativa.publicacion.enabled`), M5 fases (`prisaly.m5.exploracion.fases.enabled`) y rutas P1–P4 (`…fases.http.enabled`). Todo apagado.
- **Frontend:** `features/simulador` (exploración, investigación, narrativa, mundo técnico, Unreal) tras `requiereEntornoDemostracion`, solo en el entorno `demo`.

**Compatibilidad comprobada en PostgreSQL 16.15 desechable** (contenedor nuevo, sin volumen y eliminado; `DC-107/dsim-prueba.log`):
- V001–V023 y después V017 y V018 se aplican **sin errores** (33 tablas).
- No hay colisión de tablas, funciones ni triggers con V019–V023.
- `FaseIntento` y el `CHECK numeroIntento 1..2` de V021 son compatibles.
- **El único conflicto es el número de versión:** Flyway rechaza V017/V018 en una base que ya tiene V019–V023, como pasa en dc105 (lo demuestra `MigracionesDc92PostgreSqlIT`).

**Propuesta concreta (requiere autorización de DC):**
1. **D-SIM:** promover el simulador como **V024** (`GuionNarrativoCaso`) y **V025** (`FaseIntento`), con el mismo DDL, en `db/sim-dc107/`. Nuevo contrato M4 `bd0052-v013-integral-dc92-sim`. Las V017/V018 originales se conservan para las bases QA históricas del simulador (contratos `*-sim-a`, `*-sim-ab`).
   - **Prueba:** extender `MigracionesDc92PostgreSqlIT` con instalación limpia hasta V025 y actualización de una base dc92 a V024/V025.
2. **D-ESQ-1:** se mantiene el orden V014–V016 → V019 → V020 → V021–V023 → (V024–V025).
   - El QA FE-60 (V020 sola) se reconstruye desde `pg_data-pre-v020.tgz`. **No** se usa `outOfOrder`.
   - Antes de V021, en cada base: `SELECT count(*) FROM "Intento" WHERE "numeroIntento" > 2` debe dar 0.
3. **Decisiones académicas o de producto pendientes:** guion y pistas, decisiones académicas en la exploración y si la exploración cuenta en la evaluación. Sin ellas no se activan los interruptores del simulador.

## 7. Comparación con V27 en dc105 (demostrado de punta a punta en QA)

| RF | En dc105 (HTTP real) | Solo IT o pendiente |
|---|---|---|
| RF-01, RF-20 | ✔ código por correo, cookie, revocación | — |
| RF-02 | ✔ 401/403 por rol | — |
| RF-03 | ✔ (DC-106: alta y activación; DC-107: perfil, permisos, 403) | — |
| RF-04 | ✔ convocatoria del estudiante | — |
| RF-05 – RF-09, RF-17 | ✔ caso, escenario, pregunta, opción, rúbrica, publicación | Rúbrica con `preguntas` (M6) |
| RF-18 | ✔ sesión programada | — |
| RF-19 | ✔ apertura manual | DD-01 automático: solo IT |
| RF-10 – RF-12, RF-22, RF-23 | ✔ inicio, reanudación, respuesta, fin, E1 | Ingreso tardío y vencimiento: IT (`PoliticasInicioVencimientoM5HttpIT` 13/13) |
| RF-13, RF-14, RF-26, RF-27 | ✘ M6 apagado | IT 20/20; falta autorización DC |
| RF-15, RF-16, RF-21, RF-24, RF-25 | ✘ apagados | IT (DC-96); falta activación |

**No se declara 27/27.**

## 8. Bloqueos restantes

| Bloqueo | Tipo | Quién |
|---|---|---|
| Activar M6 en QA (`m6-qa.yaml`) y rúbricas con `preguntas` | Autorización | DC |
| D-SIM: renumerar V017/V018 como V024/V025 | Técnica, requiere autorización | DC |
| Guion y pistas, decisiones de exploración y su peso en la evaluación | Académica | DC / docentes |
| D-ESQ-1 en QA FE-60 (reconstrucción) | Técnica | DC |
| D-78-2 en producción (claves, SMTP institucional, proxy) | Técnica | DC |
| Recorrido por las pantallas Angular | Trabajo pendiente | Equipo / próximo incremento |

## 9. SHA finales

| Repositorio | Rama | SHA |
|---|---|---|
| Backend | `integration/prisaly-cierre-funcional` | `c4ceaea6bbbc0ce5b1679469241ab763d914c380` (sin cambios) |
| Frontend | `trabajo/dc107-cierre-funcional` | `72f57e9` (2 commits sobre `7ad2a2d`; sin push) |
| Frontend | `integration/prisaly-cierre-funcional` | `7ad2a2d` (sin tocar) |

**Rastro del trabajo:**
- Worktree auxiliar `DC-107/front-base`, en modo *detached* en `7ad2a2d`. Usa un *junction* a `node_modules` y tiene `angular.json` modificado solo para la prueba. Se puede quitar con `git worktree remove --force`.
- La regresión oficial completa del Backend no se repitió, porque desde 1980/1980 no hubo cambios de código salvo `ClasificacionErrorCorreo`, que tiene sus pruebas en verde.

## 10. Próximo incremento ejecutable

1. **Con la autorización de DC para M6 en QA:**
   - aplicar `m6-qa.yaml`;
   - ampliar `recorrido-m5.py` para que la rúbrica lleve `preguntas`;
   - recorrer evaluación (201) → liberación (201) → consulta del estudiante (200) → retroalimentación DD-12.

   Llegaría a **Resultados**, el último eslabón del recorrido pedido.
2. Publicar la rama `trabajo/dc107-cierre-funcional` y abrir su PR hacia `develop` (corrección de la suite Angular), sin merge.
3. Recorrido por las pantallas Angular con el navegador sobre dc105 (`/acceso` → login → paneles).
4. Implementar la propuesta D-SIM V024/V025 con su IT, en una rama propia, cuando DC la autorice.
