# Case Builder — contrato de narrativa para el Frontend (CB-02)

**Estado:** candidato. Todo sigue detrás de `prisaly.m3.narrativa.publicacion.enabled=false` y del esquema V017, que solo existe en QA. Nada de esto está en producción.
**Origen:** DC-CB-03 §4 · Rama `feature/cb02-catalogo-visual` (base `origin/develop` `7961b86`).

## 1. Operaciones (ya existían; no hay endpoints nuevos)

| Operación | Método y ruta | Permiso M1 | Respuesta |
|---|---|---|---|
| Consultar borrador y publicado | `GET /api/v1/casos/{idCaso}/narrativa` | `MODIFICAR_CASO` + `CONSULTAR_CASO`, y ser el docente del caso | `200 {idCaso, borrador, publicado}` (cada uno `GuionDto` o `null`) |
| Guardar borrador (reemplaza el vigente) | `PUT /api/v1/casos/{idCaso}/narrativa`, cuerpo = documento `prisaly-guion-1` completo | Igual | `200 GuionDto` |
| Publicar | `POST /api/v1/casos/{idCaso}/narrativa/publicar` | `PUBLICAR_CASO`, y ser el docente del caso | `200 GuionDto` |

`GuionDto = {idGuion, version, estado, hashDefinicion, fechaActualizacion, fechaPublicacion, definicion}`. `definicion` es el documento JSON tal como está guardado. Todas las respuestas llevan `Cache-Control: no-store`.

Fuente: `CasoNarrativaController.java`, `GuionNarrativoService.java`.

## 2. Errores

| HTTP | `code` | Cuándo |
|---|---|---|
| 422 | `GUION_INVALIDO` + `errores[] {ruta, motivo}` | El documento no cumple el formato o las reglas del catálogo (§4) |
| 403 | `FORBIDDEN` | Sin permiso o caso de otro docente |
| 404 | `CASE_NOT_FOUND` | El caso no existe |
| 409 | `CONTENIDO_BLOQUEADO` | El caso ya tiene una sesión programada: el guion es inmutable |
| 409 | `CASO_NO_PUBLICABLE` | Publicar el guion exige el caso `Publicado` y activo |
| 409 | `SIN_BORRADOR` | Se intenta publicar sin borrador |
| 409 | `CASE_NOT_EDITABLE` | Caso archivado |

El Front debe mostrar `errores[]` junto al campo indicado por `ruta` (por ejemplo `personajes[1].recursoVisual`).

## 3. Catálogo visual (D-CB-12: copia verificada por hash)

- Fuente canónica: `m3/src/main/resources/prisaly/catalogo-visual/catalogo-visual-1.json`.
- `hash` = SHA-256 de los bytes exactos del archivo. El Front guarda una copia idéntica y su prueba compara el SHA-256 de la copia con el valor publicado en este documento. **No edites la copia a mano:** se regenera desde el Backend.
- SHA-256 vigente: `bfa39caac8aaf2afb9c1177ac8024de14cac6a47a320f49e8b4813579ec7ccfe`

| Clave | Para qué |
|---|---|
| `edificios[].clave` (`edificio:hospital`, `edificio:comisaria`, `edificio:fiscalia`) | `espacios[].recursoVisual` |
| `edificios[].zoneId` / `puertaId` | Configuración para Unreal (no se guardan en el guion) |
| `puestos[].clave` (`npc:hosp-npc-doctor`, `npc:hosp-npc-paciente-01`, `npc:pol-npc-oficial`, `npc:fis-npc-fiscal`) | `personajes[].recursoVisual` |
| `puestos[].edificio` | Qué puestos se muestran al elegir un edificio (paso 3) |
| `capacidadMaximaPorEdificio` (3) | Capacidad efectiva = mín(3, puestos del edificio): Hospital 2, Comisaría 1, Fiscalía 1 |

Plaza Central (`comun.zoneId`) no se selecciona.

## 4. Cómo arma el constructor el documento `prisaly-guion-1`

| Paso del constructor | Campo |
|---|---|
| 2. Edificio seleccionado | Un `espacio` con `recursoVisual: "edificio:*"` (`idEspacio` y `titulo` los elige el Front, p. ej. `hospital` / «Hospital») |
| 3. Personaje en un puesto | Un `personaje` con `recursoVisual: "npc:*"`, `nombre` (narrativo, ≤ 100), `rol` (≤ 200) |
| 4. Saludo / diálogo principal / despedida | Una `intervencion` en el espacio de su edificio, con `dialogos[]` ordenados (1–50, ≤ 1000 cada uno). La despedida es opcional |
| Información obtenible (preparado) | `espacios[].informaciones[] {idInformacion, texto}` |
| `inicio` | `idEspacio` del primer edificio seleccionado |

Identificadores locales: `[a-z0-9][a-z0-9-]{0,63}`, únicos por tipo. Solo texto plano (sin `<etiquetas>` ni caracteres de control). Prohibidos los campos académicos (`pregunta`, `respuesta`, `nota`, `rubrica`…). Tamaño máximo: 256 KiB.

### Reglas que valida el Backend (motivo en `errores[]`)

| Motivo | Regla |
|---|---|
| `RECURSO_VISUAL_NO_CATALOGADO` | La clave no existe en el catálogo |
| `RECURSO_VISUAL_TIPO_INCORRECTO` | Un espacio usa `npc:*` o un personaje usa `edificio:*` |
| `EDIFICIO_DUPLICADO` | Dos espacios con el mismo edificio |
| `PUESTO_DUPLICADO` | Dos personajes en el mismo puesto físico |
| `EDIFICIO_NO_SELECCIONADO` | El puesto del personaje pertenece a un edificio que no está entre los espacios |
| `CAPACIDAD_EXCEDIDA` | Más personajes que la capacidad efectiva del edificio |
| `PERSONAJE_FUERA_DE_SU_EDIFICIO` | Un personaje con puesto interviene o dialoga en un espacio de otro edificio |

## 5. Ejemplos (los mismos que usan las pruebas)

- Válido, caso A (Hospital + Comisaría; médico + oficial; saludo, diálogo principal y despedida): `m3/src/test/resources/case-builder/guion-caso-a.json`
- Válido, caso B (Hospital + Fiscalía; el mismo médico visual con otro nombre y otro diálogo): `m3/src/test/resources/case-builder/guion-caso-b.json`
- Inválidos: ver `ValidadorGuionNarrativoCatalogoTest.java` (un caso por motivo).

Primera meta (DC-CB-03 §7): el caso A reducido al Hospital y al médico es un subconjunto válido de este contrato.

## 6. Lo que todavía no existe

- Las versiones de la narrativa y del catálogo ya se guardan; dónde guardar la versión y el hash del catálogo por guion es D-CB-3, pendiente.
- La distinción persistente entre caso tradicional e interactivo 3D es D-CB-13, pendiente.
- La lectura estudiantil (`GET /api/v1/estudiantes/intentos/{id}/narrativa`) depende de los flags de fases de M5 y de V018.
