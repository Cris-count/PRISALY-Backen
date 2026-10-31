# M5 ← M4 | Conciliación del contrato de programación

Estado al 2026-10-31. Rama `feature/m5-participacion-intentos`. Responsable: Integrante 4.

**Estado del contrato: publicado, pendiente de conciliación e integración.**

## 1. Contrato publicado por M4

Fuente: `feature/m4-programacion-convocatorias`, commit `315746e`, PR #2.
La rama está en `9941085`, y los puertos de `com.prisaly.m4.api` no cambian
después de `315746e`.

| Elemento | Firma |
|---|---|
| `ProgramacionParticipacionPort` | `Optional<ContextoProgramacion> consultarConvocatoria(UUID convocatoriaId, UUID estudianteAutenticadoId)` y `consultarSesion(UUID sesionId, UUID estudianteAutenticadoId)` |
| `ProgramacionAutenticadaPort` | Las mismas consultas sin estudiante: lo obtiene de `StudentAccessPort` de M1 |
| `ContextoProgramacion` | `convocatoriaId`, `estudianteId`, `sesionId`, `casoId`, estados de sesión, convocatoria y caso, ventana prevista y real, `tiempoMaximoParticipacion`, `observadoEn` |
| `ConsultaProgramacionNoDisponibleException` | Fallo de proveedor o esquema (`M4-CONSULTA-PROGRAMACION-NO-DISPONIBLE`); nunca concede acceso |

Semántica que declara M4:

- Vacío, tanto si la convocatoria no existe como si su titular es otro estudiante.
- Una consulta con resultado demuestra la titularidad. **No** autoriza iniciar,
  reanudar ni repetir un intento.
- M5 resuelve `Participacion."idConvocatoria"` con sus propios contratos. M4 no
  lee `Participacion` ni `Intento`.
- Activación: el adaptador JDBC solo se crea con
  `prisaly.m4.consulta-bd0052.enabled=true`. Por defecto, cualquier consulta
  lanza `ConsultaProgramacionNoDisponibleException`.

## 2. Encaje con los puertos provisionales de M5

| M5 | M4 | Diferencia |
|---|---|---|
| `TitularidadIntentos.titularDe(intento)` → titular | `consultarConvocatoria(convocatoria, estudiante)` → contexto o vacío | M4 no revela el titular sin el estudiante. El puerto de M5 pasaría a ser "¿es titular?" |
| `ControlAccesoIntentos`: inexistente → 404, ajeno → 403 | Vacío para ambos casos | Con M4, un intento ajeno resulta en 404 (decisión 4) |
| Errores de los puertos: se propagan | `ConsultaProgramacionNoDisponibleException` | M5 no la traduce: hoy acabaría en 500 genérico, no en 503 |
| Identidad: `IdentidadEstudianteActual` (parche M1) | `ProgramacionAutenticadaPort` vuelve a llamar a M1 | Usar `ProgramacionParticipacionPort` evita resolver la identidad dos veces |

Propuesta de conciliación, para acordar con el Integrante de M4 (no
implementada):

1. M5 lee `Participacion."idConvocatoria"` de un intento (tabla propia).
2. M5 llama a `consultarConvocatoria(idConvocatoria, studentId)`, con el
   `studentId` del adaptador de M1.
3. Un resultado vacío se traduce en `IntentoNoEncontradoException`.
4. `ConsultaProgramacionNoDisponibleException` se traduce en un error técnico.
   No concede acceso.

## 3. Hallazgos de M4 sobre M5

`m4/review/conciliacion-bd0052/M4M5ConsoleProbe.java` y
`evidence/contrato-m4-m5.txt` (`9941085`) ensamblan `ControlAccesoIntentos` con
M4 sobre PostgreSQL. Se escribieron contra la versión publicada de M5
(`3fc823e`):

- Confirman que el estudiante ajeno produce 404.
- Señalan una brecha: el error técnico de M4 no se traduce a 503.
- Esperan 401 para "sin identidad". Con `5c7bd7b` (403) esa comprobación ya
  no se cumpliría.

## 4. Riesgos al integrar develop y M4

| Riesgo | Detalle | Mitigación |
|---|---|---|
| Autoconfiguraciones de M4 | `AutoConfiguration.imports` registra `M4Provider`, `M4ProgramacionConsulta` y `M4Schema` en cuanto `app` carga M4 | Revisar el arranque de `app` con M5 |
| Verificación de esquema | `M4SchemaAutoConfiguration` exige exactamente 21 tablas y aborta el arranque si hay otras | M5 no crea tablas; los IT de M5 usan un contexto JPA acotado |
| V010 | Propuesta con tablas paralelas. `1d513f8` la excluye de `db/migration` | Comprobar en `app` que Flyway no la descubre |
| `ParticipacionesPort` de M4 | M4 espera que M5 indique si una sesión tiene participaciones o intentos, coordinando el bloqueo. Sin proveedor responde 503 | Contrato nuevo de M5 hacia M4, pendiente de acordar |
| Dependencias | M4 depende de M1 y M3; M5 dependería de M4. No hay ciclo mientras M4 no dependa de M5 | Mantener `ParticipacionesPort` como puerto de M4 |
| Formato de errores | M4 usa `{code, message}` con códigos `M4-…` | Pendiente de ratificación central |

## 5. Verificación (fuera de Maven)

Borrador local, no publicado y ya eliminado: esta rama (`13779e9`) más
`feature/m4-programacion-convocatorias` (`9941085`, que contiene develop
`03b0899`).

- Integración sin conflictos (`git merge-tree`).
- `git apply --check` del parche de M1: OK.
- M5 con el parche aplicado compila con `-Werror`, usando M1 y `m4.api`
  compilados aparte. `ConsultaProgramacionNoDisponibleException` emite el aviso
  `serialVersionUID`, que es de M4.
- Suite unitaria: 112/112 con JUnit 5.10.2.
- No se ejecutaron los 9 casos de `IdentidadEstudianteDesdeM1Test`: faltan los
  jars de Spring Security en este entorno. Se ejecutaron sobre develop
  `03b0899`, y M4 no modifica `m1/`.

No sustituye a `mvnw` ni a los IT con Docker.
