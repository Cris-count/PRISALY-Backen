# PRISALY Backend

Monolito modular de cuatro capas, organizado por módulos funcionales.

El lenguaje y el framework todavía no están seleccionados. Este repositorio contiene el esqueleto inicial: carpetas de arranque, sin clases vacías. Cada módulo incorpora `presentation`, `application`, `domain`, `contracts` e `infrastructure` cuando se implementa un caso de uso o un contrato.

## Módulos

| Módulo | Responsabilidad |
| --- | --- |
| `m1-identidad` | Identidad, usuarios, roles y permisos |
| `m2-gestion-academica` | Provisional. Responsabilidades sin delimitar |
| `m3-casos` | Casos, escenarios, preguntas, opciones y rúbricas |
| `m4-programacion` | Sesiones y convocatorias |
| `m5-participacion` | Participación, intentos y respuestas |
| `m6-evaluacion` | Calificación, nota final, retroalimentación y consulta de resultados |
| `m7-comunicaciones` | Reportes, notificaciones y envío de correo |

La orquestación entre módulos vive en `src/orchestration`. Lo compartido (errores, seguridad, eventos, transacciones, tiempo y utilidades) vive en `src/shared`.

## Estructura actual

```text
src/app
src/config
src/modules/m1-identidad … m7-comunicaciones
src/orchestration
src/shared
database/models
database/migrations
database/validation
database/evidence
tests
docs
scripts
```

## Decisiones abiertas

Siguen abiertos el framework, la conciliación de contratos entre Arquitectura y Backend, y las decisiones DD-03, DD-04, DD-05, DD-08, DD-12, DD-14, DD-15 y DD-16.

Las clases de dominio representan conceptos de software. Su correspondencia con las tablas se define en los mapeos de persistencia. `EnvioCorreo` es candidato de BD-005.1, no una tabla definitiva.

## Reglas

`AGENTS.md` y `.cursor/rules/` fijan cómo se implementa este backend.
