# M5 | Informe de cierre técnico

Fecha: 2026-11-01. Responsable: Integrante 4. Rama `feature/m5-cierre-tecnico`, creada desde develop `8b6dc33`.

## Dictamen: PARCIALMENTE CERRADO

**Cerrado en código (pendiente de confirmar con Maven):**

- operaciones sobre un intento existente (consultar, registrar, actualizar provisional, finalizar);
- persistencia con bloqueo y reloj de PostgreSQL;
- proxy de protección como único bean;
- identidad de M1 y titularidad de M4;
- proveedor de historial para M4 (M5-INC-08);
- orden de respuestas conciliado (M5-INC-09).

**No cerrado:**

1. **Pruebas:** las de esta rama no se ejecutaron con Maven todavía.
2. **Publicación:** la rama `feature/m5-cierre-tecnico` no está publicada ni fusionada.
3. **Disponibilidad funcional para estudiantes: bloqueada.** Sin DD-02 nadie se
   autentica. Sin ARQ-008.3 no hay endpoints. Iniciar y reanudar dependen de
   M4 y de DD-14, y el contenido del caso depende de M3.

| Condición de cierre | Estado |
|---|---|
| Proveedor de historial M4–M5 conectado | Sí, en código (`ParticipacionesM4DesdeM5`); falta Maven y la revisión del Integrante 3 |
| Orden de respuestas conciliado | Sí, en código (`ResumenIntento`); falta Maven |
| Todas las pruebas pertinentes pasan | **Pendiente:** 181 unitarias y 38 IT esperadas en M5 |
| La aplicación completa arranca | **Pendiente:** `-pl app -am -Pdocker-it verify` |
| Contratos documentados | Sí: `M5-DOCUMENTO-TECNICO.md`. API REST como propuesta, sin endpoints |
| Riesgos identificados | Sí: documento técnico, sección 10, y `M5-INCIDENCIAS.md` |
| Decisiones no aprobadas siguen bloqueadas | Sí |
| Código integrado y publicado | **No:** rama local, pendiente de PR |
| Informe de cierre verificable | Este documento, al completar la sección 3 |

## 1. Diagnóstico inicial (develop `8b6dc33`)

- El PR #9 se fusionó con `50fe1f1`, no con `a12f69d`. develop conserva la
  historia anterior a la normalización (M5-INC-10) y la rama publicada diverge
  de develop. Por eso el cierre parte de develop, en una rama nueva.
- `ParticipacionesPort` de M4 no tenía proveedor: M4 respondía 503 al editar o
  reasignar sesiones.
- El orden de las respuestas no era el mismo en memoria y tras recargar.
- ARQ-008.3 sigue sin rutas para M5 en el repositorio.
- M6 sigue en su rama, sin contrato para M5. M7 ya está en develop.

## 2. Cambios

| Commit | Contenido |
|---|---|
| `a8c6a8b` | `fix(m5)`: orden determinista de respuestas (M5-INC-09) |
| `e84c36f` | `feat(m5)`: proveedor del `ParticipacionesPort` de M4 (M5-INC-08) |
| (docs) | Incidencias, documento técnico (integración M4–M5, matriz M1–M7, riesgos) y este informe |

Solo cambia `m5/`. Los commits tienen autora y committer
`INGRID <imontoya_624@unihumboldt.edu.co>` y no llevan trailers de herramientas.

## 3. Pruebas

**Ejecutadas en Cowork (no equivalen a Maven):**

- M5 compila con `javac -Werror`.
- JUnit 5.10.2: **167/167** unitarias fuera de Maven. Son las 158 anteriores
  ejecutables aquí más 3 de orden y 6 del proveedor. Excluidas por librerías
  ausentes: `ProgramacionM4ErroresHttpTest` y `SolicitudRespuestaProvisionalValidacionTest`.
- Las IT compilan salvo por símbolos de Testcontainers, Spring Boot y Jackson;
  no hay ningún error en código de M5.
- El orden de `uuid` en PostgreSQL 16.15 se comprobó en una base aislada:
  `10… < 80… < bb…`, bytes sin signo.

**Pendientes en Windows** (los resultados se rellenan al ejecutarlos):

```powershell
git switch feature/m5-cierre-tecnico
.\mvnw.cmd -pl m5 -am test                  # esperado: M5 181 unitarias
.\mvnw.cmd -pl m5 -am verify -Pdocker-it    # esperado: M5 181 + 38 IT
.\mvnw.cmd -pl app -am test
.\mvnw.cmd -pl app -am verify -Pdocker-it   # arranque completo con ParticipacionesM4DesdeM5
```

Al ejecutar, conviene vigilar:

- `ParticipacionesM4DesdeM5IT` usa los adaptadores JPA reales de M4;
- en `app`, M4 recibe ahora el proveedor de M5 en lugar de su respaldo.

| Comando | Resultado |
|---|---|
| `-pl m5 -am test` | (pendiente) |
| `-pl m5 -am verify -Pdocker-it` | (pendiente) |
| `-pl app -am test` | (pendiente) |
| `-pl app -am verify -Pdocker-it` | (pendiente) |

## 4. Bloqueos pendientes

| Bloqueo | Quién decide | Efecto |
|---|---|---|
| DD-02 | Dirección Central y M1 | Ningún estudiante accede |
| DD-14 | Dirección Central | Sin repetición ni inicio de un segundo intento |
| ARQ-008.3 | Arquitectura | Sin endpoints de M5 |
| Catálogo de `motivoFinalizacion` | Dirección Central | `ENTREGA_ESTUDIANTE` sigue provisional |
| Elegibilidad para iniciar y reanudar; retiro | Dirección Central y M4 | Iniciar y reanudar sin implementar |
| Reloj académico y vencimiento | Dirección Central | `TIEMPO_VENCIDO` sin regla de vencimiento |
| Contenido del caso | M3 | El simulador no puede mostrar preguntas reales |
| Intento finalizado → M6 | M6 | Sin evaluación |
| Decisiones 4 y 7 (códigos HTTP) | Arquitectura | 403 provisionales |
| `consulta-bd0052` de M4 | Integración | Sin la propiedad, la titularidad responde 503 |
| Historial de develop (M5-INC-10) | Equipo | Trailers de sesión en develop |

## 5. Contrato para el simulador

**No hay endpoints.** ARQ-008.3 no define rutas de M5, y la propuesta de la
sección 7 del documento técnico sigue sin ratificar. El simulador **no puede
integrarse** con M5 hasta que se decida DD-02 y ARQ-008.3, y hasta que existan
los contratos de M3 (contenido) y M4 (elegibilidad).

Lo que el simulador puede preparar sin inventar el contrato:

- los modelos de vista basados en `IntentoVistaProvisional`;
- el manejo de errores `{code, message}` con los códigos estables del traductor;
- su capa de puertos (`ParticipacionGateway`), que ya falla con "contrato pendiente".

## 6. Integración Git (sin ejecutar)

1. Ejecutar la sección 3 en Windows y completar la tabla.
2. Revisar los commits (`git log 8b6dc33..feature/m5-cierre-tecnico`) y el diff (`git diff 8b6dc33 feature/m5-cierre-tecnico`).
3. Con autorización: publicar `feature/m5-cierre-tecnico` (es una rama nueva, sin force) y abrir
   un PR hacia develop.
4. No volver a fusionar `feature/m5-participacion-intentos` (`a12f69d`) en
   develop: duplicaría los 56 commits de M5 (M5-INC-10).
