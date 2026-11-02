# M4–M5 — Estado actual y guía de documentación

Actualizado el 01/11/2026. Base develop: ee77a57, con cierre M5 incorporado. Conciliación local: se conserva ParticipacionesM4DesdeM5 como único proveedor productivo y se elimina HistorialM4M5Configuration de app. Sin commit ni push.

Última verificación: **657 pruebas aprobadas**, cero fallos, errores u omitidas. M1:14; M3:160; M4:149; M5:221; M6:44; M7:48; app:21. Conteo independiente XML/resúmenes Maven coincidente. M2 no aporta suites. La cifra histórica207/157 incluía17 casos de un XML obsoleto; aquella ejecución correspondía a190/140.

## Qué consultar

| Documento | Contenido |
|---|---|
| [Revisión actual](revision-m4-m5/INFORME.md) | Estado de M1–M7, conexión de historial M4–M5, límites y pendientes |
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

## Historial M4–M5 conectado localmente

App descubre el único componente ParticipacionesM4DesdeM5, que implementa ParticipacionesPort usando ConvocatoriaRepository de M4 y el contrato público HistorialParticipacionPort de M5. Se elimina HistorialM4M5Configuration y su lógica duplicada de app. No crea un ciclo Maven ni consulta repositorios internos M5. Se exige una transacción de escritura; el servicio M4 mantiene el bloqueo de sesión. Se incluyen convocatorias retiradas. Con historial, edición/reasignación responden409; si la consulta falla,503 y rollback.

El futuro inicio de participaciones debe adquirir el mismo bloqueo de sesión; esta carrera no se declara resuelta mientras ese inicio no exista.

## Pendientes que siguen vigentes

- Identidad estudiantil real DD-02; el adaptador de titularidad M5→M4 ya existe y está probado.
- Inicio/reanudación, reloj, vencimiento, repetición y orden global de bloqueos.
- Ratificar el ensamblado local de historial M5 y el alcance final de comunicaciones M7. El registro de avisos M7 ya tiene adaptador y pruebas.
- Confirmar aplicación de V010 y datos en bases compartidas.
- M2 solo tiene estructura básica. M6 incorpora consultas persistidas y contratos de evaluación; falta el algoritmo y su ensamblado académico.

## Organización de la carpeta

Se guardaba un informe por fase con XML, logs, borradores de PR y diagnósticos repetidos. Con aprobación expresa del usuario se eliminaron86 archivos reemplazados y se conservaron12 referencias vigentes, además de retirar seis carpetas y siete archivos temporales de diagnóstico. Las decisiones útiles están consolidadas en los documentos enlazados. El historial versionado también puede consultarse en Git.

Los directorios target contienen artefactos de Maven y no son documentación oficial. La limpieza no modifica implementaciones, pruebas, migraciones ni configuración del sistema.
