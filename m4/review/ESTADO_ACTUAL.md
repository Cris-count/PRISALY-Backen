# M4–M5 — Estado actual y guía de documentación

> Conciliación posterior de Arquitectura: D-06 permanece pendiente de cierre definitivo. SesionRepository es un puerto público de aplicación de M4 y puede conservarse como mecanismo único si se acredita la transacción de M5. No integrar la alternativa de este candidato junto con otra implementación M5. Las ocho pruebas ProtocoloD06IT comunicadas por M5 requieren rama/SHA para contrastarlas. Véase sección 11 del informe. Las 839 pruebas siguientes corresponden al candidato 513c444 y no certifican esa implementación externa de M5.

Actualizado el 02/11/2026. Candidato en `feature/m4-cierre-d06`, desde develop `b3d77d89e324c1f61ea0364417a37bd2c7c354cc`. **839 pruebas aprobadas, cero fallos, errores u omitidas: M4 182, M5 283 y app 40.** D-06 implementado y verificado con el orden Sesion → Convocatoria → Participacion → Intento; integrado mediante PR #28 en develop 3984485, verificado con fetch el 02/11/2026. Pendiente de conciliación final de Arquitectura; las 839 pruebas son evidencia histórica del candidato, no una nueva ejecución. Detalle en la sección 10 del [informe](revision-m4-m5/INFORME.md).

El acceso estudiantil productivo, el primer inicio académico y SMTP siguen bloqueados/desactivados según su configuración y decisiones vigentes. No se modifican migraciones. La corrección de ensamblado en app selecciona explícitamente el proveedor existente M7 de entrega DD-02 sin activar esa funcionalidad. Los antecedentes de 684 pruebas, la auditoría 1960eff y la propuesta D-06 se conservan en las secciones anteriores del informe; no equivalen al corte actual.

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

App descubre el único proveedor ParticipacionesM4DesdeM5. El puente duplicado de app fue retirado en19cf52b. SesionRepositoryAdapter de M4 exige escritura y adapta errores de historial a503 usando los contratos existentes. No crea un ciclo Maven ni consulta repositorios internos M5. Se exige una transacción de escritura; el servicio M4 mantiene el bloqueo de sesión. Se incluyen convocatorias retiradas. Con historial, edición/reasignación responden409; si la consulta falla,503 y rollback.

El mecanismo interno de inicio/reanudación ya incorpora la reserva coordinada D-06 en la misma transacción, con orden Sesion → Convocatoria → Participacion → Intento. La ratificación final de Arquitectura sigue pendiente; el primer inicio productivo continúa bloqueado por elegibilidad y límite temporal no ratificados.

## Pendientes que siguen vigentes

- Identidad estudiantil real DD-02; el adaptador de titularidad M5→M4 ya existe y está probado.
- Ratificación final D-06; elegibilidad, cálculo del límite temporal, vencimiento y repetición. Inicio/reanudación internos ya implementados; sin habilitación del recorrido estudiantil.
- Ratificar el ensamblado local de historial M5 y el alcance final de comunicaciones M7. El registro de avisos M7 ya tiene adaptador y pruebas.
- Confirmar aplicación de V010 y datos en bases compartidas.
- M2 conserva estructura básica; M6 dispone de consultas de resultados, Proxy y contrato Strategy, sin algoritmo académico productivo.

## Organización de la carpeta

Se guardaba un informe por fase con XML, logs, borradores de PR y diagnósticos repetidos. Con aprobación expresa del usuario se eliminaron86 archivos reemplazados y se conservaron12 referencias vigentes, además de retirar seis carpetas y siete archivos temporales de diagnóstico. Las decisiones útiles están consolidadas en los documentos enlazados. El historial versionado también puede consultarse en Git.

Los directorios target contienen artefactos de Maven y no son documentación oficial. La limpieza no modifica implementaciones, pruebas, migraciones ni configuración del sistema.
