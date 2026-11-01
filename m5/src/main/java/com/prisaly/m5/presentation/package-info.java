/**
 * Capa de presentación de M5.
 *
 * <p>Por ahora contiene solo la traducción centralizada de errores a HTTP
 * ({@link com.prisaly.m5.presentation.TraductorErroresM5} y
 * {@link com.prisaly.m5.presentation.ManejadorErroresM5}). No hay
 * controladores: las operaciones de estudiante requieren ARQ-008.3, la
 * decisión DD-02 (identidad del estudiante en M1). Cuando existan, los
 * controladores deberán depender de
 * {@code com.prisaly.m5.application.OperacionesParticipacion}: el único bean de
 * ese tipo es el proxy {@code AccesoProtegidoParticipacion}, que verifica
 * identidad y titularidad antes de cargar el intento. Nunca de servicios,
 * puertos, repositorios ni infraestructura de M5 (lo comprueba
 * {@code ArquitecturaAccesoProtegidoTest}). Tampoco deben exponer el dominio
 * ni las entidades JPA. Detalle en {@code m5/docs/M5-REST-DEPENDENCIAS-Y-DECISIONES.md}.
 */
package com.prisaly.m5.presentation;
