# HANDOFF — Nicolás · integracion/alineacion-final

## Repositorios y Pull Requests (Draft, no fusionar)

| | Repositorio | Rama | PR |
|---|---|---|---|
| Backend (canónico) | https://github.com/Cris-count/PRISALY-Backen | `integracion/alineacion-final` | https://github.com/Cris-count/PRISALY-Backen/pull/104 |
| Frontend | https://github.com/Cris-count/PRISALY-Front | `integracion/alineacion-final` | https://github.com/Cris-count/PRISALY-Front/pull/36 |

## SHA exactos (lo que se construye y se validó)

- Backend, código: `eb88ad451e6ea9d6561bbd3c4a3a20b7460783c7` (= `b23c217c429ee53a1567850308acfdb0d95a8fc8` + un commit documental). La rama solo añade `ops/alineacion-final/`.
- Frontend, código: `66d9e7108d4a33950629dfff4f0dbe361496029e`. La rama solo añade `docs/integracion/ALINEACION-FINAL.md`.
- Las cabezas de rama pueden avanzar con commits de `ops/` o de documentación. El script verifica que los SHA de código del manifiesto estén contenidos en la rama publicada.

## Manifiesto

`PRISALY-Backen` › `integracion/alineacion-final` › `ops/alineacion-final/PRISALY-INTEGRACION-MANIFEST.json` (canónico). El Frontend lo referencia en `docs/integracion/ALINEACION-FINAL.md`.

## Actualizar (descarga, verifica SHA, crea worktrees y construye imágenes, sin arrancar)

```powershell
git -C <ruta>\PRISALY-Backen fetch origin integracion/alineacion-final
git -C <ruta>\PRISALY-Backen archive -o $env:TEMP\prisaly-ops.tar origin/integracion/alineacion-final ops/alineacion-final/ACTUALIZAR-PRISALY.ps1
tar -xf $env:TEMP\prisaly-ops.tar -C $env:TEMP
powershell -ExecutionPolicy Bypass -File $env:TEMP\ops\alineacion-final\ACTUALIZAR-PRISALY.ps1 -Backend <ruta>\PRISALY-Backen -Front <ruta>\PRISALY-Front -SoloPreparar
```

Requisitos: repositorios sin cambios sin confirmar (si los hay, el script se detiene), Docker Desktop, JDK 21, Node y npm. No cambia tus ramas: usa worktrees detached en `%USERPROFILE%\prisaly-alineacion\{backend,frontend}` y genera los secretos en `%USERPROFILE%\prisaly-alineacion\secretos` (nunca en el repositorio).

## Arrancar

```powershell
powershell -ExecutionPolicy Bypass -File $env:TEMP\ops\alineacion-final\ACTUALIZAR-PRISALY.ps1 -Backend <ruta>\PRISALY-Backen -Front <ruta>\PRISALY-Front
```

- Proyecto Docker `prisaly-alineacion`, con volúmenes propios: no toca tu base ni tus contenedores. Usa los puertos 18175 (portal), 18030 (Mailpit) y 11030 (SMTP); deben estar libres.
- **Base nueva:** Flyway aplica `001–009, 013–016, 019–023` y el inicializador oficial crea `administrador.dc106@prisaly.test`. El botón «Administrador QA» funciona con el secreto generado.
- **Base existente** (otra ejecución con el mismo proyecto): compara su historial Flyway con el manifiesto y se **detiene** si difiere. Nunca ejecuta repair, clean ni migraciones fuera de orden.
- **Detener:** `... -Detener` (conserva volúmenes).
- URL: http://localhost:18175/acceso · Mailpit http://localhost:18030.

## Configuración necesaria

- Compose: `ops/alineacion-final/compose.yaml` (+ `compose.bootstrap.yaml` solo con base nueva). Flags y Flyway en el manifiesto. El flag M5 que lee el código es `PRISALY_M5_INICIOFINALIZACION_PUBLICACION_ENABLED`.
- Contrato de esquema M4: `bd0052-v013-integral-dc92`.
- Angular: `ng build --configuration dc106-qa` (capacidades `listadoCasos`, `consultaParticipaciones`, `reporteParticipacion`, `altaEstudiantes`, `activacionDocente`; acceso rápido QA).
- Docente para el botón «Docente QA» en una base nueva:
  1. Administrador → alta de `docente.demo@prisaly.test`.
  2. Activación con el enlace D-05 que llega a Mailpit.
  3. Asignar los 6 permisos en «Roles y permisos».
  4. Escribir su contraseña en `secretos\qa-docente.txt`.
