# M5 | Inicio y reanudación de intentos

Fecha: 2026-11-02. Responsable: Integrante 4.
Rama `feature/m5-inicio-reanudacion`, creada desde `origin/develop` `19cf52b`.

## 1. Alcance autorizado

Dirección Técnica aprobó:

- M5 es el único escritor de `Participacion` en el primer inicio;
- se devuelve el mismo intento `EnCurso` del estudiante autenticado;
- reanudar no crea otro intento ni reinicia o amplía el tiempo;
- la creación y la recuperación respetan transacciones, unicidad y concurrencia.

Siguen **pendientes** y no se implementan:

- elegibilidad académica;
- cálculo de `limiteTemporal` (F3-04);
- estados y transiciones de `Participacion`;
- repetición (DD-14);
- catálogo de motivos de finalización y política de vencimiento;
- rutas REST y códigos HTTP (ARQ-008.3).

**Resultado en producción:**

| Operación | Estado |
|---|---|
| Reanudar un intento en curso | Disponible, sujeta a DD-02 y a `consulta-bd0052` |
| Primer inicio | Mecanismo implementado y probado, **bloqueado**: falla con `INICIO_NO_DISPONIBLE` |
| Endpoints REST | Ninguno |

## 2. Operación

`OperacionesParticipacion.iniciarOReanudar(ReferenciaConvocatoria)` devuelve un
`ResultadoInicio`:

- `intento`: un `ResumenIntento` con las respuestas persistidas;
- `inicio` y `limiteTemporal`: los valores persistidos en `"Intento"`;
- `reanudado`: indica si el intento ya existía.

Cadena de llamada:

```text
AccesoProtegidoParticipacion (proxy, único bean)
  └─ ControlAccesoIntentos.verificarConvocatoria
       ├─ IdentidadEstudianteActual (M1: StudentAccessPort)
       └─ TitularidadIntentos.deConvocatoria (M4: ProgramacionParticipacionPort)
  └─ FachadaParticipacionTransaccional (una transacción de escritura)
       └─ ParticipacionFacade → IniciarIntentoServicio
            ├─ RepositorioParticipaciones (RepositorioParticipacionesJdbc)
            ├─ RepositorioIntentos (RepositorioIntentosJpa, solo lectura del intento)
            ├─ ElegibilidadInicio      → producción: ElegibilidadNoRatificada
            └─ LimiteTemporalInicio    → producción: LimiteTemporalNoRatificado
```

La identidad nunca llega del cliente. Una convocatoria inexistente y una ajena
producen el mismo error (`CONVOCATORIA_NO_ENCONTRADA`), igual que en M4.

## 3. Mecanismo

1. Bloquear `"Participacion"` de la convocatoria (`SELECT ... FOR UPDATE`).
2. **Reanudación.** Si hay un intento `EnCurso`, se devuelve tal cual:
   - mismo `idIntento`, mismas respuestas, mismo `inicio` y mismo `limiteTemporal`;
   - no se consultan elegibilidad ni límite, y no se escribe nada.
3. Si la participación tiene un intento finalizado, iniciar sería una repetición:
   `REPETICION_NO_HABILITADA`.
4. **Primer inicio.**
   1. Se evalúa la elegibilidad. En producción no está ratificada, así que
      falla con `INICIO_NO_DISPONIBLE (elegibilidad)`.
   2. Se toma `inicio = clock_timestamp()` y se calcula el límite. En
      producción no está ratificado, así que falla con
      `INICIO_NO_DISPONIBLE (límite temporal)`.
   3. Se crea la participación si no existe y se bloquea
      (`INSERT ... ON CONFLICT ("idConvocatoria") DO NOTHING` + `FOR UPDATE`).
   4. Se repiten los pasos 2 y 3: otra solicitud pudo crear el intento mientras tanto.
   5. Se inserta el intento:
      - `numeroIntento = 1`, `'EnCurso'`;
      - sin `idAutorizacion`;
      - `inicio` y `limiteTemporal` calculados en el paso 4.2.

**Valores escritos:**

