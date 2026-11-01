# M7 Fase 1 — Persistencia e idempotencia

Rama: `feature/m7-notificaciones`. Punto de partida conservado: historial con `5858df9` + docs posteriores (`8b03e41`).

## Semántica

| Resultado | Significado |
| --- | --- |
| Aviso insertado / reutilizado | Registro in-app idempotente |
| `EnvioCorreo` con `estadoEnvio=Pendiente` | **Encolado**; no entrega SMTP |
| Retorno normal (vía servicio / adapter de test) | Solicitud registrada |

## Contratos

- `NotificarConvocatoriaCommand` — solicitud síncrona de entrada
- `NotificarConvocatoriaService` — `Propagation.MANDATORY`; aviso luego encolado; sin `REQUIRES_NEW`
- `ContextoConvocatoriaPort` — SQL público (`Convocatoria` + `Estudiante` + `Usuario`); sin internals M4/M1
- `RegistroAvisoPort` / `RegistroEnvioCorreoPort` — upsert + verificación de consistencia

## Valores provisionales

- `Aviso.tipo` = `Convocatoria`
- `claveEvento` / `claveIdempotencia` = `convocatoria:{idConvocatoria}`
- `estadoEnvio` = `Pendiente`, `numeroIntentos` = `0`, `idParticipacion` = NULL

## Fase 1 vs Fase 2

`M7Configuration` **no** publica `NotificacionPort`. La clase `NotificacionM4Adapter` existe para Fase 2 / cableado explícito en IT.

## Dependencias pendientes

- Catálogos ratificados de `tipo` / `estadoEnvio`
- Bean productivo `NotificacionPort` (Fase 2)
- SMTP / workers (Fase 3)
- V27

## Pruebas

```bash
./mvnw -B -pl m7 test
./mvnw -B -pl m7 -Pdocker-it verify
```

Ver también [`M7-PRUEBAS.md`](M7-PRUEBAS.md).
