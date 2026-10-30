/**
 * Capa de aplicación de M5 (Participación, Intentos y Respuestas).
 *
 * <h2>Contenido</h2>
 * <ul>
 *   <li>{@link com.prisaly.m5.application.ParticipacionFacade}: patrón Facade,
 *       punto de entrada para futuros controladores REST.</li>
 *   <li>{@code servicio}: un servicio por caso de uso (consultar, registrar
 *       respuesta, actualizar respuesta —provisional— y finalizar).</li>
 *   <li>{@code port}: {@link com.prisaly.m5.application.port.RepositorioIntentos},
 *       sin implementación de producción todavía.</li>
 * </ul>
 * Las reglas académicas viven en el dominio; los servicios coordinan carga,
 * operación de dominio y guardado. Una operación rechazada por el dominio no
 * solicita guardado. Esta capa no depende de JPA, Spring, HTTP ni de otros
 * módulos.
 *
 * <h2>Operaciones bloqueadas</h2>
 * <ul>
 *   <li><b>Iniciar intento</b>: requiere identidad del estudiante (M1),
 *       convocatoria válida y ventana de sesión (M4), número de intento y
 *       participación (BD-005.2) y, para repeticiones, la autorización
 *       (DD-14). No se implementa un inicio sin esas verificaciones.</li>
 *   <li><b>Reanudar intento</b>: requiere los mismos contratos y el límite
 *       temporal de la sesión.</li>
 *   <li><b>Consultar sesiones autorizadas</b>: pertenece a M4.</li>
 *   <li><b>Aviso a M6</b> de un intento finalizado: pendiente de su contrato;
 *       deberá emitirse después de persistir.</li>
 * </ul>
 *
 * <h2>Chain of Responsibility (aplazado)</h2>
 * Las validaciones previstas para iniciar una participación (identidad,
 * convocatoria, ventana de sesión, intentos existentes, autorización de
 * repetición, disponibilidad temporal) dependen todas de M1, M4, BD-005.2 o
 * DD-14. Una cadena sin esos servicios solo podría aprobar o rechazar por
 * defecto, así que el patrón se incorporará junto con el caso de uso de
 * inicio. Requisito para entonces: validar no debe consumir la autorización
 * de repetición; en la rama de M1, el consumo ocurre en PostgreSQL al
 * insertar el intento (V007), en la misma transacción.
 *
 * <h2>Transacciones y concurrencia</h2>
 * Esta capa no abre transacciones. En infraestructura,
 * {@code FachadaParticipacionTransaccional} ejecuta cada caso de uso en una
 * transacción y el adaptador JPA bloquea la fila del intento al cargarla
 * ({@code SELECT ... FOR UPDATE}), lo que evita actualizaciones perdidas
 * entre operaciones simultáneas sobre el mismo intento. Detalle y
 * limitaciones en {@link com.prisaly.m5.infrastructure}.
 */
package com.prisaly.m5.application;
