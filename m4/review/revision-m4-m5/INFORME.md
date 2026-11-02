# Estabilización M4 — informe y conciliación

01/11/2026. Auditoría de referencia ee77a57969ad35e5842c0f95a6a7142192ac269c. Base real inspeccionada antes de modificar código: develop19cf52bc4f84f07b03f8414d03924483ea324993. Rama: feature/m4-estabilizacion. No se modifica directamente develop ni se realiza merge unilateral.

## 1. Conciliación previa

| Comprobación | Resultado y evidencia |
|---|---|
| JPA/BD-005.2 | Sesion14 columnas y Convocatoria6 coinciden con V001: UUID, timestamptz→Instant, tamaños, nulidad y estados oficiales. Sin Version ni columnas inventadas. Restricciones y FK conservadas V002–V009. Matriz completa en ../conciliacion-bd0052/INFORME.md. |
| RF-09 | Caso Publicado y activo, consulta bajo FOR UPDATE en escritura; V009 vuelve a validar al persistir Sesion. El adaptador alternativo Java omitía activo: corregido. |
| F3-03 | V007/V008 asignan la marca en inserción/reasignación. M4 la confirma, no la escribe. La confirmación ahora exige escritura y controla errores SQL. |
| Contexto público | ProgramacionParticipacionPort/ProgramacionAutenticadaPort y ContextoProgramacion conservan firmas y records. Son hechos persistidos, no autorización de inicio. observadoEn procede de PostgreSQL. |
| Historial | Único proveedor productivo ParticipacionesM4DesdeM5. El puente duplicado de app ya fue retirado en19cf52b; no se recrea. SesionRepositoryAdapter añade guardia y frontera503 al consumirlo. |
| M1/M7 | Identidad docente y permiso exacto desde M1. DD-02 mantiene denegación estudiantil. M7 registra aviso/cola dentro de la transacción M4; SMTP opcional no equivale a entrega académica completa. |
| V010 | Originada en a1564a7. Blob18c4f98131d0caf5a0399b5139c7a3cbf37a3f09, intacto. Excluida del descubrimiento y archivada. Aplicación en bases compartidas no acreditada. Sin repair/clean compartido. |
| RF-18/F3-04 | Se conserva duración positiva independiente del estimado. F3-04 admite inferior/igual/superior y V007 no requiere migración. No se introduce máximo≤estimado. Texto original RF-18 por conciliar. |

## 2. Matriz de contratos M3–M4–M5

| Proveedor → consumidor | Contrato | Responsabilidad / error / transacción |
|---|---|---|
| M3/BD → M4 | CasoAcademicoPort: estaPublicado(UUID), solicitarBloqueoAcademico(UUID) | Solo metadatos y confirmación F3-03. Publicado+activo; indisponibilidad503; misma TX de programación. El adaptador Java no pretende confirmar un bloqueo atómico que su contrato no ofrece. |
| M4 → M5 | ProgramacionParticipacionPort.consultarSesion(UUID,UUID), consultarConvocatoria(UUID,UUID) | Optional<ContextoProgramacion>; UUID estudiante confiable del servidor. Vacío=ausente/ajeno; error=ConsultaProgramacionNoDisponibleException503. Instantánea sin reserva de elegibilidad. |
| M4/M1 → consumidor estudiantil | ProgramacionAutenticadaPort.consultarSesion(UUID), consultarConvocatoria(UUID) | Identidad obtenida exclusivamente de StudentAccessPort. Sin identidad real, denegación segura. |
| M5 → M4 | ParticipacionesPort.tieneParticipacionesOIntentos(UUID) | Convocatorias por puerto M4, participaciones por contrato público M5. M4 mantiene Sesion bloqueada y exige TX de escritura; fallo503, nunca false por indisponibilidad. |
| M4 → M7 | NotificacionPort.notificarConvocatoria(UUID,UUID) | Solicitud atómica de aviso/cola. FK y unicidad de Convocatoria se validan con flush antes de llamar a M7. Rollback conjunto. |

## 3. Respuesta y propuesta para M5

Se recibió la solicitud de activar el lector en app, publicar listado propio, definir elegibilidad y acordar bloqueo. Es una solicitud de conciliación, no aprobación de reglas ni de un escritor de Participacion.

Propuesta: M5 sería el único escritor de Participacion durante el primer inicio autorizado, junto con Intento y en una sola transacción. M4 conserva Sesion/Convocatoria. Datos: identidad confiable M1, convocatoria persistida, contexto M4 y políticas aprobadas. La restricción existente uq_participacion_idconvocatoria evita duplicados; deben acordarse la idempotencia/respuesta ante repetición y el rollback de ambos registros. No se crea Participacion desde M4.

En un primer inicio, un UUID de convocatoria candidato no concede acceso: el servidor debe comprobar identidad/titularidad. Para un intento existente, M5 resuelve Intento→Participacion→Convocatoria desde persistencia.

Propuestas Java no implementadas: listarConvocatoriasEstudianteActual() y bloquearConvocatoriaDelEstudianteActual(UUID), obteniendo estudiante desde M1. El segundo requiere TX de escritura MANDATORY y devuelve hechos, no permiso. Paginación, firma final y errores por ratificar. No se añaden rutas.