- La participación se inserta solo con `idParticipacion` e `idConvocatoria`.
- Su `estado` queda en el valor por defecto de V001 (`'Pendiente'`).
- Sus fechas y su resultado no se escriben.
- No se crean estados nuevos.

## 4. Transacciones y concurrencia

| Garantía | Cómo |
|---|---|
| Atomicidad participación + intento | Una sola transacción (`FachadaParticipacionTransaccional`). Cualquier excepción la revierte |
| Una participación por convocatoria | `uq_participacion_idconvocatoria` + `ON CONFLICT DO NOTHING`. La segunda solicitud espera y reutiliza la confirmada |
| Un intento en curso | Bloqueo de la participación antes de leer intentos; `ux_intento_unico_encurso` como respaldo de la base |
| Sin duplicados por concurrencia | Las solicitudes sobre la misma convocatoria se serializan en la fila de `"Participacion"` |
| Repetición | `prisaly_validar_intento` (V005). Su rechazo se traduce a `RepeticionNoHabilitadaException`. No se duplica en Java |
| Tiempos | `ck_intento_tiempos` (V002). Reloj de PostgreSQL |
| Orden de bloqueos | Participación → estudiante (el mismo que impone el trigger de V005) |
| Integridad referencial | FK de V001; la cadena es inmutable por los triggers de V004–V006 |
| Adaptador | `Propagation.MANDATORY`: sin la transacción del caso de uso, falla |

**Riesgo residual (protocolo M4–M5, decisión D-06 pendiente).** M4 consulta
`ParticipacionesPort` antes de editar o reasignar una sesión. Un primer inicio
simultáneo a esa edición no se bloquea mutuamente. Hoy el riesgo no se
materializa en producción, porque el primer inicio está bloqueado.

## 5. Pruebas

**Ejecutadas en Cowork (no equivalen a Maven):**

- M5 `main` compila con `javac --release 21 -Xlint:all -Werror`.
- JUnit 5.10.2, fuera de Maven: **211/211** unitarias aprobadas (167 anteriores
  y 44 nuevas). Siguen excluidas por librerías ausentes
  `ProgramacionM4ErroresHttpTest` y `SolicitudRespuestaProvisionalValidacionTest`.
- Las cuatro IT de M5 compilan sin advertencias contra sustitutos mínimos de las
  anotaciones de Spring Boot, Testcontainers y Jackson. No se ejecutaron.
- Sentencias del adaptador en PostgreSQL 16.15 aislado, con V001–V009:
  - las consultas y la inserción del intento 1;
  - V005 rechaza con SQLSTATE `23514` y el mensaje «Repetición exige
    autorización expresa»; tras `ROLLBACK` no queda la participación;
  - un límite igual al inicio viola `ck_intento_tiempos`;
  - **carrera de 4 sesiones `psql` simultáneas** con el mismo protocolo: 1
    participación, 1 intento; una sesión lo crea y tres lo reanudan.

**Nuevas:**

| Prueba | Tipo | Casos |
|---|---|---|
| `IniciarIntentoServicioTest` | Unitaria | 16: primer inicio; reanudación (id, respuestas, inicio, límite); reanudar sin consultar políticas; reanudar repetidas veces; finalizado; elegibilidad no ratificada o nula; no elegible; límite no ratificado o nulo; políticas de producción; participación sin intentos; otra solicitud se adelanta (en curso o finalizado); error de persistencia; nulos |
| `AccesoProtegidoInicioTest` | Unitaria | 8: titular; ajena e inexistente; mensaje sin referencia; sin identidad; M4 caído; titularidad nula; titularidad por defecto; convocatoria nula |
| `TitularidadConvocatoriaDesdeM4Test` | Unitaria | 7 |
| `RepositorioParticipacionesJdbcTest` | Unitaria | 4 |
| `TraductorErroresInicioTest` | Unitaria | 4 |
| `ConfiguracionParticipacionTest` | Unitaria | +5: sin repositorio no arranca; M1 real; titularidad sin convocatorias; primer inicio bloqueado y revertido; reanudación en producción |
| `InicioIntentoIT` | PostgreSQL 16.15 | 15: primer inicio y vínculo; bloqueo productivo; rollback; reanudación; reanudación productiva; finalizado; V005 entre sesiones; ajeno; inexistente; sin identidad; M1 real; M4 caído; transacción obligatoria; 8 solicitudes simultáneas; titular y ajeno simultáneos |

