# PRISALY Backend

Monolito modular por módulos funcionales. Lenguaje y framework sin definir. Estas reglas aplican a todo `PRISALY-Backen`.

## Estructura

- Mantener los siete módulos: `m1-identidad`, `m2-gestion-academica`, `m3-casos`, `m4-programacion`, `m5-participacion`, `m6-evaluacion`, `m7-comunicaciones`.
- `m2-gestion-academica` permanece provisional. No inventar funcionalidades ni entidades.
- Crear `presentation`, `application`, `domain`, `contracts` e `infrastructure` dentro de un módulo solo al implementar un caso de uso o un contrato.
- No agregar clases vacías para “completar el árbol”.
- Cuatro capas internas: presentación, aplicación, dominio e infraestructura.

## Dependencias

- Un módulo colabora con otro mediante los contratos explícitos de ese módulo.
- Un módulo no importa repositorios concretos, entidades internas ni infraestructura de otro módulo.
- Infraestructura puede depender de contratos de dominio o aplicación.
- Dominio permanece independiente de PostgreSQL, HTTP y del proveedor de correo.

## Transacciones

- Registrar sesión, convocatorias y participaciones dentro de una misma unidad de trabajo.
- Los límites transaccionales concretos se cierran con Backend antes de fijarlos en código.

## Evaluación

- Finalizar un intento, evaluarlo y publicar el resultado son tres operaciones distintas.
- Separar la ejecución del intento del procesamiento de la evaluación.

## Seguridad

- El backend verifica identidad, titularidad, permisos y condiciones de acceso.
- Los DTO y respuestas públicas no exponen nota, rúbrica ni retroalimentación reservada.

## Eventos

- Observer avisa después de persistir. No sustituye el guardado.
- Si la transacción se confirma y después falla la publicación del acontecimiento, el fallo queda registrado de forma explícita. No se revierte la persistencia en silencio ni se oculta el error.

## Crecimiento

- No introducir módulos, tablas, clases de patrones ni servicios externos sin una necesidad funcional o técnica demostrada.
- Si el comportamiento depende de una decisión abierta, detenerse y no inventarlo.

| Elemento | Decisión abierta |
| --- | --- |
| `IPoliticaCalificacion` | DD-03 |
| `IPoliticaNotaFinal` | DD-04 |
| `ReglaIngresoTardio` | DD-05 |
| `ReglaInmutabilidadCaso` | DD-08 |
| `ReglaLiberacionResultados` | DD-12 |
| `ValidadorRepeticion` | DD-14 y autorizaciones entre sesiones |
| Identidad usada por `IAutorizacionDocente` | DD-15 |
| `IAutorizacionDocente` | DD-16 |

## Persistencia

- La correspondencia clase–tabla se define en `infrastructure/persistence/mappings`.
- No tratar las entidades como equivalentes una a una de las 17 tablas candidatas.
- `EnvioCorreo` es candidato de BD-005.1, no una tabla definitiva.
- Modelos de referencia previstos: `database/models/BD-004` y `database/models/BD-005.1`, cuando existan los DBML conciliados.
- Validaciones de base pendientes de evidencia: integridad referencial, unicidad, intentos concurrentes, autorizaciones de repetición, inmutabilidad académica, estado de evaluación y persistencia de correo.
