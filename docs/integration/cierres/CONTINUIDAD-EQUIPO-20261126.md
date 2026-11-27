# Continuidad del equipo — DC-CONTROL-SALIDA-01

Línea compartida temporal: `integracion/cierre-entrega` en Backend y Frontend. No representa un merge a develop ni autorización productiva. Conservar DC-119 y DC-121.

Backend incorpora consulta administrativa `GET /api/v1/admin/usuarios` (q, rol, limite, offset), solo administrador verificado, sin secretos, Cache-Control no-store y sin migraciones. Contrato en docs/api/consulta-usuarios-administrativa.md. Frontend consume esa consulta para seleccionar usuarios y reutiliza los contratos anteriores de permisos e invitaciones.

## Evidencia de este cierre

- Compilación Maven y 9 pruebas focalizadas: 3 de servicio, 5 de arquitectura y 1 integración PostgreSQL 16.15 (múltiples comprobaciones de esquema V013/V020). Sin fallos, errores ni omitidas. No se repitió la regresión canónica completa.
- Consulta administrativa HTTP real: 12/12; autorización, límites, roles, búsqueda literal, revocación.
- Recorrido académico HTTP de datos ficticios: 81/81, incluyendo persistencia, finalización idempotente, calificación y liberación. RF-27 ya estaba habilitado por DC-118; se verificó un único aviso en vez de la expectativa histórica de cero.
- Frontend: suite completa 934/934; compilaciones productiva y QA. El informe local conserva intentos fallidos, sus causas y repeticiones; no se atribuyen resultados históricos como nuevos.

## Reparto sin solapamiento

| Responsable | Alcance | Evitar | Comprobación mínima |
|---|---|---|---|
| Mariana | Frontend academico, dashboard, autenticacion, formularios de personal | features/simulador, features/estudiante/portal-estudiante.*, contratos Backend | Suite de componentes afectados, TypeScript y build QA |
| Cristian | Backend: M1 administrativo, evaluación M6 y calidad bajo reglas aprobadas | Frontend y contratos/migraciones candidatas sin decisión | Unitarias, arquitectura y PostgreSQL afectadas |
| Nicolás | MUNDO-24, CB-04, Frontend features/simulador y portal-estudiante.* coordinado | Administración Angular, esquemas y evaluación Backend | Protocolo v1, guard, visor, ejecución real al recibir paquete |
| Ingrid | Integración de ramas y aceptación | No editar simultáneamente archivos asignados | Revisar SHA, CI y permisos antes de integrar |
| Claude | Recepción del paquete, manifiestos y preparación operativa local | Código de los responsables y QA protegidos | Hashes del paquete y compatibilidad de versión |

Crear una rama individual desde origin/integracion/cierre-entrega; no continuar sobre respaldos. No activar flags, aplicar migraciones, ni hacer merge a develop/main sin autorización.

MUNDO-24 aún no está en el equipo. Ninguna prueba con dobles certifica Unreal. Persistencia de investigación y fases permanecen sin activar. El visor optativo comparte la API y datos QA de DC-105; no es una base nueva. Configuración e instrucciones en Frontend docs/cierre-entrega-simulador.md.

Reservas: recepción MUNDO-24/CB-04, señalización y protocolo real, migraciones candidatas y decisiones pendientes, QA33-02, cierre eventual tras cierre anticipado. Las mejoras visuales y las pruebas HTTP no certifican todas las pantallas E2E: la automatización visual del navegador estuvo bloqueada por la herramienta.