**Pruebas existentes adaptadas** (sin quitar casos):

- `ParticipacionFacadeTest`: constructor con el nuevo servicio. La prueba de
  operaciones expuestas ahora incluye `iniciarOReanudar`, que devuelve
  `ResultadoInicio`.
- `AccesoProtegidoParticipacionTest`: el doble implementa la nueva operación.
- `ConfiguracionParticipacionTest`: los contextos registran el repositorio de
  participaciones.
- `AccesoEstudianteM4M5IT`: importa `RepositorioParticipacionesJdbc`.
- `CadenaIntentoPrueba` y `FachadaSinControlDePrueba`: nuevas variantes.

**Pendientes en Windows (Maven y Docker):**

| Comando | Esperado |
|---|---|
| `.\mvnw.cmd -pl m5 -am test` | M5: 225 unitarias (181 + 44) |
| `.\mvnw.cmd -pl m5 -am -Pdocker-it verify` | M5: 225 unitarias + 53 IT (38 + 15) |
| `.\mvnw.cmd -pl app -am test` | Sin regresiones |
| `.\mvnw.cmd -pl app -am -Pdocker-it verify` | El contexto completo arranca con `RepositorioParticipacionesJdbc` |

## 6. Decisiones que siguen bloqueadas

| Decisión | Efecto hoy |
|---|---|
| Elegibilidad académica | Primer inicio: `INICIO_NO_DISPONIBLE` |
| `limiteTemporal` (F3-04) | Sin cálculo no hay intento nuevo (columna NOT NULL) |
| Estados de `Participacion` | Se deja el valor por defecto `'Pendiente'`; no se escriben fechas |
| DD-14 | `REPETICION_NO_HABILITADA` |
| Motivos y vencimiento | Sin cambios en la finalización |
| ARQ-008.3 | Sin endpoints; los códigos HTTP son provisionales |
| DD-02 | Con `DenyingStudentAccessService` todo se deniega |
| `consulta-bd0052` | Sin la propiedad, la titularidad responde 503 |
| D-06 (protocolo M4–M5) | Riesgo residual, sección 4 |

Al ratificar una regla, basta con sustituir la implementación «no ratificada»
correspondiente en `ConfiguracionParticipacion`. El mecanismo no cambia.

## 7. Archivos

**Producción, nuevos:**

- `application`:
  - `ReferenciaConvocatoria`, `ResultadoInicio`;
  - `ConvocatoriaNoEncontradaException`, `RepeticionNoHabilitadaException`;
  - `port/RepositorioParticipaciones`, `servicio/IniciarIntentoServicio`.
- `application/inicio`:
  - `ElegibilidadInicio`, `ElegibilidadNoRatificada`;
  - `LimiteTemporalInicio`, `LimiteTemporalNoRatificado`;
  - `DecisionPendiente`;
  - `InicioNoDisponibleException`, `InicioNoAutorizadoException`;
  - `package-info`.
- `infrastructure/persistencia/RepositorioParticipacionesJdbc`.

**Producción, modificados:**

- `OperacionesParticipacion`, `ParticipacionFacade`;
- `AccesoProtegidoParticipacion`, `ControlAccesoIntentos`,
  `TitularidadIntentos` (método por defecto que deniega);
- `ConfiguracionParticipacion`, `FachadaParticipacionTransaccional`;
- `TitularidadIntentosDesdeM4`, `TraductorErroresM5`;
- `application/package-info`;
- Javadoc de `IntentoEntity` y `RepositorioIntentosJpa`.

No cambia ningún archivo fuera de `m5/`. No hay migraciones, tablas ni
dependencias Maven nuevas.
