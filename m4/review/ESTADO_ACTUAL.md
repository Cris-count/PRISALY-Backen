# M4–M5 — Estado actual y guía de documentación

Actualizado el31/10/2026. Base local: develop, feec0fb. M4 ya está incorporado en develop; M5 procede de3fc823e y tiene cambios locales de integración; M4 tiene la reorganización local de controladores y validaciones. Sin commit ni push de esos cambios.

Última verificación: **445 pruebas aprobadas**, cero fallos, errores u omitidas. M4:149; M5:145. El conteo coincide entre los XML de esa ejecución y el log Maven. La cifra anterior207/157 estaba contaminada por17 casos de un XML antiguo; aquella ejecución correspondía a190/140.

## Qué consultar

| Documento | Contenido |
|---|---|
| [Revisión actual](revision-m4-m5/INFORME.md) | Estado de M1–M7, incorporación local M5, corrección503, límites y pendientes |
| [Resultados verificados](revision-m4-m5/evidence/RESULTADO.md) | Pruebas ejecutadas, alcance y enlaces a evidencia actual |
| [Conciliación BD-005.2](conciliacion-bd0052/INFORME.md) | Matriz de columnas, tres estados oficiales, RF09/F303, transacciones y tratamiento de V010 |
| [Contratos y endpoints](conciliacion-bd0052/CONTRATOS-Y-ENDPOINTS.md) | Puertos M4–M5, errores, tiempo, reanudación, repetición y diagramas |
| [Decisiones REST de M5](../../m5/docs/M5-REST-DEPENDENCIAS-Y-DECISIONES.md) | Operaciones internas, acceso estudiantil y decisiones académicas pendientes |

Herramientas conservadas: [consulta de historial V010, solo lectura](conciliacion-bd0052/CONSULTAS-V010.sql), [verificación del paquete](conciliacion-bd0052/check-package.ps1) y [recolección de resultados por hora de inicio](conciliacion-bd0052/collect-evidence.ps1).

## Organización de M4

| Paquete | Responsabilidad |
|---|---|
| api | Puertos públicos y contexto informativo para otros módulos |
| presentation | SesionController para sesiones; ConvocatoriaController para convocatorias; DTO y errores compartidos |
| application | Orquestación transaccional, permisos, propiedad y carga bloqueada de sesiones |
| domain | Agregados, validaciones y State con tres estados oficiales |
| infrastructure | Adaptadores, proveedores seguros y persistencia sobre BD-005.2 |

La creación valida el agregado antes de consultar o bloquear proveedores externos. Las mutaciones de una sesión existente comparten una carga con permiso, bloqueo de fila y comprobación de propietario. Se rechaza la identidad docente nula y la programación exige una transacción de escritura.

## Pendientes que siguen vigentes

- Acceso estudiantil DD-02 y conexión del control de titularidad.
- Inicio/reanudación, reloj, vencimiento, repetición y orden global de bloqueos.
- Proveedor productivo de historial M5 y notificaciones M7.
- Confirmar aplicación de V010 y datos en bases compartidas.
- M2/M6/M7 solo tienen estructura básica en esta base de develop.

## Organización de la carpeta

Se guardaba un informe por fase con XML, logs, borradores de PR y diagnósticos repetidos. Con aprobación expresa del usuario se eliminaron86 archivos reemplazados y se conservaron12 referencias vigentes, además de retirar seis carpetas y siete archivos temporales de diagnóstico. Las decisiones útiles están consolidadas en los documentos enlazados. El historial versionado también puede consultarse en Git.

Los directorios target contienen artefactos de Maven y no son documentación oficial. La limpieza no modifica implementaciones, pruebas, migraciones ni configuración del sistema.