Protocolo propuesto: Sesion→Convocatoria→Participacion→Intento, sosteniendo los bloqueos hasta commit; volver a comprobar condiciones después de bloquear. El futuro inicio debe compartir Sesion FOR UPDATE con edición/convocatoria/cambio de estado M4. Coordinar retirada/cierre y evitar orden inverso Intento→Sesion. No se declara resuelta esa carrera sin el inicio real M5.

Activación: Integración puede proponer app con --prisaly.m4.consulta-bd0052.enabled=true. No se altera aquí la configuración productiva solicitada por M5 sin ratificación del ensamblado; activar lectura no autoriza académicamente. Firmas y protocolo ampliados en ../conciliacion-bd0052/CONTRATOS-Y-ENDPOINTS.md.

## 4. H04/H06/H08/H09/H16

La tabla suministrada por el usuario es una reconstrucción temática, no la matriz literal certificada. El chat compartido enumera los códigos; los adjuntos originales no son visibles. Los siguientes estados son del incremento técnico y no certifican cierre formal de la auditoría.

| Hallazgo / tema reconstruido | Resultado del incremento | Pendiente |
|---|---|---|
| H04: convocatoria/participación | M4 conserva sus tablas; convocatoria válida/concurrente no crea Participacion. Se documenta propuesta de escritor único M5 y restricción oficial. | Ratificar creación durante primer inicio, idempotencia y operación transaccional M5. |
| H06: tiempo de sesiones | 15 casos con fixtures de reloj fijo para tres estados y cinco fronteras, sin mutación/creación por consultar contexto. | DD-01/DD-05, autoridad temporal académica, vencimiento, reanudación y cierre. No scheduler ni políticas nuevas. |
| H08: contratos | Firmas públicas conservadas; mapeo oficial, Publicado+activo en ambos adaptadores, contexto y proveedor real verificados. | Ratificar listado propio, puerto de bloqueo y responsabilidades compartidas. |
| H09: dependencias | Guardia de escritura, errores controlados503, rollback, FK/unicidad antes de M7, proveedor único y ausencia segura verificados. | Activación controlada del lector en app y protocolo del futuro inicio. |
| H16: máximo/estimado | 30/60/120 segundos frente a estimado60 pasan en PostgreSQL. Se conserva F3-04. | Conciliar texto original RF-18 y criterio de cierre de auditoría; no imponer máximo≤estimado. |

Fuente consultada: https://chatgpt.com/share/6ae803d4-0034-83e9-8b64-5c607121a290. La reconstrucción enviada en este chat se usa como guía, no como transcripción certificada.

## 5. Clases y servicios modificados

- CasoAcademicoM3Adapter: exige caso activo además de Publicado y UUID coincidente.
- CasoAcademicoBd0052Adapter: adapta fallo SQL a503 y exige escritura al confirmar F3-03.
- SesionRepositoryAdapter: exige escritura al consultar historial; traduce fallo del proveedor a503 con causa interna y mensaje genérico.
- ConvocatoriaRepositoryAdapter: saveAndFlush antes de notificación para validar FK/unicidad en la frontera M4.

SesionAcademicaService, State, puertos/records públicos, tablas, columnas, estados y rutas productivas se conservan. Solo se adapta una prueba de app al503 de M4, comprobando también la causa original M5. Sin cambios en M1/M3/M5/M7 ni migraciones.

## 6. Verificación y límites

**684 pruebas aprobadas**, cero fallos, errores u omitidas. M4=178 (114 Surefire y64 Failsafe). Ver evidence/RESULTADO.md, conteo.md y suites.json. El total XML coincide con el resumen Maven; no se reutiliza evidencia antigua.

State: SesionAcademicaDomainTest cubre SinIniciar→EnCurso→Finalizado, rechazo de cierre sin inicio, reapertura, edición y convocatorias fuera del estado permitido. Las15 pruebas temporales controlan datos/fronteras con Clock.fixed; NO inyectan un reloj académico en producción ni validan un mecanismo de expiración inexistente. observadoEn sigue siendo PostgreSQL.

Concurrencia nueva: seis solicitudes simultáneas de la misma convocatoria producen un éxito y cinco rechazos de duplicado, con una sola fila y cero participaciones. Se conservan las pruebas de programación concurrente y cambio de estado/convocatoria. Los fallos de SQL de caso/historial se provocan solo en contenedores efímeros y se revierten.

El primer verify falló por Docker Desktop apagado, antes de M4. Se inició Docker y se repitió toda la ejecución: BUILD SUCCESS. Las38 pruebas focalizadas de adaptadores/HTTP también pasaron; están incluidas en la regresión y no se suman dos veces.

No se habilita identidad estudiantil, inicio, repetición ni evaluación. La integridad del futuro inicio según contrato ratificado no puede demostrarse aún: solo FK/UNIQUE y consultas existentes con fixtures. M4 continúa como candidato técnico, no módulo terminado.

## 7. Entrega Git

Rama feature/m4-estabilizacion desde19cf52b; commit y SHA publicados se comunican con el PR de entrega hacia develop. PR en borrador, sin merge. La evidencia corresponde a esa base más este incremento. No se modifica develop directamente.
