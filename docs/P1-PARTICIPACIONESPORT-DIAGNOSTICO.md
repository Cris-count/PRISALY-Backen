# P1 — Diagnóstico y corrección `ParticipacionesPort`

## Diagnóstico (confirmado)

`PrisalyApplication` fallaba con:

```
NoUniqueBeanDefinitionException
expected single matching bean but found 2:
  participacionesM4DesdeM5, historialM4M5
```

Consumidor: `SesionRepositoryAdapter` (inyección por tipo).

## Corrección aplicada

| Acción | Detalle |
| --- | --- |
| Proveedor oficial | `participacionesM4DesdeM5` (M5 `@Component`) |
| Retirado | `app/.../HistorialM4M5Configuration` (`historialM4M5`) |
| M4 | Sin cambios |
| `consulta-bd0052` | Sin cambios |
| TX | `HistorialParticipacionJdbc.existeParticipacion` usa `@Transactional(MANDATORY)` (verificado en código) |

## Comportamiento de errores tras la corrección

- Fallos SQL / persistencia M5 → `PersistenciaIntentoException` (no se enmascaran como “sin historial” ni como `M4-PROVEEDOR-NO-DISPONIBLE` desde el puente de app).
- Sin transacción → `IllegalTransactionStateException` (MANDATORY).
- Historial presente → `M4-SESION-CON-PARTICIPACIONES` (igual que antes).

## Pruebas

- `ParticipacionesPortWiringIT`: contexto completo arranca; exactamente 1 bean `participacionesM4DesdeM5`; inyectado en `SesionRepositoryAdapter`.
- `HistorialM4M5IT`: reorientada al proveedor M5 (mismo escenario funcional de protección de historial).

## Relacionado

- [`INTEGRACION-M1-M7.md`](INTEGRACION-M1-M7.md)
