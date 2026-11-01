# M7 Fase 2 — Integración productiva M4–M7

Rama: `feature/m7-notificaciones`. Sin SMTP, workers, REST ni eventos M6. M4 no se modifica.

## Cableado

- `M7Configuration` publica `@Bean NotificacionPort notificacionM7` → `NotificacionM4Adapter`.
- M4 declara su fallback en `M4ProviderAutoConfiguration` (`@AutoConfiguration`,
  `@ConditionalOnMissingBean(NotificacionPort.class)`). Las auto-configuraciones se evalúan después de
  las configuraciones de usuario, de modo que con M7 en el contexto el fallback no se registra.
- Sin M7 (pruebas aisladas de M4), el fallback sigue activo y rechaza con `M4-PROVEEDOR-NO-DISPONIBLE`.

## Transacción

`SesionAcademicaService.convocarEstudiante` (M4, `@Transactional`) inserta la `Convocatoria` y llama al
puerto. Adapter y servicio M7 usan `Propagation.MANDATORY`: `Aviso` y `EnvioCorreo` (`Pendiente`) se
escriben en la misma transacción. Cualquier fallo revierte las tres filas.

## Semántica de errores

| Situación | Resultado para M4 |
| --- | --- |
| Retorno normal | Solicitud registrada (aviso + correo encolado); **no** entrega SMTP |
| Fallo real de M7 (contexto, idempotencia incompatible, SQL) | `ReglaNegocioException("M4-PROVEEDOR-NO-DISPONIBLE")` → 503 |
| M7 ausente del contexto | Fallback de M4, mismo código |

El adaptador encadena la causa técnica (`getCause()`) y la registra en log `WARN` con campos
`sesionId`, `estudianteId`, `causa`. El cuerpo HTTP solo lleva código y mensaje seguro.

## Pruebas

| Suite | Qué demuestra |
| --- | --- |
| `M4FallbackSinM7Test` (m7, unitaria) | Sin M7, el único `NotificacionPort` es el fallback de M4 |
| `M7ConvocatoriaPostgreSqlIT` (m7, docker-it) | Proveedor único `notificacionM7`; commit conjunto Convocatoria + Aviso + EnvioCorreo; rollback total si falla aviso, encolado o la transacción exterior (verificado con conexión JDBC independiente); pruebas Fase 1 conservadas |
| `NotificacionPortWiringIT` (app, docker-it) | En el monolito completo solo existe `notificacionM7` y no `notificacionesNoDisponibles` |

Las restricciones `CHECK (false)` de las pruebas de rollback se eliminan en `finally`
(`DROP CONSTRAINT IF EXISTS`); cada clase usa su propio contenedor PostgreSQL.

```bash
./mvnw -B -pl m4 -am install -DskipTests
./mvnw -B -pl m7 -Pdocker-it verify
./mvnw -B install -DskipTests && ./mvnw -B -pl app -Pdocker-it verify
```

Ver [`M7-PRUEBAS.md`](M7-PRUEBAS.md) para captura de evidencia y fallos de entorno.

## Pendiente (Fase 3)

- SMTP, workers y reintentos sobre `EnvioCorreo` `Pendiente`
- Catálogos ratificados de `tipo` / `estadoEnvio`
