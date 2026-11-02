/**
 * Capa de aplicación de M5 (Participación, Intentos y Respuestas).
 *
 * <h2>Contenido</h2>
 * <ul>
 *   <li>{@link com.prisaly.m5.application.ParticipacionFacade}: patrón Facade,
 *       punto de entrada para futuros controladores REST.</li>
 *   <li>{@code servicio}: un servicio por caso de uso (iniciar o reanudar,
 *       consultar, registrar respuesta, actualizar respuesta —provisional— y
 *       finalizar).</li>
 *   <li>{@code port}: {@link com.prisaly.m5.application.port.RepositorioIntentos}
 *       y {@link com.prisaly.m5.application.port.RepositorioParticipaciones}.</li>
 *   <li>{@code inicio}: puertos de las decisiones académicas pendientes del
 *       primer inicio (elegibilidad y límite temporal) y sus implementaciones
 *       de producción, que nunca autorizan.</li>
 * </ul>
 * Las reglas académicas viven en el dominio; los servicios coordinan carga,
 * operación de dominio y guardado. Una operación rechazada por el dominio no
 * solicita guardado. Esta capa no depende de JPA, Spring, HTTP ni de otros
 * módulos.
 *
 * <h2>Iniciar y reanudar</h2>
 * <ul>
 *   <li><b>Reanudar</b> (disponible): devuelve el intento {@code EnCurso} de la
 *       convocatoria del estudiante autenticado, sin crear otro ni modificar
 *       su inicio o su límite.</li>
 *   <li><b>Primer inicio</b> (mecanismo implementado, bloqueado en producción):
 *       crea la participación y el intento 1 en una transacción, solo si la
 *       elegibilidad y el límite temporal están ratificados. Hoy no lo están y
 *       la operación falla de forma segura.</li>
 *   <li><b>Repetición</b> (DD-14): no habilitada.</li>
 *   <li><b>Consultar sesiones autorizadas</b>: pertenece a M4.</li>
 *   <li><b>Aviso a M6</b> de un intento finalizado: pendiente de su contrato;
 *       deberá emitirse después de persistir.</li>
 * </ul>
 *
 * <h2>Chain of Responsibility (aplazado)</h2>
 * Hoy el inicio depende de una sola verificación de acceso (identidad y
 * titularidad) y de dos decisiones pendientes; las demás reglas (unicidad,
 * tiempos, repetición) las aplica PostgreSQL. Una cadena con eslabones sin
 * reglas ratificadas sería ornamental, así que el patrón se incorporará cuando
 * existan varias validaciones independientes aprobadas. Requisito para
 * entonces: validar no debe consumir la autorización de repetición; el consumo
 * ocurre en PostgreSQL al insertar el intento (V007), en la misma transacción.
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
