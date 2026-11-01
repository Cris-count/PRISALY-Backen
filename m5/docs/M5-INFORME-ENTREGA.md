# M5 | Informe de entrega del trabajo autónomo extendido

Fecha: 2026-11-01. Responsable: Integrante 4.

**Rama:** `trabajo/m5-avance-nocturno`. Commit inicial `1da1c8a`; el commit
final es el que añade este informe (ver `git log`).

**Evaluación:** M5 está **listo para revisión técnica** de lo implementado
(seguridad, persistencia, integración M1/M4). **No está terminado**:

- faltan iniciar y reanudar intentos y la API REST;
- dependen de DD-02, DD-14, ARQ-008.3 y de contratos de M4.

## 1. Commits de esta sesión

| Commit | Tipo | Contenido |
|---|---|---|
| `d4a6b11` | fix | El proxy pasa a ser el único bean de operaciones (M5-INC-02); repositorios Spring Data de paquete; pruebas de contexto y de arquitectura |
| `2bfdc09` | fix | Identidad de M1 sin `userId` denegada (M5-INC-03); pruebas de suplantación y de filtración |
| `218114b` | fix | Cuerpo mal formado y tipo incorrecto con `{code, message}` y mensaje genérico (M5-INC-04) |
| (final) | docs | Registro de incidencias, documento técnico (contratos, frontend, trazabilidad, plan de cierre), aviso de ciclo en `HistorialParticipacionPort` (M5-INC-08) y este informe |

Ningún archivo fuera de `m5/` cambia:

```powershell
git diff --stat 1da1c8a -- . ':!m5'   # vacío
```

## 2. Archivos

**Código de producción:**

- `ConfiguracionParticipacion` (reescrito);
- `ConfiguracionAccesoEstudiante` (eliminado, fusionado);
- `FachadaParticipacionTransaccional` (Javadoc);
- `IntentoJpaRepository` y `RespuestaJpaRepository` (de paquete);
- `IdentidadEstudianteDesdeM1`;
- `ManejadorErroresM5`;
- `presentation/package-info`;
- `api/HistorialParticipacionPort` (Javadoc).

**Pruebas:**

- nuevas: `ConfiguracionParticipacionTest`, `ArquitecturaAccesoProtegidoTest`, `FachadaSinControlDePrueba`;
- ampliadas: `IdentidadEstudianteDesdeM1Test`, `ManejadorErroresM5Test`;
- con nueva configuración de prueba, **sin cambiar ninguna afirmación**:
  `RepositorioIntentosJpaIT` y `AccesoEstudianteM4M5IT`.

**Documentos:**

- nuevos: `M5-INCIDENCIAS.md`, `M5-DOCUMENTO-TECNICO.md`, este informe;
- actualizados: `M5-REST-DEPENDENCIAS-Y-DECISIONES.md`, `M5-CONTINUIDAD-AVANCE-NOCTURNO.md`.

## 3. Riesgos eliminados y pendientes

**Eliminados:**

- elusión del control de acceso por inyección de la fachada, la interfaz,
  repositorios, servicios o puertos desde presentación;
- arranque con un proveedor ausente;
- identidad de M1 inconsistente aceptada;
- respuestas de error con formato distinto o con texto del conversor.

**Pendientes:** documento técnico, sección 10. En resumen:

- la titularidad no evalúa elegibilidad;
- el historial para M4 está bloqueado por el ciclo de dependencias;
- el 404 incluye la referencia del cliente;
- falta validar esta rama en Windows.

## 4. Pruebas

**Nuevas o ampliadas (18 unitarias):**

- `ConfiguracionParticipacionTest`: 8;
- `ArquitecturaAccesoProtegidoTest`: 5;
- `IdentidadEstudianteDesdeM1Test`: +3;
- `ManejadorErroresM5Test`: +2.

**Ejecutadas en Cowork, sin equivaler a Maven** (`javac --release 21 -Werror` y JUnit 5.10.2):

- 158/158 unitarias aprobadas: las 140 anteriores más las 18 nuevas.
- Excluidas aquí por librerías ausentes: `ProgramacionM4ErroresHttpTest` (hamcrest), `SolicitudRespuestaProvisionalValidacionTest` (proveedor de Bean Validation) y las 30 IT.
- Las IT compilan salvo por símbolos de Testcontainers y Spring Boot Test; no hay ningún error en código de M5.
- **Reglas de arquitectura comprobadas con clases sonda:** se añadió temporalmente
  una clase de presentación con un campo `RepositorioIntentos`, y otra que
  construía `ControlAccesoIntentos`. Cada una hizo fallar su regla, y se retiraron.
- **Regresión de M5-INC-03:** la prueba 6c falla con el código anterior y pasa
  con la corrección.

**No ejecutado aquí:** Maven, Testcontainers y el arranque de `app`. Maven
Central responde 403 y no hay Docker.

**Resultados esperados en Windows:**

| Comando | Esperado |
|---|---|
| `-pl m5 -am test` | M5: **172** unitarias (154 + 18) |
| `-pl m5 -am -Pdocker-it verify` | M5: 172 unitarias + **30** IT |
| `-pl app -am -Pdocker-it verify` | Arranque del contexto completo con un único bean de operaciones |

## 5. Validación en PowerShell

```powershell
cd C:\Users\Heiner\Desktop\PRISALY\PRISALY-Backen
git switch trabajo/m5-avance-nocturno
git status --short
git log --oneline 1da1c8a..HEAD
git diff --stat 1da1c8a -- . ':!m5'          # debe estar vacío
.\mvnw.cmd -pl m5 -am test
.\mvnw.cmd -pl m5 -am -Pdocker-it verify
.\mvnw.cmd -pl app -am test
.\mvnw.cmd -pl app -am -Pdocker-it verify
```

Puntos a vigilar:

- `RepositorioIntentosJpaIT` y `AccesoEstudianteM4M5IT` arrancan con los
  repositorios Spring Data de paquete.
- `PersonalAuthSecurityIT` (`app`) arranca con `ConfiguracionParticipacion`,
  que ahora exige `IdentidadEstudianteActual` y `TitularidadIntentos`.

## 6. Plan de integración Git (sin ejecutar)

1. Validar la sección 5 en Windows.
2. Con autorización:
   `git switch integracion/m5-develop` y
   `git merge --ff-only trabajo/m5-avance-nocturno`.
3. Con autorización:
   `git switch feature/m5-participacion-intentos` y
   `git merge --ff-only integracion/m5-develop`.
   Es un avance rápido sobre `f4c981c`, sin reescribir historia.
4. Antes de publicar, comprobar que develop no avanzó; si avanzó, repetir la
   conciliación en una rama nueva.
5. `git push` de la rama M5 (sin force) y revisión del PR.

## 7. Referencias

- Contrato para el frontend, contrato para M4, decisiones DD, trazabilidad y
  plan de cierre: `M5-DOCUMENTO-TECNICO.md`.
- Incidencias y observaciones externas (incluidas las 17 IT omitidas de M4,
  remitidas a su responsable): `M5-INCIDENCIAS.md`.
