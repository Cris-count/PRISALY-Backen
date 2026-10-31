# Solicitudes de coordinación — borradores locales, NO enviadas

La instrucción posterior del usuario impide publicar o modificar GitHub en esta fase. Estos textos quedan preparados para su envío cuando lo autorice; no constituyen solicitudes ya entregadas ni aprobación de los destinatarios.

## Integrante 1 y Dirección Central

Se localizaron V001–V009, compose.yaml y docs/BD-005.2-REFERENCE.md en develop a42c738027f86915a1284905df24e6e4c28422b4. Confirmar que constituyen la línea base vigente y aportar el diagrama/DBML original aprobado de 21 tablas.

Confirmar si V010__m4_sesiones_convocatorias.sql fue aplicada en alguna base compartida; aportar historial Flyway (version, script, checksum, success) sin credenciales. Confirmar reserva de numeración y estrategia para retirar o corregir la propuesta V010 sin alterar checksums aplicados.

La propuesta M4 usa sesiones_academicas/convocatorias, mientras V001 crea "Sesion"/"Convocatoria". Acordar mapeo de estados, campos obligatorios, timestamptz, control de concurrencia sin columna version oficial y transición a las tablas aprobadas. No crear dos tablas académicas paralelas.

Confirmar conexión PostgreSQL compartida, procedimiento de pruebas y base aislada autorizada (sin publicar contraseñas), imagen postgres:16.15 y límites de uso del volumen prisaly_pg_data. Las pruebas locales no escriben en ese volumen.

## Responsable M3

En 856e0d9 se localizaron CasoRepositoryPort.findById(String), CasoJpaRepository.findById(UUID) de solo lectura y CasoApplicationService. M4 requiere un contrato público que resuelva UUID y confirme Publicado + activo sobre datos persistidos, con serialización frente a cambios concurrentes.

V007/V008 implementan F3-03 por triggers sobre "Sesion" y V009 valida RF-09 bloqueando "Caso" FOR UPDATE. Confirmar si este será el único mecanismo de escritura del bloqueo y cómo expondrá M3 la lectura. El método de dominio lockAfterFirstSessionScheduled(Caso) no debe confundirse con una actualización persistida independiente ni usarse para eludir el trigger.

Acordar atomicidad de programación/bloqueo, reasignación, rollback, aislamiento y orden de locks; aportar casos reales y pruebas de integración. La tabla propuesta sesiones_academicas no activa esos triggers.

## Responsable M5

En 8c03043 la ParticipacionFacade expone consulta/registro/actualización/finalización de intentos, y RepositorioIntentos no tiene proveedor productivo. No se encontró una consulta pública de historial por sesión.

Solicitamos contrato público para consultar participaciones o intentos por UUID de sesión, incluyendo política de consistencia y bloqueo que evite carreras entre la modificación M4 y el alta M5. El nuevo ParticipacionesPort es un puerto de salida requerido por M4, NO un contrato público de M5 ya acordado.

Confirmar semántica de "existe historial": toda Participacion, incluso sin Intento, debe impedir cambios que alteren su origen. Probar contra "Sesion"→"Convocatoria"→"Participacion"→"Intento" reales; acordar cuándo puede alterarse la programación y los estados.

## Revisión del PR #2

No se solicita revisión ni se cambia el PR en esta fase por instrucción del usuario. En la última lectura remota figuraba abierto y draft=false, aunque el estado autorizado es borrador. Pendiente devolverlo a borrador e identificar los usuarios revisores antes de actuar. No realizar merge ni habilitar auto-merge.