- Estudiante: alta desde el panel. El acceso es por código de correo (DD-02 B), sin contraseña. Límite oficial: 5 códigos por cuenta cada 15 minutos.

## Funcionalidades verificadas (QA local, 27-nov)

- **M1:** acceso de administrador, docente y estudiante; alta de docente con activación D-05 y permisos; alta de estudiante; consulta de usuarios y roles; cierre de sesión con revocación.
- **M3:** listado de casos propios; creación, contenido, rúbrica y publicación (Borrador→Lista).
- **M4:** programación, convocatoria, apertura de sesión, participaciones DD-09 y CSV.
- **M5:** E1, iniciar, reanudar, contenido sin corrección, responder, titularidad, finalizar (idempotente), intento finalizado protegido y recuperación.
- **M6:** evaluación inmutable, permisos, retroalimentación DD-12, liberación idempotente y resultado del estudiante.
- **M7:** correos de código, convocatoria y «Calificación disponible».
- **Script:** `ACTUALIZAR-PRISALY.ps1` probado en el equipo de Ingrid: base nueva, base existente y `-Detener`.
- **Evidencia:** recorrido HTTP 71/71 en `ops/alineacion-final/evidencias/`; visor Unreal 119/119 con dobles del canal.

## Pendientes estructurales (no integrados)

1. **Narrativa M3, fases P1–P4 e investigación persistida (V024 DC-116).** Hay tres barreras: el orden de Flyway, el guard M4 de contrato cerrado y los contratos permitidos por `EsquemaFasesIntentoConfig`. Diagnóstico y ensayo en `ops/alineacion-final/conciliacion/`. El candidato V025/V026 se aplica limpio, pero falta un contrato M4 combinado (decisión de DC).
2. **Tu rama `feature/cb02b-investigacion-a1`** (catálogo visual, hash canónico, núcleo A1): no integrada porque depende del punto 1.
3. **`feature/m7-comunicaciones`:** no integrada (WIP).
4. **Asignación de la instancia visual por estudiante:** no existe servicio; hoy hay una sesión fija de laboratorio.
5. **Modalidad de casos (BE-FUNC-06):** no se localizó implementación.

## Integración con MUNDO-24

- Contrato del navegador: protocolo v1 (`PRISALY-Front/public/simulador-unreal/protocolo/v1`, 23 esquemas). Cliente `@epicgames-ps/lib-pixelstreamingfrontend-ue5.8@0.1.4`, con tu reconexión acotada ya incorporada (`69e6a65`, `38a980d`).
- Señalización esperada: player `ws://127.0.0.1:8898`, streamer `8899`, `streamerId` `PRISALY-LAB-03`, sesión visual `ses-lab-ps2-0001` (la misma en `-PrisalySesion=`). El visor solo acepta `ws://` en loopback.
- `/simulador-unreal` solo se habilita con `simuladorUnrealLocal` y el origen `http://127.0.0.1:4213`. El entorno `laboratorio` está en tu rama `fix/entrega-entorno-laboratorio` (`2adff17`), no en esta.
- Pendiente de prueba real: `mundo.listo` → `sesion.iniciar`/`orden.ack` → vídeo → puerta → interior → NPC → regreso → reconexión. **No se declara integrado hasta esa prueba.**
- Material de apoyo (fuera del repositorio, en el equipo de Ingrid): `PRISALY-ENTREGA\SIM-PREPARACION-ENTREGA-1.0` (verificación del paquete, GPU, arranque y apagado de MUNDO-24).

## Recuperación

`ops/alineacion-final/ROLLBACK.md`. En resumen: `-Detener` conserva los datos. Tu entorno y tus ramas no se modifican. Los worktrees se retiran con `git worktree remove`. Nunca `down -v`, repair ni clean.
