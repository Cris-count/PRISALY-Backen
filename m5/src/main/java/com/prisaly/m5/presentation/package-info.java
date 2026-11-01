/**
 * Capa de presentación de M5.
 *
 * <p>Por ahora contiene solo la traducción centralizada de errores a HTTP
 * ({@link com.prisaly.m5.presentation.TraductorErroresM5} y
 * {@link com.prisaly.m5.presentation.ManejadorErroresM5}). No hay
 * controladores: las operaciones de estudiante requieren ARQ-008.3, la
 * decisión DD-02 (identidad del estudiante en M1) y un contrato de
 * titularidad con M4. Cuando existan, los controladores deberán delegar en
 * {@code FachadaParticipacionTransaccional}, verificar identidad y
 * titularidad antes de cargar el intento y no exponer el dominio ni las
 * entidades JPA. Detalle en {@code m5/docs/M5-REST-DEPENDENCIAS-Y-DECISIONES.md}.
 */
package com.prisaly.m5.presentation;
