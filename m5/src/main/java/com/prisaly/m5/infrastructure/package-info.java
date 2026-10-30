/**
 * Capa de infraestructura de M5: persistencia PostgreSQL (BD-005.2) y
 * ensamblado Spring de los casos de uso.
 *
 * <h2>Persistencia ({@code persistencia})</h2>
 * <ul>
 *   <li>Entidades JPA sobre tablas existentes de V001–V009, sin crear
 *       tablas ni columnas: {@code "Intento"} (solo estado, motivo y fin) y
 *       {@code "Respuesta"}.</li>
 *   <li>{@code RepositorioIntentosJpa} implementa el puerto de aplicación;
 *       {@code MapeoIntento} traduce dominio ↔ filas sin depender de JPA.</li>
 *   <li>Los intentos no se insertan desde M5 hasta que exista el caso de uso
 *       de inicio (bloqueado por M1, M4 y DD-14).</li>
 * </ul>
 *
 * <h2>Transacciones y concurrencia</h2>
 * <ul>
 *   <li>{@link com.prisaly.m5.infrastructure.FachadaParticipacionTransaccional}
 *       abre una transacción por caso de uso; el adaptador exige que exista
 *       (propagación MANDATORY). Cualquier excepción revierte la operación
 *       completa.</li>
 *   <li>Bloqueo pesimista de la fila de {@code "Intento"} al cargar en
 *       transacciones de escritura: serializa, por ejemplo, el registro de
 *       una respuesta y la finalización por tiempo del mismo intento. No hay
 *       columna de versión para un control optimista.</li>
 *   <li>La base complementa con: una respuesta por (intento, pregunta), FK
 *       compuesta opción–pregunta, pregunta del caso de la participación,
 *       único intento EnCurso por participación y prohibición de reabrir.
 *       La base NO impide escribir respuestas en un intento Finalizado: esa
 *       regla la aplica el dominio bajo el bloqueo.</li>
 *   <li>Limitaciones: el bloqueo mantiene la fila durante toda la operación
 *       (que es breve); un intento muy disputado espera en lugar de fallar.
 *       No hay tiempo máximo de espera configurado.</li>
 * </ul>
 *
 * <h2>Seguridad pendiente (M1 / DD-02)</h2>
 * Ningún endpoint de estudiante debe exponer estas operaciones todavía. Antes
 * se necesita: (1) la identidad del estudiante autenticado (DD-02; el
 * {@code IdentityAccessPort} actual de M1 cubre al personal) y (2) un
 * contrato para conocer el titular del intento (la cadena
 * Intento → Participacion → Convocatoria."idEstudiante" atraviesa tablas de
 * M4). Con ambos, la verificación debe ocurrir antes de cargar el intento y
 * fallar de forma cerrada si falta la identidad.
 */
package com.prisaly.m5.infrastructure;
